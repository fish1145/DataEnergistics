package com.fish_dan_.data_energistics.common.crafting.tree.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Node;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;

/** Immutable, server-safe projection. Folding never mutates the authoritative plan or duplicates a material. */
public final class CraftingPlanGraphView {

    private final CraftingPlanGraph graph;
    private final Map<Integer, Node> sourceNodes = new TreeMap<>();
    private final Map<Integer, Integer> aliases = new HashMap<>();
    private final Map<Integer, Integer> embedded = new HashMap<>();
    private final Map<Integer, List<Integer>> outgoing = new TreeMap<>();
    private final Map<Integer, List<Integer>> reverse = new TreeMap<>();
    private final Map<Integer, List<Integer>> displayChildren = new TreeMap<>();
    private final List<ViewEdge> edges;
    private final GraphComponents components;
    private final int root;

    public CraftingPlanGraphView(CraftingPlanGraph graph) {
        this.graph = graph;
        graph.nodes().forEach(node -> sourceNodes.put(node.id(), node));
        Map<Integer, List<Edge>> incomingEdges = new HashMap<>();
        Map<Integer, List<Edge>> outgoingEdges = new HashMap<>();
        Map<Integer, List<Integer>> sourceOutgoing = new TreeMap<>();
        sourceNodes.keySet().forEach(id -> sourceOutgoing.put(id, new ArrayList<>()));
        for (Edge edge : graph.edges()) {
            sourceOutgoing.get(edge.source()).add(edge.target());
            incomingEdges.computeIfAbsent(edge.target(), unused -> new ArrayList<>()).add(edge);
            outgoingEdges.computeIfAbsent(edge.source(), unused -> new ArrayList<>()).add(edge);
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
            if (!(sourceNodes.get(materialId) instanceof Material material)
                    || !material.key().equals(process.primaryOutput())
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
                outgoing.put(id, new ArrayList<>());
                reverse.put(id, new ArrayList<>());
                displayChildren.put(id, new ArrayList<>());
            }
        }
        Map<Connection, List<Integer>> edgeGroups = new TreeMap<>();
        for (Edge edge : graph.edges()) {
            int source = projectedId(edge.source());
            int target = projectedId(edge.target());
            if (source == target && edge.source() != edge.target() && aliases.containsKey(edge.target())) {
                continue;
            }
            edgeGroups.computeIfAbsent(new Connection(source, target), unused -> new ArrayList<>()).add(edge.id());
            outgoing.get(source).add(target);
            reverse.get(target).add(source);
            displayChildren.get(source).add(target);
            // Output/remainder arrows point toward the process, but their material cards still belong to its display.
            if (edge.role() == Role.OUTPUT || edge.role() == Role.REMAINDER) {
                displayChildren.get(target).add(source);
            }
        }
        outgoing.values().forEach(list -> list.sort(Integer::compare));
        reverse.values().forEach(list -> list.sort(Integer::compare));
        displayChildren.values().forEach(list -> list.sort(Integer::compare));
        components = GraphComponents.find(outgoing.keySet(), outgoing);
        List<ViewEdge> projectedEdges = new ArrayList<>();
        edgeGroups.forEach((connection, ids) -> {
            ids.sort(Integer::compare);
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
        if (!sourceNodes.containsKey(originalId)) {
            throw new IllegalArgumentException("Unknown plan node " + originalId);
        }
        return aliases.getOrDefault(originalId, originalId);
    }

    /** Breadth-first soft budget: complete components are admitted, and remaining frontiers are folded. */
    public Set<Integer> initialCollapsed(int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("The visible-node budget must be positive");
        }
        Set<Integer> reached = new HashSet<>();
        Set<Integer> collapsed = new TreeSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int rootComponent = components.componentByNode().get(root);
        reached.add(rootComponent);
        queue.add(rootComponent);
        int count = components.members().get(rootComponent).size();
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            Set<Integer> children = childrenOfComponent(current);
            children.removeAll(reached);
            int added = children.stream().mapToInt(child -> components.members().get(child).size()).sum();
            if (count + added > budget && !children.isEmpty()) {
                collapsed.addAll(components.members().get(current));
                continue;
            }
            count += added;
            for (int child : children) {
                reached.add(child);
                queue.addLast(child);
            }
        }
        return orderedSet(collapsed);
    }

