import React from 'react';
import { Link } from 'react-router-dom';
import './AppHeader.css';

// App-wide top bar: product mark on the left, contextual slot in the middle,
// user identity + actions on the right. `children` render in the right slot.
export default function AppHeader({ user, onLogout, children }) {
  return (
    <header className="app-header">
      <Link to="/" className="brand-mark" aria-label="Bot Constructor home">
        <span className="brand-mark__glyph">{'</>'}</span>
        <span className="brand-mark__name">
          Bot<b>Constructor</b>
        </span>
      </Link>

      <div className="app-header__slot">{children}</div>

      <div className="app-header__right">
        {user && (
          <span className="app-header__who" title={user.email || user.username}>
            <span className="app-header__avatar" aria-hidden="true">
              {(user.username || user.email || '?').slice(0, 1).toUpperCase()}
            </span>
            <span className="app-header__who-name">{user.username || user.email}</span>
          </span>
        )}
        {onLogout && (
          <button className="btn btn-ghost" onClick={onLogout}>
            Log out
          </button>
        )}
      </div>
    </header>
  );
}
