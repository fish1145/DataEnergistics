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
        TrinityPatternCatalog.LayoutSnapshot layout = catalog.layoutSnapshot();
        if (!layout.active()) {
            return TrinityPatternMigrationResult.targetUnavailable();
        }
        MEStorage storage = grid.getStorageService().getInventory();
        Batch batch = new Batch(level, grid, actionSource, catalog, layout, storage);
        batch.captureInstalledIdentities();
        List<StorageCandidate> storageSnapshot = batch.captureStorage();
        List<ContainerSnapshot> containerSnapshot = batch.captureContainers();
        batch.processStorage(storageSnapshot);
        if (!batch.targetAborted) {
            batch.processContainers(containerSnapshot);
        }
        return batch.result();
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

        private List<StorageCandidate> captureStorage() {
            ArrayList<StorageCandidate> candidates = new ArrayList<>();
            for (var entry : this.storage.getAvailableStacks()) {
                if (entry.getLongValue() > 0L && entry.getKey() instanceof AEItemKey itemKey &&
                        PatternDetailsHelper.isEncodedPattern(itemKey.getReadOnlyStack())) {
                    candidates.add(new StorageCandidate(itemKey, entry.getLongValue()));
                }
            }
            candidates.sort(Comparator.comparing(candidate -> stableKeyDigest(
                    candidate.key(), this.level.registryAccess())));
            return List.copyOf(candidates);
        }

        private List<ContainerSnapshot> captureContainers() {
            ArrayList<Class<?>> classes = new ArrayList<>();
            for (Class<?> machineClass : this.grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    classes.add(machineClass);
                }
            }
            classes.sort(Comparator.comparing(Class::getName));
            Set<PatternContainer> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            ArrayList<ContainerSnapshot> snapshots = new ArrayList<>();
            int ordinal = 0;
            for (Class<?> machineClass : classes) {
                for (Object machine : this.grid.getActiveMachines(machineClass)) {
                    if (!(machine instanceof PatternContainer container) ||
                            container instanceof TrinityPatternTerminalPartition || !identities.add(container)) {
                        continue;
                    }
                    InternalInventory inventory = container.getTerminalPatternInventory();
                    ArrayList<SlotSnapshot> slots = new ArrayList<>(inventory.size());
                    for (int slot = 0; slot < inventory.size(); slot++) {
                        ItemStack stack = inventory.getStackInSlot(slot);
                        if (!stack.isEmpty()) {
                            slots.add(new SlotSnapshot(slot, stack.copy()));
                        }
                    }
                    String identityDigest = resolveIdentityDigest(container);
                    if (identityDigest == null) {
                        this.fallbackIdentitySources++;
                    }
                    snapshots.add(new ContainerSnapshot(
                            container,
                            inventory,
                            machineClass.getName(),
                            container.getTerminalSortOrder(),
                            container.getClass().getName(),
                            identityDigest,
                            ordinal++,
                            List.copyOf(slots)));
                }
            }
            snapshots.sort(Comparator.comparing(ContainerSnapshot::discoveryClassName)
                    .thenComparingLong(ContainerSnapshot::sortOrder)
                    .thenComparing(ContainerSnapshot::className)
                    .thenComparing(snapshot -> snapshot.identityDigest() == null ? "~" : snapshot.identityDigest())
                    .thenComparingInt(ContainerSnapshot::ordinal));
            return List.copyOf(snapshots);
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

        private void captureInstalledIdentities() {
            for (TrinityPatternCatalog.CoreRange range : this.layout.ranges()) {
                TrinityPatternCore core = range.mount().core();
                for (int slot = 0; slot < range.mount().blockCapacity(); slot++) {
                    ItemStack stack = core.pattern(slot);
                    Decoded decoded = decode(stack);
                    if (decoded.identity() != null) {
                        this.seen.add(decoded.identity());
                    }
                }
            }
        }

        private void processStorage(List<StorageCandidate> candidates) {
            for (StorageCandidate candidate : candidates) {
                if (this.targetAborted) {
                    return;
                }
                Decoded decoded = decode(candidate.key().toStack());
                if (readStorageAmount(candidate.key()) != candidate.capturedAmount()) {
                    this.storageSourceUncertain++;
                    return;
                }
                if (decoded.identity() == null) {
                    this.storageInvalidKept++;
                    continue;
                }
                if (!supports(decoded.stack())) {
                    this.storageUnsupportedKept++;
                    continue;
                }
                if (this.seen.contains(decoded.identity())) {
                    this.storageDuplicateKept++;
                    continue;
                }
                TargetSlot target = nextEmptyTarget();
                if (target == null) {
                    if (!this.targetAborted) {
                        this.capacitySkipped++;
                    }
                    continue;
                }
                long simulated;
                try {
                    simulated = StorageHelper.poweredExtraction(
                            this.grid.getEnergyService(), this.storage, candidate.key(), 1L,
                            this.actionSource, Actionable.SIMULATE);
                } catch (RuntimeException failure) {
                    logStorageFailure(candidate.key(), "simulation failed", failure);
                    this.storageSourceUncertain++;
                    return;
                }
                if (simulated != 1L || !install(target, decoded.stack())) {
                    this.meBlocked++;
                    continue;
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
                    continue;
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
                    return;
                }
            }
        }

        private void processContainers(List<ContainerSnapshot> containers) {
            for (ContainerSnapshot container : containers) {
                if (this.targetAborted) {
                    return;
                }
                boolean quarantined = false;
                for (SlotSnapshot slot : container.slots()) {
                    if (quarantined || this.targetAborted) {
                        break;
                    }
                    if (container.container().getGrid() != this.grid ||
                            !matchesSnapshot(container.inventory().getStackInSlot(slot.slot()), slot.stack())) {
                        this.sourceFailures++;
                        continue;
                    }
                    if (!PatternDetailsHelper.isEncodedPattern(slot.stack())) {
                        continue;
                    }
                    Decoded decoded = decode(slot.stack());
                    if (decoded.identity() == null) {
                        quarantined = !refundContainerPattern(container, slot, false);
                    } else if (!supports(decoded.stack())) {
                        this.unsupportedKept++;
                    } else if (this.seen.contains(decoded.identity())) {
                        quarantined = !refundContainerPattern(container, slot, true);
                    } else {
                        quarantined = !moveContainerPattern(container, slot, decoded);
                    }
                }
                if (quarantined) {
                    this.quarantinedSources++;
                }
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

    private record SlotSnapshot(int slot, ItemStack stack) {}

    private record ContainerSnapshot(PatternContainer container,
                                     InternalInventory inventory,
                                     String discoveryClassName,
                                     long sortOrder,
                                     String className,
                                     @Nullable String identityDigest,
                                     int ordinal,
                                     List<SlotSnapshot> slots) {}

    private record Decoded(ItemStack stack, @Nullable TrinityPatternSemanticIdentity identity) {}

    private record TargetSlot(TrinityPatternCore core, int slot) {}

    private enum SourceOutcome {
        REMOVED,
        UNCHANGED,
        UNKNOWN
    }
}
