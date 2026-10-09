import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

const DEFAULT_API_URL = 'http://localhost:5120';

// The browser only talks to the Vite server; only /health/* is forwarded, so the API needs no CORS.
// The trailing slash matters: Vite matches proxy keys by prefix, so '/health' would also forward '/healthx'.
// TRACEON_API_URL has no VITE_ prefix on purpose: it stays in the Node process and never reaches the bundle.
function resolveApiUrl(mode: string): string {
  const configured = loadEnv(mode, process.cwd(), '').TRACEON_API_URL ?? DEFAULT_API_URL;
  const url = new URL(configured);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error(`TRACEON_API_URL deve usar http ou https; recebido: ${url.protocol}`);
  }
  return url.origin;
}

export default defineConfig(({ mode }) => {
  const proxy = { '/health/': { target: resolveApiUrl(mode), changeOrigin: false } };
  return {
    plugins: [react(), tailwindcss()],
    server: { proxy },
    preview: { proxy },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
      unstubGlobals: true,
      css: { include: [/index\.css/] },
    },
  };
});
