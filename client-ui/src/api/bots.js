import { api } from './client';

// All bot endpoints require auth and are owner-scoped.
// Bot shape (BotView / BotRequest):
//   { id?, name, type: "Instagram"|"Vkontakte"|"Telegram", ownerId?,
//     questions: [ { text, keyWords: [string], answer } ], fallbackAnswer }
// Each question carries its OWN answer; the bot has a single bot-level fallbackAnswer.
// The bodies are passed through verbatim — BotEditor maps flow nodes <-> questions[].
export const listBots = () => api('/api/bots');

export const getBot = (id) => api(`/api/bots/${id}`);

export const createBot = (body) => api('/api/bots', { method: 'POST', body });

export const updateBot = (id, body) => api(`/api/bots/${id}`, { method: 'PUT', body });

export const deleteBot = (id) => api(`/api/bots/${id}`, { method: 'DELETE' });
