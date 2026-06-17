import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ReactFlow,
  ReactFlowProvider,
  Controls,
  Background,
  BackgroundVariant,
  MiniMap,
  useNodesState,
  useEdgesState,
  useReactFlow,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import TriggerNode from '../components/nodes/TriggerNode';
import KeywordNode from '../components/nodes/KeywordNode';
import SendMessageNode from '../components/nodes/SendMessageNode';
import ConditionNode from '../components/nodes/ConditionNode';
import SetVariableNode from '../components/nodes/SetVariableNode';
import HttpRequestNode from '../components/nodes/HttpRequestNode';
import TelegramNode from '../components/nodes/TelegramNode';
import ClaudeAnthropicNode from '../components/nodes/ClaudeAnthropicNode';
import SlackNode from '../components/nodes/SlackNode';
import DiscordNode from '../components/nodes/DiscordNode';
import CodeNode from '../components/nodes/CodeNode';
import IFNode from '../components/nodes/IFNode';
import SwitchNode from '../components/nodes/SwitchNode';
import FilterNode from '../components/nodes/FilterNode';
import SetFieldsNode from '../components/nodes/SetFieldsNode';
import SplitOutNode from '../components/nodes/SplitOutNode';
import NoOpNode from '../components/nodes/NoOpNode';
import WaitNode from '../components/nodes/WaitNode';
import MergeNode from '../components/nodes/MergeNode';
import LoopNode from '../components/nodes/LoopNode';
import PresenceLayer from '../components/PresenceLayer';
import TestBotPanel from '../components/TestBotPanel';
import ExecutionsPanel, { ExecutionNodeInspect } from '../components/ExecutionsPanel';
import CredentialsPanel from '../components/CredentialsPanel';
import CredentialSelect from '../components/CredentialSelect';
import { NODE_CREDENTIAL_TYPES } from '../credentials/credentialTypes';
import { getBot, createBot, updateBot } from '../api/bots';
import { executeWorkflow } from '../api/runtime';
import { listCredentials } from '../api/credentials';
import { ApiError, getApiBaseUrl } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { useBoardCollaboration } from '../hooks/useBoardCollaboration';
import { makeTriggerNode, makeNode, toContractNode, toContractEdge } from './flowGraph';
import './BotEditor.css';

const BOT_TYPES = ['Instagram', 'Vkontakte', 'Telegram'];

// Typed workflow nodes (see docs/workflow-engine.md). Handle ids on branch nodes are
// the contract sourceHandle strings (keyword: "match"/"nomatch", condition: "true"/"false").
const nodeTypes = {
  trigger: TriggerNode,
  keyword: KeywordNode,
  sendMessage: SendMessageNode,
  condition: ConditionNode,
  setVariable: SetVariableNode,
  httpRequest: HttpRequestNode,
  telegramSend: TelegramNode,
  anthropicMessage: ClaudeAnthropicNode,
  slackSend: SlackNode,
  discordSend: DiscordNode,
  code: CodeNode,
  // Library nodes (Phase C): flow-control / data-shaping building blocks. Handle ids on branch
  // nodes are the contract sourceHandle strings (if: "true"/"false"; switch: "0"/"1"/default-null).
  if: IFNode,
  switch: SwitchNode,
  filter: FilterNode,
  set: SetFieldsNode,
  splitOut: SplitOutNode,
  noop: NoOpNode,
  wait: WaitNode,
  // Merge gathers several converging inputs into one default output; loop (Split-in-Batches)
  // emits batches on "loop" and the full items on "done" — those handle ids are the engine's
  // output bucket keys.
  merge: MergeNode,
  loop: LoopNode,
};

// Headers editor (de)serialization: the inspector textarea holds one `Key: Value` per line;
// these convert between that text and the `headers` object stored on node data.
function serializeHeaders(headers) {
  if (!headers || typeof headers !== 'object') return '';
  return Object.entries(headers)
    .map(([k, v]) => `${k}: ${v}`)
    .join('\n');
}

function parseHeaders(text) {
  const headers = {};
  text.split('\n').forEach((line) => {
    const idx = line.indexOf(':');
    if (idx === -1) return;
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim();
    if (key) headers[key] = value;
  });
  return headers;
}

