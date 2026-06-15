import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import AuthAside from './AuthAside';
import './Auth.css';

export default function Register() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setErrors([]);
    setSubmitting(true);
    try {
      await signup({ username, email, password });
      navigate('/', { replace: true });
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
          <h1>Create account</h1>
          <p className="sub">Start building bots in minutes.</p>

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
            <label className="field-label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              className="field-input"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              placeholder="ada"
              required
            />
          </div>

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
              autoComplete="new-password"
              placeholder="••••••••"
              required
            />
          </div>

          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting && <span className="spinner" />}
            {submitting ? 'Creating…' : 'Create account'}
          </button>

          <div className="auth-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
