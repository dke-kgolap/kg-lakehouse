import { test, expect } from "@playwright/test";

/**
 * End-to-end smoke for the Query Workspace. Requires the backend stack up and
 * at least one schema (`atm`) with ingested data. Verifies streaming, the
 * result views, and — critically — that the browser never calls the backend
 * directly (the microservice boundary).
 */
test("run a query, stream results, switch views, respect the boundary", async ({
  page,
}) => {
  const offBoundary: string[] = [];
  page.on("request", (req) => {
    const url = req.url();
    if (!url.startsWith("http://localhost:3001") && url.startsWith("http")) {
      offBoundary.push(url);
    }
    if (/:8080|\/surface/.test(url)) offBoundary.push(url);
  });

  await page.goto("/workspace");
  await page.locator("#schema-picker").selectOption("atm");
  await page.getByRole("button", { name: "Run query" }).click();

  // Summary strip populates from the trailing NDJSON summary line.
  await expect(page.getByText("Success")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("Quads")).toBeVisible();

  // Graph renders a canvas.
  await expect(page.locator("canvas").first()).toBeVisible();

  // Table view shows quad rows.
  await page.getByRole("tab", { name: "Table" }).click();
  await expect(page.getByText(/rows/)).toBeVisible();

  // Cube view shows matched context cells.
  await page.getByRole("tab", { name: "Cube" }).click();
  await expect(page.getByText(/matched context cell/)).toBeVisible();

  // Raw view shows NDJSON + the summary line.
  await page.getByRole("tab", { name: "Raw" }).click();
  await expect(page.getByText(/"_type":"summary"/)).toBeVisible();

  // Boundary: no request ever left :3001 for the backend.
  expect(offBoundary).toEqual([]);
});

test("error queries surface as a UI error, not a crash", async ({ page }) => {
  await page.goto("/workspace");
  await page.locator("#schema-picker").selectOption("atm");
  await page.getByRole("tab", { name: "DSL" }).click();
  await page
    .getByPlaceholder(/SELECT/)
    .fill("NOT A VALID QUERY");
  await page.getByRole("button", { name: "Run query" }).click();
  await expect(page.getByText("Error")).toBeVisible({ timeout: 30_000 });
});
