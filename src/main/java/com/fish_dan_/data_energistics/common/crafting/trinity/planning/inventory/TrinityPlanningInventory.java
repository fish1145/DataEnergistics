package com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Immutable planning-domain inventory with disjoint finite and non-consuming exact-key sources.
 *
 * <p>
 * The collections are owned and frozen by the capture or projection that creates this value. Trusted planning
 * layers pass the same instance instead of repeatedly copying and revalidating its elements.
 * </p>
 */
public record TrinityPlanningInventory(
                                       Map<AEKey, BigInteger> finiteAmounts,
                                       Set<AEKey> unlimitedKeys) {

    /** Empty inventory used when no graph has been published. */
    public static TrinityPlanningInventory empty() {
        return new TrinityPlanningInventory(Map.of(), Set.of());
    }

    /** Wraps an already immutable finite-only inventory. */
    public static TrinityPlanningInventory finite(Map<AEKey, BigInteger> finiteAmounts) {
        return new TrinityPlanningInventory(finiteAmounts, Set.of());
    }

    /** Returns a stable request-local projection without materialising any quantity-bound plan. */
    public TrinityPlanningInventory project(Collection<AEKey> keys) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> projectedFinite = new Object2ObjectLinkedOpenHashMap<>();
        ObjectOpenHashSet<AEKey> projectedUnlimited = new ObjectOpenHashSet<>();
        for (AEKey key : keys) {
            if (this.unlimitedKeys.contains(key)) {
                projectedUnlimited.add(key);
                continue;
            }
            BigInteger amount = this.finiteAmounts.get(key);
            if (amount != null) {
                projectedFinite.put(key, amount);
            }
        }
        return frozen(projectedFinite, projectedUnlimited);
    }

    /** Adds finite working inventory to a network capture; unlimited keys remain unlimited. */
    public TrinityPlanningInventory plus(KeyCounter localInventory) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> combined = new Object2ObjectLinkedOpenHashMap<>(
                this.finiteAmounts);
        for (var entry : localInventory) {
            if (!this.unlimitedKeys.contains(entry.getKey())) {
                combined.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }
        return frozen(combined, new ObjectOpenHashSet<>(this.unlimitedKeys));
    }

    /** Adds exact finite CPU-owned overflow to a request-local network capture. */
    public TrinityPlanningInventory plus(Map<AEKey, BigInteger> localInventory) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> combined = new Object2ObjectLinkedOpenHashMap<>(
                this.finiteAmounts);
        localInventory.forEach((key, amount) -> {
            if (!this.unlimitedKeys.contains(key)) {
                combined.merge(key, amount, BigInteger::add);
            }
        });
        return frozen(combined, new ObjectOpenHashSet<>(this.unlimitedKeys));
    }

    /** Returns exact finite availability, or zero for absent and unlimited keys. */
    public BigInteger finiteAmount(AEKey key) {
        return this.finiteAmounts.getOrDefault(key, BigInteger.ZERO);
    }

    /** Returns whether this exact key has a confirmed non-consuming source. */
    public boolean unlimited(AEKey key) {
        return this.unlimitedKeys.contains(key);
    }

    /** Returns the usable amount without inventing a numeric infinity sentinel. */
    public BigInteger availableUpTo(AEKey key, BigInteger usefulUpper) {
        return unlimited(key) ? usefulUpper : finiteAmount(key).min(usefulUpper);
    }

    /** Returns whether the captured inventory covers the exact request. */
    public boolean covers(AEKey key, BigInteger required) {
        return unlimited(key) || finiteAmount(key).compareTo(required) >= 0;
    }

    static TrinityPlanningInventory frozen(
                                           Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finiteAmounts,
                                           ObjectOpenHashSet<AEKey> unlimitedKeys) {
        return new TrinityPlanningInventory(
                Object2ObjectMaps.unmodifiable(finiteAmounts),
                ObjectSets.unmodifiable(unlimitedKeys));
    }
}
