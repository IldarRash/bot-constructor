import { api } from './client';

// All bot endpoints require auth and are owner-scoped.
// Bot shape (BotView / BotRequest), per docs/workflow-engine.md:
//   { id?, name, type: "Instagram"|"Vkontakte"|"Telegram", ownerId?,
//     fallbackAnswer,
//     nodes: [ { id, type, position: {x,y}, data } ],
//     edges: [ { id, source, target, sourceHandle } ] }
// The graph mirrors React Flow's native node/edge shape; bodies are passed through
// verbatim. Legacy questions-based bots are converted to a graph by client-api on read.
export const listBots = () => api('/api/bots');

export const getBot = (id) => api(`/api/bots/${id}`);

export const createBot = (body) => api('/api/bots', { method: 'POST', body });

export const updateBot = (id, body) => api(`/api/bots/${id}`, { method: 'PUT', body });

export const deleteBot = (id) => api(`/api/bots/${id}`, { method: 'DELETE' });
