package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.configuration.ResourceNotFoundException;

public class CategoryNotFoundException extends ResourceNotFoundException {

    public CategoryNotFoundException() {
        super("error.category_not_found");
    }
}
