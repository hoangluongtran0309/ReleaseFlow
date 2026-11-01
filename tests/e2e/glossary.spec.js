// The Organization glossary (ADR-0036) driven through its page in both interface languages.
// The two projects run it at 360 px in the light theme and at 1440 px in the dark one, and
// every state the page can show, an error included, is checked against Axe.
import { test, expect } from '@playwright/test';
import { expectNoAccessibilityViolations, registerAndSignIn } from './support.js';

test.describe('glossary', () => {
  for (const language of ['en', 'vi']) {
    test(`an administrator keeps terms and fixes a translation (${language})`, async ({ page, context, baseURL }) => {
      await registerAndSignIn(page);
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await page.goto('/audiences');
      await page.locator('#targetLanguages').fill('en, vi');
      await page.locator('#release-languages button[type="submit"]').click();

      await page.goto('/glossary');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(language === 'en' ? 'Glossary' : 'Bảng thuật ngữ');
      await expect(page.locator('#pair-en-vi')).toContainText(language === 'en' ? 'No entries' : 'Không có mục');
      await expectNoAccessibilityViolations(page, `empty glossary (${language})`);

      // Nothing is seeded: suggestions are offered, and only the chosen ones are kept.
      const suggestions = page.locator('#glossary-suggestions');
      await suggestions.getByRole('checkbox', { name: 'console', exact: true }).check();
      await suggestions.getByRole('checkbox', { name: 'pull request', exact: true }).check();
      await suggestions.getByRole('button').click();
      await expect(page.locator('.toast-item')).toBeVisible();
      await expect(page.locator('#kept-terms li')).toHaveText(['console', 'pull request']);
      await expect(suggestions.getByRole('checkbox', { name: 'console', exact: true })).toHaveCount(0);

      await page.getByLabel(language === 'en' ? 'Term to keep' : 'Thuật ngữ cần giữ').fill('image');
      await page.getByRole('button', { name: language === 'en' ? 'Keep term' : 'Giữ thuật ngữ' }).click();
      await expect(page.locator('#kept-terms li')).toHaveText(['console', 'image', 'pull request']);

      // A term already kept is refused in the reader's language.
      await page.getByLabel(language === 'en' ? 'Term to keep' : 'Thuật ngữ cần giữ').fill('console');
      await page.getByRole('button', { name: language === 'en' ? 'Keep term' : 'Giữ thuật ngữ' }).click();
      await expect(page.getByRole('alert')).toContainText(
        language === 'en' ? 'The glossary already has "console".' : 'Bảng thuật ngữ đã có "console".');
      await expectNoAccessibilityViolations(page, `a refused term (${language})`);

      // A fixed translation whose two languages are the same is refused under its field.
      const fixed = page.locator('#fixed-translations form').last();
      await fixed.getByLabel(language === 'en' ? 'Into' : 'Sang').fill('en');
      await fixed.getByLabel(language === 'en' ? 'Term' : 'Thuật ngữ', { exact: true }).fill('storage');
      await fixed.getByLabel(language === 'en' ? 'Always becomes' : 'Luôn dịch thành').fill('lưu trữ');
      await fixed.getByRole('button').click();
      await expect(page.locator('#differentLanguages-error')).toHaveText(language === 'en'
        ? 'Choose a language other than the one it is translated from.'
        : 'Vui lòng chọn ngôn ngữ khác với ngôn ngữ nguồn.');
      await expect(page.locator('#targetLanguage')).toHaveAttribute('aria-invalid', 'true');
      await expectNoAccessibilityViolations(page, `a refused translation (${language})`);

      await fixed.getByLabel(language === 'en' ? 'Into' : 'Sang').fill('vi');
      await fixed.getByRole('button').click();
      await expect(page.locator('#fixed-translations tbody tr')).toHaveCount(1);
      await expect(page.locator('#fixed-translations tbody')).toContainText('lưu trữ');
      await expect(page.locator('#pair-en-vi')).toContainText('4');
      await expect(page.locator('#pair-en-vi')).toContainText(
        language === 'en' ? 'Synced at the next approval' : 'Đồng bộ ở lần duyệt tới');
      const overflow = await page.evaluate(() =>
        Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth));
      expect(overflow, `horizontal overflow (${language})`).toBeLessThanOrEqual(1);
      await expectNoAccessibilityViolations(page, `a filled glossary (${language})`);

      // Deleting a term takes it away again.
      await page.getByRole('button', { name: language === 'en' ? 'Delete image' : 'Xoá image' }).click();
      await expect(page.locator('#kept-terms li')).toHaveText(['console', 'pull request']);
    });
  }
});
