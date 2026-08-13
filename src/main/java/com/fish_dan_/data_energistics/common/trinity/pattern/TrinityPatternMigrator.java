package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.util.StableDigest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.helpers.patternprovider.PatternContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Executes one server-thread, snapshot-based, best-effort pattern migration into an active Trinity catalog.
 */
public final class TrinityPatternMigrator {

    private TrinityPatternMigrator() {}

    /**
     * Captures ME storage before provider inventories, then processes each immutable snapshot in that same order.
     */
    public static TrinityPatternMigrationResult migrate(Level level,
                                                        IGrid grid,
                                                        IActionSource actionSource,
                                                        TrinityPatternCatalog catalog) {
        Job job = begin(level, grid, actionSource, catalog);
        while (!job.isDone()) {
            job.advance(Integer.MAX_VALUE, Long.MAX_VALUE);
        }
        return job.result();
    }

    /** Starts a resumable migration that performs bounded work on subsequent server ticks. */
    public static Job begin(Level level,
                            IGrid grid,
                            IActionSource actionSource,
                            TrinityPatternCatalog catalog) {
        TrinityPatternCatalog.LayoutSnapshot layout = catalog.layoutSnapshot();
        if (!layout.active()) {
            return Job.targetUnavailable();
        }
        MEStorage storage = grid.getStorageService().getInventory();
        return new Job(new Batch(level, grid, actionSource, catalog, layout, storage));
    }

    /** Coarse operation stage exposed to the Data Core maintenance task. */
    public enum Phase {
        SCANNING,
        STORAGE,
        PATTERN_CONTAINERS,
        COMPLETE,
        CANCELLED
    }

    /**
     * Server-thread cursor over one immutable migration snapshot.
     *
     * <p>
     * Each call respects both the work-unit and wall-clock boundary after taking the source collections' immutable
     * membership snapshots. Installed Trinity slots, captured AE keys, and source inventory slots are then inspected
     * incrementally.
     * </p>
     */
    public static final class Job {

        private final @Nullable Batch batch;
        private final List<StorageCandidate> storageCandidates = new ArrayList<>();
        private final List<ContainerBuilder> containerBuilders = new ArrayList<>();
        private List<ContainerSnapshot> containers = List.of();
        private CursorStage cursorStage;
        private int installedRangeIndex;
        private int installedSlot;
        private List<StorageKeySnapshot> storageKeys = List.of();
        private int storageKeyIndex;
        private int captureContainerIndex;
        private int captureContainerSlot;
        private int storageIndex;
        private int containerIndex;
        private int containerSlotIndex;
        private long scanUnits;
        private long completedUnits;
        private long totalUnits;
        private TrinityPatternMigrationResult result;

        private Job(Batch batch) {
            this.batch = batch;
            this.cursorStage = CursorStage.CAPTURE_INSTALLED;
            this.result = batch.result();
            this.storageKeys = snapshotStorage(batch);
            captureContainerSources(batch);
            this.scanUnits = scanWorkUnits(batch.layout, this.storageKeys, this.containerBuilders);
            this.totalUnits = this.scanUnits;
            if (this.scanUnits == 0L) {
                this.cursorStage = CursorStage.COMPLETE;
            }
        }

        private Job() {
            this.batch = null;
            this.cursorStage = CursorStage.COMPLETE;
            this.result = TrinityPatternMigrationResult.targetUnavailable();
        }

        private static Job targetUnavailable() {
            return new Job();
        }

        /** Advances at most {@code maximumUnits} or until the monotonic deadline is reached. */
        public void advance(int maximumUnits, long deadlineNanos) {
            if (maximumUnits <= 0) {
                throw new IllegalArgumentException("Pattern migration requires a positive work-unit budget");
            }
            int consumed = 0;
            while (!isDone() && consumed < maximumUnits && System.nanoTime() < deadlineNanos) {
                if (advanceOne()) {
                    consumed++;
                }
            }
        }

