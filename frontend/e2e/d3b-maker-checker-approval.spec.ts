import { writeFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import { expect, test, type BrowserContext, type Page } from "@playwright/test";

const frontendOrigin = "http://frontend:3000";
const keycloakOrigin = "http://keycloak:8080";
const endToEndID = "D3B-MAKER-CHECKER-0001";

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

async function login(context: BrowserContext, username: string, password: string): Promise<Page> {
  const page = await context.newPage();
  await page.goto("/");
  await expect(page).toHaveURL(new RegExp(`^${keycloakOrigin}/realms/sepa-nexus/`));
  await page.getByLabel("Username or email").fill(username);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.locator('input#password[name="password"][type="password"]').fill(password);
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page).toHaveURL(`${frontendOrigin}/payments`);
  return page;
}

async function submitPendingPayment(page: Page): Promise<string> {
  await page.getByTestId("payments.submit.end-to-end-id-input").fill(endToEndID);
  await page.getByTestId("payments.submit.amount-input").fill("10.00");
  await page.getByTestId("payments.submit.currency-input").fill("EUR");
  await page.getByTestId("payments.submit.debtor-iban-input").fill("DE89370400440532013000");
  await page.getByTestId("payments.submit.creditor-iban-input").fill("FR7630006000011234567890189");
  await page.getByTestId("payments.submit.submit-button").click();
  const submissionResponse = page.waitForResponse((response) => {
    const request = response.request();
    return new URL(response.url()).pathname === "/api/payments" && request.method() === "POST";
  });
  await page.getByTestId("payments.submit.confirm-dialog.confirm-button").click();
  const response = await submissionResponse;
  expect(response.status()).toBe(202);
  const result = (await response.json()) as { paymentId?: string; approvalStatus?: string };
  expect(result.approvalStatus).toBe("PENDING_APPROVAL");
  expect(result.paymentId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  await expect(page.getByTestId("payments.submit.error")).toHaveCount(0);
  return result.paymentId!;
}

test("D3B maker cannot approve and independent checker can approve", async ({ browser }) => {
  const maker = await browser.newContext();
  const checker = await browser.newContext();
  try {
    const makerPage = await login(maker, requiredEnv("SMOKE_SUBMITTER_USERNAME"), requiredEnv("SMOKE_SUBMITTER_PASSWORD"));
    const paymentID = await submitPendingPayment(makerPage);

    await expect(makerPage.getByTestId("payments.approvals.queue.unauthorized")).toBeVisible();
    const denied = await makerPage.evaluate(async ({ id, idempotencyKey }) => {
      const csrf = document.cookie.split("; ").find((entry) => entry.startsWith("sepa_csrf="))?.slice("sepa_csrf=".length) ?? "";
      const response = await fetch(`/api/payments/${id}/approve`, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", "x-csrf-token": csrf, "Idempotency-Key": idempotencyKey },
        body: "{}",
      });
      return response.status;
    }, { id: paymentID, idempotencyKey: randomUUID() });
    expect(denied).toBe(403);

    const checkerPage = await login(checker, requiredEnv("SMOKE_APPROVER_USERNAME"), requiredEnv("SMOKE_APPROVER_PASSWORD"));
    const approve = checkerPage.getByTestId(`payments.approvals.queue.approve.${paymentID}`);
    await expect(approve).toBeEnabled();
    await approve.click();
    await checkerPage.getByTestId("payments.approvals.approve.confirm-button").click();
    await expect(checkerPage.getByTestId(`payments.approvals.queue.row.${paymentID}`)).toHaveCount(0);
    await writeFile("/tmp/phase-d-payment-id", paymentID, { encoding: "utf8", mode: 0o600 });
  } finally {
    await maker.close();
    await checker.close();
  }
});
