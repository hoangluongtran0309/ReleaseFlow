package com.hoangluongtran0309.releaseflow.source;

public enum ChangedFileKind {
    ADDED("ui.changes.fileKind.ADDED"),
    MODIFIED("ui.changes.fileKind.MODIFIED"),
    REMOVED("ui.changes.fileKind.REMOVED"),
    RENAMED("ui.changes.fileKind.RENAMED"),
    OTHER("ui.changes.fileKind.OTHER");

    private final String labelKey;

    ChangedFileKind(String labelKey) {
        this.labelKey = labelKey;
    }

    public String getLabelKey() {
        return labelKey;
    }
}
