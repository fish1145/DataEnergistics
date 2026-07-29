package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds one resolved MDLib pattern through a two-phase inventory-and-world operation.
 *
 * <p>
 * The interface exists so machine hosts depend on the build contract while the placement transaction remains
 * replaceable and directly testable through its implementation.
 * </p>
 */
public interface MultiBlockAutoBuild {

    /**
     * Plans, preflights, reserves, and places every missing block described by the supplied context.
     *
     * @param context immutable inputs for one build attempt
     * @return the committed result, a pre-publication failure after staging changes have been rolled back, or a
     *         publication failure that preserves already observable world state
     */
    Result execute(Context context);

    /**
     * Resolves the AE2 host side for a part position in an oriented structure.
     *
     * <p>
     * Part items do not carry enough structure-local orientation information to select a host side on their own.
     * The owning machine supplies this resolver so the transaction can validate and persist the exact destination
     * before it consumes materials.
     * </p>
     */
    @FunctionalInterface
    interface PartSideResolver {

        /**
         * Resolves the destination side for one detached, single-count AE2 part stack.
         *
         * @param position  target structure position
         * @param partStack detached placement stack; changes do not affect the transaction
         * @return the explicit AE2 host side, or {@code null} when this part cannot be placed safely
         */
        @Nullable
        Direction resolve(BlockPos position, ItemStack partStack);
    }

    /**
     * Defines the explicit world states that a host has audited for pre-publication staging.
     *
     * <p>
     * The transaction never treats a generic {@code BlockItem} or AE2 part as safe merely because its normal placement
     * API currently accepts it. A host must opt in to each direct block-state write and each temporary AE2 part host.
     * </p>
     */
    interface StagingPolicy {

        /**
         * Default policy that rejects every candidate until a machine host explicitly approves it.
         */
        StagingPolicy REJECT_ALL = new StagingPolicy() {

            @Override
            public boolean canStageBlock(BlockPos position, ItemStack stack, BlockState desiredState) {
                return false;
            }

            @Nullable
            @Override
            public BlockState partHostState(BlockPos position, ItemStack partStack, Direction side) {
                return null;
            }
        };

        /**
         * Returns whether the exact desired block state may be written during the silent pre-commit phase.
         *
         * @param position     target structure position
         * @param stack        placement item selected from the player inventory
         * @param desiredState predicate-selected final block state
         */
        boolean canStageBlock(BlockPos position, ItemStack stack, BlockState desiredState);

        /**
         * Returns whether the exact approved state may temporarily exist in the real world before publication.
         *
         * <p>
         * A negative answer does not reject the candidate. It keeps the state in the transaction overlay until normal
         * publication. This distinguishes an approved final state from a reversible pre-publication world mutation.
         * </p>
         *
         * @param position     target structure position
         * @param stack        placement item selected from the player inventory
         * @param desiredState predicate-selected final block state
         * @return true only for an explicitly audited pure block state
         */
        default boolean canPhysicallyStageBlock(BlockPos position, ItemStack stack, BlockState desiredState) {
            return false;
        }

        /**
         * Returns the temporary host state for a deferred AE2 part, or {@code null} when this part is not approved.
         *
         * @param position  target structure position
         * @param partStack detached, single-count AE2 part stack
         * @param side      resolved AE2 host side
         * @return a host state that can be silently staged, or {@code null}
         */
        @Nullable
        BlockState partHostState(BlockPos position, ItemStack partStack, Direction side);
    }

    /**
     * Captures the runtime and player choices required to build one oriented structure.
     *
     * <p>
     * The builder keeps the call site readable and prevents positional constructor arguments from mixing the
     * controller origin, orientation, repeat count, and tier selections.
     * </p>
     */
    final class Context {

        /**
         * Server world that owns inventory, block, and block-entity mutations.
         */
        private final ServerLevel level;
        /**
         * Player whose permissions and inventory authorize the build.
         */
        private final Player player;
        /**
         * Structure view used by MDLib predicates during preflight.
         */
        private final StructureWorldView world;
        /**
         * Resolved pattern selected by the host machine.
         */
        private final BlockPattern pattern;
        /**
         * Controller position from which pattern coordinates are transformed.
         */
        private final BlockPos origin;
        /**
         * Stable structure name supplied to MDLib diagnostics.
         */
        private final String structureName;
        /**
         * Horizontal front used for pattern coordinate transforms.
         */
        private final Direction front;
        /**
         * Whether the selected pattern transform is mirrored.
         */
        private final boolean flipped;
        /**
         * Requested repetition for every variable pattern unit.
         */
        private final int repeatCount;
        /**
         * Maps every candidate in a selected predicate category to that category's chosen block.
         */
        private final Map<Block, Block> selectedTierBlocks;
        /**
         * Maps upgradeable candidate blocks to their positive, host-defined tier rank.
         */
        private final Map<Block, Integer> tierRanks;
        /**
         * Resolves the explicit AE2 host side required by each planned part placement.
         */
        private final PartSideResolver partSideResolver;
        /**
         * Host-owned allowlist for direct silent state staging.
         */
        private final StagingPolicy stagingPolicy;

