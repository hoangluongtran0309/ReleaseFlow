package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** An upgrade guide that is not an absolute HTTP or HTTPS address a note can link. */
public class InvalidUpgradeGuideUrlException extends LocalizedException {

    InvalidUpgradeGuideUrlException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
