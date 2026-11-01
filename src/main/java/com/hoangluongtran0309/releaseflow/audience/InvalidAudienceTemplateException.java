package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidAudienceTemplateException extends LocalizedException {

    public static final String INVALID = "template_invalid";
    public static final String NARRATIVES_PATH = "template_narratives_path";
    public static final String LANGUAGE_NOT_TARGETED = "template_language_not_targeted";

    static final String ITEM_FIELD = "templateBody";
    static final String FOOTER_FIELD = "footerTemplateBody";

    private final String code;
    private final String field;

    InvalidAudienceTemplateException(String code, String messageKey, Object... arguments) {
        this(code, ITEM_FIELD, messageKey, arguments);
    }

    private InvalidAudienceTemplateException(String code, String field, String messageKey, Object... arguments) {
        super(messageKey, arguments);
        this.code = code;
        this.field = field;
    }

    /** A footer template that does not render, with the same contract as an item template. */
    static InvalidAudienceTemplateException footer(String messageKey, Object... arguments) {
        return new InvalidAudienceTemplateException(INVALID, FOOTER_FIELD, messageKey, arguments);
    }

    static InvalidAudienceTemplateException languageNotTargeted(String language) {
        return new InvalidAudienceTemplateException(
                LANGUAGE_NOT_TARGETED,
                "error.template_language_not_targeted",
                language.strip()
        );
    }

    public String code() {
        return code;
    }

    /** The form field the template was entered in: the item template or the footer. */
    public String field() {
        return field;
    }
}
