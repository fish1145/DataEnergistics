package com.fish_dan_.data_energistics.common.crafting.tree.view;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Node;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Immutable, server-safe projection. Folding never mutates the authoritative plan or duplicates a material. */
public final class CraftingPlanGraphView {

    private final CraftingPlanGraph graph;
    private final Int2ObjectMap<Node> sourceNodes = new Int2ObjectAVLTreeMap<>();
    private final Int2IntMap aliases = new Int2IntOpenHashMap();
    private final Int2IntMap embedded = new Int2IntOpenHashMap();
    private final Int2ObjectMap<IntList> outgoing = new Int2ObjectAVLTreeMap<>();
    private final Int2ObjectMap<IntList> reverse = new Int2ObjectAVLTreeMap<>();
    private final Int2ObjectMap<IntList> displayChildren = new Int2ObjectAVLTreeMap<>();
    private final List<ViewEdge> edges;
    private final GraphComponents components;
    private final int root;

    public CraftingPlanGraphView(CraftingPlanGraph graph) {
        this.graph = graph;
        graph.nodes().forEach(node -> sourceNodes.put(node.id(), node));
        Int2ObjectMap<List<Edge>> incomingEdges = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<List<Edge>> outgoingEdges = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntList> sourceOutgoing = new Int2ObjectAVLTreeMap<>();
        for (int id : sourceNodes.keySet()) {
            sourceOutgoing.put(id, new IntArrayList());
        }
        for (Edge edge : graph.edges()) {
            sourceOutgoing.get(edge.source()).add(edge.target());
            incomingEdges.computeIfAbsent(edge.target(), unused -> new ObjectArrayList<>()).add(edge);
            outgoingEdges.computeIfAbsent(edge.source(), unused -> new ObjectArrayList<>()).add(edge);
        }
        GraphComponents sourceComponents = GraphComponents.find(sourceNodes.keySet(), sourceOutgoing);
        for (Node node : sourceNodes.values()) {
            if (!(node instanceof Process process) || !process.cycleIds().isEmpty()
                    || sourceComponents.cyclicComponents().contains(sourceComponents.componentByNode().get(node.id()))) {
                continue;
            }
            List<Edge> parents = incomingEdges.getOrDefault(node.id(), List.of());
            List<Edge> children = outgoingEdges.getOrDefault(node.id(), List.of());
            if (parents.size() != 1 || parents.getFirst().role() != Role.OUTPUT
                    || children.stream().anyMatch(edge -> edge.role() != Role.INPUT)) {
                continue;
            }
            int materialId = parents.getFirst().source();
            Material material = (Material) sourceNodes.get(materialId);
            if (!material.key().equals(process.primaryOutput())
                    || outgoingEdges.getOrDefault(materialId, List.of()).size() != 1
                    || incomingEdges.getOrDefault(materialId, List.of()).size() > 1
                    || children.stream().anyMatch(edge -> incomingEdges.getOrDefault(edge.target(), List.of()).size() > 1)) {
                continue;
            }
            aliases.put(node.id(), materialId);
            embedded.put(materialId, node.id());
        }
        for (int id : sourceNodes.keySet()) {
            if (!aliases.containsKey(id)) {
                outgoing.put(id, new IntArrayList());
                reverse.put(id, new IntArrayList());
                displayChildren.put(id, new IntArrayList());
            }
        }
        Map<Connection, IntList> edgeGroups = new Object2ObjectAVLTreeMap<>();
        for (Edge edge : graph.edges()) {
            int source = projectedId(edge.source());
            int target = projectedId(edge.target());
            if (source == target && edge.source() != edge.target() && aliases.containsKey(edge.target())) {
                continue;
            }
            edgeGroups.computeIfAbsent(new Connection(source, target), unused -> new IntArrayList()).add(edge.id());
            outgoing.get(source).add(target);
            reverse.get(target).add(source);
            displayChildren.get(source).add(target);
            // Output/remainder arrows point toward the process, but their material cards still belong to its display.
            if (edge.role() == Role.OUTPUT || edge.role() == Role.REMAINDER) {
                displayChildren.get(target).add(source);
            }
        }
        outgoing.values().forEach(list -> list.sort(IntComparators.NATURAL_COMPARATOR));
        reverse.values().forEach(list -> list.sort(IntComparators.NATURAL_COMPARATOR));
        displayChildren.values().forEach(list -> list.sort(IntComparators.NATURAL_COMPARATOR));
        components = GraphComponents.find(outgoing.keySet(), outgoing);
        List<ViewEdge> projectedEdges = new ObjectArrayList<>();
        edgeGroups.forEach((connection, ids) -> {
            ids.sort(IntComparators.NATURAL_COMPARATOR);
            int component = components.componentByNode().get(connection.source());
            boolean cyclic = component == components.componentByNode().get(connection.target())
                    && components.cyclicComponents().contains(component);
            projectedEdges.add(new ViewEdge(connection.source(), connection.target(), ids, cyclic));
        });
        edges = List.copyOf(projectedEdges);
        root = projectedId(graph.rootId());
    }

