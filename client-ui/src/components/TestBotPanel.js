import React, { useEffect, useRef, useState } from 'react';
import { startSession, sendMessage } from '../api/runtime';
import { ApiError } from '../api/client';
import './TestBotPanel.css';

// In-house test chat for a saved bot. Opens a runtime session, then exchanges messages.
// Renders a subtle note whenever a reply came from the bot's fallbackAnswer (matched === null).
export default function TestBotPanel({ botId, botName, open, onClose }) {
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]); // { from: 'bot'|'user', text, fallback? }
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const logRef = useRef(null);
  const startedRef = useRef(false);

  // Start a session the first time the panel opens for this bot.
  useEffect(() => {
    if (!open || startedRef.current || !botId) return;
    startedRef.current = true;
    setBusy(true);
    setError(null);
    startSession(botId)
      .then((res) => {
        setSessionId(res.sessionId);
        if (res.greeting) setMessages([{ from: 'bot', text: res.greeting }]);
      })
      .catch((err) => {
        const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to start session.';
        setError(msg);
      })
      .finally(() => setBusy(false));
  }, [open, botId]);

  // Reset when closed so reopening starts fresh.
  useEffect(() => {
    if (!open) {
      startedRef.current = false;
      setSessionId(null);
      setMessages([]);
      setInput('');
      setError(null);
    }
  }, [open]);

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [messages, busy]);

  async function send() {
    const text = input.trim();
    if (!text || !sessionId || busy) return;
    setInput('');
    setMessages((m) => m.concat({ from: 'user', text }));
    setBusy(true);
    setError(null);
    try {
      const res = await sendMessage(sessionId, text);
      setMessages((m) =>
        m.concat({ from: 'bot', text: res.reply, fallback: res.matched == null }),
      );
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to send message.';
      setError(msg);
    } finally {
      setBusy(false);
    }
  }

  function onKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  }

  if (!open) return null;

  return (
    <aside className="test-panel">
      <header className="test-panel__head">
        <h3>Test {botName || 'bot'}</h3>
        <button className="test-panel__close" onClick={onClose} aria-label="Close test panel">
          ×
        </button>
      </header>

      <div className="test-panel__log" ref={logRef}>
        {messages.map((m, i) => (
          <div key={i} className={`chat-msg chat-msg--${m.from}`}>
            <div className="chat-bubble">{m.text}</div>
            {m.fallback && <div className="chat-note">fallback answer (no keyword matched)</div>}
          </div>
        ))}
        {busy && (
          <div className="chat-msg chat-msg--bot">
            <div className="chat-bubble">
              <span className="chat-typing" aria-label="Bot is typing">
                <span />
                <span />
                <span />
              </span>
            </div>
          </div>
        )}
      </div>

      {error && <div className="test-panel__error">{error}</div>}

      <div className="test-panel__input">
        <textarea
          rows={2}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={sessionId ? 'Type a message…' : 'Starting session…'}
          disabled={!sessionId || busy}
        />
        <button className="btn btn-primary" onClick={send} disabled={!sessionId || busy || !input.trim()}>
          Send
        </button>
      </div>
    </aside>
  );
}
