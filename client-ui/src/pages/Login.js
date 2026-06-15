import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import AuthAside from './AuthAside';
import './Auth.css';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  const from = location.state?.from?.pathname || '/';

  async function onSubmit(e) {
    e.preventDefault();
    setErrors([]);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.messages);
      else setErrors(['Unable to reach the server. Is the gateway running?']);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <AuthAside />
      <div className="auth-main">
        <form className="auth-card" onSubmit={onSubmit}>
          <h1>Sign in</h1>
          <p className="sub">Welcome back. Let's build something.</p>

          {errors.length > 0 && (
            <div className="auth-error" role="alert">
              <ul>
                {errors.map((m, i) => (
                  <li key={i}>{m}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="auth-field">
            <label className="field-label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              className="field-input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              placeholder="you@studio.dev"
              required
            />
          </div>

          <div className="auth-field">
            <label className="field-label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              className="field-input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              placeholder="••••••••"
              required
            />
          </div>

          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting && <span className="spinner" />}
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>

          <div className="auth-footer">
            No account? <Link to="/register">Create one</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
