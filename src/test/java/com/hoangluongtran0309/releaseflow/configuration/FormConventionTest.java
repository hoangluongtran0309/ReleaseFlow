package com.hoangluongtran0309.releaseflow.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards how a form asks for input. The server decides what is valid and says so in the
 * reader's language, so no form lets the browser refuse it first with a bubble of its
 * own, and every field bound to a form object has a place where its error is written.
 */
class FormConventionTest {

    // A tag ends at the first > outside quotes: ${count > 0} inside an attribute is not its end.
    private static final String ATTRIBUTES = "((?:[^>\"]|\"[^\"]*\")*)";
    private static final Pattern FORM = Pattern.compile("<form\\b" + ATTRIBUTES + ">(.*?)</form>", Pattern.DOTALL);
    private static final Pattern CONTROL = Pattern.compile("<(input|textarea|select)\\b" + ATTRIBUTES + ">", Pattern.DOTALL);
    private static final Pattern TYPE = Pattern.compile("\\stype=\"([^\"]+)\"");
    private static final Pattern BOUND = Pattern.compile("th:field=\"\\*\\{(.+?)}\"");
    private static final Pattern ERRORS = Pattern.compile("th:errors=\"\\*\\{(.+?)}\"");
    private static final Pattern FIELD_ERROR = Pattern.compile("fieldError\\(([^,]+),");
    private static final Set<String> NOT_INPUT = Set.of("hidden", "submit", "button");

    @Test
    void noFormLetsTheBrowserRefuseItFirst() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path template : templates()) {
            String text = Files.readString(template);
            Matcher form = FORM.matcher(text);
            while (form.find()) {
                if (takesInput(form.group(2)) && !form.group(1).contains("novalidate")) {
                    found.add(template + ":" + line(text, form.start()));
                }
            }
        }

        assertThat(found).as("forms without novalidate").isEmpty();
    }

    @Test
    void everyBoundFieldHasAPlaceForItsError() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path template : templates()) {
            String text = Files.readString(template);
            Matcher form = FORM.matcher(text);
            while (form.find()) {
                String body = form.group(2);
                Set<String> shown = new HashSet<>();
                ERRORS.matcher(body).results().forEach(error -> shown.add(path(error.group(1))));
                FIELD_ERROR.matcher(body).results().forEach(error -> shown.add(path(error.group(1))));
                Matcher control = CONTROL.matcher(body);
                while (control.find()) {
                    Matcher bound = BOUND.matcher(control.group());
                    if (bound.find() && !NOT_INPUT.contains(type(control.group())) && !shown.contains(path(bound.group(1)))) {
                        found.add(template + ":" + line(text, form.start() + control.start()) + " " + bound.group(1));
                    }
                }
            }
        }

        assertThat(found).as("bound fields with nowhere to show their error").isEmpty();
    }

    private static boolean takesInput(String body) {
        Matcher control = CONTROL.matcher(body);
        while (control.find()) {
            if (!control.group(1).equals("input") || !NOT_INPUT.contains(type(control.group()))) {
                return true;
            }
        }
        return false;
    }

    private static String type(String control) {
        Matcher type = TYPE.matcher(control);
        return type.find() ? type.group(1) : "text";
    }

    // The same property however a template writes it: *{actions[__${row.index}__].secret}
    // in th:field, and |actions[${row.index}].secret| in a fragment argument.
    private static String path(String expression) {
        return expression.replace("__", "").replace("${", "").replace("}", "")
                .replace("|", "").replace("'", "").strip();
    }

    private static int line(String text, int offset) {
        return (int) text.substring(0, offset).chars().filter(character -> character == '\n').count() + 1;
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/templates"))) {
            return paths.filter(path -> path.toString().endsWith(".html")).toList();
        }
    }
}
