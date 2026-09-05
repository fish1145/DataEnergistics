package com.fish_dan_.data_energistics.orbital.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Public, render-only attack state. It intentionally contains no owner, reserve or authorization information. */
public record OrbitalAttackVisualSnapshot(
                                          UUID attackId,
                                          OrbitalAttackMode mode,
                                          ResourceLocation dimensionId,
                                          BlockPos target,
                                          BlockPos effectPosition,
                                          int effectRadius,
                                          OrbitalAttackPhase phase,
                                          long phaseAge,
                                          long randomSeed,
                                          long workCursor,
                                          long totalWork) {

    public OrbitalAttackVisualSnapshot {
        target = target.immutable();
        effectPosition = effectPosition.immutable();
        if (effectRadius < 0 || phaseAge < 0L || randomSeed < 0L || workCursor < 0L || totalWork < 0L || workCursor > totalWork) {
            throw new IllegalArgumentException("Orbital visual snapshot progress is outside its bounded range");
        }
    }
}
