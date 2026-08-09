package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Partitions the immutable AE key hypergraph with Tarjan and builds its condensation DAG.
 * <p>
 * Stable Tarjan implementation over input-to-output edges induced by every bound hypertransition.
 */
public final class TrinityGraphTopologyAnalyzer {

    /**
     * @return stateless deterministic analyzer
     */
    public static TrinityGraphTopologyAnalyzer create() {
        return new TrinityGraphTopologyAnalyzer();
    }

    /**
     * @param snapshot   graph key order and revision
     * @param variants   complete bound transition set for the snapshot
     * @param maxSccKeys configured per-component key limit
     * @return topology or {@code SCC_KEY_LIMIT}
     */
    public TrinityAlgorithmResult<TrinityCraftingTopology> analyze(
                                                                   TrinityCraftingGraphSnapshot snapshot,
                                                                   List<TrinityPatternVariant> variants,
                                                                   int maxSccKeys) {
        if (snapshot == null || variants == null || maxSccKeys <= 0) {
            throw new IllegalArgumentException(
                    "A Trinity topology analysis requires complete inputs and a positive SCC key limit");
        }

        Graph graph = Graph.create(snapshot, variants);
        if (graph.keys().isEmpty()) {
            throw new IllegalArgumentException("A Trinity topology requires at least one graph key");
        }
        List<List<Integer>> rawComponents = tarjan(graph.adjacency());
        rawComponents.sort(Comparator.comparingInt(component -> component.stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElseThrow()));

        for (List<Integer> component : rawComponents) {
            if (component.size() > maxSccKeys) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.SCC_KEY_LIMIT,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.scc_key_limit"),
                        Map.of(
                                "limit", Integer.toString(maxSccKeys),
                                "required", Integer.toString(component.size()))));
            }
        }
        return TrinityAlgorithmResult.success(buildTopology(graph, variants, rawComponents));
    }

    private static TrinityCraftingTopology buildTopology(
                                                         Graph graph,
                                                         List<TrinityPatternVariant> variants,
                                                         List<List<Integer>> rawComponents) {
        int[] componentByNode = new int[graph.keys().size()];
        Arrays.fill(componentByNode, -1);
        for (int componentIndex = 0; componentIndex < rawComponents.size(); componentIndex++) {
            for (Integer node : rawComponents.get(componentIndex)) {
                componentByNode[node] = componentIndex;
            }
        }

        ArrayList<TreeSet<Integer>> predecessors = new ArrayList<>(rawComponents.size());
        ArrayList<TreeSet<Integer>> successors = new ArrayList<>(rawComponents.size());
        for (int index = 0; index < rawComponents.size(); index++) {
            predecessors.add(new TreeSet<>());
            successors.add(new TreeSet<>());
        }
        boolean[] selfEdges = new boolean[rawComponents.size()];
        for (int input = 0; input < graph.adjacency().size(); input++) {
            int inputComponent = componentByNode[input];
            for (Integer output : graph.adjacency().get(input)) {
                int outputComponent = componentByNode[output];
                if (inputComponent == outputComponent) {
                    if (input == output) {
                        selfEdges[inputComponent] = true;
                    }
                } else {
                    successors.get(inputComponent).add(outputComponent);
                    predecessors.get(outputComponent).add(inputComponent);
                }
            }
        }

        ArrayList<List<TrinityPatternVariant>> cycleVariants = new ArrayList<>(rawComponents.size());
        ArrayList<List<TrinityPatternVariant>> outputVariants = new ArrayList<>(rawComponents.size());
        for (int index = 0; index < rawComponents.size(); index++) {
            cycleVariants.add(new ArrayList<>());
            outputVariants.add(new ArrayList<>());
        }
        for (TrinityPatternVariant variant : variants) {
            HashSet<Integer> inputComponents = new HashSet<>();
            HashSet<Integer> outputComponents = new HashSet<>();
            variant.inputs().keySet().forEach(key -> inputComponents.add(
                    componentByNode[graph.indexByKey().get(key)]));
            variant.outputs().keySet().forEach(key -> outputComponents.add(
                    componentByNode[graph.indexByKey().get(key)]));
            for (Integer outputComponent : outputComponents) {
                outputVariants.get(outputComponent).add(variant);
                if (inputComponents.contains(outputComponent)) {
                    cycleVariants.get(outputComponent).add(variant);
                }
            }
        }

        ArrayList<TrinityStronglyConnectedComponent> components = new ArrayList<>(rawComponents.size());
        LinkedHashMap<AEKey, Integer> mapping = new LinkedHashMap<>();
        for (int componentIndex = 0; componentIndex < rawComponents.size(); componentIndex++) {
            List<Integer> nodes = rawComponents.get(componentIndex).stream().sorted().toList();
            List<AEKey> keys = nodes.stream().map(graph.keys()::get).toList();
            for (AEKey key : keys) {
                mapping.put(key, componentIndex);
            }
            cycleVariants.get(componentIndex).sort(Comparator.naturalOrder());
            outputVariants.get(componentIndex).sort(Comparator.naturalOrder());
            components.add(new TrinityStronglyConnectedComponent(
                    componentIndex,
                    keys,
                    nodes.size() > 1 || selfEdges[componentIndex],
                    cycleVariants.get(componentIndex),
                    List.copyOf(predecessors.get(componentIndex)),
                    List.copyOf(successors.get(componentIndex))));
        }
        LinkedHashMap<Integer, List<TrinityPatternVariant>> variantsByOutputComponent = new LinkedHashMap<>();
        for (int componentIndex = 0; componentIndex < outputVariants.size(); componentIndex++) {
            variantsByOutputComponent.put(componentIndex, List.copyOf(outputVariants.get(componentIndex)));
        }
        LinkedHashMap<AEKey, List<TrinityPatternVariant>> variantsByOutputKey = new LinkedHashMap<>();
        LinkedHashMap<AEKey, ArrayList<TrinityPatternVariant>> producerLists = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            variant.outputs().keySet().forEach(key -> producerLists
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(variant));
        }
        for (AEKey key : graph.keys()) {
            ArrayList<TrinityPatternVariant> producers = producerLists.get(key);
            if (producers != null) {
                producers.sort(Comparator.naturalOrder());
                variantsByOutputKey.put(key, List.copyOf(producers));
            }
        }
        LinkedHashMap<TrinityPatternVariant, Integer> cyclicOwnerByVariant = new LinkedHashMap<>();
        for (TrinityStronglyConnectedComponent component : components) {
            if (!component.cyclic()) {
                continue;
            }
            for (TrinityPatternVariant variant : component.cycleVariants()) {
                Integer previous = cyclicOwnerByVariant.putIfAbsent(variant, component.index());
                if (previous != null && previous != component.index()) {
                    throw new IllegalStateException("A Trinity feedback transition cannot belong to multiple SCCs");
                }
            }
        }
        return new TrinityCraftingTopology(
                components,
                mapping,
                topologicalOrder(predecessors, successors),
                variantsByOutputComponent,
                variantsByOutputKey,
                cyclicOwnerByVariant);
    }

    private static List<Integer> topologicalOrder(
                                                  List<? extends Set<Integer>> predecessors,
                                                  List<? extends Set<Integer>> successors) {
        int[] indegree = predecessors.stream().mapToInt(Set::size).toArray();
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int index = 0; index < indegree.length; index++) {
            if (indegree[index] == 0) {
                ready.add(index);
            }
        }
        ArrayList<Integer> order = new ArrayList<>(indegree.length);
        while (!ready.isEmpty()) {
            int component = ready.remove();
            order.add(component);
            for (Integer successor : successors.get(component)) {
                indegree[successor]--;
                if (indegree[successor] == 0) {
                    ready.add(successor);
                }
            }
        }
        if (order.size() != indegree.length) {
            throw new IllegalStateException("Tarjan condensation unexpectedly contains a cycle");
        }
        return List.copyOf(order);
    }

    private static List<List<Integer>> tarjan(List<List<Integer>> adjacency) {
        TarjanState state = new TarjanState(adjacency);
        for (int node = 0; node < adjacency.size(); node++) {
            if (state.indexes[node] < 0) {
                state.visit(node);
            }
        }
        return state.components;
    }

    private record Graph(
                         List<AEKey> keys,
                         Map<AEKey, Integer> indexByKey,
                         List<List<Integer>> adjacency) {

        private static Graph create(TrinityCraftingGraphSnapshot snapshot,
                                    List<TrinityPatternVariant> variants) {
            LinkedHashSet<AEKey> orderedKeys = new LinkedHashSet<>(snapshot.keys());
            for (TrinityPatternVariant variant : variants) {
                if (variant == null) {
                    throw new IllegalArgumentException("A Trinity topology cannot contain a null variant");
                }
                orderedKeys.addAll(variant.inputs().keySet());
                orderedKeys.addAll(variant.outputs().keySet());
            }
            List<AEKey> keys = List.copyOf(orderedKeys);
            LinkedHashMap<AEKey, Integer> indexByKey = new LinkedHashMap<>();
            for (int index = 0; index < keys.size(); index++) {
                indexByKey.put(keys.get(index), index);
            }
            ArrayList<LinkedHashSet<Integer>> edges = new ArrayList<>(keys.size());
            for (int index = 0; index < keys.size(); index++) {
                edges.add(new LinkedHashSet<>());
            }
            for (TrinityPatternVariant variant : variants) {
                for (AEKey input : variant.inputs().keySet()) {
                    int inputIndex = indexByKey.get(input);
                    for (AEKey output : variant.outputs().keySet()) {
                        edges.get(inputIndex).add(indexByKey.get(output));
                    }
                }
            }
            ArrayList<List<Integer>> adjacency = new ArrayList<>(keys.size());
            edges.forEach(nodeEdges -> adjacency.add(List.copyOf(nodeEdges)));
            return new Graph(
                    keys,
                    Collections.unmodifiableMap(indexByKey),
                    List.copyOf(adjacency));
        }
    }

    private static final class TarjanState {

        private final List<List<Integer>> adjacency;
        private final int[] indexes;
        private final int[] lowLinks;
        private final boolean[] onStack;
        private final ArrayDeque<Integer> stack = new ArrayDeque<>();
        private final List<List<Integer>> components = new ArrayList<>();
        private int nextIndex;

        private TarjanState(List<List<Integer>> adjacency) {
            this.adjacency = adjacency;
            this.indexes = new int[adjacency.size()];
            this.lowLinks = new int[adjacency.size()];
            this.onStack = new boolean[adjacency.size()];
            Arrays.fill(this.indexes, -1);
        }

        private void visit(int node) {
            this.indexes[node] = this.nextIndex;
            this.lowLinks[node] = this.nextIndex;
            this.nextIndex++;
            this.stack.push(node);
            this.onStack[node] = true;

            for (Integer successor : this.adjacency.get(node)) {
                if (this.indexes[successor] < 0) {
                    visit(successor);
                    this.lowLinks[node] = Math.min(this.lowLinks[node], this.lowLinks[successor]);
                } else if (this.onStack[successor]) {
                    this.lowLinks[node] = Math.min(this.lowLinks[node], this.indexes[successor]);
                }
            }

            if (this.lowLinks[node] == this.indexes[node]) {
                ArrayList<Integer> component = new ArrayList<>();
                int member;
                do {
                    member = this.stack.pop();
                    this.onStack[member] = false;
                    component.add(member);
                } while (member != node);
                this.components.add(component);
            }
        }
    }
}
