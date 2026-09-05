package com.fish_dan_.data_energistics.common.trinity.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Aggregates every pattern processing core mounted in one Trinity crafting structure.
 *
 * <p>
 * The catalog owns stable route publication and dispatch validation so the AE provider does not depend on concrete
 * block entity types.
 * </p>
 */
public interface TrinityPatternCatalog {

    /**
     * Final outcome of one host-wide installed-pattern refund transaction.
     */
    enum PatternRefundResult {

        /**
         * Every captured installed pattern was cleared and delivered.
         */
        COMPLETED,
        /**
         * The active aggregate contained no installed patterns.
         */
        NO_PATTERNS,
        /**
         * At least one mounted core retained queued input or pending output.
         */
        BLOCKED_BY_WORK,
        /**
         * The captured layout, mounted-core identity, or exact slot state became stale before completion.
         */
        STALE,
        /**
         * The destination rejected the aggregate during its side-effect-free preparation.
         */
        DELIVERY_REJECTED,
        /**
         * The destination failed while preparing or delivering the aggregate.
         */
        DELIVERY_FAILED,
        /**
         * An unexpected core, catalog, or publication failure aborted the transaction.
         */
        INTERNAL_ERROR;

        /**
         * @return whether the transaction completed and delivery returned normally
         */
        public boolean completed() {
            return this == COMPLETED;
        }
    }

    /**
     * Reversible, incrementally prepared refund of every installed pattern in one captured catalog layout.
     *
     * <p>
     * No pattern leaves the catalog during {@link #prepareNext()}. The caller may therefore yield between cores
     * and either cancel the preparation or perform one final atomic commit.
     * </p>
     */
    interface PatternRefundPreparation {

        /** Returns how many mounted cores must be inspected. */
        int totalUnits();

        /** Returns how many mounted cores have finished preparation. */
        int completedUnits();

        /** Returns whether preparation reached a commit-ready or terminal state. */
        boolean isPrepared();

        /**
         * Prepares one mounted core. The call is invalid after preparation reached a terminal state.
         */
        void prepareNext();

        /**
         * Returns the preparation-only terminal result, or {@code null} while the transaction is commit-ready.
         */
        @Nullable
        PatternRefundResult terminalResult();

        /** Performs final revision validation, all core commits, publication, and delivery without yielding. */
        PatternRefundResult commit(TrinityPatternRefundDelivery delivery);

        /** Rolls back every prepared core transaction. Repeated cancellation has no effect. */
        void cancel();
    }

    /**
     * Describes one core found during a host structure scan.
     *
     * @param position      world position used for deterministic ordering and diagnostics
     * @param blockCapacity capacity declared by the core block
     * @param core          persistent core state supplied by its block entity
     */
    record CoreMount(BlockPos position, int blockCapacity, TrinityPatternCore core) {

        /**
         * Validates a scanned mount before it can enter a catalog.
         */
        public CoreMount {
            if (blockCapacity <= 0) {
                throw new IllegalArgumentException("A Trinity pattern core mount requires a positive block capacity");
            }
            position = position.immutable();
        }
    }

    /**
     * Defines the stable contiguous global-index range owned by one mounted core.
     *
     * @param mount                    immutable scan metadata and core reference
     * @param coreId                   core UUID captured when this layout was formed
     * @param firstGlobalIndex         inclusive first global pattern index
     * @param lastGlobalIndexExclusive exclusive last global pattern index
     */
    record CoreRange(CoreMount mount,
                     UUID coreId,
                     int firstGlobalIndex,
                     int lastGlobalIndexExclusive) {

        /**
         * Ensures the published range is positive and exactly covers its declared core capacity.
         */
        public CoreRange {
            if (!coreId.equals(mount.core().coreId()) ||
                    firstGlobalIndex < 0 || lastGlobalIndexExclusive <= firstGlobalIndex ||
                    lastGlobalIndexExclusive - firstGlobalIndex != mount.blockCapacity()) {
                throw new IllegalArgumentException(
                        "A Trinity pattern core range must capture its core identity and exact capacity");
            }
        }

        /**
         * @param globalIndex global pattern index to inspect
         * @return whether this core owns the supplied index
         */
        public boolean contains(int globalIndex) {
            return globalIndex >= this.firstGlobalIndex && globalIndex < this.lastGlobalIndexExclusive;
        }
    }

