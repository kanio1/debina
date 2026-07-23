import { test } from "@playwright/test";

test("D6 controlled graph-local navigation failure", async ({ page }) => {
  test.setTimeout(15_000);
  await page.goto("http://phase-d-browser-navigation-unavailable:65535/", {
    timeout: 5_000,
    waitUntil: "domcontentloaded",
  });
});
