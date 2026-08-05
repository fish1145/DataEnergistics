package com.fish_dan_.data_energistics.common.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned, stable identity of one physical or virtual pattern provider.
 *
 * <p>
 * The identity deliberately excludes terminal sort order, display-only tooltip data, inventory contents and menu-local
 * numeric IDs. Those values can change without the underlying provider changing. The schema version and kind-specific
 * fields are included in {@link #digest()}, allowing later schemas to coexist without reusing an older digest.
 * </p>
 */
public sealed interface ProviderIdentity
        permits ProviderIdentity.Block, ProviderIdentity.Part, ProviderIdentity.Trinity, ProviderIdentity.Matrix,
        ProviderIdentity.Virtual {

    /**
     * Current canonical field schema used by every identity declared in this type.
     */
    int SCHEMA_VERSION = 1;

    /**
     * @return canonical identity schema version included in the digest input
     */
    default int version() {
        return SCHEMA_VERSION;
    }

    /**
     * @return provider category that determines the canonical field layout
     */
    Kind kind();

    /**
     * @return stable SHA-256 key in {@code sha256:<64 lowercase hex>} form
     */
    default String digest() {
        return ProviderIdentityDigest.digest(this);
    }

    /**
     * Provider categories with explicit, permanent codes for canonical binary encoding.
     */
    enum Kind {
        /**
         * In-world block entity provider.
         */
        BLOCK(1),
        /**
         * Multipart provider mounted on a cable-bus host.
         */
        PART(2),
        /**
         * Virtual partition backed by a persistent Trinity pattern core.
         */
        TRINITY(3),
        /**
         * ExtendedAE assembler matrix target, distinguished between ordinary and Plus variants.
         */
        MATRIX(5),
        /**
         * Provider without a discoverable physical location or dedicated stable key.
         */
        VIRTUAL(4);

        /**
         * Permanent binary tag; enum ordinals are intentionally not persisted.
         */
        private final int stableCode;

        Kind(int stableCode) {
            this.stableCode = stableCode;
        }

        /**
         * @return permanent binary tag for this provider category
         */
        int stableCode() {
            return this.stableCode;
        }
    }

    /**
     * Stable cable-bus mount location, including the center slot represented by a {@code null} AE2 side.
     */
    enum Mount {
        /**
         * Center cable slot.
         */
        CENTER(0, null),
        /**
         * Bottom face.
         */
        DOWN(1, Direction.DOWN),
        /**
         * Top face.
         */
        UP(2, Direction.UP),
        /**
         * North face.
         */
        NORTH(3, Direction.NORTH),
        /**
         * South face.
         */
        SOUTH(4, Direction.SOUTH),
        /**
         * West face.
         */
        WEST(5, Direction.WEST),
        /**
         * East face.
         */
        EAST(6, Direction.EAST);

        /**
         * Permanent binary tag for the mount location.
         */
        private final int stableCode;
        /**
         * AE2 side represented by this value; {@code null} denotes the center slot.
         */
        @Nullable
        private final Direction direction;

        Mount(int stableCode, @Nullable Direction direction) {
            this.stableCode = stableCode;
            this.direction = direction;
        }

        /**
         * Converts AE2's nullable cable-bus side into an explicit identity value.
         *
         * @param direction mounted side, or {@code null} for the center slot
         * @return explicit stable mount value
         */
        public static Mount fromDirection(@Nullable Direction direction) {
            if (direction == null) {
                return CENTER;
            }
            return switch (direction) {
                case DOWN -> DOWN;
                case UP -> UP;
                case NORTH -> NORTH;
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                case EAST -> EAST;
            };
        }

        /**
         * @return AE2 side, or {@code null} for the center slot
         */
        @Nullable
        public Direction direction() {
            return this.direction;
        }

        /**
         * @return permanent binary tag for this mount location
         */
        int stableCode() {
            return this.stableCode;
        }
    }

    /**
     * Identity of a block-entity provider.
     *
     * @param dimensionId       dimension containing the provider
     * @param blockPos          immutable world position
     * @param blockEntityTypeId registered block-entity type
     */
    record Block(ResourceLocation dimensionId,
                 BlockPos blockPos,
                 ResourceLocation blockEntityTypeId) implements ProviderIdentity {

        /**
         * Validates and defensively freezes the world-location fields.
         */
        public Block {
            dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
            blockPos = Objects.requireNonNull(blockPos, "blockPos").immutable();
            blockEntityTypeId = Objects.requireNonNull(blockEntityTypeId, "blockEntityTypeId");
        }

        @Override
        public Kind kind() {
            return Kind.BLOCK;
        }
    }

    /**
     * Identity of a multipart provider.
     *
     * @param dimensionId dimension containing the cable-bus host
     * @param blockPos    immutable cable-bus host position
     * @param mount       side or center slot occupied by the part
     * @param partItemId  registered item that reconstructs the part
     */
    record Part(ResourceLocation dimensionId,
                BlockPos blockPos,
                Mount mount,
                ResourceLocation partItemId) implements ProviderIdentity {

        /**
         * Validates and defensively freezes the host and part fields.
         */
        public Part {
            dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
            blockPos = Objects.requireNonNull(blockPos, "blockPos").immutable();
            mount = Objects.requireNonNull(mount, "mount");
            partItemId = Objects.requireNonNull(partItemId, "partItemId");
        }

        @Override
        public Kind kind() {
            return Kind.PART;
        }
    }

    /**
     * Identity of one virtual Trinity terminal partition.
     *
     * @param hostId         persistent Trinity host identity
     * @param coreId         persistent physical pattern-core identity
     * @param partitionIndex zero-based partition within the core
     */
    record Trinity(UUID hostId, UUID coreId, int partitionIndex) implements ProviderIdentity {

        /**
         * Rejects incomplete or invalid persistent routing keys.
         */
        public Trinity {
            hostId = Objects.requireNonNull(hostId, "hostId");
            coreId = Objects.requireNonNull(coreId, "coreId");
            if (partitionIndex < 0) {
                throw new IllegalArgumentException("A provider identity partition index must not be negative");
            }
        }

        @Override
        public Kind kind() {
            return Kind.TRINITY;
        }
    }

    /**
     * Identity of an assembler matrix target that can receive an encoded pattern.
     *
     * @param dimensionId dimension containing the matrix
     * @param blockPos immutable matrix position
     * @param plus whether the matrix is the ExtendedAE-Plus variant
     */
    record Matrix(ResourceLocation dimensionId, BlockPos blockPos, boolean plus) implements ProviderIdentity {

        /** Validates and defensively freezes the matrix location. */
        public Matrix {
            dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
            blockPos = Objects.requireNonNull(blockPos, "blockPos").immutable();
        }

        @Override
        public Kind kind() {
            return Kind.MATRIX;
        }
    }

    /**
     * Identity of a provider that has no physical location or dedicated persistent routing key.
     *
     * @param terminalGroupIconId       optional terminal group item ID
     * @param terminalGroupNameEncoding canonical JSON encoding of the structured terminal group name component
     */
    record Virtual(Optional<ResourceLocation> terminalGroupIconId,
                   String terminalGroupNameEncoding) implements ProviderIdentity {

        /**
         * Rejects incomplete display semantics before they become a persistence key.
         */
        public Virtual {
            terminalGroupIconId = Objects.requireNonNull(terminalGroupIconId, "terminalGroupIconId");
            terminalGroupIconId.ifPresent(iconId -> Objects.requireNonNull(iconId, "terminalGroupIconId value"));
            terminalGroupNameEncoding = Objects.requireNonNull(
                    terminalGroupNameEncoding,
                    "terminalGroupNameEncoding");
            if (terminalGroupNameEncoding.isBlank()) {
                throw new IllegalArgumentException("A virtual provider identity requires a component encoding");
            }
        }

        @Override
        public Kind kind() {
            return Kind.VIRTUAL;
        }
    }
}
