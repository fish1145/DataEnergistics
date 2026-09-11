package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamPath;

import net.minecraft.core.BlockPos;
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

    /** Persisted crater profile keeps already-confirmed terrain work stable across the bowl-shape upgrade. */
    enum KineticCraterProfile {
        /** Original constant-radius layers, retained for attacks saved before a profile was recorded. */
        CYLINDER,
        /** Successively narrower layers form a bowl while retaining the independent central penetration column. */
        BOWL
    }

    /** Terrain and entity-erasure volumes frozen when an instantaneous kinetic strike is confirmed. */
    record Kinetic(
                   int columnRadius,
                   int columnDepth,
                   int craterRadius,
                   int craterDepth,
                   int shockwaveRadius,
                   KineticCraterProfile craterProfile,
                   int craterTopY)
            implements OrbitalAttackGeometry {

        public static final int DEFAULT_COLUMN_RADIUS = 8;
        public static final int DEFAULT_COLUMN_DEPTH = 192;
        /** The default impact footprint matches the 64-block shockwave envelope. */
        public static final int DEFAULT_CRATER_RADIUS = 64;
        public static final int DEFAULT_CRATER_DEPTH = 32;
        public static final int DEFAULT_SHOCKWAVE_RADIUS = 64;
        public static final int MAX_TERRAIN_RADIUS = 256;
        public static final int MAX_TERRAIN_DEPTH = 8_192;
        public static final int MAX_SHOCKWAVE_RADIUS = 256;
        public static final int UNCAPTURED_CRATER_TOP = Integer.MIN_VALUE;

        public Kinetic(int columnRadius, int columnDepth, int craterRadius, int craterDepth, int shockwaveRadius,
                       KineticCraterProfile craterProfile) {
            this(columnRadius, columnDepth, craterRadius, craterDepth, shockwaveRadius, craterProfile, UNCAPTURED_CRATER_TOP);
        }

        public Kinetic {
            if (columnRadius < 1 || columnRadius > MAX_TERRAIN_RADIUS || craterRadius < 1 || craterRadius > MAX_TERRAIN_RADIUS) {
                throw new IllegalArgumentException("Kinetic terrain radius is outside the supported range");
            }
            if (columnDepth < 1 || columnDepth > MAX_TERRAIN_DEPTH || craterDepth < 1 || craterDepth > MAX_TERRAIN_DEPTH) {
                throw new IllegalArgumentException("Kinetic terrain depth is outside the supported range");
            }
            if (shockwaveRadius < 1 || shockwaveRadius > MAX_SHOCKWAVE_RADIUS) {
                throw new IllegalArgumentException("Kinetic shockwave radius is outside the supported range");
            }
        }

        /** Captures the mutable server configuration for one new preview or confirmed attack. */
        public static Kinetic fromSettings(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
            return new Kinetic(
                    settings.kineticColumnRadius,
                    settings.kineticColumnDepth,
                    settings.kineticCraterRadius,
                    settings.kineticCraterDepth,
                    settings.kineticShockwaveRadius,
                    KineticCraterProfile.BOWL, UNCAPTURED_CRATER_TOP);
        }

        /** Normalizes untrusted persisted numbers once at the SavedData boundary. */
        public static Kinetic fromPersisted(
                                            int columnRadius,
                                            int columnDepth,
                                            int craterRadius,
                                            int craterDepth,
                                            int shockwaveRadius,
                                            KineticCraterProfile craterProfile,
                                            int craterTopY) {
            return new Kinetic(
                    Math.clamp(columnRadius, 1, MAX_TERRAIN_RADIUS),
                    Math.clamp(columnDepth, 1, MAX_TERRAIN_DEPTH),
                    Math.clamp(craterRadius, 1, MAX_TERRAIN_RADIUS),
                    Math.clamp(craterDepth, 1, MAX_TERRAIN_DEPTH),
                    Math.clamp(shockwaveRadius, 1, MAX_SHOCKWAVE_RADIUS),
                    craterProfile, craterTopY);
        }

        public static Kinetic fromPersisted(int columnRadius, int columnDepth, int craterRadius, int craterDepth,
                                            int shockwaveRadius, KineticCraterProfile craterProfile) {
            return fromPersisted(columnRadius, columnDepth, craterRadius, craterDepth, shockwaveRadius,
                    craterProfile, UNCAPTURED_CRATER_TOP);
        }

        public Kinetic withCraterTopY(int topY) {
            return new Kinetic(columnRadius, columnDepth, craterRadius, craterDepth, shockwaveRadius, craterProfile, topY);
        }

        /**
         * Tests one candidate from the captured cylindrical crater stream without accessing the world. The caller
         * must supply a position inside that stream. Keeping its indexing unchanged preserves budget accounting and
         * persisted cursors; the bowl only filters the blocks retained along its sloping sides.
         */
        public boolean containsCraterPosition(BlockPos target, BlockPos position) {
            return containsCraterPosition(target, position, target.getY() - 1);
        }

        public boolean containsCraterPosition(BlockPos target, BlockPos position, int craterTopY) {
            if (this.craterProfile == KineticCraterProfile.CYLINDER) {
                return true;
            }
            long offsetX = position.getX() - (long) target.getX();
            long offsetZ = position.getZ() - (long) target.getZ();
            int layer = Math.max(0, Math.min(this.craterDepth - 1, target.getY() - 1 - position.getY()));
            long radiusSquared = (long) this.craterRadius * this.craterRadius;
            return (offsetX * offsetX + offsetZ * offsetZ) * this.craterDepth <= radiusSquared * (this.craterDepth - layer);
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

    /** Geometry and depth value frozen for one spiral directed-energy scan. */
    record DirectedEnergy(
                          int radius,
                          OrbitalDirectedEnergyDepth depth,
                          int depthBlocks,
                          OrbitalBeamPath path)
            implements OrbitalAttackGeometry {

        public static final int DEFAULT_MIN_RADIUS = 16;
        public static final int DEFAULT_MAX_RADIUS = 256;
        public static final int DEFAULT_RADIUS_STEP = 16;
        public static final int DEFAULT_SHALLOW_DEPTH = 32;
        public static final int DEFAULT_MEDIUM_DEPTH = 128;
        public static final int DEFAULT_DEEP_DEPTH = 512;
        public static final int MAX_SUPPORTED_RADIUS = 256;
        public static final int MAX_SUPPORTED_DEPTH = 8_192;

        /** New scans use rays aimed from the fixed muzzle. */
        public DirectedEnergy(int radius, OrbitalDirectedEnergyDepth depth, int depthBlocks) {
            this(radius, depth, depthBlocks, OrbitalBeamPath.AIMED_RAYS);
        }

        public DirectedEnergy {
            OrbitalDirectedEnergyStrike.validateSupportedRadius(radius);
            if (depth == OrbitalDirectedEnergyDepth.THROUGH) {
                if (depthBlocks != 0) {
                    throw new IllegalArgumentException("Through-world directed energy cannot have a finite depth");
                }
            } else if (depthBlocks < 1 || depthBlocks > MAX_SUPPORTED_DEPTH) {
                throw new IllegalArgumentException("Directed-energy depth is outside the supported range");
            }
        }

        /** Captures the selected server-configured depth profile for a newly confirmed scan. */
        public static DirectedEnergy fromSettings(
                                                  int radius,
                                                  OrbitalDirectedEnergyDepth depth,
                                                  DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
            return new DirectedEnergy(
                    radius,
                    depth,
                    depth.configuredDepth(settings));
        }

        /** Normalizes numeric NBT values without consulting mutable live configuration. */
        public static DirectedEnergy fromPersisted(int radius, OrbitalDirectedEnergyDepth depth, int depthBlocks) {
            return fromPersisted(radius, depth, depthBlocks, OrbitalBeamPath.VERTICAL_COLUMNS);
        }

        /** Restores the explicit traversal format alongside the captured numeric geometry. */
        public static DirectedEnergy fromPersisted(
                                                   int radius,
                                                   OrbitalDirectedEnergyDepth depth,
                                                   int depthBlocks,
                                                   OrbitalBeamPath path) {
            int normalizedDepth = depth == OrbitalDirectedEnergyDepth.THROUGH ? 0 : Math.clamp(depthBlocks, 1, MAX_SUPPORTED_DEPTH);
            return new DirectedEnergy(
                    Math.clamp(radius, 1, MAX_SUPPORTED_RADIUS),
                    depth,
                    normalizedDepth,
                    path);
        }

        @Override
        public OrbitalAttackMode mode() {
            return OrbitalAttackMode.DIRECTED_ENERGY;
        }

        public int bottomY(ServerLevel level, int targetY) {
            return this.depth == OrbitalDirectedEnergyDepth.THROUGH ? level.getMinBuildHeight() : (int) Math.max(level.getMinBuildHeight(), (long) targetY - this.depthBlocks);
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
