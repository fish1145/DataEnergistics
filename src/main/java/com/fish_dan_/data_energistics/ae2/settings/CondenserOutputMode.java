package com.fish_dan_.data_energistics.ae2.settings;

import appeng.api.config.CondenserOutput;

public enum CondenserOutputMode {

    TRASH,
    MATTER_BALLS,
    SINGULARITY,
    DATA_CAPTURE_BALL;

    public static CondenserOutputMode fromOrdinal(int ordinal) {
        var values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return TRASH;
        }
        return values[ordinal];
    }

    public static CondenserOutputMode fromState(CondenserOutput output, boolean dataCaptureBallMode) {
        if (dataCaptureBallMode) {
            return DATA_CAPTURE_BALL;
        }

        return switch (output) {
            case MATTER_BALLS -> MATTER_BALLS;
            case SINGULARITY -> SINGULARITY;
            default -> TRASH;
        };
    }

    public CondenserOutput toVanillaOutput() {
        return switch (this) {
            case MATTER_BALLS -> CondenserOutput.MATTER_BALLS;
            case SINGULARITY, DATA_CAPTURE_BALL -> CondenserOutput.SINGULARITY;
            default -> CondenserOutput.TRASH;
        };
    }

    public boolean isDataCaptureBallMode() {
        return this == DATA_CAPTURE_BALL;
    }
}
