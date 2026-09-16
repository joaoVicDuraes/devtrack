import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Vite + Vitest configuration.
// The `test` block is read by Vitest (Vitest reuses Vite's config), so we keep
// a single config file for both the dev/build tooling and the test runner.
export default defineConfig({
  plugins: [react()],
  // Dev server configuration.
  server: {
    // Why this proxy exists:
    // During development the frontend runs on the Vite dev server (port 5173)
    // and the Spring Boot backend runs on port 8080 — two different origins.
    // The API module (src/api/taskApi.js) uses relative URLs like fetch('/tasks').
    // Without a proxy, '/tasks' hits Vite itself, which returns index.html (HTML),
    // and the JSON parse fails with "Unexpected token '<'". The proxy forwards
    // API routes to the backend so the relative URLs keep working unchanged.
    proxy: {
      // Any request starting with '/tasks' (GET/POST /tasks, /tasks/{id},
      // /tasks/{id}/status, /tasks/{id}/priority, ...) is forwarded to the backend.
      '/tasks': {
        target: 'http://localhost:8080',
        // changeOrigin rewrites the outgoing Host header to match the target
        // (localhost:8080). This avoids virtual-host / CORS issues in dev by
        // making the backend see the request as if it came from its own origin.
        changeOrigin: true,
      },
    },
    // Note: this proxy only applies to the Vite dev server. In production the
    // SPA would be served alongside the backend or behind a reverse proxy, so
    // no Vite proxy is involved there.
  },
  test: {
    // Component and DOM-based tests need a browser-like environment; jsdom
    // provides a simulated DOM so React Testing Library can render components.
    environment: 'jsdom',
    // Makes describe/it/expect available without importing them in every file.
    globals: true,
    // Runs once before the test suite to register jest-dom matchers.
    setupFiles: './src/test/setup.js',
    // At scaffold time there are no test files yet. Without this, Vitest exits
    // with an error when it finds none; later tasks add the actual tests.
    passWithNoTests: true,
  },
});