        private Context(Builder builder) {
            this.level = builder.level;
            this.player = builder.player;
            this.world = builder.world;
            this.pattern = builder.pattern;
            this.origin = builder.origin.immutable();
            this.structureName = builder.structureName;
            this.front = builder.front;
            this.flipped = builder.flipped;
            this.repeatCount = builder.repeatCount;
            this.selectedTierBlocks = Map.copyOf(builder.selectedTierBlocks);
            this.tierRanks = Map.copyOf(builder.tierRanks);
            this.partSideResolver = builder.partSideResolver;
            this.stagingPolicy = builder.stagingPolicy;
            if (this.structureName.isBlank()) {
                throw new IllegalArgumentException("Auto-build structure name cannot be blank");
            }
            if (this.repeatCount < 1) {
                throw new IllegalArgumentException("Auto-build repeat count must be positive: " + this.repeatCount);
            }
            for (int tierRank : this.tierRanks.values()) {
                if (tierRank < 1) {
                    throw new IllegalArgumentException("Auto-build tier ranks must be positive: " + tierRank);
                }
            }
        }

        /**
         * Returns a new context builder for one server-side build attempt.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Returns the server world that will be mutated after preflight succeeds.
         */
        public ServerLevel level() {
            return this.level;
        }

        /**
         * Returns the player that supplies permissions and materials.
         */
        public Player player() {
            return this.player;
        }

        /**
         * Returns the MDLib-compatible world view used to evaluate predicates.
         */
        public StructureWorldView world() {
            return this.world;
        }

        /**
         * Returns the resolved structure pattern.
         */
        public BlockPattern pattern() {
            return this.pattern;
        }

        /**
         * Returns the controller origin.
         */
        public BlockPos origin() {
            return this.origin;
        }

        /**
         * Returns the structure name used by predicate diagnostics.
         */
        public String structureName() {
            return this.structureName;
        }

        /**
         * Returns the chosen structure front.
         */
        public Direction front() {
            return this.front;
        }

        /**
         * Returns whether the structure transform is mirrored.
         */
        public boolean flipped() {
            return this.flipped;
        }

        /**
         * Returns the requested repetition for variable pattern units.
         */
        public int repeatCount() {
            return this.repeatCount;
        }

        /**
         * Returns the immutable candidate-to-selected-tier mapping.
         */
        public Map<Block, Block> selectedTierBlocks() {
            return this.selectedTierBlocks;
        }

        /**
         * Returns host-declared tier ranks used to permit only safe upward replacement of existing tier candidates.
         */
        public Map<Block, Integer> tierRanks() {
            return this.tierRanks;
        }

        /**
         * Returns the resolver used to choose an AE2 part host side before materials are committed.
         */
        public PartSideResolver partSideResolver() {
            return this.partSideResolver;
        }

        /**
         * Returns the host policy that approves each controlled staging path.
         */
        public StagingPolicy stagingPolicy() {
            return this.stagingPolicy;
        }

        /**
         * Collects context fields by name before creating the immutable execution context.
         */
        public static final class Builder {

            /**
             * Server world supplied by the owning host.
             */
            private ServerLevel level;
            /**
             * Player initiating the request.
             */
            private Player player;
            /**
             * Predicate world view, normally backed by {@link #level}.
             */
            private StructureWorldView world;
            /**
             * Resolved MDLib pattern selected by the host.
             */
            private BlockPattern pattern;
            /**
             * Controller origin used by coordinate transforms.
             */
            private BlockPos origin;
            /**
             * Diagnostic structure name.
             */
            private String structureName;
            /**
             * Selected front orientation.
             */
            private Direction front;
            /**
             * Selected mirror state.
             */
            private boolean flipped;
            /**
             * Selected variable-unit repetition.
             */
            private int repeatCount = 1;
            /**
             * Mutable accumulation of candidate-to-tier selections.
             */
            private final Map<Block, Block> selectedTierBlocks = new LinkedHashMap<>();
            /**
             * Mutable host-defined rank table for candidates that support upward replacement.
             */
            private final Map<Block, Integer> tierRanks = new LinkedHashMap<>();
            /**
             * Defaults to no side so an unresolved AE2 part is rejected during preflight.
             */
            private PartSideResolver partSideResolver = (position, partStack) -> null;
            /**
             * Defaults to denial so generic item placement cannot bypass the two-phase transaction contract.
             */
            private StagingPolicy stagingPolicy = StagingPolicy.REJECT_ALL;

            private Builder() {}

            /**
             * Supplies the server world that owns the transaction.
             */
            public Builder level(ServerLevel level) {
                this.level = level;
                return this;
            }

            /**
             * Supplies the initiating player.
             */
            public Builder player(Player player) {
                this.player = player;
                return this;
            }

            /**
             * Supplies the MDLib predicate world view.
             */
            public Builder world(StructureWorldView world) {
                this.world = world;
                return this;
            }

            /**
             * Supplies the resolved pattern.
             */
            public Builder pattern(BlockPattern pattern) {
                this.pattern = pattern;
                return this;
            }

