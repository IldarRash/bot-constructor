import { api, setToken } from './client';

// Login is by EMAIL + password. POST /api/users/login -> UserView
export async function login(email, password) {
  const view = await api('/api/users/login', {
    method: 'POST',
    auth: false,
    body: { email, password },
  });
  if (view?.token) setToken(view.token);
  return view;
}

// Signup. POST /api/users -> UserView
export async function signup({ username, email, password }) {
  const view = await api('/api/users', {
    method: 'POST',
    auth: false,
    body: { username, email, password },
  });
  if (view?.token) setToken(view.token);
  return view;
}

// Current user. GET /api/user -> UserView (requires auth)
export const getCurrentUser = () => api('/api/user');
