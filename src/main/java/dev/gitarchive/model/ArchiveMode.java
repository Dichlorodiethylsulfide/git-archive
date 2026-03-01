package dev.gitarchive.model;

import java.util.Locale;

public enum ArchiveMode {
    MINIMAL,
    MAX;

    public static ArchiveMode fromCliValue(String value) {
        return ArchiveMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String cliValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return cliValue();
    }
}
