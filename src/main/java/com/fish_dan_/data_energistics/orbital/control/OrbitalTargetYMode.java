package com.fish_dan_.data_energistics.orbital.control;

/** Server-resolved target-height strategies accepted by the orbital map preview intent. */
public enum OrbitalTargetYMode {

    ABSOLUTE(0),
    SURFACE_OFFSET(1);

    private final int wireCode;

    OrbitalTargetYMode(int wireCode) {
        this.wireCode = wireCode;
    }

    /** Returns the bounded protocol code without exposing enum ordinal layout to clients. */
    public int wireCode() {
        return this.wireCode;
    }

    /** Decodes a protocol code and rejects unknown future values at the packet boundary. */
    public static OrbitalTargetYMode fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 0 -> ABSOLUTE;
            case 1 -> SURFACE_OFFSET;
            default -> throw new IllegalArgumentException("Unknown orbital target-Y mode: " + wireCode);
        };
    }
}
