import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the shared fetch wrapper so we assert the exact URLs the endpoint module builds.
vi.mock('./client', () => ({ api: vi.fn() }));

import { api } from './client';
import { listExecutions, getExecution } from './executions';

beforeEach(() => {
  vi.clearAllMocks();
  api.mockResolvedValue(null);
});

describe('executions api', () => {
  it('lists a bot\'s executions with default paging', () => {
    listExecutions('bot1');
    expect(api).toHaveBeenCalledWith('/api/bots/bot1/executions?limit=20&offset=0');
  });

  it('passes through custom limit/offset', () => {
    listExecutions('bot1', { limit: 5, offset: 10 });
    expect(api).toHaveBeenCalledWith('/api/bots/bot1/executions?limit=5&offset=10');
  });

  it('fetches a full execution by id', () => {
    getExecution('exec9');
    expect(api).toHaveBeenCalledWith('/api/executions/exec9');
  });

  it('returns the resolved summary list shape', async () => {
    api.mockResolvedValue([
      { id: 'e1', botId: 'bot1', status: 'success', trigger: 'message', nodeCount: 3 },
    ]);
    const res = await listExecutions('bot1');
    expect(res).toHaveLength(1);
    expect(res[0]).toMatchObject({ id: 'e1', status: 'success', nodeCount: 3 });
  });
});
