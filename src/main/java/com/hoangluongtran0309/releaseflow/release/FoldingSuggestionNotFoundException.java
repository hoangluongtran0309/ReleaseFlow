package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class FoldingSuggestionNotFoundException extends ResourceNotFoundException {

    public FoldingSuggestionNotFoundException() {
        super("error.folding_suggestion_not_found");
    }
}
