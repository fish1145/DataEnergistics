package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolution;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
     * One stable occupied-slot entry retained by a physical core's local cache.
     * <p>
     * Runtime recipe details may be rebound or temporarily unavailable without replacing this directory entry.
     */
    final class CachedPattern {

        private final int slot;
        private TrinityPatternDefinition definition;
        private final AEItemKey encodedDefinition;
        @Nullable
        private IMolecularAssemblerSupportedPattern details;
        @Nullable
        private TrinityPatternPublicationSignature publicationSignature;
        private long runtimeBindingRevision;

        public CachedPattern(int slot,
                             TrinityPatternDefinition definition,
                             @Nullable IMolecularAssemblerSupportedPattern details) {
            if (slot < 0) {
                throw new IllegalArgumentException("Cached Trinity pattern slot must not be negative");
            }
            this.slot = slot;
            this.definition = definition;
            this.encodedDefinition = AEItemKey.of(definition.pattern());
            this.details = details;
            this.publicationSignature = details == null ? null : TrinityPatternPublicationSignature.capture(details);
        }

        /**
         * @return stable physical slot represented by this occupied-directory entry
         */
        public int slot() {
            return this.slot;
        }

        /**
         * @return immutable slot-local definition token retained by queued groups
         */
        public TrinityPatternDefinition definition() {
            return this.definition;
        }

        /**
         * @return allocation-free encoded definition key used to validate routed AE patterns
         */
        public AEItemKey encodedDefinition() {
            return this.encodedDefinition;
        }

        /**
         * @return stable resolver and recipe IDs captured by the installed definition
         */
        public TrinityPatternRecipeIdResolution recipeResolution() {
            return this.definition.resolution();
        }

        /**
         * @return current runtime recipe binding, or {@code null} while the retained pattern cannot be published
         */
        @Nullable
        public IMolecularAssemblerSupportedPattern details() {
            return this.details;
        }

        /**
         * @return revision increased only when this entry changes between distinct publishable semantics
         */
        public long runtimeBindingRevision() {
            return this.runtimeBindingRevision;
        }

        PreparedRebind prepareRebind(TrinityPatternDefinition definition,
                                     @Nullable IMolecularAssemblerSupportedPattern details) {
            if (!this.encodedDefinition.equals(AEItemKey.of(definition.pattern()))) {
                throw new IllegalArgumentException("A cached Trinity pattern cannot rebind to another encoded definition");
            }
            TrinityPatternRecipeIdResolution currentResolution = this.definition.resolution();
            TrinityPatternRecipeIdResolution nextResolution = definition.resolution();
            boolean recipeIdentityChanged = currentResolution == null ? nextResolution != null :
                    !currentResolution.equals(nextResolution);
            TrinityPatternPublicationSignature nextSignature = details == null ? null :
                    TrinityPatternPublicationSignature.capture(details);
            boolean semanticChange = recipeIdentityChanged || (this.publicationSignature == null ? nextSignature != null :
                    !this.publicationSignature.equals(nextSignature));
            long nextRevision = semanticChange ? Math.incrementExact(this.runtimeBindingRevision) :
                    this.runtimeBindingRevision;
            return new PreparedRebind(definition, details, nextSignature, nextRevision, semanticChange);
        }

        void commitRebind(PreparedRebind prepared) {
            this.definition = prepared.definition();
            this.details = prepared.details();
            this.publicationSignature = prepared.publicationSignature();
            this.runtimeBindingRevision = prepared.runtimeBindingRevision();
        }

        boolean rebind(TrinityPatternDefinition definition,
                       @Nullable IMolecularAssemblerSupportedPattern details) {
            PreparedRebind prepared = prepareRebind(definition, details);
            commitRebind(prepared);
            return prepared.semanticChange();
        }

        record PreparedRebind(TrinityPatternDefinition definition,
                              @Nullable IMolecularAssemblerSupportedPattern details,
                              @Nullable TrinityPatternPublicationSignature publicationSignature,
                              long runtimeBindingRevision,
                              boolean semanticChange) {
        }
    }

    /**
     * Slot-ordered occupied-pattern directory for one physical pattern core.
     *
     * <p>
     * The list and entry identities change only when an encoded pattern is installed, replaced, moved, or removed.
     * Each stable entry carries a separately versioned runtime recipe binding so data reloads do not rebuild the
     * directory.
     *
     * @param revision core-local occupied-directory revision
     * @param patterns occupied entries in ascending slot order, including retained patterns that are temporarily
     *                 unavailable at runtime
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
        private final List<TrinityItemAmount> countedOutputs;

        private BatchExecutionResult(boolean completed, List<TrinityItemAmount> countedOutputs) {
            this.completed = completed;
            this.countedOutputs = List.copyOf(countedOutputs);
        }

        /**
         * Creates a successful execution result.
         *
         * @param batch       counted group that produced the unit outputs
         * @param unitOutputs one-craft output and container remainders
         * @return completed result
         */
        public static BatchExecutionResult completed(TrinityCraftingBatch batch, List<ItemStack> unitOutputs) {
            ArrayList<TrinityItemAmount> countedOutputs = new ArrayList<>();
            for (ItemStack output : unitOutputs) {
                if (!output.isEmpty()) {
                    countedOutputs.addAll(TrinityItemAmount.multiply(output, batch.count()));
                }
            }
            return new BatchExecutionResult(true, countedOutputs);
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
         * @return immutable counted outputs to append when execution completed
         */
        public List<TrinityItemAmount> countedOutputs() {
            return this.countedOutputs;
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
     * @return monotonically increasing occupied-pattern directory revision for catalog cache invalidation
     */
    long revision();

    /**
     * Returns the core-local publishable pattern cache used by an aggregate host catalog.
     *
     * @return stable immutable snapshot whose entries are ordered by physical slot
     */
    PatternCacheSnapshot patternCacheSnapshot();

    /**
     * Reads one stable occupied-directory entry without scanning the core snapshot.
     *
     * @param slot physical slot index
     * @return cached entry, or {@code null} when no encoded pattern is installed
     */
    @Nullable
    CachedPattern cachedPattern(int slot);

    /**
     * Returns the sparse occupied-slot index used by reload and host cache maintenance.
     *
     * @return immutable ascending slot snapshot
     */
    List<Integer> occupiedPatternSlots();

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
     * Synchronizes externally invalidated recipe caches at the owner-controlled reload boundary.
     *
     * <p>
     * Pure logical cores use their explicit refresh lifecycle. World-backed cores compare the global data-reload epoch
     * here during host cache maintenance; dispatch and execution use {@link #runtimeBindingsCurrent()} as an O(1)
     * readiness guard.
     */
    void ensurePatternCachesCurrent();

    /**
     * Reports whether this core has rebound its runtime recipes for the latest external reload generation.
     *
     * <p>
     * Pure logical cores are always current. World-backed implementations override this as an O(1) guard so a
     * provider can pause dispatch between reload completion and the host's next cache flush without decoding in the
     * dispatch path.
     * </p>
     *
     * @return whether cached runtime bindings may currently accept routed input
     */
    default boolean runtimeBindingsCurrent() {
        return true;
    }

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
     * Atomically appends one counted input signature through a cache token captured by the host route binding.
     *
     * @param route                          exact host/core/slot destination selected by the crafting plan
     * @param expectedPattern                stable occupied-slot token that must still own the destination
     * @param expectedRuntimeBindingRevision exact runtime recipe generation used to materialize the inputs
     * @param inputs                         immutable nine-slot input prototype selected from the actual AE inputs
     * @param queuedTick                     current server tick; execution starts only on a later tick
     * @param count                          positive logical craft count represented by the input prototype
     * @return true when the cache token and definition still matched atomically and the complete group was queued
     */
    boolean enqueueBatch(PatternRoute route,
                         CachedPattern expectedPattern,
                         long expectedRuntimeBindingRevision,
                         TrinityCraftingBatch.InputSignature inputs,
                         long queuedTick,
                         long count);

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
     * Executes every FIFO group eligible in one exact physical slot. A definition mismatch or paused executor leaves
     * the current head asleep without touching other slots.
     *
     * @param slot        exact physical slot to execute
     * @param currentTick current server tick
     * @param executor    crafting callback
     * @return number of completed queue groups
     */
    int executeReadyBatches(int slot, long currentTick, BatchExecutor executor);

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
     * Tests one exact physical slot against the core's host work index without copying the full sparse slot set.
     *
     * @param hostId stable host identity whose work is requested
     * @param slot   exact physical slot to inspect
     * @return whether queued input or pending output in that slot belongs to the host
     */
    boolean isSlotWorking(UUID hostId, int slot);

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
     * Reports whether this core has retained items for the supplied host that were committed for refund but have not
     * yet reached an external destination.
     *
     * <p>
     * This deliberately does not make the core working: the source queue was already cleared into the durable refund
     * ledger and cannot execute again.
     * </p>
     *
     * @param hostId stable host identity whose pending refund ledger is requested
     * @return whether a later retained-item refund action must retry delivery for this host
     */
    default boolean hasPendingRefund(UUID hostId) {
        return false;
    }

    /**
     * Captures the installed patterns only when no queued input or pending output exists anywhere in this core.
     *
     * <p>
     * The returned transaction isolates pattern removal from retained-work refunds. Callers must commit every
     * participating core before delivering the captured stacks, and roll back all committed captures when another
     * core becomes stale.
     * </p>
     *
     * @return a prepared pattern refund, or a rejected transaction when retained work blocks the operation
     */
    PatternRefundTransaction preparePatternRefund();

    /**
     * Reversible prepared removal of installed patterns from one physical P core.
     */
    interface PatternRefundTransaction {

        /**
         * @return immutable slot-ordered installed pattern stacks captured by this transaction
         */
        List<ItemStack> patterns();

        /**
         * @return whether every slot was empty when this transaction was prepared
         */
        boolean isEmpty();

        /**
         * @return whether queued inputs or pending outputs blocked this transaction before it could be prepared
         */
        boolean isBlockedByWork();

        /**
         * Clears exactly the captured patterns while the core and every slot revision still match.
         */
        boolean commit();

        /**
         * Finalizes a committed capture after pattern delivery begins.
         *
         * @param undeliveredPatterns exact suffix not accepted by any external destination
         */
        void complete(List<ItemStack> undeliveredPatterns);

        /**
         * Restores committed patterns, or releases an uncommitted capture.
         */
        void rollback();
    }

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
         * item, because restoring the captured queue could duplicate that item. The exact undelivered suffix remains
         * in the core's durable refund ledger for a later retry.
         * </p>
         *
         * @param undeliveredItems exact suffix not accepted by any external destination
         */
        void complete(List<TrinityItemAmount> undeliveredItems);

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
     * Hydrates a pristine core from persisted state without emitting per-slot change events.
     *
     * <p>
     * Initial block-entity loading has no mounted host to update. The implementation therefore parses and applies all
     * sparse persisted slots atomically, builds one final directory snapshot and one set of work indexes, and leaves
     * later catalog construction to consume that final state directly.
     * </p>
     *
     * @param data       source tag
     * @param registries registry lookup used for item components
     */
    void hydrateFromTag(CompoundTag data, HolderLookup.Provider registries);

    /**
     * Atomically restores complete movable core state and emits precise changes for an already-live core.
     *
     * @param data       source tag
     * @param registries registry lookup used for item components
     */
    void readFromTag(CompoundTag data, HolderLookup.Provider registries);
}
