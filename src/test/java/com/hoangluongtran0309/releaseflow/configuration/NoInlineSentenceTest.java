package com.hoangluongtran0309.releaseflow.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the pages against a sentence written straight into a controller. Whatever a page
 * shows as an error comes from the bundle, so it is written in the reader's language; an
 * English literal handed to the model would reach a Vietnamese page as it is.
 */
class NoInlineSentenceTest {

    private static final Pattern INLINE_SENTENCE = Pattern.compile(
            "addAttribute\\(\"\\w*(?:Error|Notice|Message)\",\\s*\"|renderWithError\\([^;]*?\"[A-Z][^\"]*\""
    );

    @Test
    void noControllerHandsAPageASentenceOfItsOwn() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                Matcher matcher = INLINE_SENTENCE.matcher(text);
                while (matcher.find()) {
                    int line = (int) text.substring(0, matcher.start()).lines().count();
                    found.add(source + ":" + line + " " + matcher.group());
                }
            }
        }

        assertThat(found).as("sentences a page would show untranslated").isEmpty();
    }
}
