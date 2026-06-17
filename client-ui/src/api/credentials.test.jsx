import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the shared fetch wrapper so we assert the exact URLs/bodies the endpoint module builds.
vi.mock('./client', () => ({ api: vi.fn() }));

import { api } from './client';
import {
  listCredentials,
  getCredential,
  createCredential,
  updateCredential,
  deleteCredential,
} from './credentials';

beforeEach(() => {
  vi.clearAllMocks();
  api.mockResolvedValue(null);
});

describe('credentials api', () => {
  it('lists the owner credentials', () => {
    listCredentials();
    expect(api).toHaveBeenCalledWith('/api/credentials');
  });

  it('fetches one credential by id', () => {
    getCredential('c1');
    expect(api).toHaveBeenCalledWith('/api/credentials/c1');
  });

  it('creates a credential with name, type and the typed secret data', () => {
    createCredential({ name: 'Prod TG', type: 'telegramApi', data: { botToken: 'secret' } });
    expect(api).toHaveBeenCalledWith('/api/credentials', {
      method: 'POST',
      body: { name: 'Prod TG', type: 'telegramApi', data: { botToken: 'secret' } },
    });
  });

  it('updates a credential, re-encrypting when data is present', () => {
    updateCredential('c1', { name: 'Renamed', data: { botToken: 'new' } });
    expect(api).toHaveBeenCalledWith('/api/credentials/c1', {
      method: 'PUT',
      body: { name: 'Renamed', data: { botToken: 'new' } },
    });
  });

  it('deletes a credential by id', () => {
    deleteCredential('c1');
    expect(api).toHaveBeenCalledWith('/api/credentials/c1', { method: 'DELETE' });
  });

  it('returns metadata-only view shapes (never secrets)', async () => {
    api.mockResolvedValue([
      { id: 'c1', name: 'Prod TG', type: 'telegramApi', createdAt: '2026-06-16T10:00:00Z' },
    ]);
    const res = await listCredentials();
    expect(res[0]).toMatchObject({ id: 'c1', name: 'Prod TG', type: 'telegramApi' });
    // No secret fields are present on the metadata view.
    expect(res[0].botToken).toBeUndefined();
    expect(res[0].data).toBeUndefined();
  });
});
