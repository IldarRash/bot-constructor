import { api } from './client';

// Bot runtime (the in-house "test bot" engine), reached via the gateway under /api/runtime.

// Start a chat session against a bot. -> { sessionId, greeting }
export const startSession = (botId) =>
  api(`/api/runtime/bots/${botId}/sessions`, { method: 'POST' });

// Send a user message into a live session. Returns the full runtime response as-is:
// -> {
//      reply,
//      matched: { text } | null,   // matched === null means the fallback answer was used
//      trace?: Array<{ nodeId, type, handle: string|null, detail: string|null }>, // executed nodes, in order
//      vars?: object,              // final variable state after the walk
//    }
export const sendMessage = (sessionId, text) =>
  api(`/api/runtime/sessions/${sessionId}/messages`, { method: 'POST', body: { text } });

// Manually execute the saved flow ONCE (owner-scoped) with trigger "manual". The optional
// `message` / `vars` seed the run; `pinnedData` maps a nodeId to its pinned default-output items
// (`{ "<nodeId>": [ { json } ] }`) so pinned nodes are NOT re-executed (no outbound calls).
// -> ManualRunResponse = {
//      reply,
//      matched: { text } | null,
//      vars: object,
//      trace: Array<{ nodeId, type, handle, detail }>,
//      nodes: Array<{ nodeId, type, handle, detail, inputItems:[{json}],
//                     outputs:{ "<handle>": [{json}] }, error? }>,  // rich per-node data
//    }
export const executeWorkflow = (botId, { message, vars, pinnedData } = {}) =>
  api(`/api/runtime/bots/${botId}/execute`, {
    method: 'POST',
    body: { message, vars, pinnedData },
  });
