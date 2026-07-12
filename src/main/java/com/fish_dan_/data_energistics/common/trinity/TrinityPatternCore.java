package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Public contract for one independently persistent Trinity pattern processing core.
 *
 * <p>
 * The contract keeps host scans and crafting providers independent from the concrete block entity. A core owns a
 * stable identity, a fixed pattern inventory, one FIFO per slot, and pending outputs that must survive movement.
 */
public interface TrinityPatternCore {

    /**
     * One publishable recipe retained by a physical core's local cache.
     *
     * @param slot    stable slot that must be included in the host-created route
     * @param details decoded AE2 crafting details owned by this cache entry
     */
    record CachedPattern(int slot, IMolecularAssemblerSupportedPattern details) {

        public CachedPattern {
            if (slot < 0) {
                throw new IllegalArgumentException("Cached Trinity pattern slot must not be negative");
            }
        }
    }

    /**
     * Immutable, slot-ordered publication state for one physical pattern core.
     *
     * <p>
     * Hosts merge these snapshots and add their own route identity without rescanning every physical slot.
     *
     * @param revision core-local publication revision
     * @param patterns publishable entries in ascending slot order
     */
    record PatternCacheSnapshot(long revision, List<CachedPattern> patterns) {

        public PatternCacheSnapshot {
            if (revision < 0L) {
                throw new IllegalArgumentException("Trinity pattern cache revision must not be negative");
            }
            patterns = List.copyOf(patterns);
            int previousSlot = -1;
            for (CachedPattern pattern : patterns) {
                if (pattern.slot() <= previousSlot) {
                    throw new IllegalArgumentException(
                            "Trinity pattern cache entries must use unique ascending slots");
                }
                previousSlot = pattern.slot();
            }
        }
    }

    /**
     * Decodes an encoded item into a molecular-assembler-compatible crafting pattern.
     */
    @FunctionalInterface
    interface PatternDecoder {

        /**
         * @param pattern encoded pattern stack to inspect
         * @return supported crafting details, or {@code null} when the stack cannot currently be published
         */
        @Nullable
        IMolecularAssemblerSupportedPattern decode(ItemStack pattern);
    }

    /**
     * Executes one eligible batch and atomically supplies the outputs created by that batch.
     */
    @FunctionalInterface
    interface BatchExecutor {

        /**
         * @param slot  core slot that owns the batch
         * @param batch immutable crafting input snapshot
         * @return completed result with outputs, or a paused result that keeps the batch at the FIFO head
         */
        BatchExecutionResult execute(int slot, TrinityCraftingBatch batch);
    }

    /**
     * Result returned by a batch executor so queue removal and output insertion remain one state transition.
     */
    final class BatchExecutionResult {

        private static final BatchExecutionResult PAUSED = new BatchExecutionResult(false, List.of());

        private final boolean completed;
        private final List<ItemStack> outputs;

        private BatchExecutionResult(boolean completed, List<ItemStack> outputs) {
            this.completed = completed;
            this.outputs = copyStacks(outputs);
        }

        /**
         * Creates a successful execution result.
         *
         * @param outputs crafted output and any container remainders
         * @return completed result
         */
        public static BatchExecutionResult completed(List<ItemStack> outputs) {
            return new BatchExecutionResult(true, outputs);
        }

        /**
         * @return result that leaves the current batch queued for a later tick
         */
        public static BatchExecutionResult paused() {
            return PAUSED;
        }

        /**
         * @return whether the FIFO head may be removed
         */
        public boolean completed() {
            return this.completed;
        }

        /**
         * @return defensive copies of outputs to append when execution completed
         */
        public List<ItemStack> outputs() {
            return copyStacks(this.outputs);
        }

