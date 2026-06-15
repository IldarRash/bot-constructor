import React from 'react';
import './PresenceLayer.css';

// Deterministic color per collaborator id, so a peer keeps the same hue across badge + cursor.
function colorFor(id) {
  let hash = 0;
  for (let i = 0; i < (id || '').length; i++) hash = (hash * 31 + id.charCodeAt(i)) | 0;
  const hue = Math.abs(hash) % 360;
  return `hsl(${hue} 70% 55%)`;
}

function initials(name) {
  const n = (name || '?').trim();
  return n ? n.slice(0, 2).toUpperCase() : '?';
}

// Online-user badges + remote cursor overlay. `status` reflects the RSocket connection.
// Positioned absolutely; mount it inside a positioned container (e.g. the canvas wrap).
export default function PresenceLayer({ status, peers = [], cursors = {} }) {
  return (
    <>
      <div className="presence-bar">
        <span className={`presence-dot presence-dot--${status}`} title={`Collaboration: ${status}`} />
        {peers.length === 0 ? (
          <span className="presence-empty">
            {status === 'online' ? 'Only you' : status === 'connecting' ? 'Connecting…' : 'Offline'}
          </span>
        ) : (
          <div className="presence-avatars">
            {peers.map((p) => (
              <span
                key={p.id}
                className="presence-avatar"
                style={{ background: colorFor(p.id) }}
                title={p.name}
              >
                {initials(p.name)}
              </span>
            ))}
          </div>
        )}
      </div>

      <div className="cursor-overlay">
        {Object.entries(cursors).map(([id, c]) => (
          <div
            key={id}
            className="remote-cursor"
            style={{ transform: `translate(${c.x}px, ${c.y}px)`, color: colorFor(id) }}
          >
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
              <path d="M0 0 L0 12 L4 9 L7 15 L9 14 L6 8 L11 8 Z" fill="currentColor" />
            </svg>
            <span className="remote-cursor__label" style={{ background: colorFor(id) }}>
              {c.name || 'Guest'}
            </span>
          </div>
        ))}
      </div>
    </>
  );
}
