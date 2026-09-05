package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory.TrinityExactWorkingInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Map;

/**
 * Extracts exact Trinity initial inputs without assuming one aggregate-storage call must satisfy the whole request.
 *
 * <p>
 * AE2 remains a long-per-call boundary. A concrete mount may return a partial amount even while later mounts can
 * still contribute, so extraction continues until the exact request is owned by the CPU or the network returns zero.
 * Every earlier key is rolled back if a later key cannot be completed.
 * </p>
 */
public final class TrinityInitialInputExtractor {

    private static final BigInteger MAX_PHYSICAL_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);

    private TrinityInitialInputExtractor() {}

    /** Returns the unfulfilled remainder, or {@code null} after complete transactional extraction. */
    public static @Nullable GenericStack extract(
                                                 TrinityCraftingPlan plan,
                                                 IGrid grid,
                                                 ListCraftingInventory cpuInventory,
                                                 TrinityExactWorkingInventory exactInventory,
                                                 IActionSource source) {
        return reserve(
                plan.initialExpectedInputs(),
                grid.getStorageService().getInventory(),
                cpuInventory,
                exactInventory,
                source,
                false);
    }

    /**
     * Reserves the exact total required by a replacement plan, reusing material already owned by the CPU.
     */
    public static @Nullable GenericStack reserveReplacement(
                                                            Map<AEKey, BigInteger> requiredInputs,
                                                            MEStorage network,
                                                            ListCraftingInventory cpuInventory,
                                                            TrinityExactWorkingInventory exactInventory,
                                                            IActionSource source) {
        return reserve(requiredInputs, network, cpuInventory, exactInventory, source, true);
    }

    private static @Nullable GenericStack reserve(
                                                  Map<AEKey, BigInteger> requiredInputs,
                                                  MEStorage network,
                                                  ListCraftingInventory cpuInventory,
                                                  TrinityExactWorkingInventory exactInventory,
                                                  IActionSource source,
                                                  boolean reuseOwned) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> extractedOwnership = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> unlimitedOwnership = new Object2ObjectLinkedOpenHashMap<>();
        for (var input : requiredInputs.entrySet()) {
            BigInteger alreadyOwned = reuseOwned ? exactInventory.totalAmount(input.getKey(), cpuInventory) : BigInteger.ZERO;
            BigInteger remaining = input.getValue().subtract(alreadyOwned).max(BigInteger.ZERO);
            if (remaining.signum() > 0 && network instanceof FiniteNetworkStorageAccess storageAccess &&
                    storageAccess.exactAvailability(input.getKey(), source).unlimited()) {
                exactInventory.deposit(input.getKey(), remaining, cpuInventory);
                unlimitedOwnership.merge(input.getKey(), remaining, BigInteger::add);
                continue;
            }
            while (remaining.signum() > 0) {
                long requested = remaining.min(MAX_PHYSICAL_AMOUNT).longValueExact();
                long extracted;
                try {
                    extracted = network.extract(input.getKey(), requested, Actionable.MODULATE, source);
                } catch (RuntimeException exception) {
                    rollback(
                            network,
                            cpuInventory,
                            exactInventory,
                            source,
                            extractedOwnership,
                            unlimitedOwnership);
                    throw exception;
                }
                if (extracted == 0L) {
                    rollback(
                            network,
                            cpuInventory,
                            exactInventory,
                            source,
                            extractedOwnership,
                            unlimitedOwnership);
                    return new GenericStack(input.getKey(), remaining.min(MAX_PHYSICAL_AMOUNT).longValueExact());
                }
                if (extracted < 0L || extracted > requested) {
                    if (extracted > 0L) {
                        network.insert(input.getKey(), extracted, Actionable.MODULATE, source);
                    }
                    rollback(
                            network,
                            cpuInventory,
                            exactInventory,
                            source,
                            extractedOwnership,
                            unlimitedOwnership);
                    throw new IllegalStateException("AE storage violated its extraction amount contract");
                }
                exactInventory.deposit(input.getKey(), extracted, cpuInventory);
                extractedOwnership.merge(input.getKey(), BigInteger.valueOf(extracted), BigInteger::add);
                remaining = remaining.subtract(BigInteger.valueOf(extracted));
            }
        }
        return null;
    }

    private static void rollback(
                                 MEStorage network,
                                 ListCraftingInventory cpuInventory,
                                 TrinityExactWorkingInventory exactInventory,
                                 IActionSource source,
                                 Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> extractedOwnership,
                                 Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> unlimitedOwnership) {
        for (var entry : extractedOwnership.object2ObjectEntrySet()) {
            exactInventory.rollback(entry.getKey(), entry.getValue(), cpuInventory, network, source);
        }
        for (var entry : unlimitedOwnership.object2ObjectEntrySet()) {
            exactInventory.discard(entry.getKey(), entry.getValue(), cpuInventory);
        }
    }
}
