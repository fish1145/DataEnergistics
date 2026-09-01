package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Deduplicated quantity-independent unit proofs with reservoir aliases for constant-time selection.
 *
 * @param byReservoir lookup aliases; multiple aliases may reference the same canonical proof instance
 * @param uniqueCount number of strict semantic unit identities represented by the aliases
 */
public record TrinityCycleUnitProofIndex(
                                         Object2ObjectMap<AEKey, TrinityCycleUnitProof> byReservoir,
                                         int uniqueCount) {

    private static final TrinityCycleUnitProofIndex EMPTY = new TrinityCycleUnitProofIndex(
            new Object2ObjectLinkedOpenHashMap<>(),
            0);

    public TrinityCycleUnitProofIndex {
        byReservoir = Object2ObjectMaps.unmodifiable(byReservoir);
    }

    /** @return shared empty proof index */
    public static TrinityCycleUnitProofIndex empty() {
        return EMPTY;
    }

    /** Derives and strictly deduplicates every productive unit before the index can enter a shared cache. */
    public static TrinityCycleUnitProofIndex derive(TrinityStronglyConnectedComponent component) {
        Object2ObjectLinkedOpenHashMap<UnitSemanticKey, TrinityCycleUnitProof> canonical = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityCycleUnitProof> aliases = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey reservoir : component.keys()) {
            TrinityCycleUnitProof.derive(component, reservoir).ifPresent(candidate -> {
                UnitSemanticKey semanticKey = UnitSemanticKey.from(candidate);
                TrinityCycleUnitProof unit = canonical.computeIfAbsent(semanticKey, ignored -> candidate);
                aliases.put(reservoir, unit);
            });
        }
        return canonical.isEmpty() ? EMPTY : new TrinityCycleUnitProofIndex(
                aliases,
                canonical.size());
    }

    /** Merges cached component families while preserving one canonical object for every strict unit identity. */
    public static TrinityCycleUnitProofIndex merge(List<TrinityCycleUnitProofIndex> indexes) {
        if (indexes.isEmpty()) {
            return EMPTY;
        }
        Object2ObjectLinkedOpenHashMap<UnitSemanticKey, TrinityCycleUnitProof> canonical = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityCycleUnitProof> aliases = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityCycleUnitProofIndex index : indexes) {
            index.byReservoir.forEach((reservoir, candidate) -> {
                UnitSemanticKey semanticKey = UnitSemanticKey.from(candidate);
                TrinityCycleUnitProof unit = canonical.computeIfAbsent(semanticKey, ignored -> candidate);
                aliases.put(reservoir, unit);
            });
        }
        return canonical.isEmpty() ? EMPTY : new TrinityCycleUnitProofIndex(
                aliases,
                canonical.size());
    }

    /** @return whether no productive unit can be proven for the component */
    public boolean isEmpty() {
        return this.uniqueCount == 0;
    }

    private record UnitSemanticKey(
                                   List<TrinityVariantFiring> order,
                                   Map<TrinityPatternVariant, BigInteger> firings,
                                   Map<AEKey, BigInteger> netChange,
                                   Map<AEKey, BigInteger> internalSeed,
                                   Map<AEKey, BigInteger> externalInput) {

        private static UnitSemanticKey from(TrinityCycleUnitProof proof) {
            return new UnitSemanticKey(
                    proof.order(),
                    proof.firings(),
                    proof.netChange(),
                    proof.internalSeed(),
                    proof.externalInput());
        }
    }
}
