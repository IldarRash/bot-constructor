import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the shared fetch wrapper so we assert the exact URL + body each endpoint builds.
vi.mock('./client', () => ({ api: vi.fn() }));

import { api } from './client';
import { startSession, sendMessage, executeWorkflow } from './runtime';

beforeEach(() => {
  vi.clearAllMocks();
  api.mockResolvedValue(null);
});

describe('runtime api', () => {
  it('starts a session for a bot', () => {
    startSession('bot1');
    expect(api).toHaveBeenCalledWith('/api/runtime/bots/bot1/sessions', { method: 'POST' });
  });

  it('sends a message into a session', () => {
    sendMessage('s1', 'hi');
    expect(api).toHaveBeenCalledWith('/api/runtime/sessions/s1/messages', {
      method: 'POST',
      body: { text: 'hi' },
    });
  });

  it('executes the workflow with message, vars and pinnedData', () => {
    const pinnedData = { n2: [{ json: { ok: true } }] };
    executeWorkflow('bot9', { message: 'hello', vars: { age: 21 }, pinnedData });
    expect(api).toHaveBeenCalledWith('/api/runtime/bots/bot9/execute', {
      method: 'POST',
      body: { message: 'hello', vars: { age: 21 }, pinnedData },
    });
  });

  it('executes with no args (all fields undefined)', () => {
    executeWorkflow('bot9');
    expect(api).toHaveBeenCalledWith('/api/runtime/bots/bot9/execute', {
      method: 'POST',
      body: { message: undefined, vars: undefined, pinnedData: undefined },
    });
  });

  it('returns the resolved ManualRunResponse shape', async () => {
    api.mockResolvedValue({
      reply: 'Sure!',
      matched: { text: 'help' },
      vars: { age: 21 },
      trace: [{ nodeId: 'n1', type: 'keyword', handle: 'match', detail: null }],
      nodes: [
        {
          nodeId: 'n1',
          type: 'keyword',
          handle: 'match',
          inputItems: [{ json: { message: 'help' } }],
          outputs: { default: [{ json: { message: 'help' } }] },
        },
      ],
    });
    const res = await executeWorkflow('bot9', { message: 'help' });
    expect(res.reply).toBe('Sure!');
    expect(res.nodes[0].outputs.default).toHaveLength(1);
  });
});
