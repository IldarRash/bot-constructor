import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { applyNodeChanges, applyEdgeChanges, addEdge } from '@xyflow/react';
import { connectBoard } from '../collab/rsocketClient';

// Random per-tab identity so a user opening two tabs is two collaborators, and so we can ignore
// the echoes of our own fan-out events (server reflects every edit to all subscribers).
function makeClientId() {
  return `c_${Math.random().toString(36).slice(2)}_${Date.now().toString(36)}`;
}

const DRAG_DEBOUNCE_MS = 100;

/**
 * Wires @xyflow editor state to the RSocket board stream.
 *
 * Inbound: applies remote NODES_CHANGE/EDGES_CHANGE/NODE_ADD/REMOVE/EDGE_ADD/REMOVE and tracks
 * presence (ROSTER/PRESENCE_JOIN/LEAVE) + remote CURSOR positions.
 * Outbound: returns wrapped onNodesChange/onEdgesChange/onConnect that update local state AND emit
 * edit events (position drags are debounced). Also exposes sendCursor(x, y).
 *
 * Resilient: if the connection fails everything still mutates local state, collaboration just goes
 * quiet. The returned handlers are always safe to use as the ReactFlow callbacks.
 *
 * @param {string|null} boardId            null/undefined => collaboration disabled (e.g. new bot)
 * @param {object} deps { setNodes, setEdges, userName }
 */
export function useBoardCollaboration(boardId, { setNodes, setEdges, userName }) {
  const clientId = useMemo(makeClientId, []);
  const [status, setStatus] = useState('offline'); // 'connecting' | 'online' | 'offline'
  const [peers, setPeers] = useState([]); // [{ id, name }] excluding self
  const [cursors, setCursors] = useState({}); // { [senderId]: { x, y, name } }

  const connRef = useRef(null);
  const dragTimerRef = useRef(null);
  const pendingNodesRef = useRef(null);

  const enabled = Boolean(boardId);

  // Stable event sender that no-ops until/unless connected.
  const emit = useCallback(
    (type, payload) => {
      const conn = connRef.current;
      if (!conn) return;
      conn.sendEdit({ type, senderId: clientId, senderName: userName || 'Anonymous', payload });
    },
    [clientId, userName],
  );

  // --- Inbound event handling -------------------------------------------------------------------
  const handleEvent = useCallback(
    (event) => {
      if (!event || event.senderId === clientId) return; // ignore our own echoes
      const { type, payload, senderId, senderName } = event;

      switch (type) {
        case 'NODES_CHANGE':
          if (Array.isArray(payload)) setNodes((nds) => applyNodeChanges(payload, nds));
          break;
        case 'NODE_ADD':
          if (payload)
            setNodes((nds) => (nds.some((n) => n.id === payload.id) ? nds : nds.concat(payload)));
          break;
        case 'NODE_REMOVE':
          if (payload && payload.id) setNodes((nds) => nds.filter((n) => n.id !== payload.id));
          break;
        case 'EDGES_CHANGE':
          if (Array.isArray(payload)) setEdges((eds) => applyEdgeChanges(payload, eds));
          break;
        case 'EDGE_ADD':
          if (payload)
            setEdges((eds) => (eds.some((e) => e.id === payload.id) ? eds : addEdge(payload, eds)));
          break;
        case 'EDGE_REMOVE':
          if (payload && payload.id) setEdges((eds) => eds.filter((e) => e.id !== payload.id));
          break;
        case 'ROSTER':
          if (Array.isArray(payload))
            setPeers(payload.filter((p) => p && p.id && p.id !== clientId));
          break;
        case 'PRESENCE_JOIN':
          setPeers((prev) =>
            prev.some((p) => p.id === senderId) || senderId === clientId
              ? prev
              : prev.concat({ id: senderId, name: senderName }),
          );
          break;
        case 'PRESENCE_LEAVE':
          setPeers((prev) => prev.filter((p) => p.id !== senderId));
          setCursors((prev) => {
            const next = { ...prev };
            delete next[senderId];
            return next;
          });
          break;
        case 'CURSOR':
          if (payload)
            setCursors((prev) => ({
              ...prev,
              [senderId]: { x: payload.x, y: payload.y, name: senderName },
            }));
          break;
        default:
          break;
      }
    },
    [clientId, setNodes, setEdges],
  );

  // Keep the latest handler in a ref so the effect below need not re-run (and re-connect) on every
  // render-time identity change.
  const handlerRef = useRef(handleEvent);
  handlerRef.current = handleEvent;

  // --- Connect / disconnect ---------------------------------------------------------------------
  useEffect(() => {
    if (!enabled) {
      setStatus('offline');
      return undefined;
    }
    let active = true;
    let conn = null;

    connectBoard(boardId, {
      onEvent: (e) => handlerRef.current(e),
      onStatus: (s) => active && setStatus(s),
      clientId,
    })
      .then((c) => {
        if (!active) {
          c.close();
          return;
        }
        conn = c;
        connRef.current = c;
      })
      .catch(() => {
        // Connection failed — stay standalone. onStatus already reported 'offline'.
        if (active) setStatus('offline');
      });

    return () => {
      active = false;
      connRef.current = null;
      setPeers([]);
      setCursors({});
      if (conn) conn.close();
    };
  }, [boardId, enabled]);

  // --- Outbound wrapped handlers ----------------------------------------------------------------
  const onNodesChange = useCallback(
    (changes) => {
      setNodes((nds) => applyNodeChanges(changes, nds));

      const onlyDragging = changes.every((c) => c.type === 'position');
      if (onlyDragging) {
        // Debounce the stream of position updates a single drag produces.
        pendingNodesRef.current = changes;
        if (dragTimerRef.current) clearTimeout(dragTimerRef.current);
        dragTimerRef.current = setTimeout(() => {
          if (pendingNodesRef.current) emit('NODES_CHANGE', pendingNodesRef.current);
          pendingNodesRef.current = null;
        }, DRAG_DEBOUNCE_MS);
      } else {
        emit('NODES_CHANGE', changes);
      }
    },
    [setNodes, emit],
  );

  const onEdgesChange = useCallback(
    (changes) => {
      setEdges((eds) => applyEdgeChanges(changes, eds));
      emit('EDGES_CHANGE', changes);
    },
    [setEdges, emit],
  );

  const onConnect = useCallback(
    (params) => {
      setEdges((eds) => {
        const next = addEdge(params, eds);
        // Emit the freshly created edge (the last one) so peers add the same instance.
        const added = next[next.length - 1];
        if (added) emit('EDGE_ADD', added);
        return next;
      });
    },
    [setEdges, emit],
  );

  // Explicit add/remove helpers so the editor can broadcast node lifecycle precisely.
  const broadcastNodeAdd = useCallback((node) => emit('NODE_ADD', node), [emit]);
  const broadcastNodeRemove = useCallback((id) => emit('NODE_REMOVE', { id }), [emit]);
  const broadcastNodesChange = useCallback((changes) => emit('NODES_CHANGE', changes), [emit]);

  const cursorTimerRef = useRef(0);
  const sendCursor = useCallback(
    (x, y) => {
      // Throttle cursor traffic to ~20/s.
      const now = Date.now();
      if (now - cursorTimerRef.current < 50) return;
      cursorTimerRef.current = now;
      emit('CURSOR', { x, y });
    },
    [emit],
  );

  return {
    clientId,
    status,
    peers,
    cursors,
    onNodesChange,
    onEdgesChange,
    onConnect,
    broadcastNodeAdd,
    broadcastNodeRemove,
    broadcastNodesChange,
    sendCursor,
  };
}
