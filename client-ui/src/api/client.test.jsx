import { describe, it, expect } from 'vitest';
import { getApiBaseUrl } from './client';

// BASE_URL is relative ('') in the test/dev environment, so getApiBaseUrl falls back to
// the current page origin — this is what the webhook URL is built from.
describe('getApiBaseUrl', () => {
  it('resolves to the window origin when the API base is relative', () => {
    expect(getApiBaseUrl()).toBe(window.location.origin);
  });

  it('builds an absolute webhook URL that can be shown to the owner', () => {
    const token = 'abc123';
    const url = `${getApiBaseUrl()}/api/runtime/webhooks/${token}`;
    expect(url).toBe(`${window.location.origin}/api/runtime/webhooks/abc123`);
  });
});
