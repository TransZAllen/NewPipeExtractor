package org.schabi.newpipe.extractor.utils;

import javax.annotation.Nonnull;
//import androidx.annotation.NonNull;

public enum SubtitleOrigin {

    UPLOADED("uploaded"),
    AUTO_GENERATED("auto_generated"),
    AUTO_TRANSLATED("auto_translated");

    private final String id;

    SubtitleOrigin(@Nonnull final String id) {
        this.id = id;
    }

    @Nonnull
    public String getId() {
        return id;
    }
}
