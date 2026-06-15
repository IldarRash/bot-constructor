import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  // Source files use the .js extension while containing JSX (CRA legacy).
  // Tell the react plugin (and its esbuild loader) to treat .js as JSX.
  plugins: [react({ include: /\.(js|jsx)$/ })],
  // rsocket-core references the Node `Buffer` global. Provide the npm `buffer` polyfill in the
  // browser bundle and alias the bare `buffer` import to it.
  resolve: {
    alias: { buffer: 'buffer/' },
  },
  define: {
    global: 'globalThis',
  },
  esbuild: {
    loader: 'jsx',
    include: /src\/.*\.jsx?$/,
    exclude: [],
  },
  optimizeDeps: {
    esbuildOptions: {
      loader: { '.js': 'jsx' },
    },
  },
  server: {
    port: 3002,
    proxy: {
      '/api': {
        target: process.env.VITE_GATEWAY_URL || 'http://localhost:8090',
        changeOrigin: true,
      },
      // RSocket collaboration WebSocket. ws:true proxies the upgrade to the gateway.
      '/rsocket': {
        target: process.env.VITE_GATEWAY_URL || 'http://localhost:8090',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/setupTests.js',
  },
});