        /** Stops future work. Already committed best-effort candidates remain committed. */
        public void cancel() {
            if (!isDone()) {
                this.result = requireBatch().result();
                this.cursorStage = CursorStage.CANCELLED;
            }
        }

        public boolean isDone() {
            return this.cursorStage == CursorStage.COMPLETE || this.cursorStage == CursorStage.CANCELLED;
        }

        public Phase phase() {
            return switch (this.cursorStage) {
                case CAPTURE_INSTALLED, CAPTURE_STORAGE, CAPTURE_CONTAINERS -> Phase.SCANNING;
                case PROCESS_STORAGE -> Phase.STORAGE;
                case PROCESS_CONTAINERS -> Phase.PATTERN_CONTAINERS;
                case COMPLETE -> Phase.COMPLETE;
                case CANCELLED -> Phase.CANCELLED;
            };
        }

        public long completedUnits() {
            return this.completedUnits;
        }

        public long totalUnits() {
            return this.totalUnits;
        }

        public TrinityPatternMigrationResult result() {
            if (!isDone()) {
                throw new IllegalStateException("Pattern migration result is unavailable before completion");
            }
            return this.result;
        }

        private boolean advanceOne() {
            return switch (this.cursorStage) {
                case CAPTURE_INSTALLED -> captureInstalled();
                case CAPTURE_STORAGE -> captureStorage();
                case CAPTURE_CONTAINERS -> captureContainerSlot();
                case PROCESS_STORAGE -> processStorage();
                case PROCESS_CONTAINERS -> processContainer();
                case COMPLETE, CANCELLED -> false;
            };
        }

        private boolean captureInstalled() {
            Batch current = requireBatch();
            List<TrinityPatternCatalog.CoreRange> ranges = current.layout.ranges();
            if (this.installedRangeIndex >= ranges.size()) {
                this.cursorStage = CursorStage.CAPTURE_STORAGE;
                return false;
            }
            TrinityPatternCatalog.CoreRange range = ranges.get(this.installedRangeIndex);
            current.captureInstalledIdentity(range, this.installedSlot++);
            this.completedUnits++;
            if (this.installedSlot >= range.mount().blockCapacity()) {
                this.installedRangeIndex++;
                this.installedSlot = 0;
            }
            return true;
        }

        private boolean captureStorage() {
            if (this.storageKeyIndex < this.storageKeys.size()) {
                StorageKeySnapshot snapshot = this.storageKeys.get(this.storageKeyIndex++);
                AEKey key = snapshot.key();
                long amount = snapshot.amount();
                if (amount > 0L && key instanceof AEItemKey itemKey &&
                        PatternDetailsHelper.isEncodedPattern(itemKey.getReadOnlyStack())) {
                    this.storageCandidates.add(new StorageCandidate(itemKey, amount));
                }
                this.completedUnits++;
                return true;
            }
            this.cursorStage = CursorStage.CAPTURE_CONTAINERS;
            return false;
        }

        private void captureContainerSources(Batch current) {
            ArrayList<Class<?>> classes = new ArrayList<>();
            for (Class<?> machineClass : current.grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    classes.add(machineClass);
                }
            }
            classes.sort(Comparator.comparing(Class::getName));
            Set<PatternContainer> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            int ordinal = 0;
            for (Class<?> machineClass : classes) {
                for (Object machine : current.grid.getActiveMachines(machineClass)) {
                    if (!(machine instanceof PatternContainer container) ||
                            container instanceof TrinityPatternTerminalPartition || !identities.add(container)) {
                        continue;
                    }
                    InternalInventory inventory = container.getTerminalPatternInventory();
                    String identityDigest = current.resolveIdentityDigest(container);
                    if (identityDigest == null) {
                        current.fallbackIdentitySources++;
                    }
                    this.containerBuilders.add(new ContainerBuilder(
                            container,
                            inventory,
                            machineClass.getName(),
                            container.getTerminalSortOrder(),
                            container.getClass().getName(),
                            identityDigest,
                            ordinal++));
                }
            }
            this.containerBuilders.sort(ContainerBuilder.ORDER);
        }

