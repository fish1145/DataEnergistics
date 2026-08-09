package com.fish_dan_.data_energistics.common.trinity.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.registry.DEVerticalMultiBlocks;

/**
 * Revision-bound automatic-build choice emitted by the Trinity UI before transport validation.
 *
 * @param projectionFingerprint complete recipe-affecting structure selection
 * @param buildRequested        whether confirmation should execute the selected structure build
 */
public record TrinityAutoBuildSubmission(ProjectionFingerprint projectionFingerprint,
                                         boolean buildRequested) {

    /** Rejects submissions for another controller before they reach a network codec. */
    public TrinityAutoBuildSubmission {
        if (projectionFingerprint == null) {
            throw new IllegalArgumentException("Trinity auto-build submission requires a projection fingerprint");
        }
        if (!DEVerticalMultiBlocks.trinityDataCoreId().equals(projectionFingerprint.controllerId())) {
            throw new IllegalArgumentException("Trinity auto-build submission belongs to another controller: " +
                    projectionFingerprint.controllerId());
        }
    }
}
