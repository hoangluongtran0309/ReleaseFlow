package com.hoangluongtran0309.releaseflow.audience;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One text value of a group a template may print. It prints as its text, and a section over
 * it renders only when the text is not empty, with every value of its group still in reach
 * and {@code {{.}}} as its own text. A plain string would not do: in standards mode a name
 * inside a section is looked up only in the section's own value, so
 * {@code {{#status}} ({{status}}){{/status}}} could not print the status, and
 * {@code {{#compareUrl}}} could not print {@code {{version}}}.
 */
final class SectionValue implements Mustache.InvertibleLambda {

    private final String text;
    private final Map<String, Object> group;

    SectionValue(String text, Map<String, Object> group) {
        this.text = text == null ? "" : text;
        this.group = group;
    }

    @Override
    public void execute(Template.Fragment fragment, Writer out) throws IOException {
        if (!text.isEmpty()) {
            fragment.execute(new Scope(group, text), out);
        }
    }

    @Override
    public void executeInverse(Template.Fragment fragment, Writer out) throws IOException {
        if (text.isEmpty()) {
            fragment.execute(new Scope(group, text), out);
        }
    }

    @Override
    public String toString() {
        return text;
    }

    // The group's values inside a section, which print as the section's own value.
    private static final class Scope extends LinkedHashMap<String, Object> {

        private final String text;

        Scope(Map<String, Object> values, String text) {
            super(values);
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
