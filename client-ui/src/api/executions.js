import { api } from './client';

// Execution history (n8n-style debugger). Records are produced server-to-server by bot-api and
// read back here through the gateway, JWT-authenticated and owner-scoped.

// List a bot's executions, newest-first. Returns SUMMARIES (no heavy items):
//   -> Array<{ id, botId, status, trigger, startedAt, finishedAt, message, reply, nodeCount }>
export const listExecutions = (botId, { limit = 20, offset = 0 } = {}) =>
  api(`/api/bots/${botId}/executions?limit=${limit}&offset=${offset}`);

// Fetch a single FULL execution, including per-node inputItems/outputs:
//   -> { id, botId, status, trigger, startedAt, finishedAt, message, reply,
//        nodes: [ { nodeId, type, handle, detail, inputItems, outputs, error } ] }
export const getExecution = (execId) => api(`/api/executions/${execId}`);