    /**
     * Immutable topology snapshot shared by host pages and virtual terminal partitions.
     *
     * @param revision  monotonically increasing topology generation
     * @param active    whether callers may resolve slots from this snapshot
     * @param slotCount total number of globally indexed pattern slots
     * @param mounts    coordinate-sorted public core mounts
     * @param ranges    coordinate-sorted contiguous global-index ranges
     */
    record LayoutSnapshot(long revision,
                          boolean active,
                          int slotCount,
                          List<CoreMount> mounts,
                          List<CoreRange> ranges) {

        /**
         * Copies collection components and verifies that active ranges form one gap-free global index space.
         */
        public LayoutSnapshot {
            if (revision < 0L || slotCount < 0) {
                throw new IllegalArgumentException("A Trinity pattern layout requires non-negative revision and size");
            }
            mounts = List.copyOf(mounts);
            ranges = List.copyOf(ranges);
            if (!active) {
                if (slotCount != 0 || !mounts.isEmpty() || !ranges.isEmpty()) {
                    throw new IllegalArgumentException("An inactive Trinity pattern layout must not expose cores");
                }
            } else {
                if (mounts.size() != ranges.size()) {
                    throw new IllegalArgumentException("A Trinity pattern layout requires one range per core mount");
                }
                int expectedFirstIndex = 0;
                for (int index = 0; index < ranges.size(); index++) {
                    CoreRange range = ranges.get(index);
                    if (!range.mount().equals(mounts.get(index)) || range.firstGlobalIndex() != expectedFirstIndex) {
                        throw new IllegalArgumentException("A Trinity pattern layout must contain contiguous ordered ranges");
                    }
                    expectedFirstIndex = range.lastGlobalIndexExclusive();
                }
                if (expectedFirstIndex != slotCount) {
                    throw new IllegalArgumentException("A Trinity pattern layout range total does not match its size");
                }
            }
        }
    }

    /**
     * Resolves one global index to the exact core identity captured by a layout revision.
     *
     * @param layoutRevision layout generation that authorized the lookup
     * @param globalIndex    stable global pattern index inside that generation
     * @param range          captured core range owning the index
     * @param coreSlot       physical slot inside the owning core
     */
    record GlobalSlot(long layoutRevision, int globalIndex, CoreRange range, int coreSlot) {

        /**
         * Ensures the resolved physical and global indexes identify the same slot.
         */
        public GlobalSlot {
            if (!range.contains(globalIndex) ||
                    coreSlot != globalIndex - range.firstGlobalIndex()) {
                throw new IllegalArgumentException("A resolved Trinity pattern slot must belong to its core range");
            }
        }

        /**
         * @return exact mounted core that owns this resolved slot
         */
        public TrinityPatternCore core() {
            return this.range.mount().core();
        }
    }

    /**
     * Immutable host-runtime reference for one physical slot that currently owns queued input or pending output.
     *
     * <p>
     * Entries are exposed in ascending {@code globalIndex} order. The referenced slot object remains stable for the
     * lifetime of its physical core and lets the host execute work without scanning mounted-core capacities.
     * </p>
     *
     * @param globalIndex stable aggregate index from the current layout
     * @param mount       exact mounted core and world position captured by the layout
     * @param route       host/core/physical-slot identity used by queued work and pending output
     * @param slot        exact stable physical slot object
     */
    record ActiveSlot(int globalIndex, CoreMount mount, PatternRoute route, TrinityPatternSlot slot) {

