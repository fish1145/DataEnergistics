package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;

/** Converts an untrusted revision-bound UI submission into the existing atomic builder request. */
public interface TrinityAutoBuildSubmissionResolver {

    /**
     * Reconstructs every recipe-affecting field against the current server specification.
     *
     * @param spec       current Trinity preview specification from the atomic catalog snapshot
     * @param submission untrusted hosted-window submission
     * @return validated request understood by the existing builder
     */
    TrinityAutoBuildRequest resolve(MultiblockPreviewSpec spec, TrinityAutoBuildSubmission submission);
}
