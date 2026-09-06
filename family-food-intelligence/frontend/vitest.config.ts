import { defineConfig } from 'vitest/config';
import path from 'node:path';

/**
 * Separate from vite.config.ts on purpose: Vitest bundles its own copy of Vite,
 * so sharing one config file makes the two plugin type universes collide. The
 * test run does not need the PWA or React-refresh plugins anyway.
 */
export default defineConfig({
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
