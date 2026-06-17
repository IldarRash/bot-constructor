import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the executions API so the panel runs without a gateway.
vi.mock('../api/executions', () => ({
  listExecutions: vi.fn(),
  getExecution: vi.fn(),
}));

import ExecutionsPanel, { ExecutionNodeInspect } from './ExecutionsPanel';
import { listExecutions, getExecution } from '../api/executions';

const SUMMARIES = [
  {
    id: 'e1',
    botId: 'b1',
    status: 'success',
    trigger: 'message',
    startedAt: '2026-06-16T10:00:00Z',
    reply: 'Hello there',
    nodeCount: 2,
  },
  {
    id: 'e2',
    botId: 'b1',
    status: 'error',
    trigger: 'webhook',
    startedAt: '2026-06-16T09:00:00Z',
    reply: 'Boom',
    nodeCount: 1,
  },
];

const FULL = {
  id: 'e1',
  botId: 'b1',
  status: 'success',
  trigger: 'message',
  reply: 'Hello there',
  nodes: [
    {
      nodeId: 'node-a',
      type: 'keyword',
      handle: 'match',
      detail: null,
      inputItems: [{ json: { message: 'hi' } }],
      outputs: { match: [{ json: { matched: true } }] },
      error: null,
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  listExecutions.mockResolvedValue(SUMMARIES);
  getExecution.mockResolvedValue(FULL);
});

describe('ExecutionsPanel', () => {
  it('renders the bot\'s executions newest-first with status badges', async () => {
    render(<ExecutionsPanel botId="b1" open onClose={() => {}} activeExecId={null} onInspect={() => {}} />);

    expect(await screen.findByText('Hello there')).toBeTruthy();
    expect(screen.getByText('Boom')).toBeTruthy();
    expect(screen.getByText('success')).toBeTruthy();
    expect(screen.getByText('error')).toBeTruthy();
    expect(listExecutions).toHaveBeenCalledWith('b1', { limit: 20, offset: 0 });
  });

  it('loads the full execution and lifts it via onInspect when a row is clicked', async () => {
    const onInspect = vi.fn();
    render(<ExecutionsPanel botId="b1" open onClose={() => {}} activeExecId={null} onInspect={onInspect} />);

    fireEvent.click(await screen.findByText('Hello there'));

    await waitFor(() => expect(getExecution).toHaveBeenCalledWith('e1'));
    expect(onInspect).toHaveBeenCalledWith(FULL);
  });
});

describe('ExecutionNodeInspect', () => {
  it('renders the matched node\'s input items and outputs per handle', () => {
    render(<ExecutionNodeInspect exec={FULL} node={FULL.nodes[0]} />);

    // Per-handle output bucket label.
    expect(screen.getByText('match')).toBeTruthy();
    // Pretty-printed JSON contains the recorded item data.
    expect(screen.getByText(/"message": "hi"/)).toBeTruthy();
    expect(screen.getByText(/"matched": true/)).toBeTruthy();
  });

  it('handles a node with no recorded data gracefully', () => {
    render(<ExecutionNodeInspect exec={FULL} node={null} />);
    expect(screen.getByText(/no recorded data/i)).toBeTruthy();
  });
});
