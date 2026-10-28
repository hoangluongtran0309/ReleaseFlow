package com.hoangluongtran0309.releaseflow.translation;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/** Terms to keep as written, added together or not at all. Surrounding whitespace is removed. */
public class KeptTermsRequest {

    @NotEmpty(message = "{validation.glossaryTerm.required}")
    @Size(max = GlossaryService.MAX_ENTRIES, message = "{validation.glossaryTerms.tooMany}")
    private List<@GlossaryTerm String> terms = new ArrayList<>();

    public List<String> getTerms() {
        return terms;
    }

    public void setTerms(List<String> terms) {
        this.terms = terms == null ? new ArrayList<>() : new ArrayList<>(terms.stream()
                .map(term -> term == null ? null : term.strip())
                .toList());
    }
}