    public Set<Integer> setCollapsed(Set<Integer> collapsed, int nodeId, boolean fold) {
        Set<Integer> result = normalizeCollapsed(collapsed);
        int component = components.componentByNode().get(projectedId(nodeId));
        if (fold) {
            result.addAll(components.members().get(component));
        } else {
            components.members().get(component).forEach(result::remove);
        }
        return orderedSet(result);
    }

    /** Iterative recursive folding terminates on cycles and keeps independent expanded paths meaningful. */
    public Set<Integer> recursiveCollapsed(Set<Integer> collapsed, int nodeId, boolean fold) {
        Set<Integer> result = normalizeCollapsed(collapsed);
        Set<Integer> reached = new HashSet<>();
        int start = components.componentByNode().get(projectedId(nodeId));
        Set<Integer> protectedComponents = new HashSet<>();
        if (fold) {
            visible(setCollapsed(collapsed, nodeId, true), false).nodes()
                    .forEach(node -> protectedComponents.add(node.componentId()));
            protectedComponents.remove(start);
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int component = queue.removeFirst();
            if (!reached.add(component) || protectedComponents.contains(component)) {
                continue;
            }
            if (fold) {
                result.addAll(components.members().get(component));
            } else {
                components.members().get(component).forEach(result::remove);
            }
            // Follow dependency arrows, not the reverse display attachment to co-products.
            for (int member : components.members().get(component)) {
                outgoing.get(member).forEach(target -> queue.addLast(components.componentByNode().get(target)));
            }
        }
        return orderedSet(result);
    }

    public ViewGraph visible(Set<Integer> collapsed, boolean missingOnly) {
        Set<Integer> normalized = normalizeCollapsed(collapsed);
        Set<Integer> collapsedComponents = new HashSet<>();
        normalized.forEach(id -> collapsedComponents.add(components.componentByNode().get(id)));
        Set<Integer> allowed = missingOnly ? missingExplanation() : outgoing.keySet();
        Set<Integer> visible = new TreeSet<>();
        Set<Integer> reached = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(components.componentByNode().get(root));
        while (!queue.isEmpty()) {
            int component = queue.removeFirst();
            if (!reached.add(component)) {
                continue;
            }
            List<Integer> members = components.members().get(component);
            if (members.stream().noneMatch(allowed::contains)) {
                continue;
            }
            visible.addAll(members);
            if (collapsedComponents.contains(component)) {
                continue;
            }
            queue.addAll(childrenOfComponent(component));
        }
        List<ViewNode> nodes = new ArrayList<>();
        for (int id : visible) {
            int component = components.componentByNode().get(id);
            boolean folded = collapsedComponents.contains(component);
            nodes.add(new ViewNode(id, sourceNodes.get(id), embedded.get(id), component,
                    components.cyclicComponents().contains(component), folded));
        }
        List<ViewEdge> visibleEdges = edges.stream().filter(edge -> visible.contains(edge.source())
                && visible.contains(edge.target())).toList();
        List<List<Integer>> visibleComponents = components.members().stream()
                .filter(group -> visible.contains(group.getFirst())).toList();
        return new ViewGraph(graph, root, nodes, visibleEdges, visibleComponents);
    }

    private Set<Integer> normalizeCollapsed(Set<Integer> collapsed) {
        Set<Integer> result = new TreeSet<>();
        for (int id : collapsed) {
            result.add(projectedId(id));
        }
        return result;
    }

    private Set<Integer> childrenOfComponent(int component) {
        Set<Integer> result = new TreeSet<>();
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

    private Set<Integer> missingExplanation() {
        Set<Integer> reachesMissing = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Node node : sourceNodes.values()) {
            if (node instanceof Material material
                    && (material.missing().signum() > 0 || material.unresolved().signum() > 0)) {
                queue.add(projectedId(node.id()));
            }
        }
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (reachesMissing.add(id)) {
                queue.addAll(reverse.get(id));
            }
        }
        // A successful complete plan still has a meaningful target card.
        reachesMissing.add(root);
        Set<Integer> reachable = new HashSet<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (reachable.add(id)) {
                queue.addAll(outgoing.get(id));
            }
        }
        reachesMissing.retainAll(reachable);
        return reachesMissing;
    }

    private static Set<Integer> orderedSet(Set<Integer> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
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
