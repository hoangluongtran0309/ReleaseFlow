package com.hoangluongtran0309.releaseflow.translation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationWorkerTest {

    @Test
    void aRequestListsItsTextsInTheOrderOfTheirKeysWhateverOrderTheInputHas() {
        Map<String, String> written = new LinkedHashMap<>();
        written.put("whatChanged", "Tables export as CSV.");
        written.put("whyChanged", "Users asked.");
        written.put("technicalDetail", "Streams rows.");
        written.put("narrative.operator", "Watch the export queue.");
        written.put("narrative.contributor", "Use ExportJob.");
        written.put("narrative.end_user", "Download any table.");
        Map<String, String> reversed = new LinkedHashMap<>();
        written.keySet().stream().toList().reversed().forEach(key -> reversed.put(key, written.get(key)));

        // Map.copyOf, as a job hands its input out, iterates in an order salted per JVM.
        for (Map<String, String> input : new Map[] {written, reversed, Map.copyOf(written), new HashMap<>(written)}) {
            assertThat(TranslationWorker.missing(input, Map.of())).containsExactly(
                    "Use ExportJob.", "Download any table.", "Watch the export queue.",
                    "Streams rows.", "Tables export as CSV.", "Users asked.");
        }
    }

    @Test
    void aRepeatedTextIsSentOnceAtItsFirstKeyAndACachedOneNotAtAll() {
        Map<String, String> input = Map.of(
                "whatChanged", "Exports run faster.",
                "headline.operator", "Exports run faster.",
                "whyChanged", "Users asked.",
                "technicalDetail", "Streams rows.");

        assertThat(TranslationWorker.missing(input, Map.of("Users asked.", "Người dùng yêu cầu.")))
                .containsExactly("Exports run faster.", "Streams rows.");
    }
}