        private static long scanWorkUnits(TrinityPatternCatalog.LayoutSnapshot layout,
                                          List<StorageKeySnapshot> storageKeys,
                                          List<ContainerBuilder> containerBuilders) {
            long total = layout.slotCount();
            total = Math.addExact(total, storageKeys.size());
            for (ContainerBuilder builder : containerBuilders) {
                total = Math.addExact(total, builder.slotCount);
            }
            return total;
        }

        private static List<StorageKeySnapshot> snapshotStorage(Batch batch) {
            ArrayList<StorageKeySnapshot> snapshots = new ArrayList<>();
            for (var entry : batch.storage.getAvailableStacks()) {
                if (entry.getLongValue() > 0L) {
                    snapshots.add(new StorageKeySnapshot(entry.getKey(), entry.getLongValue()));
                }
            }
            snapshots.sort(Comparator.comparing(snapshot -> stableKeyDigest(snapshot.key(), batch.level.registryAccess())));
            return List.copyOf(snapshots);
        }

        private boolean captureContainerSlot() {
            if (this.captureContainerIndex >= this.containerBuilders.size()) {
                ArrayList<ContainerSnapshot> snapshots = new ArrayList<>(this.containerBuilders.size());
                for (ContainerBuilder builder : this.containerBuilders) {
                    snapshots.add(builder.snapshot());
                }
                this.containers = List.copyOf(snapshots);
                long candidateUnits = this.storageCandidates.size();
                for (ContainerSnapshot container : this.containers) {
                    candidateUnits = Math.addExact(candidateUnits, container.slots().size());
                }
                if (candidateUnits == 0L) {
                    complete();
                    return false;
                }
                this.totalUnits = Math.addExact(this.scanUnits, candidateUnits);
                this.cursorStage = CursorStage.PROCESS_STORAGE;
                return false;
            }
            ContainerBuilder builder = this.containerBuilders.get(this.captureContainerIndex);
            if (this.captureContainerSlot >= builder.slotCount) {
                this.captureContainerIndex++;
                this.captureContainerSlot = 0;
                return false;
            }
            int slot = this.captureContainerSlot++;
            this.completedUnits++;
            try {
                ItemStack stack = builder.inventory.getStackInSlot(slot);
                if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                    builder.slots.add(new SlotSnapshot(slot, stack.copy()));
                }
            } catch (RuntimeException failure) {
                requireBatch().sourceFailures++;
                builder.quarantined = true;
                long remainingSlots = builder.slotCount - this.captureContainerSlot;
                this.completedUnits = Math.addExact(this.completedUnits, remainingSlots);
                this.captureContainerSlot = builder.slotCount;
                Data_Energistics.LOGGER.error(
                        "Could not capture pattern migration source class={} ordinal={} slot={}",
                        builder.className,
                        builder.ordinal,
                        slot,
                        failure);
            }
            return true;
        }

        private boolean processStorage() {
            Batch current = requireBatch();
            if (this.storageIndex >= this.storageCandidates.size() || current.targetAborted) {
                this.cursorStage = current.targetAborted ? CursorStage.COMPLETE : CursorStage.PROCESS_CONTAINERS;
                if (current.targetAborted) {
                    complete();
                }
                return false;
            }
            boolean continueStorage = current.processStorageCandidate(this.storageCandidates.get(this.storageIndex++));
            this.completedUnits++;
            if (!continueStorage) {
                this.completedUnits += this.storageCandidates.size() - this.storageIndex;
                this.storageIndex = this.storageCandidates.size();
            }
            return true;
        }

        private boolean processContainer() {
            Batch current = requireBatch();
            if (current.targetAborted || this.containerIndex >= this.containers.size()) {
                complete();
                return false;
            }
            ContainerSnapshot container = this.containers.get(this.containerIndex);
            if (container.quarantined() || this.containerSlotIndex >= container.slots().size()) {
                if (container.quarantined()) {
                    current.quarantinedSources++;
                    this.completedUnits += container.slots().size() - this.containerSlotIndex;
                }
                this.containerIndex++;
                this.containerSlotIndex = 0;
                return false;
            }
            SlotSnapshot slot = container.slots().get(this.containerSlotIndex++);
            boolean continueSource = current.processContainerCandidate(container, slot);
            this.completedUnits++;
            if (!continueSource) {
                current.quarantinedSources++;
                this.completedUnits += container.slots().size() - this.containerSlotIndex;
                this.containerIndex++;
                this.containerSlotIndex = 0;
            }
            return true;
        }

        private void complete() {
            this.result = requireBatch().result();
            this.completedUnits = this.totalUnits;
            this.cursorStage = CursorStage.COMPLETE;
        }

        private Batch requireBatch() {
            if (this.batch == null) {
                throw new IllegalStateException("Pattern migration target is unavailable");
            }
            return this.batch;
        }
    }

    private enum CursorStage {
        CAPTURE_INSTALLED,
        CAPTURE_STORAGE,
        CAPTURE_CONTAINERS,
        PROCESS_STORAGE,
        PROCESS_CONTAINERS,
        COMPLETE,
        CANCELLED
    }

    private static final class Batch {

        private final Level level;
        private final IGrid grid;
        private final IActionSource actionSource;
        private final TrinityPatternCatalog catalog;
        private final TrinityPatternCatalog.LayoutSnapshot layout;
        private final MEStorage storage;
        private final Set<TrinityPatternSemanticIdentity> seen = new HashSet<>();
        private int movedFromStorage;
        private int movedFromContainers;
        private int invalidRefunded;
        private int duplicateRefunded;
        private int unsupportedKept;
        private int storageInvalidKept;
        private int storageUnsupportedKept;
        private int storageDuplicateKept;
        private int storageSourceUncertain;
        private int meBlocked;
        private int capacitySkipped;
        private int sourceFailures;
        private int quarantinedSources;
        private int fallbackIdentitySources;
        private boolean targetAborted;

        private Batch(Level level,
                      IGrid grid,
                      IActionSource actionSource,
                      TrinityPatternCatalog catalog,
                      TrinityPatternCatalog.LayoutSnapshot layout,
                      MEStorage storage) {
            this.level = level;
            this.grid = grid;
            this.actionSource = actionSource;
            this.catalog = catalog;
            this.layout = layout;
            this.storage = storage;
        }

        @Nullable
        private String resolveIdentityDigest(PatternContainer container) {
            try {
                return PatternProviderRuntimeBindings.resolve(container)
                        .map(binding -> binding.identity().digest())
                        .orElse(null);
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.warn(
                        "Pattern migration could not resolve stable runtime identity for provider class {}; using request-local order",
                        container.getClass().getName(),
                        failure);
                return null;
            }
        }

        private void captureInstalledIdentity(TrinityPatternCatalog.CoreRange range, int slot) {
            ItemStack stack = range.mount().core().pattern(slot);
            Decoded decoded = decode(stack);
            if (decoded.identity() != null) {
                this.seen.add(decoded.identity());
            }
        }

        private boolean processStorageCandidate(StorageCandidate candidate) {
            if (this.targetAborted) {
                return false;
            }
            Decoded decoded = decode(candidate.key().toStack());
            if (readStorageAmount(candidate.key()) != candidate.capturedAmount()) {
                this.storageSourceUncertain++;
                return false;
            }
            if (decoded.identity() == null) {
                this.storageInvalidKept++;
                return true;
            }
            if (!supports(decoded.stack())) {
                this.storageUnsupportedKept++;
                return true;
            }
            if (this.seen.contains(decoded.identity())) {
                this.storageDuplicateKept++;
                return true;
            }
            TargetSlot target = nextEmptyTarget();
            if (target == null) {
                if (!this.targetAborted) {
                    this.capacitySkipped++;
                }
                return true;
            }
            long simulated;
            try {
                simulated = StorageHelper.poweredExtraction(
                        this.grid.getEnergyService(), this.storage, candidate.key(), 1L,
                        this.actionSource, Actionable.SIMULATE);
            } catch (RuntimeException failure) {
                logStorageFailure(candidate.key(), "simulation failed", failure);
                this.storageSourceUncertain++;
                return false;
            }
            if (simulated != 1L || !install(target, decoded.stack())) {
                this.meBlocked++;
                return true;
            }
            long before = candidate.capturedAmount();
            long extracted;
            try {
                extracted = StorageHelper.poweredExtraction(
                        this.grid.getEnergyService(), this.storage, candidate.key(), 1L,
                        this.actionSource, Actionable.MODULATE);
            } catch (RuntimeException failure) {
                logStorageFailure(candidate.key(), "extraction failed", failure);
                extracted = -1L;
            }
            if (extracted == 1L) {
                this.movedFromStorage++;
                this.seen.add(decoded.identity());
                return true;
            }
            long after = readStorageAmount(candidate.key());
            if (after == before - 1L) {
                this.movedFromStorage++;
                this.seen.add(decoded.identity());
            } else if (after == before && rollbackTarget(target, decoded.stack())) {
                this.meBlocked++;
            } else if (after == before) {
                abortTarget("ME extraction did not remove the installed encoded pattern");
            } else {
                this.storageSourceUncertain++;
                Data_Energistics.LOGGER.error(
                        "Stopped AE storage pattern migration for host {} after uncertain key count before={} after={} key={}",
                        this.catalog.hostId(), before, after,
                        stableKeyDigest(candidate.key(), this.level.registryAccess()));
                return false;
            }
            return !this.targetAborted;
        }

        private boolean processContainerCandidate(ContainerSnapshot container, SlotSnapshot slot) {
            if (this.targetAborted) {
                return false;
            }
            if (container.container().getGrid() != this.grid ||
                    !matchesSnapshot(container.inventory().getStackInSlot(slot.slot()), slot.stack())) {
                this.sourceFailures++;
                return true;
            }
            if (!PatternDetailsHelper.isEncodedPattern(slot.stack())) {
                return true;
            }
            Decoded decoded = decode(slot.stack());
            if (decoded.identity() == null) {
                return refundContainerPattern(container, slot, false);
            } else if (!supports(decoded.stack())) {
                this.unsupportedKept++;
                return true;
            } else if (this.seen.contains(decoded.identity())) {
                return refundContainerPattern(container, slot, true);
            } else {
                return moveContainerPattern(container, slot, decoded);
            }
        }

        private boolean refundContainerPattern(ContainerSnapshot container, SlotSnapshot slot, boolean duplicate) {
            AEItemKey key = AEItemKey.of(slot.stack());
            if (key == null) {
                this.sourceFailures++;
                return true;
            }
            long before = readStorageAmount(key);
            if (before < 0L) {
                this.sourceFailures++;
                return false;
            }
            long simulated;
            try {
                simulated = StorageHelper.poweredInsert(
                        this.grid.getEnergyService(), this.storage, key, 1L, this.actionSource, Actionable.SIMULATE);
            } catch (RuntimeException failure) {
                logStorageFailure(key, "container refund simulation failed", failure);
                this.sourceFailures++;
                return false;
            }
            if (simulated != 1L) {
                this.meBlocked++;
                return true;
            }
            long inserted;
            try {
                inserted = StorageHelper.poweredInsert(
                        this.grid.getEnergyService(), this.storage, key, 1L, this.actionSource, Actionable.MODULATE);
            } catch (RuntimeException failure) {
                logStorageFailure(key, "container refund insertion failed", failure);
                inserted = -1L;
            }
            if (inserted != 1L) {
                long insertedAmount = readStorageAmount(key);
                if (insertedAmount == before) {
                    this.meBlocked++;
                    return true;
                }
                if (before == Long.MAX_VALUE || insertedAmount != before + 1L) {
                    logSourceFailure(container, slot, "ME changed by an uncertain amount during refund insertion");
                    this.sourceFailures++;
                    return false;
                }
            }
            SourceOutcome outcome = extractSource(container.inventory(), slot);
            if (outcome == SourceOutcome.REMOVED) {
                if (duplicate) {
                    this.duplicateRefunded++;
                } else {
                    this.invalidRefunded++;
                }
                return true;
            }
            long compensated;
            try {
                compensated = StorageHelper.poweredExtraction(
                        this.grid.getEnergyService(), this.storage, key, 1L, this.actionSource, Actionable.MODULATE);
            } catch (RuntimeException failure) {
                logStorageFailure(key, "container refund compensation failed", failure);
                compensated = -1L;
            }
            this.sourceFailures++;
            if (compensated != 1L) {
                long compensatedAmount = readStorageAmount(key);
                if (before == Long.MAX_VALUE || compensatedAmount != before) {
                    logSourceFailure(container, slot, "refund compensation could not be proven");
                    return false;
                }
            }
            if (outcome == SourceOutcome.UNKNOWN) {
                logSourceFailure(container, slot, "refund compensation or source postcondition was uncertain");
                return false;
            }
            return true;
        }

        private boolean moveContainerPattern(ContainerSnapshot container, SlotSnapshot slot, Decoded decoded) {
            TargetSlot target = nextEmptyTarget();
            if (target == null) {
                if (!this.targetAborted) {
                    this.capacitySkipped++;
                }
                return true;
            }
            ItemStack simulated;
            try {
                simulated = container.inventory().extractItem(slot.slot(), 1, true);
            } catch (RuntimeException failure) {
                logSourceFailure(container, slot, "source extraction simulation failed: " + failure.getClass().getSimpleName());
                this.sourceFailures++;
                return false;
            }
            if (!matchesOne(simulated, slot.stack()) || !install(target, decoded.stack())) {
                this.sourceFailures++;
                return true;
            }
            SourceOutcome outcome = extractSource(container.inventory(), slot);
            if (outcome == SourceOutcome.REMOVED) {
                this.movedFromContainers++;
                this.seen.add(decoded.identity());
                return true;
            }
            if (outcome == SourceOutcome.UNCHANGED) {
                if (!rollbackTarget(target, decoded.stack())) {
                    abortTarget("source extraction failed and the Trinity destination could not roll back");
                }
                this.sourceFailures++;
                return !this.targetAborted;
            }
            logSourceFailure(container, slot, "source changed to an unrecognized state after extraction");
            this.sourceFailures++;
            return false;
        }

        private SourceOutcome extractSource(InternalInventory inventory, SlotSnapshot snapshot) {
            try {
                inventory.extractItem(snapshot.slot(), 1, false);
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error(
                        "Pattern migration source extraction failed at slot {} for item {}",
                        snapshot.slot(),
                        safePatternName(snapshot.stack()),
                        failure);
            }
            ItemStack after;
            try {
                after = inventory.getStackInSlot(snapshot.slot());
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error(
                        "Pattern migration could not read source slot {} after extraction for item {}",
                        snapshot.slot(), safePatternName(snapshot.stack()), failure);
                return SourceOutcome.UNKNOWN;
            }
            if (after.isEmpty() && snapshot.stack().getCount() == 1) {
                return SourceOutcome.REMOVED;
            }
            if (ItemStack.isSameItemSameComponents(after, snapshot.stack()) &&
                    after.getCount() == snapshot.stack().getCount() - 1) {
                return SourceOutcome.REMOVED;
            }
            if (matchesSnapshot(after, snapshot.stack())) {
                return SourceOutcome.UNCHANGED;
            }
            return SourceOutcome.UNKNOWN;
        }

        private boolean supports(ItemStack stack) {
            for (TrinityPatternCatalog.CoreRange range : this.layout.ranges()) {
                if (range.mount().core().patternInventory().isItemValid(0, stack)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        private TargetSlot nextEmptyTarget() {
            if (this.catalog.layoutSnapshot().revision() != this.layout.revision()) {
                abortTarget("Trinity pattern layout changed during migration");
                return null;
            }
            for (TrinityPatternCatalog.CoreRange range : this.layout.ranges()) {
                TrinityPatternCore core = range.mount().core();
                for (int slot = 0; slot < range.mount().blockCapacity(); slot++) {
                    if (core.pattern(slot).isEmpty()) {
                        return new TargetSlot(core, slot);
                    }
                }
            }
            return null;
        }

        private boolean install(TargetSlot target, ItemStack stack) {
            if (!target.core().pattern(target.slot()).isEmpty()) {
                abortTarget("selected Trinity destination was no longer empty");
                return false;
            }
            if (!target.core().trySetPattern(target.slot(), stack.copyWithCount(1))) {
                return false;
            }
            if (!matchesOne(target.core().pattern(target.slot()), stack)) {
                abortTarget("Trinity destination did not retain the installed pattern");
                return false;
            }
            return true;
        }

        private boolean rollbackTarget(TargetSlot target, ItemStack expected) {
            if (!matchesOne(target.core().pattern(target.slot()), expected)) {
                return false;
            }
            return target.core().trySetPattern(target.slot(), ItemStack.EMPTY) &&
                    target.core().pattern(target.slot()).isEmpty();
        }

        private void abortTarget(String reason) {
            this.targetAborted = true;
            Data_Energistics.LOGGER.error("Aborted Trinity pattern migration for host {}: {}", this.catalog.hostId(), reason);
        }

        private void logSourceFailure(ContainerSnapshot container, SlotSnapshot slot, String reason) {
            Data_Energistics.LOGGER.error(
                    "Quarantined pattern migration source class={} ordinal={} slot={} item={}: {}",
                    container.className(),
                    container.ordinal(),
                    slot.slot(),
                    safePatternName(slot.stack()),
                    reason);
        }

        private TrinityPatternMigrationResult result() {
            return new TrinityPatternMigrationResult(
                    this.movedFromStorage,
                    this.movedFromContainers,
                    this.invalidRefunded,
                    this.duplicateRefunded,
                    this.unsupportedKept,
                    this.storageInvalidKept,
                    this.storageUnsupportedKept,
                    this.storageDuplicateKept,
                    this.storageSourceUncertain,
                    this.meBlocked,
                    this.capacitySkipped,
                    this.sourceFailures,
                    this.quarantinedSources,
                    this.fallbackIdentitySources,
                    this.targetAborted);
        }

        private long readStorageAmount(AEKey key) {
            try {
                return this.storage.getAvailableStacks().get(key);
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error(
                        "Could not read AE storage amount during Trinity pattern migration for key {}",
                        stableKeyDigest(key, this.level.registryAccess()),
                        failure);
                return -1L;
            }
        }

        private void logStorageFailure(AEKey key, String reason, RuntimeException failure) {
            Data_Energistics.LOGGER.error(
                    "AE storage pattern migration failure for host {} key={} reason={}",
                    this.catalog.hostId(),
                    stableKeyDigest(key, this.level.registryAccess()),
                    reason,
                    failure);
        }

        private Decoded decode(ItemStack stack) {
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return new Decoded(stack.copyWithCount(1), null);
            }
            try {
                IPatternDetails details = PatternDetailsHelper.decodePattern(stack, this.level);
                return details == null ? new Decoded(stack.copyWithCount(1), null) :
                        new Decoded(stack.copyWithCount(1), TrinityPatternSemanticIdentity.capture(details));
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.warn(
                        "Pattern migration could not decode encoded item {}",
                        safePatternName(stack),
                        failure);
                return new Decoded(stack.copyWithCount(1), null);
            }
        }
    }

    private static boolean matchesOne(ItemStack actual, ItemStack expected) {
        return actual.getCount() == 1 && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static boolean matchesSnapshot(ItemStack actual, ItemStack expected) {
        return actual.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static String safePatternName(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        return key == null ? "<empty>" : key.getId().toString();
    }

    private static String stableKeyDigest(AEKey key, HolderLookup.Provider registries) {
        return StableDigest.sha256(key.getType().getId() + "\u0000" + canonicalTag(key.toTag(registries)));
    }

    private static String canonicalTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            StringBuilder canonical = new StringBuilder("{");
            compound.getAllKeys().stream().sorted().forEach(key -> canonical
                    .append(key.length())
                    .append(':')
                    .append(key)
                    .append('=')
                    .append(canonicalTag(compound.get(key)))
                    .append(';'));
            return canonical.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder canonical = new StringBuilder("[");
            for (Tag element : list) {
                canonical.append(canonicalTag(element)).append(';');
            }
            return canonical.append(']').toString();
        }
        return tag.getId() + ":" + tag;
    }

    private record StorageCandidate(AEItemKey key, long capturedAmount) {}

    private record StorageKeySnapshot(AEKey key, long amount) {}

    private record SlotSnapshot(int slot, ItemStack stack) {}

    private record ContainerSnapshot(PatternContainer container,
                                     InternalInventory inventory,
                                     String discoveryClassName,
                                     long sortOrder,
                                     String className,
                                     @Nullable String identityDigest,
                                     int ordinal,
                                     List<SlotSnapshot> slots,
                                     boolean quarantined) {}

    private static final class ContainerBuilder {

        private static final Comparator<ContainerBuilder> ORDER = Comparator
                .comparing((ContainerBuilder builder) -> builder.discoveryClassName)
                .thenComparingLong(builder -> builder.sortOrder)
                .thenComparing(builder -> builder.className)
                .thenComparing(builder -> builder.identityDigest == null ? "~" : builder.identityDigest)
                .thenComparingInt(builder -> builder.ordinal);

        private final PatternContainer container;
        private final InternalInventory inventory;
        private final int slotCount;
        private final String discoveryClassName;
        private final long sortOrder;
        private final String className;
        private final @Nullable String identityDigest;
        private final int ordinal;
        private final List<SlotSnapshot> slots = new ArrayList<>();
        private boolean quarantined;

        private ContainerBuilder(PatternContainer container,
                                 InternalInventory inventory,
                                 String discoveryClassName,
                                 long sortOrder,
                                 String className,
                                 @Nullable String identityDigest,
                                 int ordinal) {
            this.container = container;
            this.inventory = inventory;
            this.slotCount = inventory.size();
            this.discoveryClassName = discoveryClassName;
            this.sortOrder = sortOrder;
            this.className = className;
            this.identityDigest = identityDigest;
            this.ordinal = ordinal;
        }

        private ContainerSnapshot snapshot() {
            return new ContainerSnapshot(
                    this.container,
                    this.inventory,
                    this.discoveryClassName,
                    this.sortOrder,
                    this.className,
                    this.identityDigest,
                    this.ordinal,
                    List.copyOf(this.slots),
                    this.quarantined);
        }
    }

    private record Decoded(ItemStack stack, @Nullable TrinityPatternSemanticIdentity identity) {}

    private record TargetSlot(TrinityPatternCore core, int slot) {}

    private enum SourceOutcome {
        REMOVED,
        UNCHANGED,
        UNKNOWN
    }
}
