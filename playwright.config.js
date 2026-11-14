// Drives the real application through a browser: the demo Compose stack, which already
// brings its own PostgreSQL. Nothing here stubs the server, so a page that only works
// with JavaScript, or an accessibility rule ReleaseFlow breaks, is caught for real. The
// only stand-ins are for providers: tests/e2e/compose.yaml adds a Jira that answers with an
// open issue, and a GitHub that accepts a token, so the suite can reach the linked-issue
// sections of a release.
import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.RELEASEFLOW_E2E_BASE_URL ?? 'http://127.0.0.1:8080';

// The stack the suite starts never reads an AI or DeepL setting from .env, as CI's .env has
// none: every change the suite delivers would otherwise be sent to a paid service (the 1.1.0
// run sent about 20 OpenAI requests that way). Compose takes the shell's value over .env's,
// and a blank provider leaves AI off and translation disabled.
const NO_PROVIDERS = Object.fromEntries([
  'RELEASEFLOW_AI_PROVIDER',
  'RELEASEFLOW_OPENAI_API_KEY',
  'RELEASEFLOW_OPENAI_MODEL',
  'RELEASEFLOW_ANTHROPIC_API_KEY',
  'RELEASEFLOW_ANTHROPIC_MODEL',
  'RELEASEFLOW_DEEPSEEK_API_KEY',
  'RELEASEFLOW_DEEPSEEK_MODEL',
  'RELEASEFLOW_TRANSLATION_PROVIDER',
  'RELEASEFLOW_DEEPL_API_KEY',
  'RELEASEFLOW_DEEPL_BASE_URL',
].map((name) => [name, '']));

export default defineConfig({
  testDir: 'tests/e2e',
  // A page is server-rendered, so a failure is a failure; a retry would only hide a race.
  retries: 0,
  // One worker: the suite registers Organizations in one shared database.
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    // The two shapes the accessibility pass is held to: a narrow phone in the light
    // theme, and a wide desktop in the dark one.
    {
      name: 'phone-light',
      use: { ...devices['Desktop Chrome'], viewport: { width: 360, height: 780 }, colorScheme: 'light' },
    },
    {
      name: 'desktop-dark',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, colorScheme: 'dark' },
    },
  ],
  webServer: process.env.RELEASEFLOW_E2E_BASE_URL
    ? undefined
    : {
        command: 'docker compose -f docker-compose.demo.yml -f tests/e2e/compose.yaml up --build app db providers',
        env: NO_PROVIDERS,
        url: `${baseURL}/api/status`,
        // An application already answering may have been started from .env with a provider,
        // so it is never reused: stop it, or name it with RELEASEFLOW_E2E_BASE_URL.
        reuseExistingServer: false,
        timeout: 300_000,
        stdout: 'pipe',
        stderr: 'pipe',
      },
});
