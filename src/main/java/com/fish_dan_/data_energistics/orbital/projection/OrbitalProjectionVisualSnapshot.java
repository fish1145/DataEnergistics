package com.fish_dan_.data_energistics.orbital.projection;

import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Public, render-only state for one primary orbital projection. It deliberately contains no owner, reserve or
 * authorization data; the weapon UUID is only a stable visual identity for client cache replacement.
 */
public record OrbitalProjectionVisualSnapshot(
                                               UUID weaponId,
                                               ResourceLocation dimensionId,
                                               BlockPos anchor,
                                               int projectionY,
                                               OrbitalWeaponLifecycleState lifecycleState,
                                               int redeploymentTicksRemaining,
                                               long animationTime,
                                               long randomSeed) {

    public OrbitalProjectionVisualSnapshot {
        anchor = anchor.immutable();
        if (redeploymentTicksRemaining < 0
                || animationTime < 0L
                || randomSeed < 0L) {
            throw new IllegalArgumentException("Orbital projection visual state is outside its bounded range");
        }
        if (lifecycleState == OrbitalWeaponLifecycleState.REDEPLOYING && redeploymentTicksRemaining <= 0) {
            throw new IllegalArgumentException("A redeploying projection must carry remaining ticks");
        }
        if (lifecycleState != OrbitalWeaponLifecycleState.REDEPLOYING && redeploymentTicksRemaining != 0) {
            throw new IllegalArgumentException("Only a redeploying projection may carry remaining ticks");
        }
    }
}
