package org.schabi.newpipe.extractor.utils;

import javax.annotation.Nonnull;
//import androidx.annotation.NonNull;

public enum SubtitleState {

    ORIGINAL("original"),
    DEDUPLICATED("deduplicated");

    private final String id;

    SubtitleState(@Nonnull final String id) {
        this.id = id;
    }

    @Nonnull
    public String getId() {
        return id;
    }
}
