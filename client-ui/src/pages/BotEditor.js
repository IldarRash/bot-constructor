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
import PresenceLayer from '../components/PresenceLayer';
import TestBotPanel from '../components/TestBotPanel';
import { getBot, createBot, updateBot } from '../api/bots';
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

  const onSelectionChange = useCallback(({ nodes: selected }) => {
    const node = selected && selected[0];
    setSelectedId(node ? node.id : null);
    setKeywordsText(node ? (node.data?.keyWords || []).join(', ') : '');
  }, []);

  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedId) || null,
    [nodes, selectedId],
  );

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

  async function onSave() {
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
      if (isNew) await createBot(body);
      else await updateBot(id, body);
      toast.success(isNew ? 'Bot created.' : 'Changes saved.');
      navigate('/', { replace: true });
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages : ['Failed to save bot.'];
      setErrors(msg);
      toast.error(msg.join(' · '));
    } finally {
      setSaving(false);
    }
  }

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
            <button className="btn btn-secondary" onClick={() => setTestOpen((v) => !v)}>
              {testOpen ? 'Close test' : '▷ Test bot'}
            </button>
          )}
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
            <span className="canvas-hint mono">{nodes.length} nodes</span>
          </div>
          {!isNew && <PresenceLayer status={status} peers={peers} cursors={cursors} />}
          <ReactFlow
            nodes={nodes}
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

        {selectedNode && selectedNode.type === 'keyword' && (
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

        {selectedNode && selectedNode.type === 'sendMessage' && (
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

        {selectedNode && selectedNode.type === 'condition' && (
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

        {selectedNode && selectedNode.type === 'setVariable' && (
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

        {selectedNode && selectedNode.type === 'httpRequest' && (
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

        {selectedNode && selectedNode.type === 'telegramSend' && (
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

        {selectedNode && selectedNode.type === 'anthropicMessage' && (
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

        {selectedNode && selectedNode.type === 'slackSend' && (
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

        {selectedNode && selectedNode.type === 'discordSend' && (
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

        {selectedNode && selectedNode.type === 'code' && (
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

        {selectedNode && selectedNode.type === 'trigger' && (
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

        {!selectedNode && (
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
