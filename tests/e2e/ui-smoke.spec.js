// Every page a signed-in person reaches, opened and checked against Axe. The suite runs
// at 360 px in the light theme and at 1440 px in the dark one, so a rule that only
// breaks in one of them still fails the build.
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, signIn, unique } from './support.js';

test.describe('workspace pages', () => {
  test('open, stay usable, and break no accessibility rule', async ({ page, context, baseURL }) => {
    await registerAndSignIn(page);
    const project = await createProject(page);

    const pages = [
      ['overview', '/'],
      ['change inbox', `/changes?project=${project.id}`],
      ['releases', `/releases?project=${project.id}`],
      ['projects', '/projects'],
      ['sensitive paths', `/projects/${project.id}/sensitive-paths`],
      ['project context', `/projects/${project.id}/context`],
      ['members', '/members'],
      ['audiences', '/audiences'],
      ['new audience', '/audiences/new'],
      ['categories', '/categories'],
      ['glossary', '/glossary'],
      ['automation', '/automation'],
    ];

    for (const language of ['en', 'vi']) {
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      for (const [name, path] of pages) {
        await page.goto(path);
        await expect(page.locator('main#page-content')).toBeVisible();
        // A toast answers something just done; a page opened plainly has nothing to say.
        await expect(page.locator('.toast-item'), `toasts on ${name}`).toHaveCount(0);
        const horizontalOverflow = await page.evaluate(() =>
          Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth));
        expect(horizontalOverflow, `horizontal overflow on ${name} (${language})`).toBeLessThanOrEqual(1);
        const shortSummaries = await page.locator('details > summary').evaluateAll((summaries) => summaries
          .filter((summary) => summary.getClientRects().length > 0)
          .map((summary) => summary.getBoundingClientRect().height)
          .filter((height) => height < 44));
        expect(shortSummaries, `short disclosure targets on ${name} (${language})`).toEqual([]);
        await expectNoAccessibilityViolations(page, `${name} (${language})`);
      }
    }
  });

  test('the public pages break no accessibility rule either', async ({ page }) => {
    for (const [name, path] of [['home', '/'], ['sign in', '/login'], ['register', '/register']]) {
      await page.goto(path);
      await expectNoAccessibilityViolations(page, name);
    }
  });

  test('the ReleaseFlow wordmark follows the active theme and its toggle', async ({ page }) => {
    await page.goto('/login');
    const root = page.locator('html');
    const logo = page.locator('.releaseflow-logo:visible').first();
    const lightLogo = logo.locator('.releaseflow-logo-light');
    const darkLogo = logo.locator('.releaseflow-logo-dark');

    await expect(logo).toHaveAttribute('role', 'img');
    await expect(logo).toHaveAttribute('aria-label', 'ReleaseFlow');
    const naturalWidths = await logo.locator('img').evaluateAll((images) =>
      images.map((image) => image.naturalWidth));
    expect(naturalWidths.every((width) => width > 0), 'both wordmark assets load').toBe(true);

    const initialTheme = await root.getAttribute('data-theme');
    expect(['light', 'dark']).toContain(initialTheme);
    if (initialTheme === 'light') {
      await expect(lightLogo).toBeVisible();
      await expect(darkLogo).toBeHidden();
    } else {
      await expect(lightLogo).toBeHidden();
      await expect(darkLogo).toBeVisible();
    }

    await page.locator('.theme-floating-control').click();
    const nextTheme = initialTheme === 'light' ? 'dark' : 'light';
    await expect(root).toHaveAttribute('data-theme', nextTheme);
    if (nextTheme === 'light') {
      await expect(lightLogo).toBeVisible();
      await expect(darkLogo).toBeHidden();
    } else {
      await expect(lightLogo).toBeHidden();
      await expect(darkLogo).toBeVisible();
    }
  });

  test('an unknown address answers with a page, not a stack trace', async ({ page }) => {
    // Signed in first: to a stranger every unknown address is the sign-in page, which
    // says nothing about what does or does not exist.
    await registerAndSignIn(page);
    const response = await page.goto('/nope');

    expect(response.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: /back to the workspace|về không gian làm việc/i })).toBeVisible();
    await expect(page.locator('body')).not.toContainText('Exception');
    await expectNoAccessibilityViolations(page, 'not found');

    // Something this Organization does not have is the same page, saying what was missing.
    const missing = await page.goto(`/projects/${crypto.randomUUID()}/releases/${crypto.randomUUID()}`);
    expect(missing.status()).toBe(404);
    await expect(page.locator('body')).toContainText(/release was not found|không tìm thấy release/i);
    await expectNoAccessibilityViolations(page, 'a missing release');
  });

  test('a form sent after its session ended asks to sign in again', async ({ page, context }) => {
    await registerAndSignIn(page);
    await page.goto('/projects');
    await page.getByPlaceholder(/project name|tên project/i).fill(unique('Checkout'));

    await context.clearCookies();
    await page.getByRole('button', { name: /create project|tạo project/i }).click();

    await expect(page).toHaveURL(/\/login\?expired$/);
    await expect(page.getByRole('status')).toContainText(/session ended|phiên làm việc đã hết/i);
    await expect(page.locator('body')).not.toContainText(/another Organization|Organization khác/);
    await expectNoAccessibilityViolations(page, 'sign in after an expired session');
  });

  test('a form older than the sign-in beside it is called out of date', async ({ page, context }) => {
    const owner = await registerAndSignIn(page);
    await page.goto('/projects');
    await page.getByPlaceholder(/project name|tên project/i).fill(unique('Checkout'));

    // Signing in again in another tab starts a new session and a new form token.
    const other = await context.newPage();
    await signIn(other, owner.email, owner.password);
    await other.close();
    const response = page.waitForResponse((answer) => answer.request().method() === 'POST');
    await page.getByRole('button', { name: /create project|tạo project/i }).click();

    expect((await response).status()).toBe(403);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(/out of date|đã cũ/i);
    await expect(page.locator('body')).not.toContainText(/another Organization|Organization khác/);
    await expectNoAccessibilityViolations(page, 'out-of-date form');
  });

  test('the sidebar brand fits its row in every shipped language', async ({ page, context, baseURL }) => {
    await registerAndSignIn(page);
    for (const language of ['en', 'vi']) {
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await page.goto('/');
      const menu = page.getByRole('button', { name: /open navigation|mở điều hướng/i });
      if (await menu.isVisible()) {
        await menu.click();
      }
      const brand = page.locator('#app-sidebar > div:first-child > a');
      await expect(brand).toBeVisible();
      const brandLogo = brand.locator('.releaseflow-logo');
      await expect(brandLogo).toBeVisible();
      const activeTheme = await page.locator('html').getAttribute('data-theme');
      await expect(brandLogo.locator(`.releaseflow-logo-${activeTheme}`)).toBeVisible();

      // A tagline that wraps must grow the row downwards, never spill over its top edge.
      const fit = await brand.evaluate((row) => {
        const box = row.getBoundingClientRect();
        const text = row.querySelector('.app-sidebar-brand-content').getBoundingClientRect();
        return { rowTop: box.top, rowBottom: box.bottom, textTop: text.top, textBottom: text.bottom };
      });
      expect(fit.textTop, `${language} tagline top`).toBeGreaterThanOrEqual(fit.rowTop);
      expect(fit.textBottom, `${language} tagline bottom`).toBeLessThanOrEqual(fit.rowBottom);
      expect(fit.rowBottom - fit.rowTop, `${language} brand row height`).toBeLessThanOrEqual(64);

      if (await menu.isVisible()) await page.keyboard.press('Escape');
      await page.getByRole('button', { name: /open user menu|mở menu người dùng/i }).click();
      await expect(page.locator('#user-menu')).toContainText(language === 'vi' ? 'Quản trị viên' : 'Administrator');
    }
  });

  test('the mobile drawer and project switcher keep keyboard and project context', async ({ page }) => {
    await registerAndSignIn(page);
    const first = await createProject(page, unique('First'));
    const second = await createProject(page, unique('Second'));

    await page.goto(`/releases?project=${first.id}`);
    await page.getByLabel(/^version|phiên bản$/i).fill('1.0.0');
    await page.getByRole('button', { name: /create release|tạo release/i }).click();
    await expect(page).toHaveURL(new RegExp(`/projects/${first.id}/releases/`));

    const openNavigation = page.getByRole('button', { name: /open navigation|mở điều hướng/i });
    const mobile = await openNavigation.isVisible();
    if (mobile) {
      await openNavigation.focus();
      await page.keyboard.press('Enter');
      await expect(openNavigation).toHaveAttribute('aria-expanded', 'true');
      await expect(page.getByRole('button', { name: /close navigation|đóng điều hướng/i })).toBeFocused();
    }

    const switcher = page.locator(mobile ? '#project-switcher-mobile' : '#project-switcher');
    await switcher.selectOption(second.id);
    await switcher.locator('..').getByRole('button', { name: /switch|chuyển/i }).click();
    await expect(page).toHaveURL(new RegExp(`/releases\\?project=${second.id}$`));

    await page.goto(`/members?project=${first.id}`);
    if (mobile) await openNavigation.click();
    const organizationSwitcher = page.locator(mobile ? '#project-switcher-mobile' : '#project-switcher');
    await organizationSwitcher.selectOption(second.id);
    await organizationSwitcher.locator('..').getByRole('button', { name: /switch|chuyển/i }).click();
    await expect(page).toHaveURL(new RegExp(`/\\?project=${second.id}$`));
    await page.goto('/members');
    if (mobile) {
      await openNavigation.click();
      await expect(page.locator('#project-switcher-mobile')).toHaveValue(second.id);
      await page.keyboard.press('Escape');
      await expect(openNavigation).toHaveAttribute('aria-expanded', 'false');
      await expect(openNavigation).toBeFocused();
    } else {
      await expect(page.locator('#project-switcher')).toHaveValue(second.id);
    }
  });

  test('an empty form reaches the server, which says what each field needs', async ({ page }) => {
    await page.goto('/register');
    // Required fields carry a drawn mark beside their label.
    const mark = await page.locator('label:has(#organizationName) .label-text')
      .evaluate((label) => getComputedStyle(label, '::after').content);
    expect(mark).toContain('*');

    // No browser bubble stops it: the request is sent, and the answer is the server's.
    const [response] = await Promise.all([
      page.waitForResponse((answer) => answer.request().method() === 'POST' && answer.url().endsWith('/register')),
      page.getByRole('button', { name: /create organization|tạo organization/i }).click(),
    ]);
    expect(response.status()).toBe(400);
    await expect(page.locator('.field-error')).toHaveCount(4);
    await expect(page.locator('form .alert-error')).toHaveCount(1);
    await expect(page.locator('#organizationName')).toBeFocused();
    await expectNoAccessibilityViolations(page, 'an empty form sent back');
  });

  test('an error summary spans a grid form instead of taking its first field\'s cell', async ({ page }) => {
    await registerAndSignIn(page);
    const project = await createProject(page);
    await page.goto(`/releases?project=${project.id}`);
    await page.getByRole('button', { name: /create release|tạo release/i }).click();

    const summary = page.locator('form .alert-error');
    await expect(summary).toHaveCount(1);
    const summaryBox = await summary.boundingBox();
    const formBox = await summary.locator('xpath=ancestor::form').boundingBox();
    expect(summaryBox.width).toBeGreaterThan(formBox.width * 0.95);
    const versionBox = await page.locator('#version').boundingBox();
    expect(versionBox.y).toBeGreaterThan(summaryBox.y + summaryBox.height);
  });

  test('a link chooses the language, and the form sent from that page keeps it', async ({ page }) => {
    await page.goto('/register?lang=vi');
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');

    await page.getByRole('button', { name: /tạo organization/i }).click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
    await expect(page.locator('#organizationName-error')).toHaveText('Vui lòng nhập tên Organization.');

    await page.goto('/register?lang=en');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });

  test('server validation identifies and focuses the first invalid field', async ({ page }) => {
    await page.goto('/register');
    await page.getByLabel(/organization name|tên organization/i).fill(unique('Acme'));
    await page.getByLabel(/release note language|ngôn ngữ release note/i).fill('not a language tag');
    await page.getByLabel(/^your name|tên của bạn/i).fill('Owner');
    await page.getByLabel(/^email/i).fill(`${unique('owner')}@example.test`);
    await page.getByLabel(/^password|mật khẩu/i).fill('a-secure-e2e-password');
    await page.getByRole('button', { name: /create organization|tạo organization/i }).click();

    const outputLanguage = page.locator('#outputLanguage');
    await expect(outputLanguage).toHaveAttribute('aria-invalid', 'true');
    await expect(outputLanguage).toHaveAttribute('aria-describedby', 'outputLanguage-error');
    await expect(page.locator('#outputLanguage-error')).toBeVisible();
    await expect(outputLanguage).toBeFocused();
  });

  test('what a saved form did is said once, after its redirect, as an accessible toast', async ({ page }) => {
    await registerAndSignIn(page);
    await page.goto('/projects');
    await page.locator('#outputLanguage').fill('vi');
    await page.getByRole('button', { name: /save language|lưu ngôn ngữ/i }).click();

    await expect(page).toHaveURL(/\/projects$/);
    const toast = page.locator('.toast-item');
    await expect(toast).toHaveCount(1);
    await expect(toast).toHaveAttribute('role', 'status');
    await expect(toast.locator('.toast-icon-success')).toHaveCount(1);
    await expectNoAccessibilityViolations(page, 'a toast after a redirect');

    await page.reload();
    await expect(page.locator('.toast-item')).toHaveCount(0);
  });

  test('copy success and failure are announced, with a manual fallback', async ({ page }) => {
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'clipboard', {
        configurable: true,
        value: { writeText: async () => { throw new Error('clipboard unavailable'); } },
      });
    });
    await registerAndSignIn(page);
    await page.goto('/members');
    await page.getByLabel(/email address|địa chỉ email/i).fill(`${unique('member')}@example.test`);
    await page.getByRole('button', { name: /create invitation|tạo lời mời/i }).click();

    const copy = page.locator('[x-data="copyText()"]');
    await page.evaluate(() => { navigator.clipboard.writeText = async () => {}; });
    await copy.getByRole('button', { name: /copy|sao chép/i }).click();
    await expect(copy.getByRole('status')).toContainText(/copied|đã sao chép/i);

    await page.evaluate(() => {
      navigator.clipboard.writeText = async () => { throw new Error('clipboard unavailable'); };
    });
    await copy.getByRole('button', { name: /copy|sao chép/i }).click();
    await expect(copy.getByRole('status')).toContainText(/value is selected|giá trị đã được chọn/i);
    const selected = await copy.locator('input').evaluate((input) =>
      input.selectionStart === 0 && input.selectionEnd === input.value.length);
    expect(selected).toBe(true);
  });

  test('translation polling stops with an explicit timeout state', async ({ page }) => {
    await registerAndSignIn(page);
    await page.goto('/automation');
    await page.route('**/__translation-status', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ translationStatus: 'PENDING' }]),
    }));
    await page.clock.install();
    await page.evaluate(() => {
      const fixture = document.createElement('div');
      fixture.id = 'translation-poll-test';
      fixture.setAttribute('x-data', 'translationPoll()');
      fixture.dataset.pollUrl = '/__translation-status';
      fixture.innerHTML = '<span id="translation-timeout-state" x-show="timedOut" x-cloak>timeout</span>';
      document.body.appendChild(fixture);
      window.Alpine.initTree(fixture);
    });

    await page.clock.fastForward(603_001);
    await expect(page.locator('#translation-timeout-state')).toBeVisible();
  });

  test('consequential automation actions require a disclosed confirmation', async ({ page }) => {
    await registerAndSignIn(page);
    const project = await createProject(page);
    const ruleName = unique('Announce');
    await page.goto('/automation');
    await page.locator('#name').fill(ruleName);
    await page.locator('#triggerType').selectOption('MANUAL');
    await page.locator('#projectId').selectOption(project.id);
    await page.locator('select[name="actions[0].actionType"]').selectOption('SLACK');
    await page.locator('select[name="actions[0].audienceId"]').selectOption({ index: 1 });
    await page.locator('select[name="actions[0].language"]').selectOption('en');
    await page.locator('input[name="actions[0].secret"]').fill('https://hooks.slack.com/services/T0/B0/secret');
    await page.getByRole('button', { name: /add rule|thêm quy tắc/i }).click();

    const row = page.locator('tr[id^="rule-"]', { hasText: ruleName });
    await row.getByRole('button', { name: /enable|bật/i }).click();
    const enabledRow = page.locator('tr[id^="rule-"]', { hasText: ruleName });
    await enabledRow.locator('summary').filter({ hasText: /^(Disable|Tắt)$/i }).click();
    await expect(enabledRow.getByText(/disable this rule|tắt quy tắc này/i)).toBeVisible();
    await enabledRow.locator('summary').filter({ hasText: /^(Archive|Lưu trữ)$/i }).click();
    await expect(enabledRow.getByText(/archive this rule|lưu trữ quy tắc này/i)).toBeVisible();
  });

  test('conditional automation fields remain available without JavaScript', async ({ browser, baseURL }) => {
    const context = await browser.newContext({
      baseURL,
      javaScriptEnabled: false,
      viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();
    try {
      await registerAndSignIn(page);
      await page.goto('/automation');
      await expect(page.locator('.progressive-fields').first()).toBeVisible();
      await expect(page.locator('html')).toHaveClass(/no-js/);
    } finally {
      await context.close();
    }
  });
});
