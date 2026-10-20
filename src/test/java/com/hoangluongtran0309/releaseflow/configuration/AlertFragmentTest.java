package com.hoangluongtran0309.releaseflow.configuration;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every severity a page may declare has a look of its own in the shared alert and toast,
 * and a severity outside the four fails the page instead of rendering unstyled.
 */
class AlertFragmentTest {

    private static final TemplateEngine ENGINE = engine();
    private static final Map<String, String> ICONS = Map.of(
            "success", "check",
            "info", "info",
            "warning", "alert",
            "error", "error"
    );

    @Test
    void everySeverityHasItsOwnAlert() {
        for (Map.Entry<String, String> severity : ICONS.entrySet()) {
            String html = render("<div th:insert=\"~{fragments/alert :: alert(${severity}, 'Saved.')}\"></div>",
                    severity.getKey());

            assertThat(html).as(severity.getKey())
                    .contains("class=\"alert alert-" + severity.getKey() + "\"")
                    .contains("/svg/ui-icons.svg#" + severity.getValue() + "\"")
                    .contains("role=\"" + ("error".equals(severity.getKey()) ? "alert" : "status") + "\"")
                    .contains("<span>Saved.</span>");
        }
    }

    @Test
    void anAlertCanWrapMarkupOfTheCallersOwn() {
        String html = render("<div th:insert=\"~{fragments/alert :: alertBody(${severity}, ~{::body})}\">"
                + "<span th:ref=\"body\"><button type=\"submit\">Retry</button></span></div>", "warning");

        assertThat(html)
                .contains("class=\"alert alert-warning\"")
                .contains("<button type=\"submit\">Retry</button>");
    }

    @Test
    void everySeverityHasItsOwnToast() {
        for (Map.Entry<String, String> severity : ICONS.entrySet()) {
            String html = render("<div th:replace=\"~{fragments/toast :: toast(${severity}, 'Saved.')}\"></div>",
                    severity.getKey());

            assertThat(html).as(severity.getKey())
                    .contains("toast-icon-" + severity.getKey())
                    .contains("/svg/ui-icons.svg#" + severity.getValue() + "\"");
        }
    }

    @Test
    void aSeverityOutsideTheFourFailsThePage() {
        for (String fragment : Set.of("fragments/alert :: alert", "fragments/toast :: toast")) {
            assertThatThrownBy(() -> render("<div th:replace=\"~{" + fragment + "(${severity}, 'x')}\"></div>", "danger"))
                    .as(fragment)
                    .isInstanceOf(TemplateProcessingException.class);
        }
    }

    private static String render(String template, String severity) {
        return ENGINE.process(template, new Context(Locale.ENGLISH, Map.of("severity", severity)));
    }

    private static TemplateEngine engine() {
        StringTemplateResolver inline = new StringTemplateResolver();
        inline.setTemplateMode(TemplateMode.HTML);
        inline.setResolvablePatterns(Set.of("<*"));
        inline.setOrder(1);

        ClassLoaderTemplateResolver files = new ClassLoaderTemplateResolver();
        files.setPrefix("templates/");
        files.setSuffix(".html");
        files.setTemplateMode(TemplateMode.HTML);
        files.setCharacterEncoding("UTF-8");
        files.setOrder(2);

        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(inline);
        engine.addTemplateResolver(files);
        // Outside a request there is no context path; the links only need to be written.
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
                return "";
            }
        });
        return engine;
    }
}
