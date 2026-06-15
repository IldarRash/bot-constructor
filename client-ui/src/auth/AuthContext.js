import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { getToken, setToken } from '../api/client';
import * as usersApi from '../api/users';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Load the current user from a stored token on mount.
  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!getToken()) {
        setLoading(false);
        return;
      }
      try {
        const me = await usersApi.getCurrentUser();
        if (!cancelled) setUser(me);
      } catch {
        // Token invalid/expired — clear it.
        setToken(null);
        if (!cancelled) setUser(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const view = await usersApi.login(email, password);
    setUser(view);
    return view;
  }, []);

  const signup = useCallback(async (req) => {
    const view = await usersApi.signup(req);
    setUser(view);
    return view;
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  const value = { user, loading, login, signup, logout };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
