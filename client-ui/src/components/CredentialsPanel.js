import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  listCredentials,
  createCredential,
  deleteCredential,
} from '../api/credentials';
import { ApiError } from '../api/client';
import {
  CREDENTIAL_TYPES,
  CREDENTIAL_TYPE_KEYS,
  credentialTypeLabel,
} from '../credentials/credentialTypes';
import './CredentialsPanel.css';

function formatTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString();
}

// Empties the per-field secret inputs for a given type (all start blank — stored secrets are
// never returned by the API, so creating one always means typing the values fresh).
function blankData(type) {
  const out = {};
  (CREDENTIAL_TYPES[type]?.fields || []).forEach((f) => {
    out[f.key] = '';
  });
  return out;
}

// Slide-in manager for the owner's encrypted credentials. Lists existing credentials (metadata
// only — name, type, created), creates a new one by picking a type and entering its secret fields
// as password inputs, and deletes. A stored secret value is NEVER shown (the API never returns it);
// "editing" a secret means creating a replacement. `onChanged` lets the editor refresh any open
// credential selectors after a create/delete.
export default function CredentialsPanel({ open, onClose, onChanged }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [type, setType] = useState(CREDENTIAL_TYPE_KEYS[0]);
  const [data, setData] = useState(() => blankData(CREDENTIAL_TYPE_KEYS[0]));
  const [submitting, setSubmitting] = useState(false);

  const loadedRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await listCredentials();
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to load credentials.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  // Load once when the panel opens; reset to a clean state when it closes.
  useEffect(() => {
    if (open && !loadedRef.current) {
      loadedRef.current = true;
      load();
    }
    if (!open) {
      loadedRef.current = false;
      setItems([]);
      setError(null);
      setCreating(false);
      setName('');
      setType(CREDENTIAL_TYPE_KEYS[0]);
      setData(blankData(CREDENTIAL_TYPE_KEYS[0]));
    }
  }, [open, load]);

  function onTypeChange(nextType) {
    setType(nextType);
    setData(blankData(nextType)); // a different type has different secret fields
  }

  function setField(key, value) {
    setData((prev) => ({ ...prev, [key]: value }));
  }

  async function onCreate(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const created = await createCredential({ name: name.trim(), type, data });
      // Response is metadata only — append it to the list as-is.
      setItems((prev) => [created, ...prev]);
      setCreating(false);
      setName('');
      setType(CREDENTIAL_TYPE_KEYS[0]);
      setData(blankData(CREDENTIAL_TYPE_KEYS[0]));
      onChanged?.();
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to create credential.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  async function onDelete(cred) {
    if (!window.confirm(`Delete credential "${cred.name}"? Nodes using it will fall back to inline values.`))
      return;
    setError(null);
    try {
      await deleteCredential(cred.id);
      setItems((prev) => prev.filter((c) => c.id !== cred.id));
      onChanged?.();
    } catch (err) {
      const msg = err instanceof ApiError ? err.messages.join(', ') : 'Failed to delete credential.';
      setError(msg);
    }
  }

  if (!open) return null;

  const fields = CREDENTIAL_TYPES[type]?.fields || [];

  return (
    <aside className="cred-panel">
      <header className="cred-panel__head">
        <h3>Credentials</h3>
        <button className="cred-panel__close" onClick={onClose} aria-label="Close credentials panel">
          ×
        </button>
      </header>

      <div className="cred-panel__hint">
        Encrypted, reusable secrets. Stored values are never shown — to change a secret, create a new
        credential. Bind one to a connector or HTTP node from its inspector.
      </div>

      <div className="cred-panel__list">
        {!creating && (
          <button
            type="button"
            className="btn btn-primary cred-panel__new"
            onClick={() => setCreating(true)}
          >
            <span aria-hidden="true">+</span> New credential
          </button>
        )}

        {creating && (
          <form className="cred-form" onSubmit={onCreate}>
            <label className="field-label" htmlFor="cred-name">
              Name
            </label>
            <input
              id="cred-name"
              className="field-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Prod Telegram bot"
              required
            />

            <label className="field-label" htmlFor="cred-type">
              Type
            </label>
            <select
              id="cred-type"
              className="field-select"
              value={type}
              onChange={(e) => onTypeChange(e.target.value)}
            >
              {CREDENTIAL_TYPE_KEYS.map((t) => (
                <option key={t} value={t}>
                  {credentialTypeLabel(t)}
                </option>
              ))}
            </select>

            {fields.map((f) => (
              <React.Fragment key={f.key}>
                <label className="field-label" htmlFor={`cred-${f.key}`}>
                  {f.label}
                </label>
                <input
                  id={`cred-${f.key}`}
                  className="field-input"
                  type="password"
                  autoComplete="new-password"
                  value={data[f.key] || ''}
                  onChange={(e) => setField(f.key, e.target.value)}
                  placeholder={f.placeholder}
                />
              </React.Fragment>
            ))}

            <div className="cred-form__actions">
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => setCreating(false)}
                disabled={submitting}
              >
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting && <span className="spinner" />}
                {submitting ? 'Saving…' : 'Create'}
              </button>
            </div>
          </form>
        )}

        {error && <div className="cred-panel__error">{error}</div>}

        {loading && items.length === 0 && <p className="cred-panel__empty">Loading…</p>}

        {!loading && items.length === 0 && !error && (
          <p className="cred-panel__empty">No credentials yet. Create one to reuse secrets across bots.</p>
        )}

        {items.map((cred) => (
          <div className="cred-row" key={cred.id}>
            <div className="cred-row__main">
              <span className="cred-row__name">{cred.name}</span>
              <span className="cred-row__type mono">{credentialTypeLabel(cred.type)}</span>
            </div>
            <div className="cred-row__bottom">
              <span className="cred-row__time mono">{formatTime(cred.createdAt)}</span>
              <button
                type="button"
                className="btn btn-danger cred-row__delete"
                onClick={() => onDelete(cred)}
              >
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
    </aside>
  );
}
