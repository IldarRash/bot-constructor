---
name: api-client
description: Create or extend the client-ui → gateway API layer — a fetch wrapper with JWT token handling and typed calls for the client-api endpoints. Use when the frontend needs to talk to the backend (login, signup, current user, bots, runtime).
disable-model-invocation: true
---

# api-client

This skill stands up a small, central API layer so components never call `fetch` directly. The UI
talks **only to the gateway**, never to backend services directly.

## Conventions

- Use **relative paths** (e.g. `/api/...`). In dev, Vite proxies `/api` (and `/rsocket`) to the
  gateway — see `client-ui/vite.config.js`. In prod the UI is served behind the gateway, so relative
  paths just work. Do not hardcode a backend host/port and do not point the UI at `client-api`
  directly.
- The backend issues a JWT on login/signup. Store it (in memory or `localStorage`) and send it as
  **`Authorization: Token <jwt>`** on authenticated calls (NOT `Bearer`). Public endpoints:
  `POST /api/users` (signup) and `POST /api/users/login`. Authenticated: `GET /api/user`,
  `PUT /api/user`, `/api/bots/**`, `/api/runtime/**`.
- **Login is by email** (`{ email, password }`), not username.
- Centralize fetch + error handling in `src/api/client.js`; keep endpoint-specific calls in
  `src/api/<resource>.js`.

## Templates

`src/api/client.js`:

```js
let token = localStorage.getItem('token') || null;
export const setToken = (t) => {
  token = t;
  t ? localStorage.setItem('token', t) : localStorage.removeItem('token');
};
export const getToken = () => token;

export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && token) headers.Authorization = `Token ${token}`;
  const res = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

export class ApiError extends Error {
  constructor(status, body) { super(`API ${status}`); this.status = status; this.body = body; }
}
```

`src/api/users.js`:

```js
import { api, setToken } from './client';

export async function login(email, password) {
  const view = await api('/api/users/login', { method: 'POST', auth: false, body: { email, password } });
  if (view?.token) setToken(view.token);
  return view;
}

export async function signup(req) {
  const view = await api('/api/users', { method: 'POST', auth: false, body: req });
  if (view?.token) setToken(view.token);
  return view;
}

export const getCurrentUser = () => api('/api/user');
export const updateUser = (req) => api('/api/user', { method: 'PUT', body: req });
```

## Checklist

- [ ] All calls use relative `/api/...` paths (Vite proxy in dev), no hardcoded backend host
- [ ] JWT stored on login/signup and sent as `Authorization: Token <jwt>`
- [ ] Login uses `{ email, password }`
- [ ] Public vs authenticated endpoints set the `auth` flag correctly
- [ ] Non-2xx responses throw `ApiError`; the 400 `{ errors: {...} }` shape is handled in the UI
- [ ] Components import from `src/api/*`, no inline `fetch`
