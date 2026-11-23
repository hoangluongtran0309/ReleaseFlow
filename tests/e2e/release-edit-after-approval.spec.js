// ADR-0047 through the pages: an approved release's summary is corrected without returning it
// to draft. The approval stays, the release says who changed the summary after approval, the
// notes that follow their template show the new summary, and a note edited by hand is listed
// as still showing the earlier text. No provider is reachable, so nothing is translated or
// held. Run in both interface languages; the projects run it at both sizes and in both themes,
// and every page on the way is checked by Axe.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

for (const language of ['en', 'vi']) {
  test(`an approved release's summary is corrected without a return to draft (${language})`,
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
      await approve(page);

      // The operator's note is edited by hand, so it keeps its own text.
      const operatorTab = page.locator('xpath=//div[@id="note-operator-en"]/preceding-sibling::input[1]');
      const operatorName = (await operatorTab.getAttribute('aria-label')).split(' · ')[0];
      await operatorTab.check();
      const operator = page.locator('#note-operator-en');
      await operator.locator('summary', { hasText: /^(edit markdown|sửa markdown)$/i }).click();
      await operator.locator('textarea[name="content"]').fill('# v1.2.0\n\nRestore a backup by its ID.\n');
      await operator.getByRole('button', { name: /^(save note|lưu note)$/i }).click();
      await expect(page.locator('#release-changes-after-approval')).toHaveCount(0);

      // The summary changes while the release stays approved.
      const form = page.locator('#release-text');
      await expect(form.getByText(/the approval stays|duyệt vẫn giữ nguyên/i)).toBeVisible();
      await expectNoAccessibilityViolations(page, `an approved release's details (${language})`);
      await form.locator('textarea[name="summary"]').fill('Backups restore by their ID.');
      await form.getByRole('button', { name: /^(save details|lưu thông tin)$/i }).click();

      await expect(page.locator('#approve-release')).toHaveCount(0);
      await expect(page.locator('#publish-release')).toHaveCount(1);
      await expect(page.locator('#release-changes-after-approval li'))
        .toHaveText(/summary changed after approval by .+ on .+ UTC|tóm tắt được .+ đổi sau khi duyệt, lúc .+ UTC/i);
      await expect(page.locator('#release-notes-earlier-text li')).toHaveText([`${operatorName}, en`]);
      await expect(form.locator('textarea[name="summary"]')).toHaveValue('Backups restore by their ID.');

      // A note that follows its template shows the new summary; the hand-edited one does not.
      await page.locator('xpath=//div[@id="note-end_user-en"]/preceding-sibling::input[1]').check();
      await expect(page.locator('#note-end_user-en .release-note-body')).toContainText('Backups restore by their ID.');
      await operatorTab.check();
      await expect(operator.locator('.release-note-body')).not.toContainText('Backups restore by their ID.');
      await expectNoAccessibilityViolations(page, `an approved release changed after approval (${language})`);
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

/** From a draft with its changes: adds them, approves each as shown, and approves the release. */
async function approve(page) {
  await page.getByRole('button', { name: /^(add all available|thêm tất cả)$/i }).click();
  await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).click();
  const changes = await page.locator('li[id^="review-"]').count();
  for (let index = 0; index < changes; index++) {
    await page.locator('li[id^="review-"]').nth(index)
      .getByRole('button', { name: /^(approve as shown|duyệt đúng như hiển thị)$/i }).click();
  }
  await page.locator('#approve-release summary').click();
  await page.locator('#approve-release button[type="submit"]').click();
  await expect(page.locator('#publish-release')).toHaveCount(1);
}
