package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Tarjan partition and condensation DAG for one immutable variant graph.
 *
 * @param components                stable components
 * @param componentByKey            exact key-to-component lookup
 * @param topologicalOrder          input-to-output condensation order
 * @param variantsByOutputComponent explicit transition ownership for every output-side component
 * @param variantsByOutputKey       exact pre-sorted producer index for reverse demand propagation
 * @param cyclicOwnerByVariant      unique cyclic component that owns a transition with internal feedback
 */
public record TrinityCraftingTopology(
                                      List<TrinityStronglyConnectedComponent> components,
                                      Map<AEKey, Integer> componentByKey,
                                      List<Integer> topologicalOrder,
                                      Map<Integer, List<TrinityPatternVariant>> variantsByOutputComponent,
                                      Map<AEKey, List<TrinityPatternVariant>> variantsByOutputKey,
                                      Map<TrinityPatternVariant, Integer> cyclicOwnerByVariant) {

    /**
     * Validates complete component coverage and a legal condensation ordering.
     */
    public TrinityCraftingTopology {
        if (components == null || components.isEmpty() || componentByKey == null || topologicalOrder == null ||
                variantsByOutputComponent == null || variantsByOutputKey == null || cyclicOwnerByVariant == null ||
                components.size() != topologicalOrder.size()) {
            throw new IllegalArgumentException("A Trinity crafting topology requires complete components and order");
        }
        components = List.copyOf(components);
        int componentCount = components.size();
        LinkedHashMap<AEKey, Integer> copiedMapping = new LinkedHashMap<>();
        componentByKey.forEach((key, index) -> {
            if (key == null || index == null || index < 0 || index >= componentCount) {
                throw new IllegalArgumentException("A Trinity topology key must map to a valid component");
            }
            copiedMapping.put(key, index);
        });
        for (TrinityStronglyConnectedComponent component : components) {
            if (component == null || component.index() >= components.size()) {
                throw new IllegalArgumentException("A Trinity topology component index is invalid");
            }
            for (AEKey key : component.keys()) {
                if (!Integer.valueOf(component.index()).equals(copiedMapping.get(key))) {
                    throw new IllegalArgumentException("A Trinity topology must map every component key exactly");
                }
            }
        }
        LinkedHashMap<Integer, List<TrinityPatternVariant>> copiedVariants = new LinkedHashMap<>();
        variantsByOutputComponent.forEach((index, variants) -> {
            if (index == null || index < 0 || index >= componentCount || variants == null) {
                throw new IllegalArgumentException("A Trinity output transition index must reference a component");
            }
            copiedVariants.put(index, List.copyOf(variants));
        });
        variantsByOutputComponent = Collections.unmodifiableMap(copiedVariants);
        LinkedHashMap<AEKey, List<TrinityPatternVariant>> copiedProducers = new LinkedHashMap<>();
        variantsByOutputKey.forEach((key, variants) -> {
            if (key == null || variants == null || !copiedMapping.containsKey(key)) {
                throw new IllegalArgumentException("A Trinity producer index must reference a topology key");
            }
            copiedProducers.put(key, List.copyOf(variants));
        });
        variantsByOutputKey = Collections.unmodifiableMap(copiedProducers);
        LinkedHashMap<TrinityPatternVariant, Integer> copiedOwners = new LinkedHashMap<>();
        for (Map.Entry<TrinityPatternVariant, Integer> owner : cyclicOwnerByVariant.entrySet()) {
            TrinityPatternVariant variant = owner.getKey();
            Integer index = owner.getValue();
            if (variant == null || index == null || index < 0 || index >= componentCount ||
                    !components.get(index).cyclic() || !components.get(index).cycleVariants().contains(variant)) {
                throw new IllegalArgumentException("A Trinity cyclic transition owner must reference its feedback component");
            }
            copiedOwners.put(variant, index);
        }
        cyclicOwnerByVariant = Collections.unmodifiableMap(copiedOwners);
        componentByKey = Collections.unmodifiableMap(copiedMapping);
        topologicalOrder = List.copyOf(topologicalOrder);
        boolean[] seen = new boolean[components.size()];
        int[] positions = new int[components.size()];
        for (int position = 0; position < topologicalOrder.size(); position++) {
            Integer index = topologicalOrder.get(position);
            if (index == null || index < 0 || index >= components.size() || seen[index]) {
                throw new IllegalArgumentException("A Trinity condensation order must contain every component once");
            }
            seen[index] = true;
            positions[index] = position;
        }
        for (TrinityStronglyConnectedComponent component : components) {
            for (Integer successor : component.successorIndexes()) {
                if (positions[component.index()] >= positions[successor]) {
                    throw new IllegalArgumentException("A Trinity condensation order must be topological");
                }
            }
        }
    }
}
