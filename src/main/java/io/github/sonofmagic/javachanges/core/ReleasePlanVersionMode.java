package io.github.sonofmagic.javachanges.core;

public enum ReleasePlanVersionMode {
    SNAPSHOT("snapshot"),
    RELEASE("release");

    public final String id;

    ReleasePlanVersionMode(String id) {
        this.id = id;
    }

    public static ReleasePlanVersionMode parse(String value, ReleasePlanVersionMode defaultValue) {
        String normalized = ReleaseTextUtils.trimToNull(value);
        if (normalized == null) {
            return defaultValue;
        }
        for (ReleasePlanVersionMode mode : values()) {
            if (mode.id.equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(ReleaseMessages.unsupportedReleasePlanVersionMode(value));
    }
}
