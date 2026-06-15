import { api } from './client';

// Bot runtime (the in-house "test bot" engine), reached via the gateway under /api/runtime.

// Start a chat session against a bot. -> { sessionId, greeting }
export const startSession = (botId) =>
  api(`/api/runtime/bots/${botId}/sessions`, { method: 'POST' });

// Send a user message into a live session.
// -> { reply, matched: { text } | null }  (matched === null means the fallback answer was used)
export const sendMessage = (sessionId, text) =>
  api(`/api/runtime/sessions/${sessionId}/messages`, { method: 'POST', body: { text } });
