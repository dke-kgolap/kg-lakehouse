import { defineConfig, devices } from "@playwright/test";

/**
 * E2E config. Assumes the backend stack (surface + deps) is up and the web app
 * is running on :3001 (e.g. `npm run dev` or `docker compose up web`).
 * Run: `npm run test:e2e`.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  fullyParallel: false,
  reporter: "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3001",
    trace: "on-first-retry",
    channel: "chrome",
    // The app passes through surface's HTTP Basic auth; supply test creds.
    httpCredentials: {
      username: process.env.E2E_USER ?? "admin",
      password: process.env.E2E_PASSWORD ?? "admin",
    },
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"], channel: "chrome" } },
  ],
});
