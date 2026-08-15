package com.fish_dan_.data_energistics.mixin.core.grid.storage;

import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess;
import com.fish_dan_.data_energistics.ae2.key.SaturatingKeyCounter;
import com.fish_dan_.data_energistics.ae2.key.SaturatingKeyCounterBridge;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.NavigableMap;

/** Prevents mounted AE storage totals from wrapping past {@link Long#MAX_VALUE}. */
@Mixin(NetworkStorage.class)
public abstract class NetworkStorageMixin implements FiniteNetworkStorageAccess {

    @Shadow
    private boolean mountsInUse;

    @Shadow
    @Final
    private NavigableMap<Integer, List<MEStorage>> priorityInventory;

    @Unique
    private long dataEnergistics$storageStructureRevision;

    @Unique
    private final KeyCounter dataEnergistics$storageContribution = new KeyCounter();

    @Shadow
    private boolean isQueuedForRemoval(MEStorage inventory) {
        throw new AssertionError();
    }

    @Shadow
    private void flushQueuedOperations() {
        throw new AssertionError();
    }

    @Inject(method = "mount", at = @At("RETURN"))
    private void dataEnergistics$recordMount(int priority, MEStorage inventory, CallbackInfo callbackInfo) {
        this.dataEnergistics$storageStructureRevision++;
    }

    @Inject(method = "unmount", at = @At("RETURN"))
    private void dataEnergistics$recordUnmount(MEStorage inventory, CallbackInfo callbackInfo) {
        this.dataEnergistics$storageStructureRevision++;
    }

    @Override
    public long storageStructureRevision() {
        return this.dataEnergistics$storageStructureRevision;
    }

    @Override
    public FiniteTransferResult transferFinite(AEKey what,
                                               long amount,
                                               IActionSource source,
                                               FiniteTransferTarget target) {
        MEStorage.checkPreconditions(what, amount, Actionable.MODULATE, source);
        if (this.mountsInUse) {
            return new FiniteTransferResult(0L, 0L, 0L, 0L, 0L, 0L, 0, true);
        }

        long transferred = 0L;
        long plannedSourceExtraction = 0L;
        long sourceExtracted = 0L;
        long targetAccepted = 0L;
        long sourceRollback = 0L;
        long sourceRollbackAccepted = 0L;
        int skippedInfiniteSources = 0;
        boolean retrySuggested = false;

        this.mountsInUse = true;
        try {
            extraction:
            for (List<MEStorage> inventories : this.priorityInventory.descendingMap().values()) {
                for (MEStorage inventory : inventories) {
                    if (this.isQueuedForRemoval(inventory)) {
                        continue;
                    }

                    this.dataEnergistics$storageContribution.clear();
                    inventory.getAvailableStacks(this.dataEnergistics$storageContribution);
                    long reportedAmount = this.dataEnergistics$storageContribution.get(what);
                    if (reportedAmount == Integer.MAX_VALUE || reportedAmount == Long.MAX_VALUE) {
                        skippedInfiniteSources++;
                        continue;
                    }
                    if (reportedAmount <= 0L) {
                        continue;
                    }

                    long remaining = amount - transferred;
                    long extractionLimit = Math.min(remaining, reportedAmount);
                    long simulatedExtraction = inventory.extract(
                            what,
                            extractionLimit,
                            Actionable.SIMULATE,
                            source);
                    if (simulatedExtraction <= 0L) {
                        continue;
                    }

                    long simulatedTargetInsert = target.simulateInsert(what, simulatedExtraction);
                    if (simulatedTargetInsert <= 0L) {
                        break extraction;
                    }

                    long planned = Math.min(simulatedExtraction, simulatedTargetInsert);
                    plannedSourceExtraction += planned;
                    long extracted = inventory.extract(what, planned, Actionable.MODULATE, source);
                    sourceExtracted += extracted;
                    if (extracted != planned) {
                        long restored = dataEnergistics$rollback(inventory, what, extracted, source);
                        sourceRollback += extracted;
                        sourceRollbackAccepted += restored;
                        retrySuggested = true;
                        break extraction;
                    }

                    long accepted;
                    try {
                        accepted = target.insert(what, extracted);
                    } catch (RuntimeException exception) {
                        long restored = dataEnergistics$rollback(inventory, what, extracted, source);
                        if (restored != extracted) {
                            exception.addSuppressed(new IllegalStateException(
                                    "Finite network transfer restored " + restored + " of " + extracted +
                                            " to its concrete source"));
                        }
                        throw exception;
                    }
                    targetAccepted += accepted;
                    if (accepted != extracted) {
                        long rollbackAmount = accepted < 0L || accepted > extracted ? extracted : extracted - accepted;
                        long restored = dataEnergistics$rollback(inventory, what, rollbackAmount, source);
                        sourceRollback += rollbackAmount;
                        sourceRollbackAccepted += restored;
                        if (accepted > 0L && accepted <= extracted) {
                            transferred += accepted;
                        }
                        retrySuggested = true;
                        break extraction;
                    }

                    transferred += accepted;
                    if (transferred >= amount) {
                        retrySuggested = true;
                        break extraction;
                    }
                }
            }
        } finally {
            this.mountsInUse = false;
            this.flushQueuedOperations();
        }

        return new FiniteTransferResult(
                transferred,
                plannedSourceExtraction,
                sourceExtracted,
                targetAccepted,
                sourceRollback,
                sourceRollbackAccepted,
                skippedInfiniteSources,
                retrySuggested);
    }

    @Unique
    private static long dataEnergistics$rollback(MEStorage sourceStorage,
                                                 AEKey what,
                                                 long amount,
                                                 IActionSource source) {
        if (amount <= 0L) {
            return 0L;
        }
        return sourceStorage.insert(what, amount, Actionable.MODULATE, source);
    }

    @Redirect(
              method = "getAvailableStacks",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"))
    private void dataEnergistics$mergeAvailableStacks(MEStorage storage, KeyCounter total) {
        if ((Object) total instanceof SaturatingKeyCounterBridge bridge) {
            bridge.dataEnergistics$beginSaturatingMerge();
            try {
                storage.getAvailableStacks(total);
            } finally {
                bridge.dataEnergistics$endSaturatingMerge();
            }
            return;
        }

        this.dataEnergistics$storageContribution.clear();
        storage.getAvailableStacks(this.dataEnergistics$storageContribution);
        SaturatingKeyCounter.merge(total, this.dataEnergistics$storageContribution);
    }
}
