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

import QuestionNode from '../components/QuestionNode';
import PresenceLayer from '../components/PresenceLayer';
import TestBotPanel from '../components/TestBotPanel';
import { getBot, createBot, updateBot } from '../api/bots';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { useBoardCollaboration } from '../hooks/useBoardCollaboration';
import './BotEditor.css';

const BOT_TYPES = ['Instagram', 'Vkontakte', 'Telegram'];
const nodeTypes = { question: QuestionNode };

let nodeSeq = 0;
const nextNodeId = () => `q_${Date.now()}_${nodeSeq++}`;

// A question node carries { text, keyWords, answer } in data. Build a reactflow node from a question.
function makeQuestionNode(question, index) {
  return {
    id: nextNodeId(),
    type: 'question',
    position: { x: 80 + (index % 3) * 280, y: 60 + Math.floor(index / 3) * 200 },
    data: {
      text: question.text || '',
      keyWords: question.keyWords || [],
      answer: question.answer || '',
    },
  };
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

  const [nodes, setNodes] = useNodesState([]);
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
    broadcastNodesChange,
    sendCursor,
  } = collab;

  // Load an existing bot into the canvas.
  useEffect(() => {
    if (isNew || loadedRef.current) return;
    loadedRef.current = true;
    (async () => {
      try {
        const bot = await getBot(id);
        setName(bot.name || '');
        setType(BOT_TYPES.includes(bot.type) ? bot.type : BOT_TYPES[0]);
        setFallbackAnswer(bot.fallbackAnswer || '');
        const qs = Array.isArray(bot.questions) ? bot.questions : [];
        setNodes(qs.map((q, i) => makeQuestionNode(q, i)));
      } catch (err) {
        const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to load bot.';
        setErrors([msg]);
      } finally {
        setLoading(false);
      }
    })();
  }, [id, isNew, setNodes]);

  const onSelectionChange = useCallback(({ nodes: selected }) => {
    const node = selected && selected[0];
    setSelectedId(node ? node.id : null);
    setKeywordsText(node ? (node.data.keyWords || []).join(', ') : '');
  }, []);

  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedId) || null,
    [nodes, selectedId],
  );

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

  function addQuestion() {
    const node = makeQuestionNode({ text: '', keyWords: [], answer: '' }, nodes.length);
    setNodes((nds) => nds.concat(node));
    broadcastNodeAdd(node);
    setSelectedId(node.id);
    setKeywordsText('');
  }

  function updateSelectedText(value) {
    if (selectedId) patchNodeData(selectedId, { text: value });
  }

  function updateSelectedAnswer(value) {
    if (selectedId) patchNodeData(selectedId, { answer: value });
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
    setNodes((nds) => nds.filter((n) => n.id !== removeId));
    setEdges((eds) => eds.filter((e) => e.source !== removeId && e.target !== removeId));
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
    const questions = nodes.map((n) => ({
      text: n.data.text || '',
      keyWords: n.data.keyWords || [],
      answer: n.data.answer || '',
    }));
    const body = { name, type, questions, fallbackAnswer };

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
            <button className="btn btn-primary" onClick={addQuestion}>
              <span aria-hidden="true">+</span> Add question
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

        {selectedNode ? (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Edit question</h3>
              <span className="badge mono">node</span>
            </div>
            <p className="hint">Define the prompt, the keywords that trigger it, and its answer.</p>

            <label className="field-label" htmlFor="q-text">
              Question text
            </label>
            <textarea
              id="q-text"
              className="field-area"
              rows={3}
              value={selectedNode.data.text || ''}
              onChange={(e) => updateSelectedText(e.target.value)}
              placeholder="What can I help you with?"
            />

            <label className="field-label" htmlFor="q-keywords">
              Keywords (comma-separated)
            </label>
            <input
              id="q-keywords"
              className="field-input"
              value={keywordsText}
              onChange={(e) => updateSelectedKeywords(e.target.value)}
              placeholder="price, cost, how much"
            />

            <label className="field-label" htmlFor="q-answer">
              Answer
            </label>
            <textarea
              id="q-answer"
              className="field-area"
              rows={4}
              value={selectedNode.data.answer || ''}
              onChange={(e) => updateSelectedAnswer(e.target.value)}
              placeholder="Our pricing starts at…"
            />

            <button className="btn btn-danger inspector__delete" onClick={deleteSelected}>
              Delete question
            </button>
          </aside>
        ) : (
          <aside className="inspector">
            <div className="inspector__head">
              <h3>Fallback answer</h3>
              <span className="badge badge--violet mono">default</span>
            </div>
            <p className="hint">
              Sent when no question keyword matches. Select a question node to edit it.
            </p>
            <textarea
              className="field-area inspector__fallback"
              value={fallbackAnswer}
              onChange={(e) => setFallbackAnswer(e.target.value)}
              placeholder="Sorry, I didn't quite get that. Let me connect you with support…"
            />
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
