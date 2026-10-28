import { defineConfig } from "@playwright/test";

// Acceptance uses real installed browsers; no response interception anywhere.
const browserUse = {
  headless: true,
  locale: "en-US",
  userAgent: "PortalAcceptance/1.0",
  extraHTTPHeaders: { "Accept-Language": "en-US" },
  timezoneId: "Asia/Kolkata",
  trace: "off",
  screenshot: "off",
  video: "off",
} as const;

export default defineConfig({
  testDir: "./tests/e2e",
  workers: 1,
  retries: 0,
  timeout: 120_000,
  reporter: "line",
  use: browserUse,
  projects: [
    { name: "chromium", use: { ...browserUse, browserName: "chromium", channel: "chrome" } },
    { name: "firefox", use: { ...browserUse, browserName: "firefox" } },
    { name: "webkit", use: { ...browserUse, browserName: "webkit" } },
  ],
});