        /**
         * Validates that every identity component describes the same mounted physical slot.
         */
        public ActiveSlot {
            if (globalIndex < 0 || route.slot() != slot.index() || route.slot() >= mount.blockCapacity() ||
                    !route.coreId().equals(mount.core().coreId())) {
                throw new IllegalArgumentException("An active Trinity slot must match its mounted core and route");
            }
        }

        /**
         * @return exact mounted core that owns this active slot
         */
        public TrinityPatternCore core() {
            return this.mount.core();
        }

        /**
         * @return physical slot index inside the mounted core
         */
        public int coreSlot() {
            return this.route.slot();
        }
    }

    /**
     * Reports whether a scanned structure can publish patterns.
     *
     * @param valid           true when every scanned core passed validation
     * @param changed         true when the mounted cores or their published patterns changed
     * @param failurePosition offending core position, or {@code null} after a successful rebuild
     * @param failureReason   human-readable diagnostic, empty after a successful rebuild
     */
    record RebuildResult(boolean valid, boolean changed, @Nullable BlockPos failurePosition, String failureReason) {

        /**
         * Validates that success and failure diagnostics cannot contradict each other.
         */
        public RebuildResult {
            if (valid && (failurePosition != null || !failureReason.isEmpty())) {
                throw new IllegalArgumentException("A successful Trinity pattern catalog rebuild cannot have a failure");
            }
            if (!valid && failureReason.isBlank()) {
                throw new IllegalArgumentException("A failed Trinity pattern catalog rebuild requires a diagnostic");
            }
            if (failurePosition != null) {
                failurePosition = failurePosition.immutable();
            }
        }
    }

    /**
     * @return stable UUID of the host that owns every route in this catalog
     */
    UUID hostId();

    /**
     * @return current immutable public layout, inactive with no mounts while the structure is unavailable
     */
    LayoutSnapshot layoutSnapshot();

    /**
     * Returns the generation of the pattern set currently visible to AE2.
     *
     * <p>
     * Unlike the layout revision, this value also advances for a pattern mutation inside an unchanged core layout.
     * Callers use it to coalesce provider updates without depending on collection object identity.
     * </p>
     *
     * @return monotonically increasing AE pattern publication generation
     */
    long publicationRevision();

    /**
     * Verifies that a previously captured mount still belongs to the exact public layout generation.
     *
     * @param expectedRevision layout generation that supplied the mount
     * @param mount            mount identity and scan metadata to verify
     * @return true only while the layout, core instance, UUID, position, and capacities still match
     */
    boolean isMountCurrent(long expectedRevision, CoreMount mount);

    /**
     * Resolves a global slot only while the caller's layout revision is still authoritative.
     *
     * @param expectedRevision layout revision captured by the caller
     * @param globalIndex      global pattern index to resolve
     * @return exact resolved slot, or {@code null} when the layout or index is stale
     */
    @Nullable
    GlobalSlot resolveGlobalSlot(long expectedRevision, int globalIndex);

    /**
     * Resolves one physical core slot through the captured mount without scanning the aggregate ranges.
     *
     * <p>
     * Terminal partitions use this path for every live inventory read, so lookup cost must not grow with the number
     * of mounted cores.
     *
     * @param expectedRevision layout revision captured by the caller
     * @param mount            exact mount captured from that layout
     * @param coreSlot         physical slot inside the mounted core
     * @return exact resolved slot, or {@code null} when the layout, mount, or slot is stale
     */
    @Nullable
    GlobalSlot resolveCoreSlot(long expectedRevision, CoreMount mount, int coreSlot);

    /**
     * Tests exact mounted-core object identity without scanning the public mount list.
     *
     * @param core core instance to verify
     * @return whether the current active layout owns that exact instance
     */
    boolean isCoreMounted(TrinityPatternCore core);

    /**
     * Replaces the mounted-core snapshot after validating capacities, positions, and persistent core identities.
     *
     * @param mounts cores found in the crafting child structure
     * @return rebuild status and any formation-blocking diagnostic
     */
    RebuildResult rebuild(List<CoreMount> mounts);

