// An administrator changes a member's role and removes them (ADR-0043). The member is
// somebody else, in their own browser, whose session must stop on its next request. The
// whole path runs in both interface languages, and every page on it is checked by Axe.
import { test, expect } from '@playwright/test';
import { expectNoAccessibilityViolations, registerAndSignIn, signIn, unique } from './support.js';

const PASSWORD = 'a-secure-e2e-password';

async function inviteAndJoin(page, browser, baseURL, language, name) {
  const email = `${unique('teammate')}@example.test`;
  await page.goto('/members');
  await page.getByRole('textbox').first().fill(email);
  await page.getByRole('button', { name: /create invitation|tạo lời mời/i }).click();
  const link = await page.getByRole('textbox').first().inputValue();
  await page.getByRole('link', { name: /i have sent the link|tôi đã gửi liên kết/i }).click();

  const context = await browser.newContext();
  await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
  const memberPage = await context.newPage();
  await memberPage.goto(link);
  await memberPage.getByLabel(/^your name|tên của bạn/i).fill(name);
  await memberPage.getByLabel(/^password|mật khẩu/i).fill(PASSWORD);
  await memberPage.getByRole('button', { name: /join organization|tham gia organization/i }).click();
  await expect(memberPage).toHaveURL(/\/login/);
  await signIn(memberPage, email, PASSWORD);
  return { context, memberPage };
}

for (const language of ['en', 'vi']) {
  test(`a member's role is changed and they are removed, their session ending each time (${language})`,
    async ({ page, browser, context, baseURL }) => {
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await registerAndSignIn(page);
      const name = unique('Linh');
      const member = await inviteAndJoin(page, browser, baseURL, language, name);
      const row = page.locator('tr[id^="member-"]', { hasText: name });

      // The last administrator is refused, and the page says why.
      await page.goto('/members');
      await expectNoAccessibilityViolations(page, `members with actions (${language})`);
      const owner = page.locator('tr[id^="member-"]').first();
      await owner.getByRole('combobox').selectOption('MEMBER');
      await owner.getByRole('button', { name: /change role|đổi vai trò/i }).click();
      await expect(page.getByRole('alert')).toContainText(/at least one administrator|ít nhất một quản trị viên/i);

      // Promoting the member ends the session they made as a member.
      await page.goto('/members');
      await row.getByRole('combobox').selectOption('ADMIN');
      await row.getByRole('button', { name: /change role|đổi vai trò/i }).click();
      await expect(page.locator('#membership-changes')).toContainText(name);
      await member.memberPage.goto('/changes');
      await expect(member.memberPage).toHaveURL(/\/login\?ended/);
      await expect(member.memberPage.getByRole('status'))
        .toContainText(/access to this Organization changed|quyền truy cập của bạn vào Organization này đã thay đổi/i);
      await expectNoAccessibilityViolations(member.memberPage, `session ended (${language})`);

      // Removing them asks first, then ends the session they sign in with again.
      await signIn(member.memberPage, await emailOf(page, name), PASSWORD);
      await page.goto('/members');
      await row.locator('summary').click();
      await expect(row).toContainText(name);
      await expectNoAccessibilityViolations(page, `remove confirmation (${language})`);
      await row.getByRole('button', { name: /remove member|xoá thành viên/i }).click();
      await expect(page.locator('#removed-members')).toContainText(name);
      await expectNoAccessibilityViolations(page, `removed members (${language})`);

      const refused = await member.memberPage.request.get('/api/session');
      expect(refused.status()).toBe(401);
      // The API call already ended the session, so a signed-in page asks for a sign-in.
      await member.memberPage.goto('/changes');
      await expect(member.memberPage).toHaveURL(/\/login/);
      await member.context.close();
    });
}

async function emailOf(page, name) {
  await page.goto('/members');
  return (await page.locator('tr[id^="member-"]', { hasText: name }).locator('td').nth(1).innerText()).trim();
}