    public CraftingPlanGraph graph() {
        return graph;
    }

    /** Maps an original process id to its material card when that process is safely embedded. */
    public int projectedId(int originalId) {
        return aliases.getOrDefault(originalId, originalId);
    }

    /** Breadth-first soft budget: complete components are admitted, and remaining frontiers are folded. */
    public Set<Integer> initialCollapsed(int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("The visible-node budget must be positive");
        }
        IntSet reached = new IntOpenHashSet();
        IntSet collapsed = new IntAVLTreeSet();
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        int rootComponent = components.componentByNode().get(root);
        reached.add(rootComponent);
        queue.enqueue(rootComponent);
        int count = components.members().get(rootComponent).size();
        while (!queue.isEmpty()) {
            int current = queue.dequeueInt();
            IntSet children = childrenOfComponent(current);
            children.removeAll(reached);
            int added = 0;
            for (int child : children) {
                added += components.members().get(child).size();
            }
            if (count + added > budget && !children.isEmpty()) {
                collapsed.addAll(components.members().get(current));
                continue;
            }
            count += added;
            for (int child : children) {
                reached.add(child);
                queue.enqueue(child);
            }
        }
        return orderedSet(collapsed);
    }

    public Set<Integer> setCollapsed(Set<Integer> collapsed, int nodeId, boolean fold) {
        IntSet result = normalizeCollapsed(collapsed);
        int component = components.componentByNode().get(projectedId(nodeId));
        if (fold) {
            result.addAll(components.members().get(component));
        } else {
            for (int member : components.members().get(component)) {
                result.remove(member);
            }
        }
        return orderedSet(result);
    }

    /** Iterative recursive folding terminates on cycles and keeps independent expanded paths meaningful. */
    public Set<Integer> recursiveCollapsed(Set<Integer> collapsed, int nodeId, boolean fold) {
        IntSet result = normalizeCollapsed(collapsed);
        IntSet reached = new IntOpenHashSet();
        int start = components.componentByNode().get(projectedId(nodeId));
        IntSet protectedComponents = new IntOpenHashSet();
        if (fold) {
            visible(setCollapsed(collapsed, nodeId, true), false).nodes()
                    .forEach(node -> protectedComponents.add(node.componentId()));
            protectedComponents.remove(start);
        }
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        queue.enqueue(start);
        while (!queue.isEmpty()) {
            int component = queue.dequeueInt();
            if (!reached.add(component) || protectedComponents.contains(component)) {
                continue;
            }
            if (fold) {
                result.addAll(components.members().get(component));
            } else {
                for (int member : components.members().get(component)) {
                    result.remove(member);
                }
            }
            // Follow dependency arrows, not the reverse display attachment to co-products.
            for (int member : components.members().get(component)) {
                for (int target : outgoing.get(member)) {
                    queue.enqueue(components.componentByNode().get(target).intValue());
                }
            }
        }
        return orderedSet(result);
    }

    public ViewGraph visible(Set<Integer> collapsed, boolean missingOnly) {
        IntSet normalized = normalizeCollapsed(collapsed);
        IntSet collapsedComponents = new IntOpenHashSet();
        for (int id : normalized) {
            collapsedComponents.add(components.componentByNode().get(id).intValue());
        }
        IntSet allowed = missingOnly ? missingExplanation() : outgoing.keySet();
        IntSet visible = new IntAVLTreeSet();
        IntSet reached = new IntOpenHashSet();
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        queue.enqueue(components.componentByNode().get(root).intValue());
        while (!queue.isEmpty()) {
            int component = queue.dequeueInt();
            if (!reached.add(component)) {
                continue;
            }
            List<Integer> members = components.members().get(component);
            boolean allowedMember = false;
            for (int member : members) {
                if (allowed.contains(member)) {
                    allowedMember = true;
                    break;
                }
            }
            if (!allowedMember) {
                continue;
            }
            visible.addAll(members);
            if (collapsedComponents.contains(component)) {
                continue;
            }
            for (int child : childrenOfComponent(component)) {
                queue.enqueue(child);
            }
        }
        List<ViewNode> nodes = new ObjectArrayList<>();
        for (int id : visible) {
            int component = components.componentByNode().get(id);
            boolean folded = collapsedComponents.contains(component);
            nodes.add(new ViewNode(id, sourceNodes.get(id), embedded.containsKey(id) ? embedded.get(id) : null, component,
                    components.cyclicComponents().contains(component), folded));
        }
        List<ViewEdge> visibleEdges = edges.stream().filter(edge -> visible.contains(edge.source())
                && visible.contains(edge.target())
                && (edge.cyclic() || !collapsedComponents.contains(components.componentByNode().get(edge.source()).intValue())))
                .toList();
        List<List<Integer>> visibleComponents = components.members().stream()
                .filter(group -> visible.contains(group.getFirst().intValue())).toList();
        return new ViewGraph(graph, root, nodes, visibleEdges, visibleComponents);
    }

    private IntSet normalizeCollapsed(Set<Integer> collapsed) {
        IntSet result = new IntAVLTreeSet();
        for (int id : collapsed) {
            result.add(projectedId(id));
        }
        return result;
    }

    private IntSet childrenOfComponent(int component) {
        IntSet result = new IntAVLTreeSet();
        for (int id : components.members().get(component)) {
            for (int child : displayChildren.get(id)) {
                int target = components.componentByNode().get(child);
                if (target != component) {
                    result.add(target);
                }
            }
        }
        return result;
    }

    private IntSet missingExplanation() {
        IntSet reachesMissing = new IntOpenHashSet();
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (Node node : sourceNodes.values()) {
            if (node instanceof Material material
                    && (material.missing().signum() > 0 || material.unresolved().signum() > 0)) {
                queue.enqueue(projectedId(node.id()));
            }
        }
        while (!queue.isEmpty()) {
            int id = queue.dequeueInt();
            if (reachesMissing.add(id)) {
                for (int parent : reverse.get(id)) {
                    queue.enqueue(parent);
                }
            }
        }
        // A successful complete plan still has a meaningful target card.
        reachesMissing.add(root);
        IntSet reachable = new IntOpenHashSet();
        queue.enqueue(root);
        while (!queue.isEmpty()) {
            int id = queue.dequeueInt();
            if (reachable.add(id)) {
                for (int child : outgoing.get(id)) {
                    queue.enqueue(child);
                }
            }
        }
        reachesMissing.retainAll(reachable);
        return reachesMissing;
    }

    private static Set<Integer> orderedSet(Set<Integer> values) {
        return IntSets.unmodifiable(new IntAVLTreeSet(values));
    }

    public record ViewGraph(CraftingPlanGraph source, int rootId, List<ViewNode> nodes,
            List<ViewEdge> edges, List<List<Integer>> components) {

        public ViewGraph {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            components = components.stream().map(List::copyOf).toList();
        }
    }

    public record ViewNode(int id, Node sourceNode, @Nullable Integer embeddedProcessId,
            int componentId, boolean cyclic, boolean collapsed) {}

    public record ViewEdge(int source, int target, List<Integer> originalEdgeIds, boolean cyclic) {

        public ViewEdge {
            originalEdgeIds = List.copyOf(originalEdgeIds);
        }
    }

    private record Connection(int source, int target) implements Comparable<Connection> {

        @Override
        public int compareTo(Connection other) {
            int sourceOrder = Integer.compare(source, other.source);
            return sourceOrder != 0 ? sourceOrder : Integer.compare(target, other.target);
        }
    }
}