    /**
     * Applies all core-local catalog notifications accumulated since the previous host tick.
     *
     * <p>
     * This method reads only cores explicitly marked dirty and rebuilds the immutable aggregate at most once.
     * </p>
     *
     * @return true when the published AE pattern list changed
     */
    boolean refreshChangedPatterns();

    /**
     * Records one exact mounted-core mutation. Catalog changes are deferred to the next flush; work changes update
     * only the supplied physical slot's active binding.
     *
     * @param core   exact mounted core that emitted the change
     * @param change physical slot and changed state surface
     */
    void onCoreChanged(TrinityPatternCore core, TrinityPatternSlot.Change change);

    /**
     * @return immutable routed AE patterns in stable core-position and slot order
     */
    List<IPatternDetails> getAvailablePatterns();

    /**
     * @return immutable work-bearing slot references in ascending global-index order
     */
    List<ActiveSlot> activeSlots();

    /**
     * Validates and enqueues one routed AE dispatch without partially consuming its input counters.
     *
     * @param patternDetails routed pattern selected by AE2
     * @param inputHolder    extracted pattern inputs
     * @param queuedTick     server tick on which the dispatch arrived
     * @return true only when a complete 3 by 3 snapshot was enqueued in the exact routed slot
     */
    default boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, long queuedTick) {
        return pushPattern(patternDetails, inputHolder, queuedTick, 1L);
    }

    /**
     * Validates and enqueues one homogeneous counted routed dispatch without partially consuming its input counters.
     *
     * @param patternDetails routed pattern selected by AE2
     * @param inputHolder    one exact per-craft input prototype
     * @param queuedTick     server tick on which the dispatch arrived
     * @param count          positive number of identical logical crafts in the holder
     * @return true only when one exact 3 by 3 prototype and its complete count were enqueued
     */
    boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, long queuedTick, long count);

    /**
     * @return immutable mounted cores in stable world-position order
     */
    List<CoreMount> mountedCores();

    /**
     * @return whether any mounted core owns queued inputs or pending outputs
     */
    boolean hasWork();

    /**
     * @return whether the active aggregate contains queued input or pending output eligible for return
     */
    boolean hasRefundableState();

    /**
     * Atomically returns every installed encoded pattern from the current active aggregate.
     *
     * <p>
     * This transaction is intentionally separate from {@link #tryRefundAll(TrinityRefundDelivery)}. It refuses to
     * clear any pattern while any mounted core retains queued input or pending output, and never converts patterns
     * into {@link TrinityItemAmount} entries.
     * </p>
     *
     * @param delivery two-phase AE, inventory, and world-drop destination for the complete installed-pattern aggregate
     * @return precise final outcome, including no-op, stale-state, and delivery failure cases
     */
    default PatternRefundResult tryRefundPatterns(TrinityPatternRefundDelivery delivery) {
        PatternRefundPreparation preparation = beginPatternRefund();
        while (!preparation.isPrepared()) {
            preparation.prepareNext();
        }
        PatternRefundResult terminal = preparation.terminalResult();
        return terminal == null ? preparation.commit(delivery) : terminal;
    }

    /** Captures one reversible pattern-refund preparation owned by the caller until commit or cancel. */
    PatternRefundPreparation beginPatternRefund();

    /**
     * Atomically returns every queued input and pending output from every core in the current active aggregate.
     *
     * <p>
     * Delivery preparation runs before any core mutation. Delivery itself runs exactly once after every reversible
     * core transaction committed, so a core failure cannot cause an external partial delivery.
     * </p>
     *
     * @param delivery two-phase external destination for the complete aggregate
     * @return true when all currently mounted queued state was cleared and delivery was invoked
     */
    boolean tryRefundAll(TrinityRefundDelivery delivery);

    /**
     * Invalidates every public mount and slot reference while retaining cores solely for pending-work detection.
     */
    void invalidateLayout();

    /**
     * Permanently releases public layout, retained work references, and pattern caches.
     */
    void clear();
}
