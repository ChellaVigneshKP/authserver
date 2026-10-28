import { mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { defineConfig } from "@playwright/test";

// Firefox 157 on macOS 27 cannot read ~/Library/Application Support/Firefox when
// launched directly instead of through LaunchServices (Mozilla bug 2069536, fixed
// in Firefox 158), so profile selection fails before the browser starts. Pointing
// the app-data directories at owned temp roots keeps each project isolated.
function firefoxAppData(name: string) {
  const root = join(tmpdir(), `portal-firefox-${name}`);
  const appData = join(root, "app-data");
  const localAppData = join(root, "local-app-data");
  for (const directory of [appData, localAppData])
    mkdirSync(directory, { recursive: true });
  return {
    ...process.env,
    MOZ_APP_DATA: appData,
    MOZ_LOCAL_APP_DATA: localAppData,
  } as Record<string, string>;
}

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
    {
      name: "firefox",
      use: {
        ...browserUse,
        browserName: "firefox",
        launchOptions: { env: firefoxAppData("firefox") },
      },
    },
    { name: "webkit", use: { ...browserUse, browserName: "webkit" } },
  ],
});
