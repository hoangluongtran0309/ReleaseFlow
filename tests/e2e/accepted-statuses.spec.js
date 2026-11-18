// A linked issue still open as of review reaches the notes only when a person accepts
// showing its status (ADR-0041), and the Review tab then lists it with who accepted it and
// when. The issue comes from the suite's provider stand-in (tests/e2e/compose.yaml), whose
// Jira answers "In Progress" for every issue. A change links issues only when its code host
// has an access token, so the GitHub source gets one the stand-in accepts. The whole path runs in both interface languages,
// and every page on it is checked by Axe.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

async function connectGitHub(page) {
  const repository = unique('app');
  await page.goto('/projects');
  await page.getByLabel(/repository owner|chủ repository/i).fill('acme');
  await page.getByLabel(/repository name|tên repository/i).fill(repository);
  await page.getByRole('button', { name: /generate webhook credentials|sinh thông tin webhook/i }).click();
  const webhookPath = await page.getByRole('textbox', { name: /webhook path|đường dẫn webhook/i }).inputValue();
  const secret = await page.getByRole('textbox', { name: /webhook secret/i }).inputValue();

  await page.goto('/projects');
  const source = page.locator('div[id^="source-"]', { hasText: repository });
  await source.getByLabel(/^access token/i).fill('e2e-github-token');
  await source.getByRole('button', { name: /save access token|lưu access token/i }).click();
  await expect(page.getByRole('status').first()).toContainText(/access token saved|đã lưu access token/i);
  return { repository, webhookPath, secret };
}

async function connectJira(page) {
  await page.goto('/projects');
  await page.getByLabel(/source type|loại nguồn/i).selectOption('JIRA');
  await page.getByLabel(/jira site|site jira/i).fill('https://acme.atlassian.net');
  await page.getByLabel(/project key|mã project/i).fill('RFE');
  await page.getByLabel(/account email|email tài khoản/i).fill('release-bot@acme.test');
  await page.getByLabel(/^api token/i).fill('e2e-jira-token');
  await page.getByRole('button', { name: /connect the project|kết nối project/i }).click();
}

async function deliver(request, github, title) {
  const payload = JSON.stringify({
    action: 'closed',
    repository: { full_name: `acme/${github.repository}` },
    pull_request: {
      number: 1,
      merged: true,
      title,
      body: 'Shows which backups a retention policy would delete before it runs.',
      user: { login: 'octocat' },
      labels: [],
      base: { ref: 'main' },
      merge_commit_sha: 'b'.repeat(40),
      merged_at: new Date().toISOString(),
      html_url: `https://github.com/acme/${github.repository}/pull/1`,
    },
  });
  const delivered = await request.post(github.webhookPath, {
    data: payload,
    headers: {
      'Content-Type': 'application/json',
      'X-GitHub-Event': 'pull_request',
      'X-GitHub-Delivery': randomUUID(),
      'X-Hub-Signature-256': `sha256=${createHmac('sha256', github.secret).update(payload).digest('hex')}`,
    },
  });
  expect(delivered.status()).toBe(200);
}

for (const language of ['en', 'vi']) {
  test(`an accepted open status is listed on the Review tab with who accepted it (${language})`,
    async ({ page, request, context, baseURL }) => {
      test.setTimeout(90_000);
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await registerAndSignIn(page);
      const project = await createProject(page);
      const github = await connectGitHub(page);
      await connectJira(page);
      await deliver(request, github, 'feat: preview a retention policy (RFE-53)');

      // The change links RFE-53 once it has been processed.
      const linked = page.locator('li[id^="change-"] details', { hasText: /linked issues|issue liên kết/i });
      await expect(async () => {
        await page.goto(`/changes?project=${project.id}`);
        await expect(linked).toBeVisible({ timeout: 1000 });
      }).toPass({ timeout: 30_000 });
      await linked.locator('summary').click();
      await expect(linked).toContainText('RFE-53');

      await page.goto(`/releases?project=${project.id}`);
      await page.getByLabel(/^version|phiên bản/i).fill('1.0.0');
      await page.getByRole('button', { name: /create release|tạo release/i }).click();
      await expect(page.locator('h1.page-heading')).toHaveText('1.0.0');
      await page.getByRole('button', { name: /^(add all available|thêm tất cả)/i }).click();
      await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).click();

      // The issue is read again for the review, and is still open.
      const open = page.locator('#open-linked-issues');
      await expect(async () => {
        await page.reload();
        await expect(open).toContainText('RFE-53', { timeout: 1000 });
      }).toPass({ timeout: 30_000 });
      await expect(open).toContainText(/status: in progress|trạng thái: in progress/i);
      await expectNoAccessibilityViolations(page, `open linked issue (${language})`);

      await open.getByRole('button', { name: /show this status|hiển thị trạng thái này/i }).click();
      const accepted = page.locator('#accepted-linked-issues');
      await expect(page.locator('#open-linked-issues')).toHaveCount(0);
      await expect(accepted.getByRole('link', { name: 'RFE-53' })).toBeVisible();
      await expect(accepted).toContainText(/the notes print: in progress|note ghi: in progress/i);
      await expect(accepted).toContainText(/accepted by owner on|được owner chấp nhận lúc/i);
      await expect(accepted.locator('time')).toHaveAttribute('datetime', /^\d{4}-\d{2}-\d{2}T/);
      await expect(accepted.locator('time')).toContainText(/UTC$/);
      await expectNoAccessibilityViolations(page, `accepted linked issue (${language})`);
    });
}
