package com.fish_dan_.data_energistics.blockentity.machine;

import lombok.Getter;

@Getter
public enum DataExtractorAutoExportMode {

    OFF("off"),
    CONTAINER("container"),
    AE("ae");

    private static final DataExtractorAutoExportMode[] VALUES = values();

    private final String serializedName;

    DataExtractorAutoExportMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public DataExtractorAutoExportMode next() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }

    public static DataExtractorAutoExportMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : OFF;
    }
}
