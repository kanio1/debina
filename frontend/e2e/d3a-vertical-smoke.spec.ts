import { expect, test } from "@playwright/test";

const frontendOrigin = "http://frontend:3000";
const keycloakOrigin = "http://keycloak:8080";

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

test("ADR-N16 D3A: Keycloak PKCE login establishes a BFF-only session", async ({ page, context }) => {
  await page.goto("/");

  await expect(page).toHaveURL(new RegExp(`^${keycloakOrigin}/realms/sepa-nexus/`));
  const usernameInput = page.getByLabel("Username or email");
  await expect(usernameInput).toHaveCount(1);
  await expect(usernameInput).toBeVisible();
  await usernameInput.fill(requiredEnv("SMOKE_SUBMITTER_USERNAME"));

  const continueButton = page.getByRole("button", { name: "Sign In" });
  await expect(continueButton).toHaveCount(1);
  await continueButton.click();

  const passwordInput = page.locator('input#password[name="password"][type="password"]');
  await expect(passwordInput).toHaveCount(1);
  await expect(passwordInput).toBeVisible();
  await passwordInput.fill(requiredEnv("SMOKE_SUBMITTER_PASSWORD"));
  await expect(page.getByRole("button", { name: "Sign In" })).toHaveCount(1);
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page).toHaveURL(`${frontendOrigin}/payments`);

  const sessionCookie = (await context.cookies(frontendOrigin)).find((cookie) => cookie.name === "sepa_session");
  expect(sessionCookie).toMatchObject({
    httpOnly: true,
    sameSite: "Lax",
    path: "/",
    secure: false,
  });
  expect(sessionCookie?.domain).toBe("frontend");

  const session = await page.evaluate(async () => {
    const response = await fetch("/api/session", { credentials: "same-origin" });
    return { status: response.status, body: await response.json() as unknown };
  });
  expect(session.status).toBe(200);
  expect(session.body).toMatchObject({
    claims: {
      roles: expect.arrayContaining(["payment_submitter"]),
    },
  });
  expect(session.body).toHaveProperty("claims.sub");
  expect((session.body as { claims: { sub: unknown } }).claims.sub).toEqual(expect.any(String));
  expect((session.body as { claims: { sub: string } }).claims.sub).not.toBe("");
  expect((session.body as { claims: { preferredUsername: unknown } }).claims.preferredUsername).toBeNull();
  expect(session.body).not.toHaveProperty("accessToken");
  expect(session.body).not.toHaveProperty("refreshToken");
  expect(session.body).not.toHaveProperty("idToken");
  expect(session.body).not.toHaveProperty("code");
  expect(session.body).not.toHaveProperty("clientSecret");
  expect(session.body).not.toHaveProperty("sepa_session");

  await expect(page.getByTestId("app-shell.sidebar")).toBeVisible();
  await expect(page.getByTestId("app-shell.current-user")).toHaveText("unknown user");
});
