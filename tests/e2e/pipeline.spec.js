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

// ADR-0035: a person tells each audience how much a change concerns it, and the notes
// follow. Run in both languages; the projects run it in both themes.
test('edit summary sets a relevance, a headline, and a body for each audience', async ({ page, context, request, baseURL }) => {
  for (const language of ['en', 'vi']) {
    await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
    await registerAndSignIn(page);
    const project = await projectWithMergedPullRequest(page, request, {
      title: 'feat: add table export',
      body: 'Exports any table as a CSV file, for the people who asked for spreadsheets.',
    });
    // The release offers the change once the worker has finished processing it.
    await expect(async () => {
      await page.goto(`/changes?project=${project.id}`);
      await expect(page.locator('li[id^="change-"] summary', { hasText: /exclude from releases|loại khỏi release/i }))
        .toBeVisible({ timeout: 1000 });
    }).toPass({ timeout: 20_000 });

    await page.goto(`/releases?project=${project.id}`);
    await page.getByLabel(/^version|phiên bản/i).fill('1.0.0');
    await page.getByRole('button', { name: /create release|tạo release/i }).click();
    await expect(page.locator('h1.page-heading')).toHaveText('1.0.0');
    await page.getByRole('button', { name: /^(add all available|thêm tất cả)$/i }).click();
    await page.getByRole('button', { name: /^(request review|yêu cầu review)$/i }).click();

    const details = page.locator('details[id^="summary-"]');
    if (!(await details.evaluate((element) => element.open))) {
      await details.locator('summary').first().click();
    }
    const form = page.getByRole('form', { name: /edit change summary|sửa tóm tắt thay đổi/i });
    // Audiences are named in the Organization's output language, not the interface's.
    const endUser = form.getByRole('group', { name: /^(for|cho) (end user|người dùng cuối)$/i });
    const operator = form.getByRole('group', { name: /^(for|cho) (operator|vận hành)$/i });
    // By accessible name: a label's text also holds the options and the hint it wraps.
    await endUser.getByRole('combobox', { name: /^(relevance|mức liên quan)$/i }).selectOption('NONE');
    await operator.getByRole('combobox', { name: /^(relevance|mức liên quan)$/i }).selectOption('BRIEF');
    await operator.getByRole('textbox', { name: /^(headline|dòng tiêu đề)$/i }).fill('Exports queue in the background.');
    await operator.getByRole('textbox', { name: /^(narrative|diễn giải)$/i }).fill('Watch the export queue.');
    await expectNoAccessibilityViolations(page, `edit summary (${language})`);
    await form.getByRole('button', { name: /save summary|lưu tóm tắt/i }).click();

    // The previews follow at once: the end user's note says nothing concerns it, and the
    // operator's leads with the headline.
    await page.getByRole('radio', { name: /^(end user|người dùng cuối)$/i }).check();
    await expect(page.locator('#preview-end_user'))
      .toContainText(/nothing in this release concerns this audience|không có thay đổi nào liên quan tới nhóm đọc này/i);
    await page.getByRole('radio', { name: /^(operator|vận hành)$/i }).check();
    await expect(page.locator('#preview-operator strong')).toHaveText('Exports queue in the background.');
    await expectNoAccessibilityViolations(page, `release page after editing the summary (${language})`);
  }
});

test('the audience editor offers the headline and the brief section, and previews them', async ({ page, context, baseURL }) => {
  await registerAndSignIn(page);
  for (const language of ['en', 'vi']) {
    await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
    // Registration seeds the support audience with the other presets.
    await page.goto('/audiences');
    await page.locator('#audience-list a', { hasText: 'support' }).click();
    const variables = page.getByRole('group', { name: /insert a variable|chèn một biến/i });
    for (const variable of ['headline', 'relevance', 'brief']) {
      await expect(variables.getByRole('button', { name: `{{${variable}}}`, exact: true })).toBeVisible();
    }

    await page.locator('#templateBody').fill('- **{{headline}}** ({{relevance}}){{^brief}} — {{narrative}}{{/brief}}');
    await page.getByRole('button', { name: /^(preview|xem thử)$/i }).click();
    await expect(page.locator('#audience-preview pre')).toContainText(
      "- **Review each audience's note before publishing.** (full) — Release managers can review");
    await expectNoAccessibilityViolations(page, `audience editor (${language})`);
  }
});
