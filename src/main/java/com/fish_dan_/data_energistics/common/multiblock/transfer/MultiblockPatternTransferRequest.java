package com.fish_dan_.data_energistics.common.multiblock.transfer;

import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;

import net.minecraft.resources.ResourceLocation;

/**
 * Untrusted XEI request identifying one current multiblock projection and the exact pattern menu that should receive
 * it.
 *
 * @param containerId           open pattern-terminal menu id
 * @param registeredRecipeId    stable controller-level XEI recipe id
 * @param projectionFingerprint complete revision-bound projection identity
 */
public record MultiblockPatternTransferRequest(int containerId,
                                               ResourceLocation registeredRecipeId,
                                               ProjectionFingerprint projectionFingerprint) {

    /**
     * Largest menu id accepted by the bounded network protocol.
     */
    public static final int MAX_CONTAINER_ID = 1_000_000_000;

    /**
     * Rejects an invalid envelope before catalog reconstruction begins.
     */
    public MultiblockPatternTransferRequest {
        if (containerId < 0 || containerId > MAX_CONTAINER_ID) {
            throw new IllegalArgumentException("Invalid multiblock pattern transfer container id: " + containerId);
        }
        if (registeredRecipeId == null || projectionFingerprint == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer identities cannot be null");
        }
    }
}
