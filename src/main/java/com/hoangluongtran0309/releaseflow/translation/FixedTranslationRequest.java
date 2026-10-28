package com.hoangluongtran0309.releaseflow.translation;

import jakarta.validation.constraints.AssertTrue;

import java.util.Locale;

/**
 * A term that always becomes another between two languages. A language is given as its
 * primary language: {@code vi-VN} is taken as {@code vi}, since a DeepL dictionary applies
 * to every region of its language.
 */
public class FixedTranslationRequest {

    @PrimaryLanguage
    private String sourceLanguage;

    @PrimaryLanguage
    private String targetLanguage;

    @GlossaryTerm
    private String sourceTerm;

    @GlossaryTerm
    private String targetTerm;

    @AssertTrue(message = "{validation.glossaryLanguage.same}")
    public boolean isDifferentLanguages() {
        return sourceLanguage == null || !sourceLanguage.equals(targetLanguage);
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = primary(sourceLanguage);
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = primary(targetLanguage);
    }

    public String getSourceTerm() {
        return sourceTerm;
    }

    public void setSourceTerm(String sourceTerm) {
        this.sourceTerm = sourceTerm == null ? null : sourceTerm.strip();
    }

    public String getTargetTerm() {
        return targetTerm;
    }

    public void setTargetTerm(String targetTerm) {
        this.targetTerm = targetTerm == null ? null : targetTerm.strip();
    }

    private static String primary(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String tag = language.strip().replace('_', '-');
        int dash = tag.indexOf('-');
        return (dash < 0 ? tag : tag.substring(0, dash)).toLowerCase(Locale.ROOT);
    }
}