        private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
            return stacks.stream()
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList();
        }
    }

    /**
     * @return stable UUID used by persistent pattern routes
     */
    UUID coreId();

    /**
     * @return fixed slot count derived from the core block tier
     */
    int patternCapacity();

    /**
     * Returns the stable object representing one physical slot. Its identity never changes during this core's
     * lifetime, including after successful NBT loads.
     *
     * @param slot physical slot index
     * @return stable slot state boundary
     */
    TrinityPatternSlot patternSlot(int slot);

    /**
     * @return monotonically increasing runtime revision for catalog cache invalidation
     */
    long revision();

    /**
     * Returns the core-local publishable pattern cache used by an aggregate host catalog.
     *
     * @return stable immutable snapshot whose entries are ordered by physical slot
     */
    PatternCacheSnapshot patternCacheSnapshot();

    /**
     * @return fixed-size inventory exposed to menus
     */
    InternalInventory patternInventory();

    /**
     * @param slot pattern slot index
     * @return defensive copy of the installed encoded pattern
     */
    ItemStack pattern(int slot);

    /**
     * Attempts to replace one pattern. Non-empty stacks are accepted only when the decoder produces an
     * {@link IMolecularAssemblerSupportedPattern} at the time of insertion.
     *
     * @param slot    pattern slot index
     * @param pattern encoded crafting pattern, or empty to clear
     * @return true when the requested state was accepted
     */
    boolean trySetPattern(int slot, ItemStack pattern);

    /**
     * @param slot pattern slot index
     * @return cached supported details, or {@code null} while unavailable or invalid
     */
    @Nullable
    IMolecularAssemblerSupportedPattern decodedPattern(int slot);

    /**
     * Re-decodes one retained pattern after recipe or tag data changes.
     *
     * @param slot pattern slot index
     */
    void refreshPatternCache(int slot);

    /**
     * Re-decodes every retained pattern after a level becomes ready or a data pack reload completes.
     */
    void refreshAllPatternCaches();

    /**
     * Synchronizes externally invalidated recipe caches before a catalog publishes, dispatches, or executes them.
     *
     * <p>
     * Pure logical cores use their explicit refresh lifecycle. World-backed cores additionally compare the global
     * data-reload epoch here so correctness does not depend on block-entity tick order.
     */
    void ensurePatternCachesCurrent();

    /**
     * Atomically appends one complete crafting input snapshot to a slot FIFO.
     *
     * @param route           exact host/core/slot destination selected by the crafting plan
     * @param patternSnapshot exact encoded pattern used by the route
     * @param inputs          nine row-major crafting inputs
     * @param queuedTick      current server tick; execution starts only on a later tick
     * @return true when the snapshot matched the current slot and was queued
     */
    boolean enqueueBatch(PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs, long queuedTick);

    /**
     * @param slot pattern slot index
     * @return immutable defensive snapshot of that slot's FIFO
     */
    List<TrinityCraftingBatch> queuedBatches(int slot);

    /**
     * @param slot pattern slot index
     * @return number of physical queue groups waiting in that slot after adjacent merging
     */
    int queuedBatchCount(int slot);

    /**
     * @return total number of physical queue groups across every slot
     */
    int queuedBatchCount();

    /**
     * Executes every FIFO head eligible on this tick. A pattern mismatch or paused executor leaves that slot asleep;
     * other slots continue independently.
     *
     * @param currentTick current server tick
     * @param executor    crafting callback
     * @return number of completed batches
     */
    int executeReadyBatches(long currentTick, BatchExecutor executor);

    /**
     * @param route exact owner route to query
     * @return immutable defensive snapshot of counted outputs still awaiting host routing
     */
    List<TrinityItemAmount> pendingOutputs(PatternRoute route);

    /**
     * Appends crafted output or container remainders to a slot's persistent output queue.
     *
     * @param route   exact host/core/slot route that owns these outputs
     * @param outputs positive counted outputs to append
     */
    void appendPendingOutputs(PatternRoute route, List<TrinityItemAmount> outputs);

    /**
     * Opens the authoritative exclusive cursor used to checkpoint every successful external insertion in place.
     *
     * @param route exact host/core/slot route whose outputs are being routed
     * @return exclusive cursor that must be closed by the caller
     */
    TrinityPatternOutputRouter.PendingOutputCursor openPendingOutputCursor(PatternRoute route);

    /**
     * Finds only slots that currently retain output for one host without scanning the core's full capacity.
     *
     * @param hostId stable host identity whose routed outputs are requested
     * @return immutable ascending slot indexes
     */
    List<Integer> pendingOutputSlots(UUID hostId);

    /**
     * Finds physical slots containing queued inputs or pending outputs owned by one host without scanning capacity.
     *
     * @param hostId stable host identity whose active slots are requested
     * @return immutable ascending physical slot indexes
     */
    List<Integer> workingSlots(UUID hostId);

    /**
     * @return whether queued inputs or pending outputs lock this core to its current host/grid
     */
    boolean hasWork();

    /**
     * Reports whether one host owns queued inputs or pending outputs in constant time.
     *
     * <p>
     * Host scoping is required because a moved core may retain sleeping routes belonging to a different host.
     *
     * @param hostId stable host identity to inspect
     * @return whether that host has work retained by this core
     */
    boolean hasWork(UUID hostId);

    /**
     * Reversible prepared refund for one physical P core.
     *
     * <p>
     * A host uses this contract to combine several cores into one transaction. Preparation must not mutate the
     * core. A successful {@link #commit()} clears only the exact queued inputs and pending outputs captured at
     * preparation time; installed patterns and their caches remain untouched. {@link #rollback()} restores a
     * previously committed capture when another participant fails.
     * </p>
     */
    interface RefundTransaction {

        /**
         * @return immutable counted entries for every queued input and pending output in the capture
         */
        List<TrinityItemAmount> refundableItems();

        /**
         * Clears the captured core state when it is still current.
         *
         * @return false when the core changed after preparation or this transaction is no longer usable
         */
        boolean commit();

        /**
         * Finalizes a committed capture after external delivery begins and releases its short-lived core lock.
         *
         * <p>
         * A caller must use this instead of {@link #rollback()} once any external destination might have received an
         * item, because restoring the captured queue could duplicate that item.
         * </p>
         */
        void complete();

        /**
         * Restores the captured state after a successful commit, or abandons an uncommitted capture.
         */
        void rollback();
    }

    /**
     * Captures a reversible refund of this core without changing persistent state.
     *
     * <p>
     * Callers that coordinate more than one core must prepare delivery before committing every capture, then roll
     * back every capture when any participant fails before external delivery begins.
     * </p>
     *
     * @return a prepared refund transaction for this core's current queued inputs and pending outputs
     */
    RefundTransaction prepareRefund();

    /**
     * Captures a reversible refund containing only queue batches and pending outputs owned by one host route.
     *
     * <p>
     * This isolates a host aggregate from sleeping work that belongs to another host after a movable core is
     * re-mounted elsewhere. Installed encoded patterns remain outside both refund scopes.
     * </p>
     *
     * @param hostId stable host identity that must own every returned route
     * @return a prepared refund transaction limited to the supplied host routes
     */
    RefundTransaction prepareRefund(UUID hostId);

    /**
     * Returns queued inputs and pending outputs through a prepared two-phase delivery after atomically clearing them.
     * Installed encoded patterns, recipe caches, and pattern publication remain unchanged.
     *
     * @param delivery external two-phase destination for the refundable state
     * @return true when the delivery prepared successfully and queued state was cleared
     */
    boolean tryRefundAll(TrinityRefundDelivery delivery);

    /**
     * Writes the complete movable core state into a block entity tag.
     *
     * @param data       destination tag
     * @param registries registry lookup used for item components
     */
    void writeToTag(CompoundTag data, HolderLookup.Provider registries);

    /**
     * Atomically restores complete movable core state from a block entity tag.
     *
     * @param data       source tag
     * @param registries registry lookup used for item components
     */
    void readFromTag(CompoundTag data, HolderLookup.Provider registries);
}
