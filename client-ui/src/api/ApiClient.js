import {
  encodeCompositeMetadata,
  encodeRoute, MESSAGE_RSOCKET_COMPOSITE_METADATA,
  MESSAGE_RSOCKET_ROUTING, RSocketClient
} from "rsocket-core";
import {connect} from "./RSocket";
import RSocketWebSocketClient from "rsocket-websocket-client";
import {BufferEncoders} from "rsocket-core/build";

export function urlFromLocation() {
  const port = window.location.port ? `:${window.location.port}` : "";
  const isSecure = window.location.protocol === 'https:';
  return `${isSecure ? 'wss' : 'ws'}://localhost:9000/rsocket`;
}

export async function rsocket() {
  const connector = new RSocketClient({
    setup: {
      keepAlive: 30000,
      lifetime: 90000,
      dataMimeType: 'application/json',
      metadataMimeType: MESSAGE_RSOCKET_COMPOSITE_METADATA.string,
    },
    transport: new RSocketWebSocketClient({url: urlFromLocation()}, BufferEncoders),
  })
  return new Promise((resolve, reject) => {
    connector.connect()
      .subscribe({
        onComplete(r) {
          resolve(r);
        },
        onError(e) {
          reject(e);
        }
      })
  });
}
