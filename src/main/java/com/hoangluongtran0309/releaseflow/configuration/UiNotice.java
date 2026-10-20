package com.hoangluongtran0309.releaseflow.configuration;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Something a page tells somebody: how serious it is, and the bundle key and values of
 * the sentence, never the sentence itself. The page writes it in the reader's language
 * when it renders, so a notice carried across a redirect reads the same as one shown in
 * place. It travels in the session as a flash attribute, which is why it is serializable.
 *
 * @param severity  which of the shared alert's four looks it takes
 * @param key       the bundle key of the sentence
 * @param arguments what the sentence fills in
 */
public record UiNotice(Severity severity, String key, List<Object> arguments) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The flash attribute the layout shows as a toast after a redirect. */
    public static final String FLASH_ATTRIBUTE = "notice";

    /** The model attribute a page shows as an alert above its content. */
    public static final String PAGE_ATTRIBUTE = "pageNotice";

    public UiNotice {
        arguments = arguments == null
                ? List.of()
                : Collections.unmodifiableList(Arrays.asList(arguments.toArray()));
    }

    public static UiNotice success(String key, Object... arguments) {
        return new UiNotice(Severity.SUCCESS, key, Arrays.asList(arguments));
    }

    public static UiNotice error(String key, Object... arguments) {
        return new UiNotice(Severity.ERROR, key, Arrays.asList(arguments));
    }

    /** A failure, in the words its own key gives it. */
    public static UiNotice error(LocalizedException failure) {
        return error(failure.messageKey(), failure.arguments());
    }


    /** Says {@code notice} on the page the redirect lands on. */
    public static void flash(RedirectAttributes redirect, UiNotice notice) {
        redirect.addFlashAttribute(FLASH_ATTRIBUTE, notice);
    }

    /** The values, in the shape {@code #messages.msgWithParams} takes them. */
    public Object[] args() {
        return arguments.toArray();
    }

    /** The severity as the shared alert and toast name it. */
    public String look() {
        return severity.name().toLowerCase(Locale.ROOT);
    }

    public enum Severity {
        SUCCESS,
        INFO,
        WARNING,
        ERROR
    }
}
