(() => {
    const themeKey = 'releaseflow:theme';

    const currentTheme = () => document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';

    const focusFirstInvalid = () => {
        if (document.activeElement?.matches('[aria-invalid="true"]')) return;
        document.querySelector('[aria-invalid="true"]')?.focus();
    };

    const toggleTheme = () => {
        const next = currentTheme() === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        try {
            localStorage.setItem(themeKey, next);
        } catch (ignored) {
            // Storage can be unavailable in private windows; the theme still applies to this page.
        }
        window.dispatchEvent(new CustomEvent('theme:changed', { detail: { theme: next } }));
        return next;
    };

    // Invitation links carry the token in the URL fragment, which browsers never send to
    // the server. Move it into the server-rendered form, drop it from the address bar and
    // history, then submit. Without JavaScript the invitee pastes the code instead.
    document.addEventListener('DOMContentLoaded', () => {
        const form = document.querySelector('form[data-invitation-open]');
        const match = /^#token=([A-Za-z0-9_-]{1,128})$/.exec(window.location.hash);
        if (form && match) {
            form.elements.token.value = match[1];
            history.replaceState(null, '', window.location.pathname);
            form.requestSubmit();
            return;
        }

        focusFirstInvalid();
    });

    document.addEventListener('alpine:initialized', focusFirstInvalid);

    document.addEventListener('alpine:init', () => {
        // Every label is rendered on the server and read from the element's data
        // attributes, so no English wording is kept in this file.
        Alpine.data('mobileNavigation', () => ({
            open: false,
            returnFocus: null,
            focusTimer: null,
            desktop: null,
            desktopListener: null,
            init() {
                this.desktop = window.matchMedia('(min-width: 1024px)');
                this.desktopListener = (event) => {
                    if (event.matches) this.close(false);
                };
                this.desktop.addEventListener('change', this.desktopListener);
            },
            show() {
                this.returnFocus = document.activeElement;
                this.open = true;
                this.$nextTick(() => this.focusCloseButton());
            },
            focusCloseButton(attempt = 0) {
                const button = this.$root.querySelector('[data-mobile-navigation-close]');
                button?.focus();
                if (document.activeElement === button || attempt >= 12) {
                    this.focusTimer = null;
                    return;
                }
                this.focusTimer = window.setTimeout(() => this.focusCloseButton(attempt + 1), 50);
            },
            close(restoreFocus) {
                if (!this.open) return;
                if (this.focusTimer !== null) window.clearTimeout(this.focusTimer);
                this.focusTimer = null;
                this.open = false;
                if (restoreFocus && this.returnFocus) {
                    this.$nextTick(() => this.returnFocus.focus());
                }
            },
            trapFocus(event) {
                if (!this.open || this.desktop.matches) return;
                const focusable = [...this.$refs.panel.querySelectorAll(
                    'a[href], button:not([disabled]), select:not([disabled]), input:not([disabled]):not([type="hidden"]), [tabindex]:not([tabindex="-1"])'
                )].filter((element) => element.getClientRects().length > 0);
                if (focusable.length === 0) return;
                const first = focusable[0];
                const last = focusable[focusable.length - 1];
                if (event.shiftKey && document.activeElement === first) {
                    event.preventDefault();
                    last.focus();
                } else if (!event.shiftKey && document.activeElement === last) {
                    event.preventDefault();
                    first.focus();
                }
            },
            destroy() {
                if (this.focusTimer !== null) window.clearTimeout(this.focusTimer);
                this.desktop?.removeEventListener('change', this.desktopListener);
            },
        }));

        Alpine.data('themeToggle', () => ({
            theme: currentTheme(),
            labels: { light: '', dark: '' },
            init() {
                this.labels = {
                    light: this.$el.dataset.labelLight ?? '',
                    dark: this.$el.dataset.labelDark ?? '',
                };
                window.addEventListener('theme:changed', (event) => { this.theme = event.detail.theme; });
                window.addEventListener('storage', (event) => {
                    if (event.key === themeKey && (event.newValue === 'dark' || event.newValue === 'light')) {
                        document.documentElement.dataset.theme = event.newValue;
                        this.theme = event.newValue;
                    }
                });
            },
            get label() { return this.theme === 'dark' ? this.labels.light : this.labels.dark; },
            toggle() { this.theme = toggleTheme(); },
        }));

        // Kept under explicit state rather than DaisyUI's focus-driven dropdown
        // so the sign-out form inside the panel stays reachable by keyboard.
        Alpine.data('userMenu', () => ({
            open: false,
            toggle() { this.open ? this.close(false) : this.show(); },
            show() { this.open = true; },
            close(focusTrigger) {
                if (!this.open) return;
                this.open = false;
                if (focusTrigger) this.$refs.trigger.focus();
            },
            closeOnFocusOut(event) {
                if (!this.$el.contains(event.relatedTarget)) this.close(false);
            },
        }));

        Alpine.data('passwordField', () => ({
            visible: false,
            labels: { show: '', hide: '' },
            init() {
                this.labels = {
                    show: this.$el.dataset.labelShow ?? '',
                    hide: this.$el.dataset.labelHide ?? '',
                };
            },
            get label() { return this.visible ? this.labels.hide : this.labels.show; },
            toggle() { this.visible = !this.visible; },
        }));

        // Inserts a template variable at the caret and warns before leaving with unsaved
        // edits. The variable name comes from the button's data attribute, never from
        // an Alpine expression.
        Alpine.data('audienceEditor', () => ({
            dirty: false,
            init() {
                window.addEventListener('beforeunload', (event) => {
                    if (this.dirty) event.preventDefault();
                });
            },
            insert(variable) {
                const template = this.$refs.template;
                const token = `{{${variable}}}`;
                const start = template.selectionStart ?? template.value.length;
                const end = template.selectionEnd ?? start;
                template.setRangeText(token, start, end, 'end');
                template.focus();
                this.dirty = true;
            },
        }));

        // Checks the release's notes every three seconds while some are still being
        // translated, and reloads the page once none is. The URL comes from the element's
        // data attribute, never from an Alpine expression.
        Alpine.data('translationPoll', () => ({
            timer: null,
            timedOut: false,
            init() {
                const url = this.$el.dataset.pollUrl;
                const stopAt = Date.now() + 10 * 60 * 1000;
                this.timer = setInterval(async () => {
                    if (Date.now() > stopAt) {
                        this.stop();
                        this.timedOut = true;
                        return;
                    }
                    try {
                        const response = await fetch(url, { headers: { Accept: 'application/json' } });
                        if (!response.ok) return;
                        const notes = await response.json();
                        if (!notes.some((note) => note.translationStatus === 'PENDING')) {
                            this.stop();
                            window.location.reload();
                        }
                    } catch (ignored) {
                        // A missed check is retried on the next tick.
                    }
                }, 3000);
            },
            stop() {
                if (this.timer !== null) clearInterval(this.timer);
                this.timer = null;
            },
            reload() { window.location.reload(); },
            destroy() { this.stop(); },
        }));

        // Reads the value from the element marked x-ref="source" so server data
        // never has to be interpolated into an Alpine expression.
        Alpine.data('copyText', () => ({
            copied: false,
            announcement: '',
            labels: { copy: '', copied: '', manual: '' },
            init() {
                this.labels = {
                    copy: this.$el.dataset.labelCopy ?? '',
                    copied: this.$el.dataset.labelCopied ?? '',
                    manual: this.$el.dataset.labelCopyManual ?? '',
                };
            },
            async copy() {
                const source = this.$refs.source;
                try {
                    await navigator.clipboard.writeText(source.value ?? source.textContent.trim());
                } catch (ignored) {
                    if (typeof source.select === 'function') {
                        source.focus();
                        source.select();
                    } else {
                        const selection = window.getSelection();
                        const range = document.createRange();
                        range.selectNodeContents(source);
                        selection.removeAllRanges();
                        selection.addRange(range);
                    }
                    this.announcement = this.labels.manual;
                    return;
                }
                this.copied = true;
                this.announcement = this.labels.copied;
                setTimeout(() => {
                    this.copied = false;
                    this.announcement = '';
                }, 2000);
            },
            get label() { return this.copied ? this.labels.copied : this.labels.copy; },
        }));
    });
})();
