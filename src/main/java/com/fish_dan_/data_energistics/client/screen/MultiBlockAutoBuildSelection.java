package com.fish_dan_.data_energistics.client.screen;

/**
 * Immutable user choice emitted by the shared multiblock auto-build overlay.
 *
 * <p>
 * The overlay only owns presentation state. Hosts interpret these opaque numeric identifiers when they create their
 * own network request or local build action.
 * </p>
 *
 * @param structureId    selected host-defined structure identifier
 * @param buildRequested whether confirmation should execute a build
 * @param repeatCount    selected repetition count for the structure
 * @param tierValue      selected host-defined tier value
 */
public record MultiBlockAutoBuildSelection(int structureId,
                                           boolean buildRequested,
                                           int repeatCount,
                                           int tierValue) {

    /** Validates the positive values that every generic overlay selection must carry. */
    public MultiBlockAutoBuildSelection {
        if (structureId < 0) {
            throw new IllegalArgumentException("Multiblock auto-build structure id cannot be negative: " + structureId);
        }
        if (repeatCount < 1) {
            throw new IllegalArgumentException("Multiblock auto-build repeat count must be positive: " + repeatCount);
        }
        if (tierValue < 1) {
            throw new IllegalArgumentException("Multiblock auto-build tier value must be positive: " + tierValue);
        }
    }
}
