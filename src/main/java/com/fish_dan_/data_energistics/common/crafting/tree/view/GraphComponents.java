package com.fish_dan_.data_energistics.common.crafting.tree.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Iterative Kosaraju decomposition; even a very deep dependency chain does not use the Java stack. */
public record GraphComponents(Map<Integer, Integer> componentByNode, List<List<Integer>> members,
        Set<Integer> cyclicComponents) {

    public GraphComponents {
        componentByNode = Collections.unmodifiableMap(new TreeMap<>(componentByNode));
        members = members.stream().map(List::copyOf).toList();
        cyclicComponents = Set.copyOf(cyclicComponents);
    }

    public static GraphComponents find(Collection<Integer> nodes, Map<Integer, List<Integer>> outgoing) {
        List<Integer> ordered = nodes.stream().sorted().toList();
        Map<Integer, List<Integer>> reverse = new HashMap<>();
        ordered.forEach(id -> reverse.put(id, new ArrayList<>()));
        for (int source : ordered) {
            for (int target : outgoing.getOrDefault(source, List.of())) {
                if (!reverse.containsKey(target)) {
                    throw new IllegalArgumentException("Unknown dependency node " + target);
                }
                reverse.get(target).add(source);
            }
        }
        reverse.values().forEach(list -> list.sort(Integer::compare));
        Set<Integer> visited = new HashSet<>();
        List<Integer> finished = new ArrayList<>();
        ArrayDeque<Visit> stack = new ArrayDeque<>();
        for (int start : ordered) {
            stack.push(new Visit(start, false));
            while (!stack.isEmpty()) {
                Visit visit = stack.pop();
                if (visit.finish()) {
                    finished.add(visit.id());
                } else if (visited.add(visit.id())) {
                    stack.push(new Visit(visit.id(), true));
                    List<Integer> targets = outgoing.getOrDefault(visit.id(), List.of());
                    for (int index = targets.size() - 1; index >= 0; index--) {
                        if (!visited.contains(targets.get(index))) {
                            stack.push(new Visit(targets.get(index), false));
                        }
                    }
                }
            }
        }
        visited.clear();
        List<List<Integer>> groups = new ArrayList<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int index = finished.size() - 1; index >= 0; index--) {
            int start = finished.get(index);
            if (visited.contains(start)) {
                continue;
            }
            List<Integer> group = new ArrayList<>();
            pending.push(start);
            while (!pending.isEmpty()) {
                int node = pending.pop();
                if (visited.add(node)) {
                    group.add(node);
                    reverse.get(node).forEach(pending::push);
                }
            }
            group.sort(Integer::compare);
            groups.add(group);
        }
        groups.sort(Comparator.comparingInt(List::getFirst));
        Map<Integer, Integer> byNode = new HashMap<>();
        Set<Integer> cyclic = new HashSet<>();
        for (int index = 0; index < groups.size(); index++) {
            List<Integer> group = groups.get(index);
            for (int id : group) {
                byNode.put(id, index);
            }
            if (group.size() > 1 || outgoing.getOrDefault(group.getFirst(), List.of()).contains(group.getFirst())) {
                cyclic.add(index);
            }
        }
        return new GraphComponents(byNode, groups, cyclic);
    }

    private record Visit(int id, boolean finish) {}
}
