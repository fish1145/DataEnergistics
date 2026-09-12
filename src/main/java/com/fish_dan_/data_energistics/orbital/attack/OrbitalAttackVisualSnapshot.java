package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamSweep;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

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
                                          long totalWork,
                                          @Nullable OrbitalBeamSweep beamSweep) {

    /** Snapshot without a processed beam, used by non-beam modes and static model previews. */
    public OrbitalAttackVisualSnapshot(UUID attackId, OrbitalAttackMode mode, ResourceLocation dimensionId,
                                       BlockPos target, BlockPos effectPosition, int effectRadius, OrbitalAttackPhase phase, long phaseAge,
                                       long randomSeed, long workCursor, long totalWork) {
        this(attackId, mode, dimensionId, target, effectPosition, effectRadius, phase, phaseAge, randomSeed,
                workCursor, totalWork, null);
    }

    public OrbitalAttackVisualSnapshot {
        target = target.immutable();
        effectPosition = effectPosition.immutable();
        if (effectRadius < 0 || phaseAge < 0L || randomSeed < 0L || workCursor < 0L || totalWork < 0L || workCursor > totalWork) {
            throw new IllegalArgumentException("Orbital visual snapshot progress is outside its bounded range");
        }
        if (beamSweep != null && (mode != OrbitalAttackMode.DIRECTED_ENERGY || beamSweep.fromCursor() > workCursor || beamSweep.scan(target, effectRadius).totalWork() != totalWork)) {
            throw new IllegalArgumentException("Orbital beam sweep differs from its work geometry");
        }
    }
}
