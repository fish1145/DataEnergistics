package com.fish_dan_.data_energistics.network;

/**
 * Identifies which client or optional integration performed a confirmed pattern upload.
 */
public enum PatternUploadSource {

    DATA_ENERGISTICS(0),
    EAEP(1);

    private final int wireId;

    PatternUploadSource(int wireId) {
        this.wireId = wireId;
    }

    int wireId() {
        return this.wireId;
    }

    static PatternUploadSource fromWireId(int wireId) {
        for (PatternUploadSource source : values()) {
            if (source.wireId == wireId) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown pattern upload source id: " + wireId);
    }
}
