import { expect, test } from "@playwright/test";

test("ADR-N16 D3A: Keycloak PKCE login establishes a BFF-only session and runtime is healthy", async ({ page, context }) => {
  const baseURL = process.env.SMOKE_BASE_URL ?? "http://localhost:3000";
  const healthURL = process.env.SMOKE_BACKEND_HEALTH_URL;
  if (!healthURL) throw new Error("SMOKE_BACKEND_HEALTH_URL is required");

  const health = await page.request.get(healthURL);
  await expect(health).toBeOK();
  await expect(health.json()).resolves.toMatchObject({ status: "UP" });

  await page.goto(`${baseURL}/api/auth/login`);
  await page.locator("#username").fill("submitter");
  await page.locator("#password").fill(process.env.SMOKE_SUBMITTER_PASSWORD ?? "");
  await page.locator("#kc-login").click();
  await page.waitForURL(`${baseURL}/`);

  await page.goto(`${baseURL}/api/session`);
  const session = JSON.parse(await page.locator("body").textContent() ?? "{}") as {
    claims?: { preferredUsername?: string | null; roles?: string[] };
    accessToken?: unknown;
    refreshToken?: unknown;
  };
  expect(session.claims?.preferredUsername).toBe("submitter");
  expect(session.claims?.roles).toContain("payment_submitter");
  expect(session).not.toHaveProperty("accessToken");
  expect(session).not.toHaveProperty("refreshToken");

  const cookies = await context.cookies(baseURL);
  expect(cookies.find((cookie) => cookie.name === "sepa_session")).toMatchObject({ httpOnly: true });
});
