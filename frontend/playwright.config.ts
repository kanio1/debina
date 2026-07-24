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
  projects: [
    {
      name: "chromium",
      use: { browserName: "chromium" },
      testIgnore: ["**/e1-signed-pain001.spec.ts"],
    },
    {
      name: "e1-pain001",
      testMatch: ["**/e1-signed-pain001.spec.ts"],
      workers: 1,
      use: { browserName: "chromium" },
    },
  ],
});
