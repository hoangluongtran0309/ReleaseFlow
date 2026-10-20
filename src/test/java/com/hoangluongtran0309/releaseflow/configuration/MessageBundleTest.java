package com.hoangluongtran0309.releaseflow.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundle itself: the two languages say the same things, every key a template
 * or a constraint asks for exists, and no pattern is written in a way MessageFormat would
 * refuse when a page fills it in.
 */
class MessageBundleTest {

    private static final Pattern MESSAGE_KEY = Pattern.compile("#\\{(?:'([A-Za-z0-9_.]+)'|([A-Za-z0-9_.]+))[(}]");
    private static final Pattern CONSTRAINT_KEY = Pattern.compile("message = \"\\{([A-Za-z0-9_.]+)}\"");
    // A key named in Java: one passed to UiMessages, or any literal in the ui. or
    // validation. namespaces, such as the notice a controller flashes after a redirect.
    private static final Pattern CODE_KEY = Pattern.compile(
            "messages\\.get\\(\"([A-Za-z0-9_.]+)\"[,)]|\"((?:ui|validation)\\.[A-Za-z0-9_.]*[A-Za-z0-9_])\""
    );
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)[,}]");
    private static final List<String> BUNDLES = List.of("messages/ui", "messages/release-note", "messages/audience-presets");

    @Test
    void bothLanguagesCarryTheSameKeys() throws IOException {
        Properties english = load("messages/ui.properties");
        Properties vietnamese = load("messages/ui_vi.properties");

        assertThat(new TreeSet<>(vietnamese.stringPropertyNames()))
                .as("every English key is translated")
                .isEqualTo(new TreeSet<>(english.stringPropertyNames()));
        assertThat(english.stringPropertyNames()).isNotEmpty();
        for (String key : english.stringPropertyNames()) {
            assertThat(english.getProperty(key)).as(key).isNotBlank();
            assertThat(vietnamese.getProperty(key)).as(key).isNotBlank();
        }
    }

    @Test
    void everyBundleIsTranslatedKeyForKey() throws IOException {
        for (String bundle : BUNDLES) {
            Properties english = load(bundle + ".properties");
            Properties vietnamese = load(bundle + "_vi.properties");

            assertThat(new TreeSet<>(vietnamese.stringPropertyNames()))
                    .as("every key of %s is translated", bundle)
                    .isEqualTo(new TreeSet<>(english.stringPropertyNames()));
        }
    }

    /**
     * A translation fills in the same values as the English it stands for. How it fills
     * them may differ: English picks between "1 change" and "2 changes" with a choice
     * format, while Vietnamese has no plural and simply prints the number.
     */
    @Test
    void everyTranslationFillsInTheSameArguments() throws IOException {
        for (String bundle : BUNDLES) {
            Properties english = load(bundle + ".properties");
            Properties vietnamese = load(bundle + "_vi.properties");
            for (String key : english.stringPropertyNames()) {
                if (vietnamese.containsKey(key)) {
                    assertThat(arguments(vietnamese.getProperty(key)))
                            .as("arguments of %s in %s", key, bundle)
                            .isEqualTo(arguments(english.getProperty(key)));
                }
            }
        }
    }

    /**
     * A value that cannot be converted is reported in the reader's language rather than
     * in Spring's own English, through the shared {@code typeMismatch} keys.
     */
    @Test
    void aValueThatCannotBeConvertedIsReportedInTheReadersLanguage() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/ui");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);

        Map<String, String> english = Map.of(
                "number", "Enter a whole number.",
                "choice", "This choice is not valid. Choose it again.",
                "flag", "Choose yes or no.",
                "kind", "This value is not valid."
        );
        Map<String, String> vietnamese = Map.of(
                "number", "Vui lòng nhập một số nguyên.",
                "choice", "Lựa chọn này không hợp lệ. Vui lòng chọn lại.",
                "flag", "Vui lòng chọn có hoặc không.",
                "kind", "Giá trị này không hợp lệ."
        );
        DataBinder binder = new DataBinder(new Typed(), "typed");
        binder.bind(new MutablePropertyValues(Map.of(
                "number", "twelve", "choice", "not-an-id", "flag", "maybe", "kind", "NOPE"
        )));

        for (String field : english.keySet()) {
            FieldError error = binder.getBindingResult().getFieldError(field);
            assertThat(error).as(field).isNotNull();
            assertThat(messageSource.getMessage(error, Locale.ENGLISH)).as(field).isEqualTo(english.get(field));
            assertThat(messageSource.getMessage(error, Locale.forLanguageTag("vi"))).as(field).isEqualTo(vietnamese.get(field));
        }
    }

    @Test
    void everyKeyATemplateAsksForIsTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path template : templates()) {
            Matcher matcher = MESSAGE_KEY.matcher(Files.readString(template));
            while (matcher.find()) {
                String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                // A key built from a variable, such as #{${status.labelKey}}, is checked by
                // the enum test rather than here.
                if (!key.isBlank()) {
                    keys.add(key);
                }
            }
        }

        assertThat(keys).isNotEmpty();
        assertThat(load("messages/ui.properties").stringPropertyNames()).containsAll(keys);
        assertThat(load("messages/ui_vi.properties").stringPropertyNames()).containsAll(keys);
    }

    @Test
    void everyKeyAConstraintAsksForIsTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = CONSTRAINT_KEY.matcher(Files.readString(source));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }

        assertThat(keys).isNotEmpty();
        assertThat(load("messages/ui.properties").stringPropertyNames()).containsAll(keys);
        assertThat(load("messages/ui_vi.properties").stringPropertyNames()).containsAll(keys);
    }

    @Test
    void everyKeyTheCodeAsksForIsTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = CODE_KEY.matcher(Files.readString(source));
                while (matcher.find()) {
                    keys.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
                }
            }
        }

        assertThat(keys).isNotEmpty();
        assertThat(load("messages/ui.properties").stringPropertyNames()).containsAll(keys);
        assertThat(load("messages/ui_vi.properties").stringPropertyNames()).containsAll(keys);
    }

    @Test
    void everyPatternWithPlaceholdersCanBeFilledIn() throws IOException {
        for (String bundle : List.of("messages/ui.properties", "messages/ui_vi.properties")) {
            Properties properties = load(bundle);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (!value.contains("{0}") && !value.contains("{0,")) {
                    continue;
                }
                Object[] arguments = {1, 1, 1, 1};
                assertThat(new MessageFormat(value, Locale.ENGLISH).format(arguments))
                        .as("%s in %s", key, bundle)
                        .doesNotContain("{0");
            }
        }
    }

    private static Set<String> arguments(String pattern) {
        Set<String> indexes = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(pattern);
        while (matcher.find()) {
            indexes.add(matcher.group(1));
        }
        return indexes;
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/templates"))) {
            return paths.filter(path -> path.toString().endsWith(".html")).toList();
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = MessageBundleTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    /** One field of each kind a form binds that a browser can send unconvertible text for. */
    public static class Typed {

        private Integer number;
        private UUID choice;
        private Boolean flag;
        private Kind kind;

        public Integer getNumber() {
            return number;
        }

        public void setNumber(Integer number) {
            this.number = number;
        }

        public UUID getChoice() {
            return choice;
        }

        public void setChoice(UUID choice) {
            this.choice = choice;
        }

        public Boolean getFlag() {
            return flag;
        }

        public void setFlag(Boolean flag) {
            this.flag = flag;
        }

        public Kind getKind() {
            return kind;
        }

        public void setKind(Kind kind) {
            this.kind = kind;
        }
    }

    public enum Kind {
        ONE
    }
}
