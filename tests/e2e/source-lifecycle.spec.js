// A source's lifecycle through the pages (ADR-0042): its health where people look, a new
// webhook secret that keeps the path, a disconnect that keeps the change it recorded, and a
// reconnect that brings it back under its own ID. Every page on the way is checked by Axe.
import { createHmac, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

async function deliver(request, webhookPath, secret, repository, number) {
  const payload = JSON.stringify({
    action: 'closed',
    repository: { full_name: `acme/${repository}` },
    pull_request: {
      number,
      merged: true,
      title: `feat: change ${number}`,
      body: 'A change delivered by the lifecycle test.',
      user: { login: 'octocat' },
      labels: [],
      base: { ref: 'main' },
      merge_commit_sha: String(number).repeat(40).slice(0, 40),
      merged_at: new Date().toISOString(),
      html_url: `https://github.com/acme/${repository}/pull/${number}`,
    },
  });
  return request.post(webhookPath, {
    data: payload,
    headers: {
      'Content-Type': 'application/json',
      'X-GitHub-Event': 'pull_request',
      'X-GitHub-Delivery': randomUUID(),
      'X-Hub-Signature-256': `sha256=${createHmac('sha256', secret).update(payload).digest('hex')}`,
    },
  });
}

test('a source is rotated, disconnected, and reconnected without losing its change', async ({ page, request, context, baseURL }) => {
  await registerAndSignIn(page);
  const project = await createProject(page);
  const repository = unique('app');

  await page.goto('/projects');
  await page.getByLabel(/repository owner|chủ repository/i).fill('acme');
  await page.getByLabel(/repository name|tên repository/i).fill(repository);
  await page.getByRole('button', { name: /generate webhook credentials|sinh thông tin webhook/i }).click();
  const webhookPath = await page.getByRole('textbox', { name: /webhook path|đường dẫn webhook/i }).inputValue();
  const firstSecret = await page.getByRole('textbox', { name: /webhook secret/i }).inputValue();
  expect((await deliver(request, webhookPath, firstSecret, repository, 1)).status()).toBe(200);

  // A delivery signed with something else is counted, and shown where people look.
  expect((await deliver(request, webhookPath, 'not-the-secret', repository, 2)).status()).toBe(401);
  await page.goto(`/changes?project=${project.id}`);
  await expect(page.locator('#source-imports')).toContainText(`acme/${repository}`);
  await expect(page.locator('#source-imports')).toContainText(/refused for their signature|bị từ chối vì chữ ký/i);
  await expectNoAccessibilityViolations(page, 'change inbox with source health');
  await page.goto('/');
  await expect(page.locator('#source-health')).toContainText(`acme/${repository}`);
  await expectNoAccessibilityViolations(page, 'overview with source health');

  // The source's own page, and a new secret that keeps the path.
  await page.goto('/projects');
  await page.getByRole('link', { name: /manage source|quản lý nguồn/i }).click();
  await expect(page.getByRole('heading', { name: `acme/${repository}` })).toBeVisible();
  await expectNoAccessibilityViolations(page, 'source page');
  await page.getByRole('button', { name: /generate a new secret|tạo secret mới/i }).click();
  await expect(page.getByRole('heading', { name: /put the new secret in the provider|đưa secret mới/i })).toBeVisible();
  await expect(page.getByRole('textbox', { name: /webhook path|đường dẫn webhook/i })).toHaveValue(webhookPath);
  const secondSecret = await page.getByRole('textbox', { name: /webhook secret/i }).inputValue();
  expect(secondSecret).not.toBe(firstSecret);
  await expectNoAccessibilityViolations(page, 'rotated secret');
  expect((await deliver(request, webhookPath, firstSecret, repository, 2)).status()).toBe(401);
  expect((await deliver(request, webhookPath, secondSecret, repository, 2)).status()).toBe(200);

  // Disconnecting says what happens first, on a page of its own.
  await page.getByRole('link', { name: /i have saved the secret|tôi đã lưu secret/i }).click();
  await page.getByRole('link', { name: /review and disconnect|xem lại và ngắt kết nối/i }).click();
  await expect(page.getByRole('heading', { name: /what happens|điều gì sẽ xảy ra/i })).toBeVisible();
  await expectNoAccessibilityViolations(page, 'disconnect confirmation');
  await page.getByRole('button', { name: /^disconnect$|^ngắt kết nối$/i }).click();
  await expect(page.locator('#source-health')).toContainText(/disconnected|đã ngắt kết nối/i);
  await expectNoAccessibilityViolations(page, 'disconnected source');
  expect((await deliver(request, webhookPath, secondSecret, repository, 3)).status()).toBe(401);

  // Its changes stay in the Change Inbox.
  await page.goto(`/changes?project=${project.id}`);
  await expect(page.getByRole('heading', { name: /feat: change 1/ })).toBeVisible();
  await expect(page.locator('#source-imports')).toContainText(/disconnected|đã ngắt kết nối/i);

  // Reconnecting gives a new path and secret; the change it already has is not taken twice.
  await page.goto('/projects');
  await page.getByRole('link', { name: /reconnect source|kết nối lại nguồn/i }).click();
  await page.getByRole('button', { name: /^reconnect$|^kết nối lại$/i }).click();
  await expect(page.locator('.page-eyebrow')).toContainText(/reconnected|đã kết nối lại/i);
  await expectNoAccessibilityViolations(page, 'source reconnected');
  const newPath = await page.getByRole('textbox', { name: /webhook path|đường dẫn webhook/i }).inputValue();
  const newSecret = await page.getByRole('textbox', { name: /webhook secret/i }).inputValue();
  expect(newPath).not.toBe(webhookPath);
  const duplicate = await deliver(request, newPath, newSecret, repository, 1);
  expect(duplicate.status()).toBe(200);
  expect((await duplicate.json()).outcome).toBe('duplicate');

  // The pages that stay are read in Vietnamese too.
  await context.addCookies([{ name: 'releaseflow_lang', value: 'vi', url: baseURL }]);
  await page.goto('/projects');
  await page.getByRole('link', { name: /quản lý nguồn/i }).click();
  await expectNoAccessibilityViolations(page, 'source page (vi)');
  await page.getByRole('link', { name: /xem lại và ngắt kết nối/i }).click();
  await expectNoAccessibilityViolations(page, 'disconnect confirmation (vi)');
});
