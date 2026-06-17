import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the runtime API so the panel runs without a gateway.
vi.mock('../api/runtime', () => ({
  startSession: vi.fn(),
  sendMessage: vi.fn(),
}));

import TestBotPanel from './TestBotPanel';
import { startSession, sendMessage } from '../api/runtime';

beforeEach(() => {
  vi.clearAllMocks();
  startSession.mockResolvedValue({ sessionId: 's1', greeting: 'Hi there' });
});

async function typeAndSend(text) {
  const box = await screen.findByPlaceholderText('Type a message…');
  fireEvent.change(box, { target: { value: text } });
  fireEvent.click(screen.getByRole('button', { name: 'Send' }));
}

describe('TestBotPanel execution trace', () => {
  it('shows a collapsed trace toggle and expands to steps + variables', async () => {
    sendMessage.mockResolvedValue({
      reply: 'Sure!',
      matched: { text: 'help' },
      trace: [
        { nodeId: 'n1', type: 'condition', handle: 'true', detail: '{{age}} > 18' },
        { nodeId: 'n2', type: 'http', handle: 'error', detail: 'timeout' },
        { nodeId: 'n3', type: 'message', handle: null, detail: 'Sure!' },
      ],
      vars: { age: 21, profile: { name: 'Ada' } },
    });

    render(<TestBotPanel botId="b1" botName="Demo" open onClose={() => {}} />);
    await screen.findByText('Hi there');

    await typeAndSend('help');

    // Toggle present, collapsed by default (steps not yet shown).
    const toggle = await screen.findByRole('button', { name: /trace \(3 steps\)/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('condition')).toBeNull();

    fireEvent.click(toggle);

    // Steps render with type, handle chip and detail.
    expect(await screen.findByText('condition')).toBeTruthy();
    expect(screen.getByText('→ true')).toBeTruthy();
    expect(screen.getByText('→ error')).toBeTruthy();
    expect(screen.getByText('{{age}} > 18')).toBeTruthy();

    // Final variables, objects JSON-stringified.
    expect(screen.getByText('variables')).toBeTruthy();
    expect(screen.getByText('age')).toBeTruthy();
    expect(screen.getByText('21')).toBeTruthy();
    expect(screen.getByText('{"name":"Ada"}')).toBeTruthy();
  });

  it('renders no trace toggle when the response omits a trace', async () => {
    sendMessage.mockResolvedValue({ reply: 'Hmm?', matched: null });

    render(<TestBotPanel botId="b1" botName="Demo" open onClose={() => {}} />);
    await screen.findByText('Hi there');

    await typeAndSend('???');

    await screen.findByText('Hmm?');
    // Fallback note still shown, but no trace disclosure.
    expect(screen.getByText('fallback answer (no keyword matched)')).toBeTruthy();
    await waitFor(() => expect(sendMessage).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: /trace/ })).toBeNull();
  });
});
