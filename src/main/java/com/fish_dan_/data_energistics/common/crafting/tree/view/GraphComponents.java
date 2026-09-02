package com.fish_dan_.data_energistics.common.crafting.tree.view;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Iterative Kosaraju decomposition; even a very deep dependency chain does not use the Java stack. */
public record GraphComponents(Map<Integer, Integer> componentByNode, List<List<Integer>> members,
                              Set<Integer> cyclicComponents) {

    public GraphComponents {
        componentByNode = Int2IntMaps.unmodifiable(new Int2IntAVLTreeMap(componentByNode));
        members = members.stream().map(group -> (List<Integer>) IntLists.unmodifiable(new IntArrayList(group))).toList();
        cyclicComponents = IntSets.unmodifiable(new IntOpenHashSet(cyclicComponents));
    }

    public static GraphComponents find(Collection<Integer> nodes, Map<Integer, ? extends List<Integer>> outgoing) {
        IntList ordered = new IntArrayList(nodes);
        ordered.sort(IntComparators.NATURAL_COMPARATOR);
        Int2ObjectMap<IntList> reverse = new Int2ObjectOpenHashMap<>();
        for (int id : ordered) {
            reverse.put(id, new IntArrayList());
        }
        for (int source : ordered) {
            for (int target : outgoing.get(source)) {
                reverse.get(target).add(source);
            }
        }
        reverse.values().forEach(list -> list.sort(IntComparators.NATURAL_COMPARATOR));
        IntSet visited = new IntOpenHashSet();
        IntList finished = new IntArrayList();
        IntArrayList stack = new IntArrayList();
        for (int start : ordered) {
            stack.push(start);
            while (!stack.isEmpty()) {
                int visit = stack.popInt();
                // Validated graph IDs are nonnegative, so complemented IDs encode the finish phase without objects.
                if (visit < 0) {
                    finished.add(~visit);
                } else if (visited.add(visit)) {
                    stack.push(~visit);
                    List<Integer> targets = outgoing.get(visit);
                    for (int index = targets.size() - 1; index >= 0; index--) {
                        int target = targets.get(index);
                        if (!visited.contains(target)) {
                            stack.push(target);
                        }
                    }
                }
            }
        }
        visited.clear();
        List<List<Integer>> groups = new ObjectArrayList<>();
        IntArrayList pending = new IntArrayList();
        for (int index = finished.size() - 1; index >= 0; index--) {
            int start = finished.getInt(index);
            if (visited.contains(start)) {
                continue;
            }
            IntList group = new IntArrayList();
            pending.push(start);
            while (!pending.isEmpty()) {
                int node = pending.popInt();
                if (visited.add(node)) {
                    group.add(node);
                    for (int predecessor : reverse.get(node)) {
                        pending.push(predecessor);
                    }
                }
            }
            group.sort(IntComparators.NATURAL_COMPARATOR);
            groups.add(group);
        }
        groups.sort(Comparator.comparingInt(List::getFirst));
        Int2IntMap byNode = new Int2IntOpenHashMap();
        IntSet cyclic = new IntOpenHashSet();
        for (int index = 0; index < groups.size(); index++) {
            List<Integer> group = groups.get(index);
            for (int id : group) {
                byNode.put(id, index);
            }
            if (group.size() > 1 || outgoing.get(group.getFirst()).contains(group.getFirst())) {
                cyclic.add(index);
            }
        }
        return new GraphComponents(byNode, groups, cyclic);
    }
}
