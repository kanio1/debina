import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.SMOKE_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
    launchOptions: {
      // The realm permits localhost callbacks. Within the Dagger browser
      // container only, resolve that approved callback host to the frontend
      // service binding; no host networking or realm change is involved.
      args: ["--host-resolver-rules=MAP localhost frontend"],
    },
  },
  projects: [{ name: "chromium", use: { browserName: "chromium" } }],
});
