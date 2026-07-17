package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

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
        if (!ModVerticalMultiBlocks.trinityDataCoreId().equals(projectionFingerprint.controllerId())) {
            throw new IllegalArgumentException("Trinity auto-build submission belongs to another controller: " +
                    projectionFingerprint.controllerId());
        }
    }
}
