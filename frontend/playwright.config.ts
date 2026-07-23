import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  retries: 0,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.SMOKE_BASE_URL ?? "http://frontend:3000",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [{ name: "chromium", use: { browserName: "chromium" } }],
});
