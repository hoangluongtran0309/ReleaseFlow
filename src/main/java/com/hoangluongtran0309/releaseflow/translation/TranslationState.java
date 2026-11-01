package com.hoangluongtran0309.releaseflow.translation;

import java.util.Map;

/**
 * Where one change's content stands in one language. Only a ready state carries texts;
 * they are keyed as they were given, and texts that were blank come back unchanged.
 * {@code glossary} says what the translation did with the Organization's glossary.
 */
public record TranslationState(Status status, Map<String, String> texts, GlossaryUse glossary) {

    public enum Status {
        READY,
        PENDING,
        FAILED
    }

    public TranslationState {
        texts = texts == null ? Map.of() : Map.copyOf(texts);
        glossary = glossary == null ? GlossaryUse.NONE : glossary;
    }

    static TranslationState ready(Map<String, String> texts) {
        return new TranslationState(Status.READY, texts, GlossaryUse.NONE);
    }

    static TranslationState ready(Map<String, String> texts, GlossaryUse glossary) {
        return new TranslationState(Status.READY, texts, glossary);
    }

    static TranslationState pending(GlossaryUse glossary) {
        return new TranslationState(Status.PENDING, Map.of(), glossary);
    }

    static TranslationState failed(GlossaryUse glossary) {
        return new TranslationState(Status.FAILED, Map.of(), glossary);
    }

    public boolean ready() {
        return status == Status.READY;
    }
}
