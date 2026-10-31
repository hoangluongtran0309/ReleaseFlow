// ADR-0037 through the pages: a security change in the Change Inbox and in a release's
// review, the release's upgrade guide and previous release, and the audience footer that
// links the comparison. Run in both interface languages; the projects run it in both themes.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

const GUIDE = 'https://docs.example.com/upgrade/1.0';

for (const language of ['en', 'vi']) {
  test(`a security change is reviewed, listed under Security, and the note links the comparison (${language})`,
    async ({ page, context, request, baseURL }) => {
      test.setTimeout(120_000);
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await registerAndSignIn(page);
      const source = await projectWithGitHubSource(page);

      await deliver(request, source, 1, 'feat: add the vault', []);
      await waitUntilProcessed(page, source.project.id, 1);
      await page.goto(`/releases?project=${source.project.id}`);
      await page.getByLabel(/^version|phiên bản/i).fill('v0.1.0');
      await page.getByRole('button', { name: /create release|tạo release/i }).click();
      await expect(page.locator('h1.page-heading')).toHaveText('v0.1.0');
      await approveAndPublish(page);

      // The rules mark a change labelled security, and the Inbox shows and reviews the flag.
      await deliver(request, source, 2, 'fix: derive a key per target', ['security']);
      await deliver(request, source, 3, 'feat!: remove CSRF protection from the API', ['security']);
      await waitUntilProcessed(page, source.project.id, 3);
      const card = page.locator('li[id^="change-"]', { hasText: 'derive a key per target' });
      await expect(card.locator('.badge.badge-info', { hasText: /^(security|bảo mật)$/i })).toBeVisible();
      await expect(card.getByRole('checkbox', { name: /^(security change|thay đổi bảo mật)$/i })).toBeChecked();
      await expectNoAccessibilityViolations(page, `a security change in the Change Inbox (${language})`);

      // A new release follows the last published one, and links its upgrade guide.
      await page.goto(`/releases?project=${source.project.id}`);
      await expect(page.locator('#previousReleaseId option:checked')).toHaveText('v0.1.0');
      await page.getByLabel(/^version|phiên bản/i).fill('v1.0.0');
      await page.locator('#upgradeGuideUrl').fill('docs.example.com/upgrade');
      await page.getByRole('button', { name: /create release|tạo release/i }).click();
      await expect(page.locator('#upgradeGuideUrl-error')).toBeVisible();
      await expect(page.locator('#upgradeGuideUrl')).toHaveAttribute('aria-invalid', 'true');
      await expectNoAccessibilityViolations(page, `a refused upgrade guide (${language})`);
      await page.locator('#upgradeGuideUrl').fill(GUIDE);
      await page.getByRole('button', { name: /create release|tạo release/i }).click();
      await expect(page.locator('h1.page-heading')).toHaveText('v1.0.0');
      await expect(page.locator('#release-details #upgradeGuideUrl')).toHaveValue(GUIDE);
      await expect(page.locator('#release-details #previousReleaseId option:checked')).toHaveText('v0.1.0');
      await page.getByRole('button', { name: /^(add all available|thêm tất cả)$/i }).click();

      // Before approval, the operator preview already has the section and the footer.
      const compare = `https://github.com/acme/${source.repository}/compare/v0.1.0...v1.0.0`;
      await page.getByRole('radio', { name: /^(operator|vận hành)$/i }).check();
      await expect(page.locator('#preview-operator')).toContainText(/🔒 (Security|Bảo mật)/);
      await expect(page.locator(`#preview-operator a[href="${compare}"]`)).toHaveText('v0.1.0...v1.0.0');
      // The breaking warning and the footer both link the upgrade guide.
      await expect(page.locator(`#preview-operator a[href="${GUIDE}"]`))
        .toHaveText(['read the upgrade guide', 'Read before upgrading']);
      await expectNoAccessibilityViolations(page, `a draft with release links (${language})`);

      await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).click();
      const review = page.locator('li[id^="review-"]', { hasText: 'derive a key per target' });
      await expect(review.locator('.badge.badge-info', { hasText: /^(security|bảo mật)$/i })).toBeVisible();
      await review.locator('summary', { hasText: /edit classification|sửa phân loại/i }).click();
      await expect(review.getByRole('checkbox', { name: /^(security change|thay đổi bảo mật)$/i })).toBeChecked();
      await expectNoAccessibilityViolations(page, `a security change in review (${language})`);

      await approveAndPublish(page, { publish: false });
      const operator = await page.locator('#note-operator-en textarea').inputValue();
      expect(operator).toContain('This release includes 2 changes: 1 breaking change and 1 security change.');
      expect(operator).toContain('## ⚠️ Breaking Changes\n\n- 🔒 Security: **remove CSRF protection from the API**');
      expect(operator).toContain('## 🔒 Security\n\n- **derive a key per target**');
      expect(operator).toContain(`[read the upgrade guide](${GUIDE})`);
      expect(operator).toContain(`**Compare:** [v0.1.0...v1.0.0](${compare})`);
      expect(await page.locator('#note-end_user-en textarea').inputValue()).not.toContain('Compare');
      await expectNoAccessibilityViolations(page, `approved notes with a footer (${language})`);
    });

  test(`the audience editor previews and checks a footer (${language})`, async ({ page, context, baseURL }) => {
    await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
    await registerAndSignIn(page);
    await page.goto('/audiences');
    await page.locator('#audience-list a', { hasText: 'operator' }).click();
    const footer = page.locator('#footerTemplateBody');
    await expect(footer).toHaveValue(/\{\{#compareUrl\}\}/);

    await footer.fill('{{#releaseUrl}}[Install {{version}}]({{releaseUrl}}){{/releaseUrl}}');
    await page.getByRole('button', { name: /^(preview|xem thử)$/i }).click();
    await expect(page.locator('#audience-preview pre'))
      .toContainText('[Install v1.4.0](https://github.com/acme/app/releases/tag/v1.4.0)');
    await expectNoAccessibilityViolations(page, `a footer preview (${language})`);

    // A change's variable is unknown to a footer, and the refusal is under the footer.
    await footer.fill('{{whatChanged}}');
    await page.getByRole('button', { name: /^(save audience|lưu audience)$/i }).click();
    await expect(page.locator('#footerTemplateBody-error')).toBeVisible();
    await expect(footer).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#templateBody-error')).toHaveCount(0);
    await expectNoAccessibilityViolations(page, `a refused footer (${language})`);
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
async function deliver(request, source, number, title, labels) {
  const payload = JSON.stringify({
    action: 'closed',
    repository: { full_name: `acme/${source.repository}` },
    pull_request: {
      number,
      merged: true,
      title,
      body: 'A change delivered by the browser suite.',
      user: { login: 'octocat' },
      labels: labels.map((name) => ({ name })),
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
async function approveAndPublish(page, { publish = true } = {}) {
  if (await page.getByRole('button', { name: /^(add all available|thêm tất cả)$/i }).count()) {
    await page.getByRole('button', { name: /^(add all available|thêm tất cả)$/i }).click();
  }
  if (await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).count()) {
    await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).click();
  }
  const changes = await page.locator('li[id^="review-"]').count();
  for (let index = 0; index < changes; index++) {
    await page.locator('li[id^="review-"]').nth(index)
      .getByRole('button', { name: /^(approve as shown|duyệt đúng như hiển thị)$/i }).click();
  }
  await page.locator('#approve-release summary').click();
  await page.locator('#approve-release button[type="submit"]').click();
  await expect(page.locator('#publish-release')).toBeAttached();
  if (publish) {
    await page.locator('#publish-release summary').click();
    await page.locator('#publish-release button[type="submit"]').click();
    await expect(page.locator('#publish-release')).toHaveCount(0);
  }
}
