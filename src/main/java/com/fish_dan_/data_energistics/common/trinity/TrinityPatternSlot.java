package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Stable state boundary for one physical slot in a Trinity pattern processing core.
 *
 * <p>
 * A slot owns its installed pattern, decoded cache, complete definition table, FIFO of counted groups, and counted
 * pending outputs. The core owns only cross-slot indexes and host-level transactions.
 * </p>
 */
public interface TrinityPatternSlot {

    /**
     * Classifies changes so hosts can update only the indexes and persistence surfaces that actually changed.
     */
    enum ChangeKind {
        /**
         * Published recipe details changed and catalog revision must advance.
         */
        CATALOG,
        /**
         * This slot's queue or pending-route index changed.
         */
        WORK,
        /**
         * Durable slot state changed and the owner must be marked dirty.
         */
        PERSISTENT
    }

    /**
     * Typed slot mutation delivered to the owning core.
     *
     * @param slot stable physical slot index
     * @param kind changed state surface
     */
    record Change(int slot, ChangeKind kind) {

        public Change {
            if (slot < 0) {
                throw new IllegalArgumentException("Trinity pattern slot change index must not be negative");
            }
        }
    }

    /**
     * Receives typed slot changes after the slot has established its new internal state.
     */
    @FunctionalInterface
    interface ChangeListener {

        /**
         * @param change exact slot and state surface that changed
         */
        void onChanged(Change change);
    }

    /**
     * @return stable physical slot index
     */
    int index();

    /**
     * @return monotonically increasing revision of this stable physical slot object
     */
    long revision();

    /**
     * @return defensive copy of the installed encoded pattern
     */
    ItemStack pattern();

    /**
     * Tests whether a new pattern can establish both supported details and a unique recipe identity.
     *
     * @param pattern encoded pattern candidate
     * @return whether the candidate may be installed
     */
    boolean acceptsPattern(ItemStack pattern);

    /**
     * Replaces the installed pattern after resolving its complete stable definition.
     *
     * @param pattern encoded pattern candidate, or empty to clear
     * @return whether the requested state was accepted
     */
    boolean trySetPattern(ItemStack pattern);

    /**
     * @return current decoded details, or {@code null} while unavailable, opaque, or identity-mismatched
     */
    @Nullable
    IMolecularAssemblerSupportedPattern decodedPattern();

    /**
     * Rebuilds the transient decoded cache without discarding retained pattern or queue state.
     */
    void refreshPatternCache();

    /**
     * Appends or tail-merges one exact dispatch.
     *
     * @param route           exact owner route
     * @param patternSnapshot encoded definition selected by the crafting plan
     * @param inputs          nine row-major inputs
     * @param queuedTick      accepting server tick
     * @return whether the current installed definition accepted the dispatch
     */
    default boolean enqueue(PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs, long queuedTick) {
        return enqueue(route, patternSnapshot, inputs, queuedTick, 1L);
    }

    /**
     * Appends or tail-merges one homogeneous counted dispatch.
     *
     * @param route           exact owner route
     * @param patternSnapshot encoded definition selected by the crafting plan
     * @param inputs          one nine-slot row-major input prototype
     * @param queuedTick      accepting server tick
     * @param count           positive number of identical logical crafts represented by the prototype
     * @return whether the current installed definition accepted the complete counted dispatch
     */
    boolean enqueue(PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs, long queuedTick, long count);

    /**
     * @return immutable defensive FIFO snapshot of counted groups
     */
    List<TrinityCraftingBatch> queuedBatches();

    /**
     * @return number of physical queue groups after adjacent merging
     */
    int queuedBatchCount();

    /**
     * @return whether this slot contains at least one queued group
     */
    boolean hasQueuedWork();

    /**
     * @return whether this slot retains at least one counted pending-output route
     */
    boolean hasPendingOutputs();

    /**
     * @return immutable insertion-ordered route snapshot for this slot's pending outputs
     */
    List<PatternRoute> pendingOutputRoutes();

    /**
     * @param route exact route owned by this physical slot
     * @return immutable defensive snapshot of that route's counted outputs
     */
    List<TrinityItemAmount> pendingOutputs(PatternRoute route);

    /**
     * Opens the sole authoritative mutable cursor for one route.
     *
     * @param route exact route owned by this physical slot
     * @return exclusive cursor that must be closed by the caller
     */
    TrinityPatternOutputRouter.PendingOutputCursor openPendingOutputCursor(PatternRoute route);

    /**
     * Returns the current executable FIFO head without removing it.
     *
     * @param currentTick current server tick
     * @return eligible group, or {@code null} when the slot must sleep
     */
    @Nullable
    TrinityCraftingBatch readyHead(long currentTick);

    /**
     * Atomically moves the exact completed FIFO head into its counted pending outputs.
     *
     * <p>
     * Queue removal and output publication form one durable mutation so observers never see the intermediate state.
     * </p>
     *
     * @param completed group previously returned by {@link #readyHead(long)}
     * @param outputs   counted outputs produced by the complete group
     */
    void completeHead(TrinityCraftingBatch completed, List<TrinityItemAmount> outputs);

    /**
     * Removes the exact completed FIFO head.
     *
     * @param completed group previously returned by {@link #readyHead(long)}
     */
    void removeCompletedHead(TrinityCraftingBatch completed);

    /**
     * Removes every queued group while preserving installed pattern state.
     */
    void clearQueuedBatches();

    /**
     * Removes queued groups owned by one refund scope.
     *
     * @param hostId exact host owner to remove
     */
    void clearQueuedBatches(UUID hostId);

    /**
     * Replaces the FIFO with a previously captured exact snapshot during refund rollback or V2 commit.
     *
     * @param batches ordered queue snapshot
     */
    void replaceQueuedBatches(List<TrinityCraftingBatch> batches);

    /**
     * Writes this complete non-empty slot as one V2 atomic unit.
     *
     * @param registries item component registry access
     * @return slot tag containing pattern, definition table, and queue references
     */
    CompoundTag writeV2(HolderLookup.Provider registries);
}
