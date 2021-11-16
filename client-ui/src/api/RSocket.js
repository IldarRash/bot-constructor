import RSocketWebSocketClient from "rsocket-websocket-client";
import {
  MESSAGE_RSOCKET_COMPOSITE_METADATA,
  RSocketClient
} from "rsocket-core";
import {BufferEncoders} from "rsocket-core/build";

function urlFromLocation() {
  const port = window.location.port ? `:${window.location.port}` : "";
  const isSecure = window.location.protocol === 'https:';
  return `${isSecure ? 'wss' : 'ws'}://localhost:9000/rsocket`;
}

export async function connect() {
  console.log("connecting");
  const socketClient = new RSocketClient({
    setup: {
      keepAlive: 30000,
      lifetime: 90000,
      dataMimeType: 'application/json',
      metadataMimeType: MESSAGE_RSOCKET_COMPOSITE_METADATA.string,
    },
    transport: new RSocketWebSocketClient({url : urlFromLocation()}, BufferEncoders),
  });
  return socketClient.connect();
}
