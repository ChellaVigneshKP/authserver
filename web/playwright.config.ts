import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/e2e",
  workers: 1,
  retries: 0,
  timeout: 120_000,
  reporter: "line",
  use: {
    browserName: "chromium",
    channel: "chrome",
    headless: true,
    locale: "en-US",
    userAgent: "PortalAcceptance/1.0",
    extraHTTPHeaders: { "Accept-Language": "en-US" },
    timezoneId: "Asia/Kolkata",
    trace: "off",
    screenshot: "off",
    video: "off",
  },
});
