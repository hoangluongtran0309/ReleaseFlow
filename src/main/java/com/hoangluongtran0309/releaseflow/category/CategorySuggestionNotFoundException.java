package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class CategorySuggestionNotFoundException extends ResourceNotFoundException {

    public CategorySuggestionNotFoundException() {
        super("error.category_suggestion_not_found");
    }
}
