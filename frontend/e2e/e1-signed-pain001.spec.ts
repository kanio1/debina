import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";

const frontendOrigin = "http://frontend:3000";
const keycloakOrigin = "http://keycloak:8080";

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function loadFixtureMeta() {
  const fixturesDir = join(process.cwd(), "e2e", "fixtures");
  return {
    fixturesDir,
    meta: JSON.parse(readFileSync(join(fixturesDir, "e1-signed-pain001.meta.json"), "utf8")) as {
      participantId: string;
      signatureBase64: string;
      endToEndId: string;
      amount: string;
      currency: string;
      debtorIban: string;
      creditorIban: string;
    },
  };
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

test("E1 signed pain.001 upload reaches payment detail", async ({ page }) => {
  const { fixturesDir, meta: fixtureMeta } = loadFixtureMeta();
  await login(page);

  await page.getByTestId("payments.pain001-upload.file-input").setInputFiles(join(fixturesDir, "e1-signed-pain001.xml"));
  await expect(page.getByTestId("payments.pain001-upload.selected-file")).toContainText("e1-signed-pain001.xml");
  await page.getByTestId("payments.pain001-upload.signer-id-input").fill(fixtureMeta.participantId);
  await page.getByTestId("payments.pain001-upload.signature-input").fill(fixtureMeta.signatureBase64);

  const submissionResponse = page.waitForResponse((response) => {
    const request = response.request();
    return new URL(response.url()).pathname === "/api/iso/pain001" && request.method() === "POST";
  });

  await page.getByTestId("payments.pain001-upload.submit-button").click();
  const response = await submissionResponse;
  const request = response.request();
  expect(request.method()).toBe("POST");
  expect(new URL(request.url()).pathname).toBe("/api/iso/pain001");
  expect(new URL(request.url()).origin).toBe(frontendOrigin);
  expect(request.headers()["x-csrf-token"]).toBeTruthy();
  expect(request.headers().authorization ?? "").toBe("");
  expect(response.status()).toBe(201);
  expect(response.headers()["content-type"] ?? "").not.toContain("application/json");

  await expect(page.getByTestId("payments.pain001-upload.result")).toBeVisible();
  await expect(page.getByTestId("payments.pain001-upload.result.status")).toContainText("NOT_REQUIRED");

  const detailLink = page.getByTestId("payments.pain001-upload.result.detail-link");
  await expect(detailLink).toBeVisible();
  const href = await detailLink.getAttribute("href");
  expect(href).toMatch(/^\/payments\/[0-9a-f-]{36}$/);
  const resultPaymentId = href!.slice("/payments/".length);

  await detailLink.click();
  await expect(page).toHaveURL(new RegExp(`${frontendOrigin}/payments/[0-9a-f-]{36}$`));
  const detailPaymentId = page.url().slice(`${frontendOrigin}/payments/`.length);
  expect(detailPaymentId).toBe(resultPaymentId);
  await expect(page.getByTestId("payment.detail.end-to-end-id")).toContainText(fixtureMeta.endToEndId);
  await expect(page.getByTestId("payment.detail.amount")).toContainText(fixtureMeta.amount);
  await expect(page.getByTestId("payment.detail.amount")).toContainText(fixtureMeta.currency);
  await expect(page.getByTestId("payment.detail.debtor-iban")).toContainText(fixtureMeta.debtorIban);
  await expect(page.getByTestId("payment.detail.creditor-iban")).toContainText(fixtureMeta.creditorIban);
});
