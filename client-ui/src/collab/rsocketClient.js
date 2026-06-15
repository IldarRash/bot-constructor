import { Buffer } from 'buffer';
import { RSocketConnector } from 'rsocket-core';
import { WebsocketClientTransport } from 'rsocket-websocket-client';
import {
  encodeCompositeMetadata,
  encodeRoute,
  WellKnownMimeType,
} from 'rsocket-composite-metadata';
import { getToken } from '../api/client';

// rsocket-core works with Buffers; expose one globally for any code path that expects it.
if (typeof window !== 'undefined' && !window.Buffer) window.Buffer = Buffer;

const JSON_MIME = 'application/json';
const COMPOSITE_MIME = 'message/x.rsocket.composite-metadata.v0';

// Build the "/rsocket" websocket URL relative to the page origin so the Vite proxy applies in dev
// and the gateway serves it in prod.
function rsocketUrl() {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/rsocket`;
}

// Composite metadata carrying just the RSocket routing entry for a destination like "board.123".
function routeMetadata(route) {
  return encodeCompositeMetadata(
    new Map([[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, encodeRoute(route)]]),
  );
}

const encode = (obj) => Buffer.from(JSON.stringify(obj));
const decode = (buf) => {
  try {
    return JSON.parse(Buffer.from(buf).toString('utf8'));
  } catch {
    return null;
  }
};

/**
 * Connect to the collaboration server, subscribe to a board's event stream, and expose helpers to
 * push edits / cursor updates. Resilient: connection or stream errors are reported via callbacks but
 * never thrown to the caller — the editor keeps working standalone.
 *
 * @param {string} boardId
 * @param {object} handlers { onEvent(boardEvent), onStatus('connecting'|'online'|'offline') }
 * @returns {Promise<{ sendEdit(event): void, close(): void }>}
 */
export async function connectBoard(boardId, { onEvent, onStatus, clientId } = {}) {
  const status = (s) => onStatus && onStatus(s);
  status('connecting');

  const token = getToken();
  const connector = new RSocketConnector({
    transport: new WebsocketClientTransport({
      url: rsocketUrl(),
      wsCreator: (url) => new WebSocket(url),
    }),
    setup: {
      dataMimeType: JSON_MIME,
      metadataMimeType: COMPOSITE_MIME,
      // clientId binds this tab's collaborator identity server-side so the server tags our events
      // with it — letting this tab suppress its own echoes and exclude itself from presence.
      payload: { data: encode({ token, clientId }) },
    },
  });

  let rsocket;
  try {
    rsocket = await connector.connect();
  } catch (err) {
    status('offline');
    throw err;
  }

  let closed = false;
  status('online');

  // Subscribe to the board stream. requestN large so the server can push freely.
  const requester = rsocket.requestStream(
    {
      data: Buffer.alloc(0),
      metadata: routeMetadata(`board.${boardId}`),
    },
    2147483647,
    {
      onNext: (payload) => {
        const event = payload && payload.data ? decode(payload.data) : null;
        if (event && onEvent) onEvent(event);
      },
      onComplete: () => {
        if (!closed) status('offline');
      },
      onError: () => {
        if (!closed) status('offline');
      },
      onExtension: () => {},
    },
  );

  const sendEdit = (event) => {
    if (closed) return;
    try {
      rsocket.fireAndForget(
        {
          data: encode(event),
          metadata: routeMetadata(`board.${boardId}.edit`),
        },
        { onError: () => {}, onComplete: () => {} },
      );
    } catch {
      // best-effort; collaboration is additive
    }
  };

  const close = () => {
    if (closed) return;
    closed = true;
    try {
      requester && requester.cancel && requester.cancel();
    } catch {
      /* ignore */
    }
    try {
      rsocket.close();
    } catch {
      /* ignore */
    }
    status('offline');
  };

  return { sendEdit, close };
}
