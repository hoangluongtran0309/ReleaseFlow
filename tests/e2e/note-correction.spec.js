// ADR-0046 through the pages: an administrator corrects a published note by hand, after
// reading the difference, and the published release lists the note's revisions with the
// difference each made. Run in both interface languages; the projects run it in both themes,
// and every page on the way is checked by Axe.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

for (const language of ['en', 'vi']) {
  test(`a published note is corrected by hand and its revisions are listed (${language})`,
    async ({ page, context, request, baseURL }) => {
      test.setTimeout(120_000);
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await registerAndSignIn(page);
      const source = await projectWithGitHubSource(page);
      await deliver(request, source, 1, 'feat: restore a backup by its id');
      await waitUntilProcessed(page, source.project.id, 1);
      await page.goto(`/releases?project=${source.project.id}`);
      await page.getByLabel(/^version|phiên bản/i).fill('v1.2.0');
      await page.getByRole('button', { name: /create release|tạo release/i }).click();
      await expect(page.locator('h1.page-heading')).toHaveText('v1.2.0');
      await approveAndPublish(page);
      const releaseUrl = page.url();

      // The published note is revision 1, and an administrator may correct it.
      const tab = await openOperatorNote(page);
      await expect(tab.getByText(/^(revision 1|bản sửa đổi 1)$/i).first()).toBeVisible();
      await expect(tab.getByText(/no automation has delivered this note|chưa có automation nào gửi note này/i))
        .toBeVisible();
      await expectNoAccessibilityViolations(page, `a published note at revision 1 (${language})`);
      await tab.getByRole('link', { name: /^(correct this note|sửa note này)$/i }).click();

      // The correction page starts from the latest revision and shows the difference first.
      await expect(page.locator('h1.page-heading')).toContainText(/correct the .+ note in en|sửa note .+ bằng en/i);
      await expectNoAccessibilityViolations(page, `the correction page (${language})`);
      const markdown = page.locator('textarea[name="content"]');
      // One line changes and one is added, so the difference both removes and adds.
      const published = await markdown.inputValue();
      expect(published).toContain('restore a backup by its id');
      await markdown.fill(`${published.replace('restore a backup by its id', 'restore a backup by its <backup-id>')}`
        + '\nRestore <backup-id> first.\n');
      await page.locator('textarea[name="reason"]').fill('Placeholders in the right case.');
      await expect(page.getByRole('button', { name: /^(save as revision 2|lưu thành bản sửa đổi 2)$/i })).toHaveCount(0);
      await page.getByRole('button', { name: /^(show the difference|xem khác biệt)$/i }).click();
      const difference = page.getByRole('region', { name: /^(difference from revision 1|khác biệt so với bản sửa đổi 1)$/i });
      await expect(difference.locator('ins', { hasText: 'Restore <backup-id> first.' })).toBeVisible();
      await expect(difference.locator('del', { hasText: 'restore a backup by its id' })).toBeVisible();
      await expectNoAccessibilityViolations(page, `a correction's difference (${language})`);

      await page.getByRole('button', { name: /^(save as revision 2|lưu thành bản sửa đổi 2)$/i }).click();
      await expect(page).toHaveURL(releaseUrl);
      await expect(page.getByRole('status').first())
        .toContainText(/revision 2 of the .+ is saved|đã lưu bản sửa đổi 2/i);

      // The note reads as its correction now, and both revisions stay listed.
      const corrected = await openOperatorNote(page);
      await expect(corrected.locator('.badge', { hasText: /^(corrected|đã sửa)$/i })).toBeVisible();
      await expect(corrected.locator('.release-note-body')).toContainText('Restore <backup-id> first.');
      await expect(corrected.locator('li.note-revision')).toHaveCount(2);
      await corrected.getByText(/^(difference from revision 1|khác biệt so với bản sửa đổi 1)$/i).click();
      await expect(corrected.getByRole('region', { name: /revision 2|bản sửa đổi 2/i })
        .locator('ins', { hasText: 'Restore <backup-id> first.' })).toBeVisible();
      await expectNoAccessibilityViolations(page, `a corrected note and its revisions (${language})`);
    });
}

// The operator's English note: its tab is chosen, and its panel answered.
async function openOperatorNote(page) {
  await page.locator('xpath=//div[@id="note-operator-en"]/preceding-sibling::input[1]').check();
  const tab = page.locator('#note-operator-en');
  await expect(tab).toBeVisible();
  return tab;
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
      body: 'A change delivered by the browser suite.',
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
async function waitUntilProcessed(page, projectId, count) {
  await expect(async () => {
    await page.goto(`/changes?project=${projectId}`);
    await expect(page.locator('li[id^="change-"] summary', { hasText: /exclude from releases|loại khỏi release/i }))
      .toHaveCount(count, { timeout: 1000 });
  }).toPass({ timeout: 30_000 });
}

/** From a draft with its changes: adds them, approves each as shown, approves, and publishes. */
async function approveAndPublish(page) {
  await page.getByRole('button', { name: /^(add all available|thêm tất cả)$/i }).click();
  await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).click();
  const changes = await page.locator('li[id^="review-"]').count();
  for (let index = 0; index < changes; index++) {
    await page.locator('li[id^="review-"]').nth(index)
      .getByRole('button', { name: /^(approve as shown|duyệt đúng như hiển thị)$/i }).click();
  }
  await page.locator('#approve-release summary').click();
  await page.locator('#approve-release button[type="submit"]').click();
  await page.locator('#publish-release summary').click();
  await page.locator('#publish-release button[type="submit"]').click();
  await expect(page.locator('#publish-release')).toHaveCount(0);
}
