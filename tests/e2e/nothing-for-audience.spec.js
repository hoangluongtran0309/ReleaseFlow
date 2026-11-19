// A note with nothing for its audience is not delivered (ADR-0035 and ADR-0044, amended
// 2026-11-19). A person marks the one change none for end users, the release is published,
// and a public changelog rule for end users is run by hand: its action is skipped, and the
// run page says why. Run in both interface languages; every page on the path is checked
// by Axe.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

for (const language of ['en', 'vi']) {
  test(`an action whose note has nothing for its audience is skipped, and the run page says why (${language})`,
    async ({ page, context, request, baseURL }) => {
      test.setTimeout(120_000);
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await registerAndSignIn(page);
      const source = await projectWithGitHubSource(page);
      await deliver(request, source, 1, 'feat: add table export');
      await waitUntilProcessed(page, source.project.id);

      // Nothing in the release concerns end users, by a person's choice.
      await page.goto(`/releases?project=${source.project.id}`);
      await page.getByLabel(/^version|phiên bản/i).fill('1.4.1');
      await page.getByRole('button', { name: /create release|tạo release/i }).click();
      await expect(page.locator('h1.page-heading')).toHaveText('1.4.1');
      await page.getByRole('button', { name: /^(add all available|thêm tất cả)$/i }).click();
      await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).click();
      const details = page.locator('details[id^="summary-"]');
      if (!(await details.evaluate((element) => element.open))) {
        await details.locator('summary').first().click();
      }
      const form = page.getByRole('form', { name: /edit change summary|sửa tóm tắt thay đổi/i });
      await form.getByRole('group', { name: /^(for|cho) (end user|người dùng cuối)$/i })
        .getByRole('combobox', { name: /^(relevance|mức liên quan)$/i }).selectOption('NONE');
      await form.getByRole('button', { name: /save summary|lưu tóm tắt/i }).click();
      await approveAndPublish(page);

      // A rule that would write the end users' note to the public changelog.
      const ruleName = unique('Changelog');
      await page.goto('/automation');
      await page.locator('#name').fill(ruleName);
      await page.locator('#triggerType').selectOption('MANUAL');
      await page.locator('#projectId').selectOption(source.project.id);
      await page.locator('select[name="actions[0].actionType"]').selectOption('PUBLIC_CHANGELOG');
      const endUser = await page.locator('select[name="actions[0].audienceId"] option', { hasText: /end user|người dùng cuối/i })
        .getAttribute('value');
      await page.locator('select[name="actions[0].audienceId"]').selectOption(endUser);
      await page.locator('select[name="actions[0].language"]').selectOption('en');
      await page.getByRole('button', { name: /add rule|thêm quy tắc/i }).click();
      const row = page.locator('tr[id^="rule-"]', { hasText: ruleName });
      await row.getByRole('button', { name: /enable|bật/i }).click();

      const enabled = page.locator('tr[id^="rule-"]', { hasText: ruleName });
      await enabled.locator('summary').filter({ hasText: /^(run now|chạy ngay)$/i }).click();
      await enabled.getByRole('button', { name: /^(run|chạy)$/i }).click();

      // The worker skips the action; the run succeeds with nothing delivered, and says why.
      const run = page.locator('tr[id^="run-"]', { hasText: ruleName });
      await expect(async () => {
        await page.goto('/automation');
        await expect(run.locator('li[id^="action-run-"]')).toContainText(/skipped|bỏ qua/i, { timeout: 1000 });
      }).toPass({ timeout: 30_000 });
      await expect(run.locator('li[id^="action-run-"] code')).toHaveText('nothing_for_audience');
      await expect(run.locator('li[id^="action-run-"]')).toContainText(
        /nothing in this release concerns its audience, so nothing was sent|không có thay đổi nào liên quan tới audience của nó, nên không gửi gì/i);
      await expect(run.locator('li[id^="action-run-"]')).not.toContainText(/audience is retired|đã ngừng dùng/i);
      await expectNoAccessibilityViolations(page, `run with a skipped action (${language})`);
    });
}

/** A new Project with a GitHub source, and the credentials its webhook was given. */
async function projectWithGitHubSource(page) {
  const project = await createProject(page);
  const repository = unique('vault');
  await page.goto('/projects');
  await page.getByLabel(/repository owner|chủ repository/i).fill('acme');
  await page.getByLabel(/repository name|tên repository/i).fill(repository);
  await page.getByRole('button', { name: /generate webhook credentials|sinh thông tin webhook/i }).click();
  const webhookPath = await page.getByRole('textbox', { name: /webhook path|đường dẫn webhook/i }).inputValue();
  const secret = await page.getByRole('textbox', { name: /webhook secret/i }).inputValue();
  return { project, repository, webhookPath, secret };
}

/** Delivers one merged pull request, signed with the source's secret, as GitHub would. */
async function deliver(request, source, number, title) {
  const payload = JSON.stringify({
    action: 'closed',
    repository: { full_name: `acme/${source.repository}` },
    pull_request: {
      number,
      merged: true,
      title,
      body: 'Exports any table as a CSV file, for the people who asked for spreadsheets.',
      user: { login: 'octocat' },
      labels: [],
      base: { ref: 'main' },
      merge_commit_sha: String(number).repeat(40).slice(0, 40),
      merged_at: new Date().toISOString(),
      html_url: `https://github.com/acme/${source.repository}/pull/${number}`,
    },
  });
  const delivered = await request.post(source.webhookPath, {
    data: payload,
    headers: {
      'Content-Type': 'application/json',
      'X-GitHub-Event': 'pull_request',
      'X-GitHub-Delivery': randomUUID(),
      'X-Hub-Signature-256': `sha256=${createHmac('sha256', source.secret).update(payload).digest('hex')}`,
    },
  });
  expect(delivered.status()).toBe(200);
}

// The Inbox offers a change's forms once the worker has finished processing it.
async function waitUntilProcessed(page, projectId) {
  await expect(async () => {
    await page.goto(`/changes?project=${projectId}`);
    await expect(page.locator('li[id^="change-"] summary', { hasText: /exclude from releases|loại khỏi release/i }))
      .toHaveCount(1, { timeout: 1000 });
  }).toPass({ timeout: 30_000 });
}

/** From a release in review: approves each change as shown, approves, and publishes. */
async function approveAndPublish(page) {
  const changes = await page.locator('li[id^="review-"]').count();
  for (let index = 0; index < changes; index++) {
    await page.locator('li[id^="review-"]').nth(index)
      .getByRole('button', { name: /^(approve as shown|duyệt đúng như hiển thị)$/i }).click();
  }
  await page.locator('#approve-release summary').click();
  await page.locator('#approve-release button[type="submit"]').click();
  await expect(page.locator('#publish-release')).toBeAttached();
  await page.locator('#publish-release summary').click();
  await page.locator('#publish-release button[type="submit"]').click();
  await expect(page.locator('#publish-release')).toHaveCount(0);
}
