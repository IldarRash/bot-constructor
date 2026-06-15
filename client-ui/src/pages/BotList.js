import React, { useEffect, useState, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { listBots, deleteBot } from '../api/bots';
import { ApiError } from '../api/client';
import AppHeader from '../components/AppHeader';
import { useToast } from '../components/Toast';
import './BotList.css';

// Map a bot platform to a short monospace glyph for its card.
const TYPE_GLYPH = { Telegram: 'TG', Instagram: 'IG', Vkontakte: 'VK' };

export default function BotList() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [bots, setBots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listBots();
      setBots(Array.isArray(data) ? data : []);
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to load bots.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function onDelete(id, name) {
    if (!window.confirm(`Delete bot "${name}"? This cannot be undone.`)) return;
    try {
      await deleteBot(id);
      setBots((prev) => prev.filter((b) => b.id !== id));
      toast.success(`Deleted "${name}".`);
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to delete bot.';
      toast.error(msg);
    }
  }

  function onLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="botlist-page">
      <AppHeader user={user} onLogout={onLogout} />

      <div className="botlist-body">
        <div className="toolbar">
          <div>
            <h2>Your bots</h2>
            <p className="toolbar__sub">
              {loading
                ? 'Loading…'
                : `${bots.length} bot${bots.length === 1 ? '' : 's'} in your workspace`}
            </p>
          </div>
          <Link className="btn btn-primary" to="/bots/new">
            <span aria-hidden="true">+</span> New bot
          </Link>
        </div>

        {loading && (
          <div className="bot-grid" aria-hidden="true">
            {[0, 1, 2].map((i) => (
              <div className="bot-card bot-card--skeleton" key={i}>
                <div className="sk sk--badge" />
                <div className="sk sk--title" />
                <div className="sk sk--meta" />
              </div>
            ))}
          </div>
        )}

        {error && !loading && (
          <div className="error-panel" role="alert">
            <span>{error}</span>
            <button className="btn btn-secondary" onClick={load}>
              Retry
            </button>
          </div>
        )}

        {!loading && !error && bots.length === 0 && (
          <div className="empty-state">
            <div className="empty-state__art" aria-hidden="true">
              {'{ }'}
            </div>
            <h3>No bots yet</h3>
            <p>Spin up your first conversational flow — it only takes a minute.</p>
            <Link className="btn btn-primary" to="/bots/new">
              Create your first bot
            </Link>
          </div>
        )}

        {!loading && !error && bots.length > 0 && (
          <div className="bot-grid">
            {bots.map((bot) => {
              const count = bot.questions?.length || 0;
              return (
                <Link className="bot-card" key={bot.id} to={`/bots/${bot.id}`}>
                  <div className="bot-card__top">
                    <span className="bot-card__glyph" aria-hidden="true">
                      {TYPE_GLYPH[bot.type] || bot.type?.slice(0, 2).toUpperCase()}
                    </span>
                    <span className="badge">{bot.type}</span>
                  </div>
                  <h3 className="bot-card__name">{bot.name || 'Untitled bot'}</h3>
                  <span className="bot-card__meta mono">
                    {count} question{count === 1 ? '' : 's'}
                  </span>
                  <div className="bot-card__actions">
                    <span className="bot-card__edit">Open editor →</span>
                    <button
                      className="btn btn-danger bot-card__delete"
                      onClick={(e) => {
                        e.preventDefault();
                        onDelete(bot.id, bot.name);
                      }}
                    >
                      Delete
                    </button>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
