import React, { useCallback, useEffect, useRef, useState } from 'react';
import { listExecutions, getExecution } from '../api/executions';
import { ApiError } from '../api/client';
import './ExecutionsPanel.css';

const PAGE = 20;

function formatTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString();
}

// What a single node saw (inputItems) and emitted (outputs, per handle) for one execution.
// Rendered in the editor's right inspector when an execution is active and a node is selected.
// `node` is the matching ExecutionNodeView (by nodeId), or null if this node was never executed.
export function ExecutionNodeInspect({ exec, node }) {
  if (!node) {
    return (
      <p className="exec-inspect__empty">
        This node has no recorded data for the selected execution.
      </p>
    );
  }

  const inputs = Array.isArray(node.inputItems) ? node.inputItems : [];
  const outputs = node.outputs && typeof node.outputs === 'object' ? node.outputs : {};
  const outputHandles = Object.keys(outputs);

  return (
    <div className="exec-inspect">
      <div className="exec-inspect__exec">
        execution {exec.id} · {node.type}
        {node.handle ? ` → ${node.handle}` : ''}
      </div>

      {node.error && <div className="exec-inspect__error">error: {node.error}</div>}

      <div className="exec-inspect__section-head">
        input ({inputs.length} {inputs.length === 1 ? 'item' : 'items'})
      </div>
      {inputs.length === 0 ? (
        <p className="exec-inspect__empty">No input items.</p>
      ) : (
        <pre className="exec-inspect__json">{JSON.stringify(inputs, null, 2)}</pre>
      )}

      <div className="exec-inspect__section-head">output</div>
      {outputHandles.length === 0 ? (
        <p className="exec-inspect__empty">No output items.</p>
      ) : (
        outputHandles.map((handle) => (
          <div key={handle}>
            <div className="exec-inspect__handle">{handle}</div>
            <pre className="exec-inspect__json">{JSON.stringify(outputs[handle], null, 2)}</pre>
          </div>
        ))
      )}
    </div>
  );
}

// One row in the executions list. Clicking it loads the full execution into inspect mode.
function ExecutionRow({ exec, active, onSelect }) {
  return (
    <button
      type="button"
      className={`exec-row${active ? ' is-active' : ''}`}
      onClick={() => onSelect(exec)}
    >
      <div className="exec-row__top">
        <span className={`exec-badge exec-badge--${exec.status === 'success' ? 'ok' : 'err'}`}>
          {exec.status}
        </span>
        <span className="exec-row__trigger mono">{exec.trigger}</span>
        <span className="exec-row__time mono">{formatTime(exec.startedAt)}</span>
      </div>
      <div className="exec-row__reply">{exec.reply || exec.message || '(no reply)'}</div>
      <div className="exec-row__meta mono">
        {exec.nodeCount} {exec.nodeCount === 1 ? 'node' : 'nodes'}
      </div>
    </button>
  );
}

// Slide-in history panel for a saved bot. Lists this bot's executions newest-first with simple
// load-more paging. Selecting an execution loads the FULL ExecutionView and lifts it via
// onInspect so the editor can map canvas node clicks to recorded node input/output items.
export default function ExecutionsPanel({ botId, open, onClose, activeExecId, onInspect }) {
  const [items, setItems] = useState([]);
  const [offset, setOffset] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const loadedRef = useRef(false);

  const load = useCallback(
    async (nextOffset) => {
      if (!botId) return;
      setLoading(true);
      setError(null);
      try {
        const page = await listExecutions(botId, { limit: PAGE, offset: nextOffset });
        const list = Array.isArray(page) ? page : [];
        setItems((prev) => (nextOffset === 0 ? list : prev.concat(list)));
        setOffset(nextOffset + list.length);
        setHasMore(list.length === PAGE);
      } catch (err) {
        const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to load executions.';
        setError(msg);
      } finally {
        setLoading(false);
      }
    },
    [botId],
  );

  // Load the first page once when the panel opens.
  useEffect(() => {
    if (!open || loadedRef.current || !botId) return;
    loadedRef.current = true;
    load(0);
  }, [open, botId, load]);

  // Reset when closed so reopening starts fresh.
  useEffect(() => {
    if (open) return;
    loadedRef.current = false;
    setItems([]);
    setOffset(0);
    setHasMore(true);
    setError(null);
    onInspect(null);
  }, [open, onInspect]);

  async function selectExecution(summary) {
    if (summary.id === activeExecId) {
      onInspect(null); // toggle off
      return;
    }
    setError(null);
    try {
      const full = await getExecution(summary.id);
      onInspect(full);
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to load execution.';
      setError(msg);
    }
  }

  if (!open) return null;

  return (
    <aside className="exec-panel">
      <header className="exec-panel__head">
        <h3>Executions</h3>
        <button className="exec-panel__close" onClick={onClose} aria-label="Close executions panel">
          ×
        </button>
      </header>

      {activeExecId && (
        <div className="exec-panel__hint">
          Inspecting an execution — click a node on the canvas to see what it saw and emitted.
        </div>
      )}

      <div className="exec-panel__list">
        {items.length === 0 && !loading && !error && (
          <p className="exec-panel__empty">No executions yet. Run the bot to record one.</p>
        )}

        {items.map((exec) => (
          <ExecutionRow
            key={exec.id}
            exec={exec}
            active={exec.id === activeExecId}
            onSelect={selectExecution}
          />
        ))}

        {error && <div className="exec-panel__error">{error}</div>}

        {hasMore && items.length > 0 && (
          <button
            type="button"
            className="btn btn-secondary exec-panel__more"
            onClick={() => load(offset)}
            disabled={loading}
          >
            {loading ? 'Loading…' : 'Load more'}
          </button>
        )}

        {loading && items.length === 0 && <p className="exec-panel__empty">Loading…</p>}
      </div>
    </aside>
  );
}
