package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Removes target routes that cannot be reached from the captured inventory in an acyclic transition graph.
 *
 */
public final class TrinityAcyclicRoutePruner {

    /**
     * @return stateless event-driven pruner
     */
    public static TrinityAcyclicRoutePruner create() {
        return new TrinityAcyclicRoutePruner();
    }

    /**
     * Retains every and only structurally executable transition that can still contribute to the target.
     * Quantities remain the responsibility of the exact propagator or MIP; this boundary only proves that each
     * retained input key has an inventory-backed production path.
     *
     * @param variants  complete transition set
     * @param target    requested output
     * @param inventory finite/unlimited captured inventory
     * @return stable identity-ordered executable target routes
     */
    public List<TrinityPatternVariant> retainExecutableTargetRoutes(
                                                                    List<TrinityPatternVariant> variants,
                                                                    AEKey target,
                                                                    TrinityPlanningInventory inventory) {
        List<TrinityPatternVariant> backward = targetReachableVariants(variants, target);
        if (backward.isEmpty()) {
            return List.of();
        }
        List<TrinityPatternVariant> executable = forwardExecutableVariants(backward, inventory);
        return executable.size() == backward.size() ? backward : targetReachableVariants(executable, target);
    }

    private static List<TrinityPatternVariant> forwardExecutableVariants(
                                                                         List<TrinityPatternVariant> variants,
                                                                         TrinityPlanningInventory inventory) {
        ObjectOpenHashSet<AEKey> producibleKeys = new ObjectOpenHashSet<>(inventory.unlimitedKeys());
        inventory.finiteAmounts().forEach((key, amount) -> {
            if (amount.signum() > 0) {
                producibleKeys.add(key);
            }
        });

        int[] missingInputs = new int[variants.size()];
        boolean[] executable = new boolean[variants.size()];
        HashMap<AEKey, ArrayList<Integer>> waitingByInput = new HashMap<>();
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int index = 0; index < variants.size(); index++) {
            TrinityPatternVariant variant = variants.get(index);
            for (AEKey input : variant.inputs().keySet()) {
                if (!producibleKeys.contains(input)) {
                    missingInputs[index]++;
                    waitingByInput.computeIfAbsent(input, ignored -> new ArrayList<>()).add(index);
                }
            }
            if (missingInputs[index] == 0) {
                ready.addLast(index);
            }
        }

        while (!ready.isEmpty()) {
            int index = ready.removeFirst();
            if (executable[index]) {
                continue;
            }
            executable[index] = true;
            for (AEKey output : variants.get(index).outputs().keySet()) {
                if (!producibleKeys.add(output)) {
                    continue;
                }
                List<Integer> waiting = waitingByInput.remove(output);
                if (waiting == null) {
                    continue;
                }
                for (Integer consumer : waiting) {
                    missingInputs[consumer]--;
                    if (missingInputs[consumer] == 0) {
                        ready.addLast(consumer);
                    }
                }
            }
        }

        ArrayList<TrinityPatternVariant> retained = new ArrayList<>();
        for (int index = 0; index < variants.size(); index++) {
            if (executable[index]) {
                retained.add(variants.get(index));
            }
        }
        return List.copyOf(retained);
    }

    private static List<TrinityPatternVariant> targetReachableVariants(
                                                                       List<TrinityPatternVariant> variants,
                                                                       AEKey target) {
        ArrayList<TrinityPatternVariant> ordered = new ArrayList<>(variants.size());
        for (TrinityPatternVariant variant : variants) {
            if (variant == null) {
                throw new IllegalArgumentException("A Trinity acyclic graph cannot contain a null variant");
            }
            ordered.add(variant);
        }
        ordered.sort(Comparator.naturalOrder());

        HashMap<AEKey, ArrayList<TrinityPatternVariant>> producersByOutput = new HashMap<>();
        for (TrinityPatternVariant variant : ordered) {
            variant.outputs().keySet().forEach(output -> producersByOutput
                    .computeIfAbsent(output, ignored -> new ArrayList<>())
                    .add(variant));
        }

        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        LinkedHashSet<AEKey> visitedKeys = new LinkedHashSet<>();
        LinkedHashSet<TrinityPatternVariant> reachable = new LinkedHashSet<>();
        pending.add(target);
        while (!pending.isEmpty()) {
            AEKey required = pending.removeFirst();
            if (!visitedKeys.add(required)) {
                continue;
            }
            List<TrinityPatternVariant> producers = producersByOutput.get(required);
            if (producers == null) {
                continue;
            }
            for (TrinityPatternVariant producer : producers) {
                if (reachable.add(producer)) {
                    pending.addAll(producer.inputs().keySet());
                }
            }
        }
        ArrayList<TrinityPatternVariant> result = new ArrayList<>(reachable);
        result.sort(Comparator.naturalOrder());
        return Collections.unmodifiableList(result);
    }
}