function EditorInner() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const toast = useToast();
  const isNew = !id;

  const [name, setName] = useState('');
  const [type, setType] = useState(BOT_TYPES[0]);
  const [fallbackAnswer, setFallbackAnswer] = useState('');
  const [schedule, setSchedule] = useState('');
  const [webhookToken, setWebhookToken] = useState('');

  // New bots seed a single trigger node.
  const [nodes, setNodes] = useNodesState(isNew ? [makeTriggerNode()] : []);
  const [edges, setEdges] = useEdgesState([]);
  const [selectedId, setSelectedId] = useState(null);

  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState([]);
  const [testOpen, setTestOpen] = useState(false);
  const [execOpen, setExecOpen] = useState(false);
  const [credOpen, setCredOpen] = useState(false);
  // Owner's credentials (metadata only) for the inspector selectors. Loaded once and refreshed
  // when the management panel reports a create/delete.
  const [credentials, setCredentials] = useState([]);
  // The full ExecutionView currently being inspected, or null. When set, clicking a canvas node
  // shows that node's recorded input/output for this execution instead of its config editor.
  const [inspectExecution, setInspectExecution] = useState(null);

  // Manual "Run" state. `running` guards the toolbar button; `runResult` holds the last
  // ManualRunResponse (also fed to inspect mode as an execution-like object). `runMessage` is the
  // optional test message seeding the run. `pinnedData` maps nodeId -> [{ json }] of a node's
  // captured default output; pinned nodes are skipped (not re-executed) on the next Run.
  const [running, setRunning] = useState(false);
  const [runResult, setRunResult] = useState(null);
  const [runMessage, setRunMessage] = useState('');
  const [pinnedData, setPinnedData] = useState({});

  // Keyword editing uses a raw comma-separated string so commas can be typed freely.
  const [keywordsText, setKeywordsText] = useState('');

  const loadedRef = useRef(false);
  const { screenToFlowPosition } = useReactFlow();

  const userName = user?.username || user?.email || 'Anonymous';

  // Collaboration is keyed by the bot id; a brand-new (unsaved) bot has no board yet.
  const collab = useBoardCollaboration(id || null, { setNodes, setEdges, userName });
  const {
    status,
    peers,
    cursors,
    onNodesChange,
    onEdgesChange,
    onConnect,
    broadcastNodeAdd,
    broadcastNodeRemove,
    broadcastEdgeRemove,
    broadcastNodesChange,
    sendCursor,
  } = collab;

  // Load an existing bot's graph into the canvas. The backend returns {nodes, edges}
  // (legacy questions-based bots are converted to a graph on read).
  useEffect(() => {
    if (isNew || loadedRef.current) return;
    loadedRef.current = true;
    (async () => {
      try {
        const bot = await getBot(id);
        setName(bot.name || '');
        setType(BOT_TYPES.includes(bot.type) ? bot.type : BOT_TYPES[0]);
        setFallbackAnswer(bot.fallbackAnswer || '');
        setSchedule(bot.schedule || '');
        setWebhookToken(bot.webhookToken || '');
        setNodes(Array.isArray(bot.nodes) ? bot.nodes : []);
        setEdges(Array.isArray(bot.edges) ? bot.edges : []);
      } catch (err) {
        const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to load bot.';
        setErrors([msg]);
      } finally {
        setLoading(false);
      }
    })();
  }, [id, isNew, setNodes, setEdges]);

  // Load the owner's credentials once for the inspector selectors (metadata only — no secrets).
  // Silent on failure: the selectors simply show only "None (inline)".
  const loadCredentials = useCallback(async () => {
    try {
      const list = await listCredentials();
      setCredentials(Array.isArray(list) ? list : []);
    } catch {
      /* ignore — inline fields remain available */
    }
  }, []);

  useEffect(() => {
    loadCredentials();
  }, [loadCredentials]);

  const onSelectionChange = useCallback(({ nodes: selected }) => {
    const node = selected && selected[0];
    setSelectedId(node ? node.id : null);
    setKeywordsText(node ? (node.data?.keyWords || []).join(', ') : '');
  }, []);

  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedId) || null,
    [nodes, selectedId],
  );

  // Decorate pinned nodes with a class so the canvas shows a visible "pinned" badge without
  // mutating the persisted graph data. Pinned nodes are skipped (not re-executed) on the next Run.
  const displayNodes = useMemo(
    () =>
      nodes.map((n) =>
        pinnedData[n.id]
          ? { ...n, className: `${n.className || ''} node--pinned`.trim() }
          : n,
      ),
    [nodes, pinnedData],
  );

  const isSelectedPinned = Boolean(selectedId && pinnedData[selectedId]);

  // True when the selected node has a credential bound (data.credentialId set). The connector /
  // httpRequest inspectors hide their inline secret field(s) while this holds.
  const hasBoundCredential = useMemo(() => {
    if (!selectedNode || !NODE_CREDENTIAL_TYPES[selectedNode.type]) return false;
    return Boolean(selectedNode.data?.credentialId);
  }, [selectedNode]);

  // While inspecting an execution, map the selected React Flow node id to the recorded
  // execution node (matched by nodeId). null when the node was not part of that run.
  const inspectNode = useMemo(() => {
    if (!inspectExecution || !selectedId) return null;
    const recorded = Array.isArray(inspectExecution.nodes) ? inspectExecution.nodes : [];
    return recorded.find((n) => n.nodeId === selectedId) || null;
  }, [inspectExecution, selectedId]);

  // Absolute webhook URL for invoking the bot externally. Built from the gateway base
  // (shared with the API client) so we never hardcode a host. Empty until the bot is saved.
  const webhookUrl = useMemo(
    () => (webhookToken ? `${getApiBaseUrl()}/api/runtime/webhooks/${webhookToken}` : ''),
    [webhookToken],
  );

  const copyWebhookUrl = useCallback(async () => {
    if (!webhookUrl) return;
    try {
      await navigator.clipboard.writeText(webhookUrl);
      toast.success('Webhook URL copied!');
    } catch {
      toast.error('Could not copy the URL.');
    }
  }, [webhookUrl, toast]);

  // Mutate one node's data locally and broadcast a "replace" change so peers stay in sync.
  const patchNodeData = useCallback(
    (nodeId, patch) => {
      let updated = null;
      setNodes((nds) =>
        nds.map((n) => {
          if (n.id !== nodeId) return n;
          updated = { ...n, data: { ...n.data, ...patch } };
          return updated;
        }),
      );
      if (updated) broadcastNodesChange([{ type: 'replace', id: nodeId, item: updated }]);
    },
    [setNodes, broadcastNodesChange],
  );

  // Add a typed node to the canvas, offset so it does not stack on the trigger.
  const addNode = useCallback(
    (nodeType, data) => {
      const position = {
        x: 120 + (nodes.length % 3) * 280,
        y: 200 + Math.floor(nodes.length / 3) * 180,
      };
      const node = makeNode(nodeType, position, data);
      setNodes((nds) => nds.concat(node));
      broadcastNodeAdd(node);
      setSelectedId(node.id);
      setKeywordsText('');
    },
    [nodes.length, setNodes, broadcastNodeAdd],
  );

  const addKeyword = () => addNode('keyword', { label: '', keyWords: [] });
  const addSendMessage = () => addNode('sendMessage', { text: '' });
  const addCondition = () => addNode('condition', { left: '', op: 'eq', right: '' });
  const addSetVariable = () => addNode('setVariable', { name: '', value: '' });
  const addHttpRequest = () =>
    addNode('httpRequest', { method: 'GET', url: '', headers: {}, body: '', saveAs: 'http' });
  const addTelegram = () =>
    addNode('telegramSend', { botToken: '', chatId: '', text: '{{message}}', saveAs: 'telegram' });
  const addAnthropic = () =>
    addNode('anthropicMessage', {
      apiKey: '',
      model: 'claude-opus-4-8',
      maxTokens: '1024',
      prompt: '{{message}}',
      saveAs: 'ai',
    });
  const addSlack = () =>
    addNode('slackSend', { webhookUrl: '', text: '{{message}}', saveAs: 'slack' });
  const addDiscord = () =>
    addNode('discordSend', { webhookUrl: '', content: '{{message}}', saveAs: 'discord' });
  const addCode = () =>
    addNode('code', {
      code: '// $items is the array of input items, $vars the variables.\nreturn $items;',
    });
  // Library nodes (Phase C) — default data per docs/workflow-engine.md.
  const addIf = () =>
    addNode('if', {
      left: '{{message}}',
      op: 'contains',
      right: '',
      combine: 'and',
      left2: '',
      op2: 'eq',
      right2: '',
    });
  const addSwitch = () => addNode('switch', { value: '{{message}}', case0: '', case1: '' });
  const addFilter = () => addNode('filter', { left: '{{message}}', op: 'contains', right: '' });
  const addSetFields = () => addNode('set', { assignments: 'greeting=Hi {{message}}' });
  const addSplitOut = () => addNode('splitOut', { field: 'items' });
  const addNoOp = () => addNode('noop', {});
  const addWait = () => addNode('wait', { seconds: '1' });
  const addMerge = () => addNode('merge', { mode: 'append' });
  const addLoop = () => addNode('loop', { batchSize: '1' });

  function updateSelectedLabel(value) {
    if (selectedId) patchNodeData(selectedId, { label: value });
  }

  function updateSelectedText(value) {
    if (selectedId) patchNodeData(selectedId, { text: value });
  }

  function updateSelectedKeywords(value) {
    setKeywordsText(value);
    const keyWords = value
      .split(',')
      .map((k) => k.trim())
      .filter(Boolean);
    if (selectedId) patchNodeData(selectedId, { keyWords });
  }

  function deleteSelected() {
    if (!selectedId) return;
    const removeId = selectedId;
    // Cascade-remove the node's connected edges locally AND broadcast each removal, so peers
    // prune the same edges instead of being left with orphan edges pointing at a gone node.
    setEdges((eds) => {
      const connected = eds.filter((e) => e.source === removeId || e.target === removeId);
      connected.forEach((e) => broadcastEdgeRemove(e.id));
      return eds.filter((e) => e.source !== removeId && e.target !== removeId);
    });
    setNodes((nds) => nds.filter((n) => n.id !== removeId));
    broadcastNodeRemove(removeId);
    setSelectedId(null);
  }

  // Throttled cursor broadcast in flow coordinates so peers map to the same canvas point.
  const onPaneMouseMove = useCallback(
    (e) => {
      if (status !== 'online') return;
      const pos = screenToFlowPosition({ x: e.clientX, y: e.clientY });
      sendCursor(pos.x, pos.y);
    },
    [status, screenToFlowPosition, sendCursor],
  );

  // Persist the current graph. Returns the saved bot id on success (or null on failure) so callers
  // like Run can save-then-act. By default also navigates home; pass { navigateHome: false } to
  // stay in the editor (the Run flow saves in place before executing).
  const persist = useCallback(
    async ({ navigateHome = true } = {}) => {
      setErrors([]);
      const body = {
        name,
        type,
        fallbackAnswer,
        schedule: schedule.trim() || null,
        nodes: nodes.map(toContractNode),
        edges: edges.map(toContractEdge),
      };

      setSaving(true);
      try {
        const saved = isNew ? await createBot(body) : await updateBot(id, body);
        toast.success(isNew ? 'Bot created.' : 'Changes saved.');
        if (navigateHome) navigate('/', { replace: true });
        return saved?.id || id || null;
      } catch (err) {
        const msg = err instanceof ApiError ? err.messages : ['Failed to save bot.'];
        setErrors(msg);
        toast.error(msg.join(' · '));
        return null;
      } finally {
        setSaving(false);
      }
    },
    [name, type, fallbackAnswer, schedule, nodes, edges, isNew, id, navigate, toast],
  );

  const onSave = useCallback(() => persist({ navigateHome: true }), [persist]);

  // Run the saved flow on demand: save in place first (so the engine runs the current graph),
  // then POST /execute with the optional test message and the pinned-data map. On success enter
  // inspect mode over the rich nodes[] — clicking a canvas node shows its run input/output inline.
  const onRun = useCallback(async () => {
    if (isNew) return; // a brand-new bot must be created before it can run
    setRunning(true);
    setErrors([]);
    try {
      const savedId = await persist({ navigateHome: false });
      if (!savedId) return; // save failed — errors already surfaced
      const result = await executeWorkflow(savedId, {
        message: runMessage || undefined,
        pinnedData: Object.keys(pinnedData).length ? pinnedData : undefined,
      });
      setRunResult(result);
      // Project the ManualRunResponse into the execution-like shape ExecutionNodeInspect expects.
      // `status` is the engine's own success/error (error == the 20s timeout fallback), so the
      // inspector badge reflects an errored run instead of always showing green.
      setInspectExecution({
        id: 'manual',
        status: result?.status || 'success',
        nodes: Array.isArray(result?.nodes) ? result.nodes : [],
      });
      toast.success('Run complete.');
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages : ['Failed to run bot.'];
      setErrors(msg);
      toast.error(msg.join(' · '));
    } finally {
      setRunning(false);
    }
  }, [isNew, persist, runMessage, pinnedData, toast]);

  // Capture the selected node's default-output items (from the last run) as pinned data, keyed by
  // nodeId. Subsequent Run calls send these so the node is skipped (no outbound HTTP/connector call).
  const pinSelected = useCallback(() => {
    if (!selectedId || !inspectNode) return;
    const outputs = inspectNode.outputs && typeof inspectNode.outputs === 'object' ? inspectNode.outputs : {};
    const items = outputs.default || outputs[Object.keys(outputs)[0]] || [];
    setPinnedData((prev) => ({ ...prev, [selectedId]: items }));
    toast.success('Output pinned.');
  }, [selectedId, inspectNode, toast]);

  const unpinSelected = useCallback(() => {
    if (!selectedId) return;
    setPinnedData((prev) => {
      const next = { ...prev };
      delete next[selectedId];
      return next;
    });
  }, [selectedId]);

  if (loading) {
    return (
      <div className="editor-page">
        <div className="editor-loading">
          <div className="spinner spinner--lg" />
          <span>Loading bot…</span>
        </div>
      </div>
    );
  }

  return (
    <div className="editor-page">
      <header className="editor-header">
        <div className="editor-header__left">
          <button
            className="btn btn-ghost editor-back"
            onClick={() => navigate('/')}
            aria-label="Back to bots"
          >
            ←
          </button>
          <div className="meta-fields">
            <div className="field">
              <label className="field-label" htmlFor="bot-name">
                Bot name
              </label>
              <input
                id="bot-name"
                className="field-input name-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Support assistant"
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="bot-type">
                Platform
              </label>
              <select
                id="bot-type"
                className="field-select"
                value={type}
                onChange={(e) => setType(e.target.value)}
              >
                {BOT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="editor-actions">
          {!isNew && (
            <div className="run-control">
              <input
                className="field-input run-control__msg"
                value={runMessage}
                onChange={(e) => setRunMessage(e.target.value)}
                placeholder="Test message (optional)"
                aria-label="Test message for run"
              />
              <button
                className="btn btn-primary run-control__btn"
                onClick={onRun}
                disabled={running || saving}
              >
                {running && <span className="spinner" />}
                {running ? 'Running…' : '▶ Run'}
              </button>
            </div>
          )}
          {!isNew && (
            <button className="btn btn-secondary" onClick={() => setTestOpen((v) => !v)}>
              {testOpen ? 'Close test' : '▷ Test bot'}
            </button>
          )}
          {!isNew && (
            <button className="btn btn-secondary" onClick={() => setExecOpen((v) => !v)}>
              {execOpen ? 'Close executions' : '⧗ Executions'}
            </button>
          )}
          <button className="btn btn-secondary" onClick={() => setCredOpen((v) => !v)}>
            {credOpen ? 'Close credentials' : '🔑 Credentials'}
          </button>
          <button className="btn btn-ghost" onClick={() => navigate('/')}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={onSave} disabled={saving}>
            {saving && <span className="spinner" />}
            {saving ? 'Saving…' : isNew ? 'Create bot' : 'Save changes'}
          </button>
        </div>

        {errors.length > 0 && (
          <div className="editor-error full-width" role="alert">
            {errors.join(' · ')}
          </div>
        )}
      </header>

      <div className="editor-main">
        <div className="canvas-wrap" onMouseMove={onPaneMouseMove}>
          <div className="canvas-toolbar">
            <button className="btn btn-primary" onClick={addKeyword}>
              <span aria-hidden="true">+</span> Keyword
            </button>
            <button className="btn btn-secondary" onClick={addSendMessage}>
              <span aria-hidden="true">+</span> Send message
            </button>
            <button className="btn btn-secondary" onClick={addCondition}>
              <span aria-hidden="true">+</span> Condition
            </button>
            <button className="btn btn-secondary" onClick={addSetVariable}>
              <span aria-hidden="true">+</span> Set variable
            </button>
            <button className="btn btn-secondary" onClick={addHttpRequest}>
              <span aria-hidden="true">+</span> HTTP request
            </button>
            <button className="btn btn-secondary" onClick={addTelegram}>
              <span aria-hidden="true">+</span> Telegram
            </button>
            <button className="btn btn-secondary" onClick={addAnthropic}>
              <span aria-hidden="true">+</span> Claude (Anthropic)
            </button>
            <button className="btn btn-secondary" onClick={addSlack}>
              <span aria-hidden="true">+</span> Slack
            </button>
            <button className="btn btn-secondary" onClick={addDiscord}>
              <span aria-hidden="true">+</span> Discord
            </button>
            <button className="btn btn-secondary" onClick={addCode}>
              <span aria-hidden="true">+</span> Code (JS)
            </button>
            <button className="btn btn-secondary" onClick={addIf}>
              <span aria-hidden="true">+</span> If
            </button>
            <button className="btn btn-secondary" onClick={addSwitch}>
              <span aria-hidden="true">+</span> Switch
            </button>
            <button className="btn btn-secondary" onClick={addFilter}>
              <span aria-hidden="true">+</span> Filter
            </button>
            <button className="btn btn-secondary" onClick={addSetFields}>
              <span aria-hidden="true">+</span> Set fields
            </button>
            <button className="btn btn-secondary" onClick={addSplitOut}>
              <span aria-hidden="true">+</span> Split out
            </button>
            <button className="btn btn-secondary" onClick={addNoOp}>
              <span aria-hidden="true">+</span> No-op
            </button>
            <button className="btn btn-secondary" onClick={addWait}>
              <span aria-hidden="true">+</span> Wait
            </button>
            <button className="btn btn-secondary" onClick={addMerge}>
              <span aria-hidden="true">+</span> Merge
            </button>
            <button className="btn btn-secondary" onClick={addLoop}>
              <span aria-hidden="true">+</span> Loop
            </button>
            <span className="canvas-hint mono">{nodes.length} nodes</span>
          </div>
          {!isNew && <PresenceLayer status={status} peers={peers} cursors={cursors} />}
          <ReactFlow
            nodes={displayNodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onSelectionChange={onSelectionChange}
            colorMode="dark"
            proOptions={{ hideAttribution: true }}
            fitView
          >
            <Background variant={BackgroundVariant.Dots} gap={22} size={1.6} color="#2a3344" />
            <Controls />
            <MiniMap
              pannable
              zoomable
              nodeColor="#1d2330"
              nodeStrokeColor="#2dd4bf"
              maskColor="rgba(7, 9, 15, 0.7)"
            />
          </ReactFlow>
        </div>

        {inspectExecution && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Inspect node</h3>
              <span
                className={`badge mono${inspectExecution.status === 'error' ? ' badge--error' : ''}`}
              >
                {inspectExecution.status}
              </span>
            </div>

            {runResult && inspectExecution.id === 'manual' && (
              <div className="run-summary">
                <div className="run-summary__reply">
                  <span className="run-summary__label">reply</span>
                  <span className="run-summary__text">{runResult.reply || '(no reply)'}</span>
                </div>
                <button
                  className="btn btn-ghost run-summary__close"
                  onClick={() => {
                    setInspectExecution(null);
                    setRunResult(null);
                  }}
                >
                  Close run
                </button>
              </div>
            )}

            <p className="hint">
              Showing what each node saw and emitted for the selected execution. Click a node on the
              canvas to inspect it.
            </p>

            {selectedNode ? (
              <>
                {runResult && inspectExecution.id === 'manual' && (
                  <div className="pin-control">
                    {isSelectedPinned ? (
                      <>
                        <span className="badge badge--pin mono">📌 pinned</span>
                        <button className="btn btn-secondary" onClick={unpinSelected}>
                          Unpin output
                        </button>
                      </>
                    ) : (
                      <button
                        className="btn btn-secondary"
                        onClick={pinSelected}
                        disabled={!inspectNode}
                        title={
                          inspectNode
                            ? 'Pin this output so the next Run skips this node'
                            : 'No recorded output to pin'
                        }
                      >
                        📌 Pin output
                      </button>
                    )}
                  </div>
                )}
                <ExecutionNodeInspect exec={inspectExecution} node={inspectNode} />
              </>
            ) : (
              <p className="exec-inspect__empty">Select a node on the canvas to inspect it.</p>
            )}
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'keyword' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Edit keyword</h3>
              <span className="badge mono">keyword</span>
            </div>
            <p className="hint">
              Matches when any keyword appears in the user message. Wire the <code>match</code> and{' '}
              <code>no match</code> outputs to other nodes.
            </p>

            <label className="field-label" htmlFor="k-label">
              Label
            </label>
            <input
              id="k-label"
              className="field-input"
              value={selectedNode.data.label || ''}
              onChange={(e) => updateSelectedLabel(e.target.value)}
              placeholder="greeting"
            />

            <label className="field-label" htmlFor="k-keywords">
              Keywords (comma-separated)
            </label>
            <input
              id="k-keywords"
              className="field-input"
              value={keywordsText}
              onChange={(e) => updateSelectedKeywords(e.target.value)}
              placeholder="hi, hello, hey"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'sendMessage' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Edit message</h3>
              <span className="badge mono">sendMessage</span>
            </div>
            <p className="hint">Text appended to the reply when this node is reached.</p>

            <label className="field-label" htmlFor="m-text">
              Message text
            </label>
            <textarea
              id="m-text"
              className="field-area"
              rows={4}
              value={selectedNode.data.text || ''}
              onChange={(e) => updateSelectedText(e.target.value)}
              placeholder="Hello! How can I help?"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'condition' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Edit condition</h3>
              <span className="badge mono">condition</span>
            </div>
            <p className="hint">
              Evaluates <code>left {selectedNode.data.op || 'eq'} right</code>. Wire the{' '}
              <code>true</code> and <code>false</code> outputs to other nodes. String fields support{' '}
              <code>{'{{variable}}'}</code> expressions.
            </p>

            <label className="field-label" htmlFor="c-left">
              Left
            </label>
            <input
              id="c-left"
              className="field-input"
              value={selectedNode.data.left || ''}
              onChange={(e) => patchNodeData(selectedId, { left: e.target.value })}
              placeholder="{{message}}"
            />

            <label className="field-label" htmlFor="c-op">
              Operator
            </label>
            <select
              id="c-op"
              className="field-select"
              value={selectedNode.data.op || 'eq'}
              onChange={(e) => patchNodeData(selectedId, { op: e.target.value })}
            >
              <option value="eq">eq (=)</option>
              <option value="neq">neq (≠)</option>
              <option value="contains">contains</option>
              <option value="gt">gt (&gt;)</option>
              <option value="lt">lt (&lt;)</option>
            </select>

            <label className="field-label" htmlFor="c-right">
              Right
            </label>
            <input
              id="c-right"
              className="field-input"
              value={selectedNode.data.right || ''}
              onChange={(e) => patchNodeData(selectedId, { right: e.target.value })}
              placeholder="hello"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'setVariable' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Set variable</h3>
              <span className="badge mono">setVariable</span>
            </div>
            <p className="hint">
              Sets <code>vars[name] = value</code> for later nodes. The value supports{' '}
              <code>{'{{variable}}'}</code> expressions.
            </p>

            <label className="field-label" htmlFor="v-name">
              Variable name
            </label>
            <input
              id="v-name"
              className="field-input"
              value={selectedNode.data.name || ''}
              onChange={(e) => patchNodeData(selectedId, { name: e.target.value })}
              placeholder="userName"
            />

            <label className="field-label" htmlFor="v-value">
              Value
            </label>
            <input
              id="v-value"
              className="field-input"
              value={selectedNode.data.value || ''}
              onChange={(e) => patchNodeData(selectedId, { value: e.target.value })}
              placeholder="{{message}}"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'httpRequest' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>HTTP request</h3>
              <span className="badge mono">httpRequest</span>
            </div>
            <p className="hint">
              Calls the URL and stores the parsed response in <code>vars[saveAs]</code>. Wire the{' '}
              <code>success</code> and <code>error</code> outputs to other nodes. The URL, body and
              header values support <code>{'{{variable}}'}</code> expressions; later nodes read the
              response as <code>{'{{saveAs.field}}'}</code>.
            </p>

            <CredentialSelect
              nodeType="httpRequest"
              credentials={credentials}
              value={selectedNode.data.credentialId}
              onChange={(credentialId) => patchNodeData(selectedId, { credentialId })}
            />
            {hasBoundCredential && (
              <p className="hint">
                The bound credential adds its auth header (httpHeaderAuth) or{' '}
                <code>Authorization: Bearer</code> (httpBearerAuth) on top of the headers below.
              </p>
            )}

            <label className="field-label" htmlFor="h-method">
              Method
            </label>
            <select
              id="h-method"
              className="field-select"
              value={selectedNode.data.method || 'GET'}
              onChange={(e) => patchNodeData(selectedId, { method: e.target.value })}
            >
              <option value="GET">GET</option>
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
              <option value="PATCH">PATCH</option>
              <option value="DELETE">DELETE</option>
            </select>

            <label className="field-label" htmlFor="h-url">
              URL
            </label>
            <input
              id="h-url"
              className="field-input"
              value={selectedNode.data.url || ''}
              onChange={(e) => patchNodeData(selectedId, { url: e.target.value })}
              placeholder="https://api.example.com/users/{{user.id}}"
            />

            <label className="field-label" htmlFor="h-saveas">
              Save response as
            </label>
            <input
              id="h-saveas"
              className="field-input"
              value={selectedNode.data.saveAs || ''}
              onChange={(e) => patchNodeData(selectedId, { saveAs: e.target.value })}
              placeholder="http"
            />

            <label className="field-label" htmlFor="h-headers">
              Headers (one <code>Key: Value</code> per line)
            </label>
            <textarea
              id="h-headers"
              className="field-area"
              rows={3}
              value={serializeHeaders(selectedNode.data.headers)}
              onChange={(e) => patchNodeData(selectedId, { headers: parseHeaders(e.target.value) })}
              placeholder={'Authorization: Bearer {{token}}\nContent-Type: application/json'}
            />

            <label className="field-label" htmlFor="h-body">
              Body
            </label>
            <textarea
              id="h-body"
              className="field-area"
              rows={4}
              value={selectedNode.data.body || ''}
              onChange={(e) => patchNodeData(selectedId, { body: e.target.value })}
              placeholder={'{"name": "{{user.name}}"}'}
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'telegramSend' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Telegram</h3>
              <span className="badge mono">telegramSend</span>
            </div>
            <p className="hint">
              Sends a message via the Telegram Bot API and stores the response in{' '}
              <code>vars[saveAs]</code> (read <code>{'{{saveAs.ok}}'}</code> /{' '}
              <code>{'{{saveAs.messageId}}'}</code>). The chat id and text support{' '}
              <code>{'{{variable}}'}</code> expressions. Wire the <code>success</code> and{' '}
              <code>error</code> outputs.
            </p>

            <CredentialSelect
              nodeType="telegramSend"
              credentials={credentials}
              value={selectedNode.data.credentialId}
              onChange={(credentialId) => patchNodeData(selectedId, { credentialId })}
            />

            {!hasBoundCredential && (
              <>
                <label className="field-label" htmlFor="tg-token">
                  Bot token
                </label>
                <input
                  id="tg-token"
                  className="field-input"
                  type="password"
                  value={selectedNode.data.botToken || ''}
                  onChange={(e) => patchNodeData(selectedId, { botToken: e.target.value })}
                  placeholder="123456:ABC-DEF..."
                />
              </>
            )}

            <label className="field-label" htmlFor="tg-chat">
              Chat ID
            </label>
            <input
              id="tg-chat"
              className="field-input"
              value={selectedNode.data.chatId || ''}
              onChange={(e) => patchNodeData(selectedId, { chatId: e.target.value })}
              placeholder="123456789 or @channel"
            />

            <label className="field-label" htmlFor="tg-text">
              Message text
            </label>
            <textarea
              id="tg-text"
              className="field-area"
              rows={4}
              value={selectedNode.data.text || ''}
              onChange={(e) => patchNodeData(selectedId, { text: e.target.value })}
              placeholder="Hello {{message}}"
            />

            <label className="field-label" htmlFor="tg-saveas">
              Save response as
            </label>
            <input
              id="tg-saveas"
              className="field-input"
              value={selectedNode.data.saveAs || ''}
              onChange={(e) => patchNodeData(selectedId, { saveAs: e.target.value })}
              placeholder="telegram"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'anthropicMessage' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Claude (Anthropic)</h3>
              <span className="badge mono">anthropicMessage</span>
            </div>
            <p className="hint">
              Calls the Anthropic Messages API and stores <code>{'{ text, raw }'}</code> in{' '}
              <code>vars[saveAs]</code> (read the reply as <code>{'{{saveAs.text}}'}</code>). The
              prompt supports <code>{'{{variable}}'}</code> expressions. Wire the <code>success</code>{' '}
              and <code>error</code> outputs.
            </p>

            <CredentialSelect
              nodeType="anthropicMessage"
              credentials={credentials}
              value={selectedNode.data.credentialId}
              onChange={(credentialId) => patchNodeData(selectedId, { credentialId })}
            />

            {!hasBoundCredential && (
              <>
                <label className="field-label" htmlFor="an-key">
                  API key
                </label>
                <input
                  id="an-key"
                  className="field-input"
                  type="password"
                  value={selectedNode.data.apiKey || ''}
                  onChange={(e) => patchNodeData(selectedId, { apiKey: e.target.value })}
                  placeholder="sk-ant-..."
                />
              </>
            )}

            <label className="field-label" htmlFor="an-model">
              Model
            </label>
            <select
              id="an-model"
              className="field-select"
              value={selectedNode.data.model || 'claude-opus-4-8'}
              onChange={(e) => patchNodeData(selectedId, { model: e.target.value })}
            >
              <option value="claude-opus-4-8">claude-opus-4-8</option>
              <option value="claude-sonnet-4-6">claude-sonnet-4-6</option>
              <option value="claude-haiku-4-5-20251001">claude-haiku-4-5-20251001</option>
            </select>

            <label className="field-label" htmlFor="an-maxtokens">
              Max tokens
            </label>
            <input
              id="an-maxtokens"
              className="field-input"
              value={selectedNode.data.maxTokens || ''}
              onChange={(e) => patchNodeData(selectedId, { maxTokens: e.target.value })}
              placeholder="1024"
            />

            <label className="field-label" htmlFor="an-prompt">
              Prompt
            </label>
            <textarea
              id="an-prompt"
              className="field-area"
              rows={4}
              value={selectedNode.data.prompt || ''}
              onChange={(e) => patchNodeData(selectedId, { prompt: e.target.value })}
              placeholder="{{message}}"
            />

            <label className="field-label" htmlFor="an-saveas">
              Save as
            </label>
            <input
              id="an-saveas"
              className="field-input"
              value={selectedNode.data.saveAs || ''}
              onChange={(e) => patchNodeData(selectedId, { saveAs: e.target.value })}
              placeholder="ai"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'slackSend' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Slack</h3>
              <span className="badge mono">slackSend</span>
            </div>
            <p className="hint">
              Posts a message to a Slack Incoming Webhook and stores <code>{'{ ok: true }'}</code> in{' '}
              <code>vars[saveAs]</code> on success. The text supports <code>{'{{variable}}'}</code>{' '}
              expressions. Wire the <code>success</code> and <code>error</code> outputs.
            </p>

            <CredentialSelect
              nodeType="slackSend"
              credentials={credentials}
              value={selectedNode.data.credentialId}
              onChange={(credentialId) => patchNodeData(selectedId, { credentialId })}
            />

            {!hasBoundCredential && (
              <>
                <label className="field-label" htmlFor="sl-url">
                  Webhook URL
                </label>
                <input
                  id="sl-url"
                  className="field-input"
                  type="password"
                  value={selectedNode.data.webhookUrl || ''}
                  onChange={(e) => patchNodeData(selectedId, { webhookUrl: e.target.value })}
                  placeholder="https://hooks.slack.com/services/T.../B.../xxxx"
                />
              </>
            )}

            <label className="field-label" htmlFor="sl-text">
              Message text
            </label>
            <textarea
              id="sl-text"
              className="field-area"
              rows={4}
              value={selectedNode.data.text || ''}
              onChange={(e) => patchNodeData(selectedId, { text: e.target.value })}
              placeholder="{{message}}"
            />

            <label className="field-label" htmlFor="sl-saveas">
              Save result as
            </label>
            <input
              id="sl-saveas"
              className="field-input"
              value={selectedNode.data.saveAs || ''}
              onChange={(e) => patchNodeData(selectedId, { saveAs: e.target.value })}
              placeholder="slack"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'discordSend' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Discord</h3>
              <span className="badge mono">discordSend</span>
            </div>
            <p className="hint">
              Posts content to a Discord webhook and stores <code>{'{ ok: true }'}</code> in{' '}
              <code>vars[saveAs]</code> on success. The content supports{' '}
              <code>{'{{variable}}'}</code> expressions. Wire the <code>success</code> and{' '}
              <code>error</code> outputs.
            </p>

            <CredentialSelect
              nodeType="discordSend"
              credentials={credentials}
              value={selectedNode.data.credentialId}
              onChange={(credentialId) => patchNodeData(selectedId, { credentialId })}
            />

            {!hasBoundCredential && (
              <>
                <label className="field-label" htmlFor="dc-url">
                  Webhook URL
                </label>
                <input
                  id="dc-url"
                  className="field-input"
                  type="password"
                  value={selectedNode.data.webhookUrl || ''}
                  onChange={(e) => patchNodeData(selectedId, { webhookUrl: e.target.value })}
                  placeholder="https://discord.com/api/webhooks/..."
                />
              </>
            )}

            <label className="field-label" htmlFor="dc-content">
              Content
            </label>
            <textarea
              id="dc-content"
              className="field-area"
              rows={4}
              value={selectedNode.data.content || ''}
              onChange={(e) => patchNodeData(selectedId, { content: e.target.value })}
              placeholder="{{message}}"
            />

            <label className="field-label" htmlFor="dc-saveas">
              Save as
            </label>
            <input
              id="dc-saveas"
              className="field-input"
              value={selectedNode.data.saveAs || ''}
              onChange={(e) => patchNodeData(selectedId, { saveAs: e.target.value })}
              placeholder="discord"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'code' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Code (JS)</h3>
              <span className="badge mono">code</span>
            </div>
            <p className="hint">
              Runs JavaScript over the input items. <code>$items</code> is the array of input items
              and <code>$vars</code> the variables; <code>return</code> the items to emit. A guest
              error routes the <code>error</code> output.
            </p>

            <label className="field-label" htmlFor="code-js">
              JavaScript
            </label>
            <textarea
              id="code-js"
              className="field-area"
              rows={8}
              value={selectedNode.data.code || ''}
              onChange={(e) => patchNodeData(selectedId, { code: e.target.value })}
              placeholder={'return $items.map(i => ({...i, upper: (i.message||"").toUpperCase()}))'}
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'if' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>If</h3>
              <span className="badge mono">if</span>
            </div>
            <p className="hint">
              Evaluates <code>left op right</code>, optionally combined with a second clause, and wires
              the <code>true</code> and <code>false</code> outputs. String fields support{' '}
              <code>{'{{variable}}'}</code> expressions.
            </p>

            <label className="field-label" htmlFor="if-left">
              Left
            </label>
            <input
              id="if-left"
              className="field-input"
              value={selectedNode.data.left || ''}
              onChange={(e) => patchNodeData(selectedId, { left: e.target.value })}
              placeholder="{{message}}"
            />

            <label className="field-label" htmlFor="if-op">
              Operator
            </label>
            <select
              id="if-op"
              className="field-select"
              value={selectedNode.data.op || 'eq'}
              onChange={(e) => patchNodeData(selectedId, { op: e.target.value })}
            >
              <option value="eq">eq</option>
              <option value="neq">neq</option>
              <option value="contains">contains</option>
              <option value="gt">gt</option>
              <option value="lt">lt</option>
            </select>

            <label className="field-label" htmlFor="if-right">
              Right
            </label>
            <input
              id="if-right"
              className="field-input"
              value={selectedNode.data.right || ''}
              onChange={(e) => patchNodeData(selectedId, { right: e.target.value })}
              placeholder="value or {{expr}}"
            />

            <label className="field-label" htmlFor="if-combine">
              Combine
            </label>
            <select
              id="if-combine"
              className="field-select"
              value={selectedNode.data.combine || 'and'}
              onChange={(e) => patchNodeData(selectedId, { combine: e.target.value })}
            >
              <option value="and">and</option>
              <option value="or">or</option>
            </select>

            <label className="field-label" htmlFor="if-left2">
              Left (2nd)
            </label>
            <input
              id="if-left2"
              className="field-input"
              value={selectedNode.data.left2 || ''}
              onChange={(e) => patchNodeData(selectedId, { left2: e.target.value })}
              placeholder="optional {{expr}}"
            />

            <label className="field-label" htmlFor="if-op2">
              Operator (2nd)
            </label>
            <select
              id="if-op2"
              className="field-select"
              value={selectedNode.data.op2 || 'eq'}
              onChange={(e) => patchNodeData(selectedId, { op2: e.target.value })}
            >
              <option value="eq">eq</option>
              <option value="neq">neq</option>
              <option value="contains">contains</option>
              <option value="gt">gt</option>
              <option value="lt">lt</option>
            </select>

            <label className="field-label" htmlFor="if-right2">
              Right (2nd)
            </label>
            <input
              id="if-right2"
              className="field-input"
              value={selectedNode.data.right2 || ''}
              onChange={(e) => patchNodeData(selectedId, { right2: e.target.value })}
              placeholder="optional value or {{expr}}"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'switch' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Switch</h3>
              <span className="badge mono">switch</span>
            </div>
            <p className="hint">
              Routes by string equality: <code>value</code> matching <code>case 0</code> follows
              output <code>0</code>, <code>case 1</code> follows <code>1</code>, otherwise the default
              output. Fields support <code>{'{{variable}}'}</code> expressions.
            </p>

            <label className="field-label" htmlFor="sw-value">
              Value
            </label>
            <input
              id="sw-value"
              className="field-input"
              value={selectedNode.data.value || ''}
              onChange={(e) => patchNodeData(selectedId, { value: e.target.value })}
              placeholder="{{message}}"
            />

            <label className="field-label" htmlFor="sw-case0">
              Case 0
            </label>
            <input
              id="sw-case0"
              className="field-input"
              value={selectedNode.data.case0 || ''}
              onChange={(e) => patchNodeData(selectedId, { case0: e.target.value })}
              placeholder="value routed to output 0"
            />

            <label className="field-label" htmlFor="sw-case1">
              Case 1
            </label>
            <input
              id="sw-case1"
              className="field-input"
              value={selectedNode.data.case1 || ''}
              onChange={(e) => patchNodeData(selectedId, { case1: e.target.value })}
              placeholder="value routed to output 1"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'filter' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Filter</h3>
              <span className="badge mono">filter</span>
            </div>
            <p className="hint">
              Keeps only items where <code>left op right</code> holds; dropped items are discarded.
              Fields support <code>{'{{variable}}'}</code> expressions.
            </p>

            <label className="field-label" htmlFor="fl-left">
              Left
            </label>
            <input
              id="fl-left"
              className="field-input"
              value={selectedNode.data.left || ''}
              onChange={(e) => patchNodeData(selectedId, { left: e.target.value })}
              placeholder="{{message}}"
            />

            <label className="field-label" htmlFor="fl-op">
              Operator
            </label>
            <select
              id="fl-op"
              className="field-select"
              value={selectedNode.data.op || 'eq'}
              onChange={(e) => patchNodeData(selectedId, { op: e.target.value })}
            >
              <option value="eq">eq</option>
              <option value="neq">neq</option>
              <option value="contains">contains</option>
              <option value="gt">gt</option>
              <option value="lt">lt</option>
            </select>

            <label className="field-label" htmlFor="fl-right">
              Right
            </label>
            <input
              id="fl-right"
              className="field-input"
              value={selectedNode.data.right || ''}
              onChange={(e) => patchNodeData(selectedId, { right: e.target.value })}
              placeholder="value"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'set' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Set fields</h3>
              <span className="badge mono">set</span>
            </div>
            <p className="hint">
              Writes one <code>name=value</code> per line onto each item (and into vars). Values
              support <code>{'{{variable}}'}</code> expressions and can reference fields set on
              earlier lines.
            </p>

            <label className="field-label" htmlFor="set-assignments">
              Assignments
            </label>
            <textarea
              id="set-assignments"
              className="field-area"
              rows={5}
              value={selectedNode.data.assignments || ''}
              onChange={(e) => patchNodeData(selectedId, { assignments: e.target.value })}
              placeholder="one name=value per line, value supports {{expr}}"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'splitOut' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Split out</h3>
              <span className="badge mono">splitOut</span>
            </div>
            <p className="hint">
              Splits the array at <code>field</code> into one item per element (downstream nodes run
              once per element). A non-array value passes the item through unchanged.
            </p>

            <label className="field-label" htmlFor="so-field">
              Field to split out
            </label>
            <input
              id="so-field"
              className="field-input"
              value={selectedNode.data.field || ''}
              onChange={(e) => patchNodeData(selectedId, { field: e.target.value })}
              placeholder="items"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'noop' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>No-op</h3>
              <span className="badge mono">noop</span>
            </div>
            <p className="hint">
              Passes input items straight through on the default output. No configuration — useful as
              a join/label point in the graph.
            </p>

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'wait' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Wait</h3>
              <span className="badge mono">wait</span>
            </div>
            <p className="hint">
              Pauses the walk before passing items through. The delay is interpolated and clamped to
              0–15 seconds server-side. Supports <code>{'{{variable}}'}</code> expressions.
            </p>

            <label className="field-label" htmlFor="wait-seconds">
              Seconds (0–15)
            </label>
            <input
              id="wait-seconds"
              className="field-input"
              value={selectedNode.data.seconds || ''}
              onChange={(e) => patchNodeData(selectedId, { seconds: e.target.value })}
              placeholder="1"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'merge' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Merge</h3>
              <span className="badge mono">merge</span>
            </div>
            <p className="hint">
              Gathers every input edge that converges here (wait-all) and runs once over the full
              inbox, emitting the joined items on the default output. Use it as a fan-in join point.
            </p>

            <label className="field-label" htmlFor="mg-mode">
              Mode
            </label>
            <select
              id="mg-mode"
              className="field-select"
              value={selectedNode.data.mode || 'append'}
              onChange={(e) => patchNodeData(selectedId, { mode: e.target.value })}
            >
              <option value="append">append</option>
            </select>

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'loop' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Loop</h3>
              <span className="badge mono">loop</span>
            </div>
            <p className="hint">
              Split-in-Batches. Emits one batch of <code>batchSize</code> items on the{' '}
              <code>loop</code> output; wire that to your processing nodes and add a feedback edge
              back to this node for the next batch. The <code>done</code> output fires once all
              batches are exhausted.
            </p>

            <label className="field-label" htmlFor="lp-batchsize">
              Batch size
            </label>
            <input
              id="lp-batchsize"
              className="field-input"
              value={selectedNode.data.batchSize || ''}
              onChange={(e) => patchNodeData(selectedId, { batchSize: e.target.value })}
              placeholder="1"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete node
            </button>
          </aside>
        )}

        {!inspectExecution && selectedNode && selectedNode.type === 'trigger' && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Trigger</h3>
              <span className="badge badge--violet mono">trigger</span>
            </div>
            <p className="hint">
              The flow entry point. Execution starts here on each user message. Wire its output to a
              keyword node. Exactly one trigger per flow.
            </p>
          </aside>
        )}

        {!inspectExecution && !selectedNode && (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Fallback answer</h3>
              <span className="badge badge--violet mono">default</span>
            </div>
            <p className="hint">
              Sent when the flow produces no reply. Select a node to edit it.
            </p>
            <textarea
              className="field-area inspector__fallback"
              value={fallbackAnswer}
              onChange={(e) => setFallbackAnswer(e.target.value)}
              placeholder="Sorry, I didn't quite get that. Let me connect you with support…"
            />

            <section className="schedule" aria-label="Schedule">
              <div className="inspector__head schedule__head">
                <h3>Schedule</h3>
                <span className="badge mono">cron</span>
              </div>
              <p className="hint">
                Run this bot's flow on a recurring cadence. Leave blank for no schedule. Uses a
                6-field Spring cron expression: <code>sec min hour day month weekday</code> — e.g.{' '}
                <code>0 0 * * * *</code> runs hourly.
              </p>
              <input
                className="field-input"
                value={schedule}
                onChange={(e) => setSchedule(e.target.value)}
                placeholder="0 0 * * * *"
                spellCheck={false}
                autoComplete="off"
              />
              {schedule.trim() && (
                <p className="hint schedule__note mono">Scheduled: {schedule.trim()}</p>
              )}
            </section>

            {!isNew && (
              <section className="webhook" aria-label="Webhook">
                <div className="inspector__head webhook__head">
                  <h3>Webhook</h3>
                  <span className="badge mono">POST</span>
                </div>
                {webhookToken ? (
                  <>
                    <p className="hint">
                      Invoke this bot externally by POSTing a JSON body like{' '}
                      <code>{'{ "message": "hi" }'}</code> to this URL.
                    </p>
                    <div className="webhook__url">
                      <code className="webhook__url-text mono" title={webhookUrl}>
                        {webhookUrl}
                      </code>
                      <button
                        type="button"
                        className="btn btn-secondary webhook__copy"
                        onClick={copyWebhookUrl}
                      >
                        Copy
                      </button>
                    </div>
                  </>
                ) : (
                  <p className="hint">Save the bot to get its webhook URL.</p>
                )}
              </section>
            )}
          </aside>
        )}

        {!isNew && (
          <TestBotPanel
            botId={id}
            botName={name}
            open={testOpen}
            onClose={() => setTestOpen(false)}
          />
        )}

        {!isNew && (
          <ExecutionsPanel
            botId={id}
            open={execOpen}
            onClose={() => {
              setExecOpen(false);
              setInspectExecution(null);
            }}
            activeExecId={inspectExecution?.id || null}
            onInspect={setInspectExecution}
          />
        )}

        <CredentialsPanel
          open={credOpen}
          onClose={() => setCredOpen(false)}
          onChanged={loadCredentials}
        />
      </div>
    </div>
  );
}

export default function BotEditor() {
  return (
    <ReactFlowProvider>
      <EditorInner />
    </ReactFlowProvider>
  );
}
