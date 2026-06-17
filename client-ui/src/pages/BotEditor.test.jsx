import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// --- Mocks ------------------------------------------------------------------
// Stub @xyflow/react so node selection is deterministic in jsdom: each node renders as a button
// that fires onSelectionChange with that node. We don't need the real flow engine to test the
// Run -> inline node data -> pin/unpin loop.
let selectionCb = null;
const { useStateRef } = vi.hoisted(() => {
  // eslint-disable-next-line global-require
  return { useStateRef: require('react').useState };
});
vi.mock('@xyflow/react', () => {
  const Passthrough = ({ children }) => <div>{children}</div>;
  const ReactFlow = ({ nodes, onSelectionChange, children }) => {
    selectionCb = onSelectionChange;
    return (
      <div data-testid="flow">
        {nodes.map((n) => (
          <button
            key={n.id}
            data-testid={`node-${n.id}`}
            className={n.className || ''}
            onClick={() => onSelectionChange && onSelectionChange({ nodes: [n] })}
          >
            {n.id}
          </button>
        ))}
        {children}
      </div>
    );
  };
  return {
    ReactFlow,
    ReactFlowProvider: Passthrough,
    Controls: () => null,
    Background: () => null,
    BackgroundVariant: { Dots: 'dots' },
    MiniMap: () => null,
    Handle: () => null,
    Position: { Top: 'top', Bottom: 'bottom', Left: 'left', Right: 'right' },
    useNodesState: (initial) => {
      const [nodes, setNodes] = useStateRef(initial);
      return [nodes, setNodes, () => {}];
    },
    useEdgesState: (initial) => {
      const [edges, setEdges] = useStateRef(initial);
      return [edges, setEdges, () => {}];
    },
    useReactFlow: () => ({ screenToFlowPosition: ({ x, y }) => ({ x, y }) }),
  };
});

// Collaboration is irrelevant here — return inert handlers.
vi.mock('../hooks/useBoardCollaboration', () => ({
  useBoardCollaboration: () => ({
    status: 'offline',
    peers: [],
    cursors: {},
    onNodesChange: () => {},
    onEdgesChange: () => {},
    onConnect: () => {},
    broadcastNodeAdd: () => {},
    broadcastNodeRemove: () => {},
    broadcastEdgeRemove: () => {},
    broadcastNodesChange: () => {},
    sendCursor: () => {},
  }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => () => {},
  useParams: () => ({ id: 'bot1' }),
}));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { email: 'owner@example.com' } }),
}));

vi.mock('../components/Toast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

vi.mock('../api/bots', () => ({
  getBot: vi.fn(),
  createBot: vi.fn(),
  updateBot: vi.fn(),
}));

vi.mock('../api/credentials', () => ({ listCredentials: vi.fn() }));

vi.mock('../api/runtime', () => ({ executeWorkflow: vi.fn() }));

import BotEditor from './BotEditor';
import { getBot, updateBot } from '../api/bots';
import { listCredentials } from '../api/credentials';
import { executeWorkflow } from '../api/runtime';

const HTTP_OUTPUT = { json: { status: 200, body: { hi: 'there' } } };

beforeEach(() => {
  vi.clearAllMocks();
  selectionCb = null;
  listCredentials.mockResolvedValue([]);
  updateBot.mockResolvedValue({ id: 'bot1' });
  getBot.mockResolvedValue({
    name: 'Demo',
    type: 'Telegram',
    nodes: [
      { id: 'trigger', type: 'trigger', position: { x: 0, y: 0 }, data: {} },
      {
        id: 'http1',
        type: 'httpRequest',
        position: { x: 100, y: 100 },
        data: { method: 'GET', url: 'https://api.example.com', saveAs: 'http' },
      },
    ],
    edges: [],
  });
  executeWorkflow.mockResolvedValue({
    reply: 'Hello there',
    matched: { text: 'hi' },
    vars: { http: { hi: 'there' } },
    trace: [{ nodeId: 'http1', type: 'httpRequest', handle: 'success', detail: null }],
    nodes: [
      {
        nodeId: 'http1',
        type: 'httpRequest',
        handle: 'success',
        inputItems: [{ json: { message: 'hi' } }],
        outputs: { default: [HTTP_OUTPUT] },
      },
    ],
  });
});

async function runFlow() {
  // Wait for the loaded editor (Run button present), then click Run.
  const runBtn = await screen.findByRole('button', { name: /Run/ });
  fireEvent.click(runBtn);
}

describe('BotEditor manual Run + pin/unpin', () => {
  it('runs the saved flow and shows inline node data when a node is selected', async () => {
    render(<BotEditor />);
    await runFlow();

    // Saves in place, then executes the saved bot.
    await waitFor(() => expect(updateBot).toHaveBeenCalled());
    await waitFor(() => expect(executeWorkflow).toHaveBeenCalledWith('bot1', expect.any(Object)));

    // The run summary shows the reply.
    expect(await screen.findByText('Hello there')).toBeTruthy();

    // Select the http node on the canvas -> inline input/output from the run is shown.
    fireEvent.click(screen.getByTestId('node-http1'));
    expect(await screen.findByText(/execution manual/)).toBeTruthy();
    // The HTTP output item JSON is rendered by the reused ExecutionNodeInspect.
    expect(screen.getByText(/"hi": "there"/)).toBeTruthy();
  });

  it('pin captures the node output and the next run sends pinnedData; unpin clears it', async () => {
    render(<BotEditor />);
    await runFlow();
    await screen.findByText('Hello there');

    // Select http node, then pin its output.
    fireEvent.click(screen.getByTestId('node-http1'));
    const pinBtn = await screen.findByRole('button', { name: /Pin output/ });
    fireEvent.click(pinBtn);

    // Pinned badge appears and the canvas node gets the pinned class.
    expect(await screen.findByText('📌 pinned')).toBeTruthy();
    await waitFor(() =>
      expect(screen.getByTestId('node-http1').className).toMatch(/node--pinned/),
    );

    // Run again -> pinnedData includes the captured default output for http1.
    fireEvent.click(screen.getByRole('button', { name: /Run/ }));
    await waitFor(() => expect(executeWorkflow).toHaveBeenCalledTimes(2));
    const secondCall = executeWorkflow.mock.calls[1];
    expect(secondCall[0]).toBe('bot1');
    expect(secondCall[1].pinnedData).toEqual({ http1: [HTTP_OUTPUT] });

    // Re-select (run reset inspect mode) and unpin.
    fireEvent.click(screen.getByTestId('node-http1'));
    const unpinBtn = await screen.findByRole('button', { name: /Unpin output/ });
    fireEvent.click(unpinBtn);
    await waitFor(() =>
      expect(screen.getByTestId('node-http1').className).not.toMatch(/node--pinned/),
    );

    // A subsequent run no longer sends pinnedData.
    fireEvent.click(screen.getByRole('button', { name: /Run/ }));
    await waitFor(() => expect(executeWorkflow).toHaveBeenCalledTimes(3));
    expect(executeWorkflow.mock.calls[2][1].pinnedData).toBeUndefined();
  });
});
