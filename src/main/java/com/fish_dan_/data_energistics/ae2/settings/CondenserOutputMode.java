package com.fish_dan_.data_energistics.ae2.settings;

import appeng.api.config.CondenserOutput;

public enum CondenserOutputMode {

    TRASH,
    MATTER_BALLS,
    SINGULARITY,
    RADIX_CONTAINMENT_SPHERE;

    public static CondenserOutputMode fromOrdinal(int ordinal) {
        var values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return TRASH;
        }
        return values[ordinal];
    }

    public static CondenserOutputMode fromState(CondenserOutput output, boolean radixContainmentSphereMode) {
        if (radixContainmentSphereMode) {
            return RADIX_CONTAINMENT_SPHERE;
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
            case SINGULARITY, RADIX_CONTAINMENT_SPHERE -> CondenserOutput.SINGULARITY;
            default -> CondenserOutput.TRASH;
        };
    }

    public boolean isRadixContainmentSphereMode() {
        return this == RADIX_CONTAINMENT_SPHERE;
    }
}
