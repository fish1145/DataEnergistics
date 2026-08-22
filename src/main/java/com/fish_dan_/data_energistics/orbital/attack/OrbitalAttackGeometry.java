package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import net.minecraft.server.level.ServerLevel;

/**
 * Immutable geometry captured with an orbital attack.
 *
 * <p>
 * Keeping geometry separate from the mutable work cursor makes restart recovery deterministic and prevents a
 * configuration reload from silently changing a reserved effect.
 * </p>
 */
public sealed interface OrbitalAttackGeometry
                                              permits OrbitalAttackGeometry.Kinetic,
                                              OrbitalAttackGeometry.DirectedEnergy,
                                              OrbitalAttackGeometry.DigitalAnnihilation {

    OrbitalAttackMode mode();

    /** Geometry and impact values frozen when an instantaneous kinetic strike is confirmed. */
    record Kinetic(
                   int columnRadius,
                   int columnDepth,
                   int craterRadius,
                   int craterDepth,
                   int shockwaveRadius,
                   long entityDamage,
                   double knockbackStrength) implements OrbitalAttackGeometry {

        public static final int DEFAULT_COLUMN_RADIUS = 8;
        public static final int DEFAULT_COLUMN_DEPTH = 192;
        public static final int DEFAULT_CRATER_RADIUS = 24;
        public static final int DEFAULT_CRATER_DEPTH = 16;
        public static final int DEFAULT_SHOCKWAVE_RADIUS = 64;
        public static final long DEFAULT_ENTITY_DAMAGE = 500L;
        public static final double DEFAULT_KNOCKBACK_STRENGTH = 4.0D;
        public static final int MAX_TERRAIN_RADIUS = 256;
        public static final int MAX_TERRAIN_DEPTH = 8_192;
        public static final int MAX_SHOCKWAVE_RADIUS = 256;
        public static final double MAX_KNOCKBACK_STRENGTH = 128.0D;

        public Kinetic {
            if (columnRadius < 1 || columnRadius > MAX_TERRAIN_RADIUS
                    || craterRadius < 1 || craterRadius > MAX_TERRAIN_RADIUS) {
                throw new IllegalArgumentException("Kinetic terrain radius is outside the supported range");
            }
            if (columnDepth < 1 || columnDepth > MAX_TERRAIN_DEPTH
                    || craterDepth < 1 || craterDepth > MAX_TERRAIN_DEPTH) {
                throw new IllegalArgumentException("Kinetic terrain depth is outside the supported range");
            }
            if (shockwaveRadius < 1 || shockwaveRadius > MAX_SHOCKWAVE_RADIUS) {
                throw new IllegalArgumentException("Kinetic shockwave radius is outside the supported range");
            }
            if (entityDamage < 1L || entityDamage > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Kinetic entity damage is outside the supported range");
            }
            if (!Double.isFinite(knockbackStrength)
                    || knockbackStrength < 0.0D
                    || knockbackStrength > MAX_KNOCKBACK_STRENGTH) {
                throw new IllegalArgumentException("Kinetic knockback strength is outside the supported range");
            }
        }

        /** Captures the mutable server configuration for one new preview or confirmed attack. */
        public static Kinetic fromSettings(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
            return new Kinetic(
                    settings.kineticColumnRadius(),
                    settings.kineticColumnDepth(),
                    settings.kineticCraterRadius(),
                    settings.kineticCraterDepth(),
                    settings.kineticShockwaveRadius(),
                    settings.kineticEntityDamage(),
                    settings.kineticKnockbackStrength());
        }

        /** Restores the exact geometry used by saves written before kinetic settings were persisted. */
        public static Kinetic legacyDefaults() {
            return new Kinetic(
                    DEFAULT_COLUMN_RADIUS,
                    DEFAULT_COLUMN_DEPTH,
                    DEFAULT_CRATER_RADIUS,
                    DEFAULT_CRATER_DEPTH,
                    DEFAULT_SHOCKWAVE_RADIUS,
                    DEFAULT_ENTITY_DAMAGE,
                    DEFAULT_KNOCKBACK_STRENGTH);
        }

        /** Normalizes untrusted persisted numbers once at the SavedData boundary. */
        public static Kinetic fromPersisted(
                                            int columnRadius,
                                            int columnDepth,
                                            int craterRadius,
                                            int craterDepth,
                                            int shockwaveRadius,
                                            long entityDamage,
                                            double knockbackStrength) {
            double normalizedKnockback = Double.isFinite(knockbackStrength)
                    ? Math.clamp(knockbackStrength, 0.0D, MAX_KNOCKBACK_STRENGTH)
                    : DEFAULT_KNOCKBACK_STRENGTH;
            return new Kinetic(
                    Math.clamp(columnRadius, 1, MAX_TERRAIN_RADIUS),
                    Math.clamp(columnDepth, 1, MAX_TERRAIN_DEPTH),
                    Math.clamp(craterRadius, 1, MAX_TERRAIN_RADIUS),
                    Math.clamp(craterDepth, 1, MAX_TERRAIN_DEPTH),
                    Math.clamp(shockwaveRadius, 1, MAX_SHOCKWAVE_RADIUS),
                    Math.clamp(entityDamage, 1L, Integer.MAX_VALUE),
                    normalizedKnockback);
        }

        /** Largest horizontal radius touched by the budgeted terrain worker. */
        public int terrainRadius() {
            return Math.max(this.columnRadius, this.craterRadius);
        }

        /** Largest horizontal radius touched by terrain or entity effects. */
        public int maximumRadius() {
            return Math.max(terrainRadius(), this.shockwaveRadius);
        }

        @Override
        public OrbitalAttackMode mode() {
            return OrbitalAttackMode.KINETIC;
        }
    }

    /** Geometry for one spiral directed-energy scan. */
    record DirectedEnergy(int radius, OrbitalDirectedEnergyDepth depth, long entityDamage) implements OrbitalAttackGeometry {

        public DirectedEnergy {
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

    /** Geometry and frozen work settings for the vertical digital-annihilation payload. */
    record DigitalAnnihilation(int workIntervalTicks, int maxRadius, double centerEntityConsumeRadius)
            implements OrbitalAttackGeometry {

        public DigitalAnnihilation {
            if (workIntervalTicks < 1 || maxRadius < 1 || !Double.isFinite(centerEntityConsumeRadius) || centerEntityConsumeRadius < 0.0D) {
                throw new IllegalArgumentException("Invalid digital annihilation geometry settings");
            }
        }

        @Override
        public OrbitalAttackMode mode() {
            return OrbitalAttackMode.DIGITAL_ANNIHILATION;
        }
    }
}
