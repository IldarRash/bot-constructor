// Central fetch wrapper for the gateway API.
// Uses RELATIVE urls so CRA's dev "proxy" (package.json -> http://localhost:8080)
// forwards /api calls to the gateway and avoids CORS in development.
const BASE_URL = process.env.REACT_APP_API_URL || '';

const TOKEN_KEY = 'token';

let token = (typeof localStorage !== 'undefined' && localStorage.getItem(TOKEN_KEY)) || null;

export function getToken() {
  return token;
}

export function setToken(t) {
  token = t || null;
  if (typeof localStorage === 'undefined') return;
  if (t) {
    localStorage.setItem(TOKEN_KEY, t);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export class ApiError extends Error {
  constructor(status, body) {
    super(`API ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }

  // Flattens the backend 400 shape { errors: { field: [msg] } } into readable lines.
  get messages() {
    const errors = this.body && this.body.errors;
    if (errors && typeof errors === 'object') {
      return Object.entries(errors).flatMap(([field, msgs]) =>
        (Array.isArray(msgs) ? msgs : [msgs]).map((m) => `${field}: ${m}`),
      );
    }
    if (this.body && typeof this.body.message === 'string') return [this.body.message];
    return [this.message];
  }
}

export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  // Backend expects the literal "Token " prefix, NOT "Bearer ".
  if (auth && token) headers.Authorization = `Token ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const data =
    res.status === 204 || res.status === 205
      ? null
      : await res.json().catch(() => null);

  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}
