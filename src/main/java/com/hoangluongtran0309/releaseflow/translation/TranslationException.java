package com.hoangluongtran0309.releaseflow.translation;

/**
 * A translation that did not succeed. The code is a fixed, safe value stored on the job;
 * it never carries the provider's response or the exception text.
 */
class TranslationException extends Exception {

    static final String UNAVAILABLE = "provider_unavailable";
    static final String RATE_LIMITED = "rate_limited";
    static final String REJECTED = "provider_rejected";
    static final String QUOTA_EXCEEDED = "quota_exceeded";
    static final String INVALID_RESPONSE = "invalid_response";
    /** The answer came back in its source language (ADR-0036). */
    static final String UNTRANSLATED = "untranslated";
    /** DeepL refused a translation over its glossary, so the glossary is sent again. */
    static final String GLOSSARY_REFUSED = "glossary_refused";
    /** The glossary DeepL was asked to change is gone. */
    static final String GLOSSARY_NOT_FOUND = "glossary_not_found";

    private final String code;
    private final boolean retryable;

    TranslationException(String code, boolean retryable) {
        super(code);
        this.code = code;
        this.retryable = retryable;
    }

    String code() {
        return code;
    }

    boolean retryable() {
        return retryable;
    }
}
