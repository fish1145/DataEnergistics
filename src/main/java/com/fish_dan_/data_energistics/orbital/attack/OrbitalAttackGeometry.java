package com.fish_dan_.data_energistics.orbital.attack;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/**
 * Immutable geometry captured with an orbital attack.
 *
 * <p>
 * Keeping geometry separate from the mutable work cursor makes restart recovery deterministic and prevents a
 * configuration reload from silently changing a reserved effect.
 * </p>
 */
public sealed interface OrbitalAttackGeometry
                                              permits OrbitalAttackGeometry.Kinetic, OrbitalAttackGeometry.DirectedEnergy {

    OrbitalAttackMode mode();

    /** Geometry for the fixed-radius instantaneous kinetic strike. */
    record Kinetic() implements OrbitalAttackGeometry {

        @Override
        public OrbitalAttackMode mode() {
            return OrbitalAttackMode.KINETIC;
        }
    }

    /** Geometry for one spiral directed-energy scan. */
    record DirectedEnergy(int radius, OrbitalDirectedEnergyDepth depth, long entityDamage) implements OrbitalAttackGeometry {

        public DirectedEnergy {
            Objects.requireNonNull(depth, "depth");
            if (radius < OrbitalDirectedEnergyStrike.MIN_RADIUS || radius > OrbitalDirectedEnergyStrike.MAX_RADIUS || radius % OrbitalDirectedEnergyStrike.RADIUS_STEP != 0) {
                throw new IllegalArgumentException("Directed-energy radius must be a 16-grid value from 16 to 256");
            }
            if (entityDamage <= 0L || entityDamage > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Directed-energy entity damage is outside the supported range");
            }
        }

        @Override
        public OrbitalAttackMode mode() {
            return OrbitalAttackMode.DIRECTED_ENERGY;
        }

        public int bottomY(ServerLevel level, int targetY) {
            return this.depth.bottomY(level, targetY);
        }
    }
}
