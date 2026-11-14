// An administrator retires an audience and brings it back (ADR-0044). Retiring keeps the
// audience in the list with a badge and says who retired it; the last active one is
// refused with a reason; bringing one back says who did. The whole path runs in both
// interface languages, and every page on it is checked by Axe.
import { test, expect } from '@playwright/test';
import { expectNoAccessibilityViolations, registerAndSignIn } from './support.js';

async function open(page, code) {
  await page.goto('/audiences');
  await page.locator('#audience-list a', { hasText: code }).click();
  await expect(page.locator('#audience-editor')).toBeVisible();
}

async function retire(page, code) {
  await open(page, code);
  await page.locator('#retire-audience summary').click();
  await page.getByRole('button', { name: /retire this audience|ngừng dùng audience này/i }).click();
}

for (const language of ['en', 'vi']) {
  test(`an audience is retired, the last active one is refused, and one is brought back (${language})`,
    async ({ page, context, baseURL }) => {
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await registerAndSignIn(page);

      // Retiring keeps the audience, marks it, and says who retired it.
      await open(page, 'support');
      await page.locator('#retire-audience summary').click();
      await expectNoAccessibilityViolations(page, `retire confirmation (${language})`);
      await page.getByRole('button', { name: /retire this audience|ngừng dùng audience này/i }).click();
      await expect(page.getByRole('status').first())
        .toContainText(/audience retired|đã ngừng dùng audience/i);
      await expect(page.locator('#audience-retired')).toContainText(/retired by owner|được owner ngừng dùng/i);
      await expect(page.locator('#audience-list a', { hasText: 'support' }))
        .toContainText(/retired|ngừng dùng/i);
      await expectNoAccessibilityViolations(page, `retired audience (${language})`);

      // The last active audience is refused, and the page says why.
      for (const code of ['end_user', 'contributor']) {
        await retire(page, code);
        await expect(page.locator('#audience-retired')).toBeVisible();
      }
      await retire(page, 'operator');
      await expect(page.getByRole('alert'))
        .toContainText(/at least one active audience|ít nhất một audience đang dùng/i);
      await expectNoAccessibilityViolations(page, `last active audience refused (${language})`);
      await open(page, 'operator');
      await expect(page.locator('#audience-retired')).toHaveCount(0);

      // Bringing one back says who did, and it can be retired again.
      await open(page, 'support');
      await page.getByRole('button', { name: /^(bring back|dùng lại)$/i }).click();
      await expect(page.getByRole('status').first())
        .toContainText(/audience brought back|đã dùng lại audience/i);
      await expect(page.locator('#audience-restored')).toContainText(/brought back by owner|được owner dùng lại/i);
      await expect(page.locator('#audience-list a', { hasText: 'support' }))
        .not.toContainText(/retired|ngừng dùng/i);
      await expect(page.locator('#retire-audience')).toBeVisible();
      await expectNoAccessibilityViolations(page, `brought-back audience (${language})`);
    });
}
