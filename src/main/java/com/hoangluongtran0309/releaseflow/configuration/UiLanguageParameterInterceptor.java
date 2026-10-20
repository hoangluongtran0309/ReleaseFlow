package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Keeps the language a link asked for with {@code ?lang=} in the {@code releaseflow_lang}
 * cookie, so the pages after it, and the form sent from it, stay in that language. A value
 * this deployment does not ship is ignored and leaves the cookie as it was. The account's
 * saved choice is never changed: that belongs to the picker in the user menu.
 */
class UiLanguageParameterInterceptor implements HandlerInterceptor {

    private final UiLocaleResolver localeResolver;

    UiLanguageParameterInterceptor(UiLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        localeResolver.fromParameter(request).ifPresent(locale -> localeResolver.setLocale(request, response, locale));
        return true;
    }
}
