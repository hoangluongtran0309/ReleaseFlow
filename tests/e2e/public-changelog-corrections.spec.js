// A published note is corrected and the correction is delivered to the public changelog
// (ADR-0046, parts 4 and 5). A rule run by hand publishes the end users' note; the list and
// the uncorrected entry are read; a person corrects the note by hand and delivers the
// correction from the rule's correction form; then the corrected entry, its earlier revision,
// and the list's mark are read. Each interface language is a reader context of its own, and
// every page on the path is checked by Axe at both sizes.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

for (const language of ['en', 'vi']) {
  test(`a correction reaches the public changelog, which keeps the revision it replaced (${language})`,
    async ({ page, context, request, baseURL }) => {
      test.setTimeout(180_000);
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await registerAndSignIn(page);
      const source = await projectWithGitHubSource(page);
      await deliver(request, source, 1, 'feat: restore a backup by its id');
      await waitUntilProcessed(page, source.project.id);
      await page.goto(`/releases?project=${source.project.id}`);
      await page.getByLabel(/^version|phiên bản/i).fill('v1.2.0');
      await page.getByRole('button', { name: /create release|tạo release/i }).click();
      await expect(page.locator('h1.page-heading')).toHaveText('v1.2.0');
      await approveAndPublish(page);
      const releaseUrl = page.url();

      // A rule run by hand writes the end users' note to the public changelog.
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
      const rule = page.locator('tr[id^="rule-"]', { hasText: ruleName });
      await rule.getByRole('button', { name: /enable|bật/i }).click();
      await rule.locator('summary').filter({ hasText: /^(run now|chạy ngay)$/i }).click();
      await rule.getByRole('button', { name: /^(run|chạy)$/i }).click();
      const delivery = page.locator('tr[id^="run-"]', { hasText: ruleName }).first();
      await expect(async () => {
        await page.goto('/automation');
        await expect(delivery.locator('li[id^="action-run-"] a.link')).toHaveCount(1, { timeout: 1000 });
      }).toPass({ timeout: 30_000 });
      // The address a run records comes from the deployment's public base URL; the suite reads
      // the same path on the stack it drives.
      const entryPath = new URL(await delivery.locator('li[id^="action-run-"] a.link').getAttribute('href')).pathname;
      const listPath = entryPath.replace(/\/releases\/[^/]+$/, '');

      await page.goto(listPath);
      await expect(page.locator('h1.page-heading')).toBeVisible();
      await expect(page.locator('.badge', { hasText: /^(corrected|đã sửa)$/i })).toHaveCount(0);
      await expectNoAccessibilityViolations(page, `the public changelog before a correction (${language})`);
      await page.goto(entryPath);
      await expect(page.locator('.correction-line')).toHaveCount(0);
      await expect(page.locator('article.release-note-body')).toContainText('restore a backup by its id');
      await expectNoAccessibilityViolations(page, `an entry nobody corrected (${language})`);

      // The note is corrected by hand.
      await page.goto(releaseUrl);
      await page.locator('xpath=//div[@id="note-end_user-en"]/preceding-sibling::input[1]').check();
      const tab = page.locator('#note-end_user-en');
      await tab.getByRole('link', { name: /^(correct this note|sửa note này)$/i }).click();
      const markdown = page.locator('textarea[name="content"]');
      await markdown.fill((await markdown.inputValue()).replace('restore a backup by its id', 'restore a backup by its <backup-id>'));
      await page.locator('textarea[name="reason"]').fill('Name the placeholder <backup-id>.');
      await page.getByRole('button', { name: /^(show the difference|xem khác biệt)$/i }).click();
      await page.getByRole('button', { name: /^(save as revision 2|lưu thành bản sửa đổi 2)$/i }).click();
      await expect(page).toHaveURL(releaseUrl);

      // The release page says the changelog is behind and points at the correction form.
      await page.locator('xpath=//div[@id="note-end_user-en"]/preceding-sibling::input[1]').check();
      await page.locator('#note-end_user-en').getByRole('link', { name: /^(deliver the correction|gửi bản sửa)$/i }).click();
      await expect(page.locator('h1.page-heading')).toContainText(ruleName);
      const offer = page.locator('#correction-offer');
      await expect(offer.locator('input[name="ruleActionIds"]')).toBeChecked();
      await expect(offer).toContainText(/delivers revision 2; it holds revision 1|gửi bản sửa đổi 2; nơi này đang giữ bản sửa đổi 1/i);
      await expect(offer.locator('textarea[name="reason"]')).toHaveValue('Name the placeholder <backup-id>.');
      await expectNoAccessibilityViolations(page, `the correction form (${language})`);
      await offer.locator('textarea[name="reason"]').fill('The placeholder is <backup-id> & keeps its case.');
      await offer.getByRole('button', { name: /^(deliver the correction|gửi bản sửa)$/i }).click();
      await expect(page).toHaveURL(/\/automation$/);

      // The run history shows a correction, with its reason and what it replaced.
      const correction = page.locator('tr[id^="run-"]', { hasText: ruleName })
        .filter({ hasText: 'The placeholder is <backup-id> & keeps its case.' });
      await expect(async () => {
        await page.goto('/automation');
        await expect(correction.locator('li[id^="action-run-"]')).toContainText(/succeeded|thành công/i, { timeout: 1000 });
      }).toPass({ timeout: 30_000 });
      await expect(correction).toContainText('The placeholder is <backup-id> & keeps its case.');
      await expect(correction).toContainText(/revision 2, replaces revision 1|bản sửa đổi 2, thay cho bản sửa đổi 1/i);
      await expectNoAccessibilityViolations(page, `a correction in the run history (${language})`);

      // The entry shows the latest revision under its line, in the note's language, and links
      // the one it replaced. The browser keeps the entry for five minutes, as any reader's
      // would; reloading asks again, and the new revision's ETag earns the whole page.
      await page.goto(entryPath);
      await page.reload();
      await expect(page.locator('.correction-line')).toHaveAttribute('lang', 'en');
      await expect(page.locator('.correction-line')).toContainText(/^Corrected on \d{1,2} \w+ \d{4}: The placeholder is <backup-id> & keeps its case\.$/);
      await expect(page.locator('article.release-note-body')).toContainText('restore a backup by its <backup-id>');
      await expectNoAccessibilityViolations(page, `a corrected entry (${language})`);
      // Like every public link, it is absolute and comes from the public base URL: the suite
      // opens its path on the stack it drives rather than following it.
      const earlier = await page.getByRole('navigation', { name: /^(earlier revisions|các bản sửa đổi trước)$/i })
        .getByRole('link').first().getAttribute('href');
      expect(new URL(earlier).pathname).toBe(`${entryPath}/revisions/1`);
      await page.goto(new URL(earlier).pathname);
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex');
      await expect(page.locator('.revision-notice')).toContainText(/revision 2 replaced it|bản sửa đổi 2 đã thay thế nó/i);
      await expect(page.locator('article.release-note-body')).toContainText('restore a backup by its id');
      await expectNoAccessibilityViolations(page, `an earlier revision (${language})`);

      await page.goto(listPath);
      await page.reload();
      await expect(page.locator('.badge', { hasText: /^(corrected|đã sửa)$/i })).toHaveCount(1);
      await expectNoAccessibilityViolations(page, `the public changelog with a corrected entry (${language})`);
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
      body: 'Restores a backup from the id the backup command printed, for the people who asked.',
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
