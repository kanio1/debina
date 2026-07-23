import { writeFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";

const frontendOrigin = "http://frontend:3000";
const keycloakOrigin = "http://keycloak:8080";
const endToEndID = "D3B-DETAIL-LINEAGE-0001";

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

test("D3B Payment Detail exposes existing JSON_DIRECT lineage and timeline", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(new RegExp(`^${keycloakOrigin}/realms/sepa-nexus/`));
  await page.getByLabel("Username or email").fill(requiredEnv("SMOKE_SUBMITTER_USERNAME"));
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.locator('input#password[name="password"][type="password"]').fill(requiredEnv("SMOKE_SUBMITTER_PASSWORD"));
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page).toHaveURL(`${frontendOrigin}/payments`);

  await page.getByTestId("payments.submit.end-to-end-id-input").fill(endToEndID);
  await page.getByTestId("payments.submit.amount-input").fill("10.00");
  await page.getByTestId("payments.submit.currency-input").fill("EUR");
  await page.getByTestId("payments.submit.debtor-iban-input").fill("DE89370400440532013000");
  await page.getByTestId("payments.submit.creditor-iban-input").fill("FR7630006000011234567890189");
  await page.getByTestId("payments.submit.submit-button").click();
  await page.getByTestId("payments.submit.confirm-dialog.confirm-button").click();

  const link = page.getByTestId("payments.list.end-to-end-id-link").filter({ hasText: endToEndID });
  await expect(link).toHaveCount(1);
  const href = await link.getAttribute("href");
  expect(href).toMatch(/^\/payments\/[0-9a-f-]{36}$/);
  const paymentID = href!.slice("/payments/".length);
  await link.click();
  await expect(page).toHaveURL(`${frontendOrigin}${href}`);
  await expect(page.getByTestId("payment.detail.end-to-end-id")).toHaveText(endToEndID);
  await expect(page.getByTestId("payment.detail.status")).toHaveText(/RECEIVED/);
  await expect(page.getByTestId("payment.detail.timeline.entry")).toHaveCount(1);
  await expect(page.getByTestId("payment.detail.timeline.entry.event-ref")).toHaveText(/[0-9a-f-]{36}/);
  await expect(page.getByTestId("payment.detail.iso-identifiers.row")).toHaveCount(1);
  await expect(page.getByTestId("payment.detail.iso-identifiers.table")).toContainText("JSON_DIRECT");
  await expect(page.getByTestId("payment.detail.iso-identifiers.table")).toContainText(endToEndID);
  await writeFile("/tmp/phase-d-payment-id", paymentID, { encoding: "utf8", mode: 0o600 });
});
