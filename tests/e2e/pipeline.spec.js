// The path a release actually takes, through the pages rather than the API: a Project,
// a source, a draft release, and the switcher that remembers which Project was open.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

test('a project is created, a source connected, and a release drafted', async ({ page }) => {
  await registerAndSignIn(page);

  // The overview offers exactly one next step, and it is the first unfinished one.
  await expect(page.locator('#next-step')).toContainText(/create a project|tạo một project/i);

  const project = await createProject(page);
  await page.goto('/');
  await expect(page.locator('#next-step')).toContainText(/connect a source|kết nối một nguồn/i);

  await page.goto('/projects');
  await page.getByLabel(/repository owner|chủ repository/i).fill('acme');
  await page.getByLabel(/repository name|tên repository/i).fill(unique('app'));
  await page.getByRole('button', { name: /generate webhook credentials|sinh thông tin webhook/i }).click();
  await expect(page.getByRole('heading', { name: /save these webhook credentials|lưu thông tin webhook/i }))
    .toBeVisible();
  await expectNoAccessibilityViolations(page, 'source connected');

  // With a source but no changes, the overview waits rather than inventing work.
  await page.goto('/');
  await expect(page.locator('#next-step')).toContainText(/waiting for the first change|chờ thay đổi đầu tiên/i);

  const version = '1.0.0';
  await page.goto(`/releases?project=${project.id}`);
  await page.getByLabel(/^version|phiên bản/i).fill(version);
  await page.getByRole('button', { name: /create release|tạo release/i }).click();
  // The version appears twice on the release page: as its heading and above the note
  // preview. The heading is the one that says the release was created.
  await expect(page.locator('h1.page-heading')).toHaveText(version);
  await expectNoAccessibilityViolations(page, 'release page');
});

test('the header remembers which project was open', async ({ page }) => {
  await registerAndSignIn(page);
  const first = await createProject(page, unique('First'));
  const second = await createProject(page, unique('Second'));

  // Switching from the header, which is a plain GET form.
  await page.goto(`/changes?project=${second.id}`);
  await expect(page.locator('#project-switcher')).toHaveValue(second.id);

  // A URL that names no Project opens on the one last looked at, server-side: the
  // selected option is right in the very first response, with no script involved.
  await page.goto('/changes');
  await expect(page.locator('#project-switcher')).toHaveValue(second.id);

  await page.goto(`/releases?project=${first.id}`);
  await page.goto('/releases');
  await expect(page.locator('#project-switcher')).toHaveValue(first.id);
});

/**
 * Connects a GitHub source to a new Project and delivers one merged pull request to it,
 * signed with the secret the page showed, as GitHub would.
 */
async function projectWithMergedPullRequest(page, request, { title, body }) {
  const project = await createProject(page);
  const repository = unique('app');
  await page.goto('/projects');
  await page.getByLabel(/repository owner|chủ repository/i).fill('acme');
  await page.getByLabel(/repository name|tên repository/i).fill(repository);
  await page.getByRole('button', { name: /generate webhook credentials|sinh thông tin webhook/i }).click();
  const webhookPath = await page.getByRole('textbox', { name: /webhook path|đường dẫn webhook/i }).inputValue();
  const secret = await page.getByRole('textbox', { name: /webhook secret/i }).inputValue();

  const payload = JSON.stringify({
    action: 'closed',
    repository: { full_name: `acme/${repository}` },
    pull_request: {
      number: 1,
      merged: true,
      title,
      body,
      user: { login: 'octocat' },
      labels: [],
      base: { ref: 'main' },
      merge_commit_sha: 'a'.repeat(40),
      merged_at: new Date().toISOString(),
      html_url: `https://github.com/acme/${repository}/pull/1`,
    },
  });
  const delivered = await request.post(webhookPath, {
    data: payload,
    headers: {
      'Content-Type': 'application/json',
      'X-GitHub-Event': 'pull_request',
      'X-GitHub-Delivery': randomUUID(),
      'X-Hub-Signature-256': `sha256=${createHmac('sha256', secret).update(payload).digest('hex')}`,
    },
  });
  expect(delivered.status()).toBe(200);
  return project;
}

// A path is one long word to the browser. At phone width the Change Inbox must still wrap
// it inside its card rather than scroll sideways.
test('a change with a long file path fits the change inbox', async ({ page, request }) => {
  await registerAndSignIn(page);
  const path = 'adapters/src/main/java/dev/example/adapter/persistence/'
    + 'TargetNotificationSubscriptionRepositoryAdapterWithAVeryLongName.java';
  const project = await projectWithMergedPullRequest(page, request, {
    title: `fix: rename ${path}`,
    body: `Moves ${path} next to its port.`,
  });

  await page.goto(`/changes?project=${project.id}`);
  await expect(page.locator('li[id^="change-"]')).toHaveCount(1);
  const horizontalOverflow = await page.evaluate(() =>
    Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth));
  expect(horizontalOverflow, 'horizontal overflow on the change inbox').toBeLessThanOrEqual(1);
  await expectNoAccessibilityViolations(page, 'change inbox with a long path');
});

// A change that will never ship is excluded from the Inbox, with a reason, and stays there.
test('an administrator excludes a change from releases and includes it again', async ({ page, request }) => {
  await registerAndSignIn(page);
  const project = await projectWithMergedPullRequest(page, request, {
    title: 'chore: revert the export',
    body: 'Undoes the export change.',
  });

  // The card offers the form once the worker has finished processing the change.
  const card = page.locator('li[id^="change-"]');
  await expect(async () => {
    await page.goto(`/changes?project=${project.id}`);
    await expect(card.locator('summary', { hasText: /exclude from releases|loại khỏi release/i })).toBeVisible({ timeout: 1000 });
  }).toPass({ timeout: 20_000 });
  await card.locator('summary', { hasText: /exclude from releases|loại khỏi release/i }).click();
  // Sent empty first: the reason is required, and the form says so in place.
  await card.getByRole('button', { name: /exclude from releases|loại khỏi release/i }).click();
  await expect(page.locator('.field-error')).toBeVisible();
  await expectNoAccessibilityViolations(page, 'exclusion form with an error');

  await page.getByRole('textbox', { name: /why it will never ship|vì sao nó sẽ không bao giờ/i }).fill('Reverted by the next change.');
  await page.locator('li[id^="change-"]').getByRole('button', { name: /exclude from releases|loại khỏi release/i }).click();
  await expect(page.locator('li[id^="change-"]')).toContainText(/reverted by the next change\./i);
  await expect(page.locator('li[id^="change-"] .badge', { hasText: /excluded from releases|đã loại khỏi release/i })).toBeVisible();
  await expectNoAccessibilityViolations(page, 'excluded change');

  await page.getByRole('button', { name: /include in releases again|đưa trở lại vào release/i }).click();
  await expect(page.locator('li[id^="change-"] .badge', { hasText: /excluded from releases|đã loại khỏi release/i })).toHaveCount(0);
});