            /**
             * Supplies the controller origin.
             */
            public Builder origin(BlockPos origin) {
                this.origin = origin;
                return this;
            }

            /**
             * Supplies the non-blank diagnostic structure name.
             */
            public Builder structureName(String structureName) {
                this.structureName = structureName;
                return this;
            }

            /**
             * Supplies the selected horizontal front.
             */
            public Builder front(Direction front) {
                this.front = front;
                return this;
            }

            /**
             * Supplies whether the pattern is mirrored.
             */
            public Builder flipped(boolean flipped) {
                this.flipped = flipped;
                return this;
            }

            /**
             * Supplies the repetition requested for variable pattern units.
             */
            public Builder repeatCount(int repeatCount) {
                this.repeatCount = repeatCount;
                return this;
            }

            /**
             * Supplies candidate-to-tier selections for controlled predicate categories.
             *
             * <p>
             * The builder validates each affected predicate during preflight. Every block candidate in that predicate
             * must map to the same candidate block, otherwise the operation fails before materials are reserved.
             * </p>
             */
            public Builder selectedTierBlocks(Map<Block, Block> selectedTierBlocks) {
                this.selectedTierBlocks.clear();
                this.selectedTierBlocks.putAll(selectedTierBlocks);
                return this;
            }

            /**
             * Supplies positive tier ranks for selected candidate blocks.
             *
             * <p>
             * A planned selected block can replace an existing valid candidate only when its supplied rank is strictly
             * greater than the existing block's rank. Omitting ranks preserves the default no-replacement behavior.
             * </p>
             */
            public Builder tierRanks(Map<Block, Integer> tierRanks) {
                this.tierRanks.clear();
                this.tierRanks.putAll(tierRanks);
                return this;
            }

            /**
             * Supplies the resolver that chooses the AE2 host side for every part placement.
             *
             * <p>
             * The default resolver returns {@code null}, which deliberately rejects AE2 parts instead of guessing a
             * side from the pattern coordinates.
             * </p>
             */
            public Builder partSideResolver(PartSideResolver partSideResolver) {
                this.partSideResolver = partSideResolver;
                return this;
            }

            /**
             * Supplies the host-owned allowlist for controlled pre-commit staging.
             */
            public Builder stagingPolicy(StagingPolicy stagingPolicy) {
                this.stagingPolicy = stagingPolicy;
                return this;
            }

            /**
             * Creates the immutable context after semantic scalar validation.
             */
            public Context build() {
                return new Context(this);
            }
        }
    }

    /**
     * Reports whether the complete operation committed and how much of the requested structure was already reusable.
     *
     * @param success true only when every planned placement committed
     * @param placed  number of blocks or parts published; zero after a pre-publication rollback
     * @param reused  number of non-air pattern positions that already matched during preflight
     * @param failure first failure, absent after a successful commit
     */
    record Result(boolean success, int placed, int reused, @Nullable Failure failure) {

        /**
         * Creates a successful committed result.
         */
        public static Result success(int placed, int reused) {
            return new Result(true, placed, reused, null);
        }

        /**
         * Creates a failed result after the transaction has left no committed placement.
         */
        public static Result failure(int reused, Failure failure) {
            return new Result(false, 0, reused, failure);
        }

        /**
         * Creates a failure result after publication began. Unpublished material reservations are returned separately,
         * while already published world state remains observable.
         */
        public static Result publishFailure(int placed, int reused, Failure failure) {
            return new Result(false, placed, reused, failure);
        }
    }

    /**
     * Classifies the first reason an atomic build could not commit.
     */
    enum FailureType {
        /**
         * A requested repeat count is incompatible with the resolved pattern.
         */
        INVALID_REPETITION,
        /**
         * A selected tier mapping is incomplete, inconsistent, or points outside its predicate candidates.
         */
        INVALID_TIER_SELECTION,
        /**
         * At least one required position is not currently loaded.
         */
        UNLOADED,
        /**
         * A non-replaceable or wrong-tier block occupies a required position.
         */
        BLOCKED,
        /**
         * The player inventory cannot satisfy every planned placement.
         */
        MISSING_MATERIAL,
        /**
         * A predicate has no supported block or AE2 part placement candidate.
         */
        UNSUPPORTED_CANDIDATE,
        /**
         * A selected candidate lacks a host-approved silent staging path.
         */
        UNSUPPORTED_STAGING,
        /**
         * The player does not have permission to place at a required position.
         */
        PERMISSION_DENIED,
        /**
         * A placement or post-placement predicate verification failed.
         */
        PLACE_FAILED,
        /**
         * Restoring a captured world snapshot failed after a placement error.
         */
        ROLLBACK_FAILED,
        /**
         * Publication started and could not finish, so published world state is not rolled back.
         */
        PUBLISH_FAILED
    }

    /**
     * Describes the first failed position without exposing mutable planning state.
     *
     * @param type     stable failure category for UI translation
     * @param position relevant world position, absent for pattern-wide validation
     * @param detail   concise server diagnostic suitable for logs
     */
    record Failure(FailureType type, @Nullable BlockPos position, String detail) {}
}
