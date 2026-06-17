import { api } from './client';

// Encrypted, owner-scoped credentials. Secrets live in client-api (AES-256-GCM) and are NEVER
// returned by these endpoints — responses carry METADATA ONLY (CredentialView):
//   { id, name, type, createdAt }
// A node opts into a credential via data.credentialId; the decrypted secret is resolved
// server-to-server at run time and never reaches the browser.

// List the owner's credentials (metadata only) -> [CredentialView]
export const listCredentials = () => api('/api/credentials');

// Fetch one credential's metadata -> CredentialView (404 for missing/foreign, like bots)
export const getCredential = (id) => api(`/api/credentials/${id}`);

// Create a credential. `data` holds the typed secret fields (e.g. { botToken }); client-api
// encrypts and stores it, returning metadata only. -> CredentialView
export const createCredential = ({ name, type, data }) =>
  api('/api/credentials', { method: 'POST', body: { name, type, data } });

// Update a credential. Omit `data` to rename only; passing `data` re-encrypts the secret. The
// stored secret is never returned, so changing it always means entering a fresh value. -> CredentialView
export const updateCredential = (id, { name, data } = {}) =>
  api(`/api/credentials/${id}`, { method: 'PUT', body: { name, data } });

// Delete a credential (owner-scoped) -> 204
export const deleteCredential = (id) => api(`/api/credentials/${id}`, { method: 'DELETE' });
