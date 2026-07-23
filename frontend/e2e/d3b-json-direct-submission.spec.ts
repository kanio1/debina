import { writeFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";

const frontendOrigin = "http://frontend:3000";
const keycloakOrigin = "http://keycloak:8080";
const endToEndID = "D3B-JSON-DIRECT-0001";

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

async function login(page: import("@playwright/test").Page) {
  await page.goto("/");
  await expect(page).toHaveURL(new RegExp(`^${keycloakOrigin}/realms/sepa-nexus/`));
  await page.getByLabel("Username or email").fill(requiredEnv("SMOKE_SUBMITTER_USERNAME"));
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.locator('input#password[name="password"][type="password"]').fill(requiredEnv("SMOKE_SUBMITTER_PASSWORD"));
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page).toHaveURL(`${frontendOrigin}/payments`);
}

test("D3B JSON_DIRECT submits through the existing UI and BFF", async ({ page }) => {
  await login(page);
  await page.getByTestId("payments.submit.end-to-end-id-input").fill(endToEndID);
  await page.getByTestId("payments.submit.amount-input").fill("10.00");
  await page.getByTestId("payments.submit.currency-input").fill("EUR");
  await page.getByTestId("payments.submit.debtor-iban-input").fill("DE89370400440532013000");
  await page.getByTestId("payments.submit.creditor-iban-input").fill("FR7630006000011234567890189");
  await page.getByTestId("payments.submit.submit-button").click();
  await expect(page.getByTestId("payments.submit.confirm-dialog")).toBeVisible();
  const confirmButton = page.getByTestId("payments.submit.confirm-dialog.confirm-button");
  await expect(confirmButton).toBeEnabled();
  const submissionRequest = page.waitForRequest((request) =>
    new URL(request.url()).pathname === "/api/payments"
    && request.method() === "POST",
  );
  await confirmButton.click();
  await expect(confirmButton).toHaveText("Submitting…");
  const response = await (await submissionRequest).response();
  expect(response).not.toBeNull();
  expect(response!.status()).toBe(201);
  await expect(page.getByTestId("payments.submit.error")).toHaveCount(0);

  const payment = page.getByTestId("payments.list.end-to-end-id-link").filter({ hasText: endToEndID });
  await expect(payment).toHaveCount(1);
  const href = await payment.getAttribute("href");
  expect(href).toMatch(/^\/payments\/[0-9a-f-]{36}$/);
  await writeFile("/tmp/phase-d-payment-id", href!.slice("/payments/".length), { encoding: "utf8", mode: 0o600 });
});
