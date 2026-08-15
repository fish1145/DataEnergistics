package com.fish_dan_.data_energistics.blockentity.machine;

import lombok.Getter;

@Getter
public enum DataExtractorDropRoutingMode {

    OFF("off"),
    CONTAINER("container"),
    AE("ae");

    private static final DataExtractorDropRoutingMode[] VALUES = values();

    private final String serializedName;

    DataExtractorDropRoutingMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public DataExtractorDropRoutingMode next() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }

    public static DataExtractorDropRoutingMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : OFF;
    }
}
