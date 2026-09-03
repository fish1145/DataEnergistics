package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/** Semantic lane identity; equal palette colors never make different cycles share a route. */
public record CraftingPlanRouteGroup(boolean materialFlow, IntList cycleIds) {

    public CraftingPlanRouteGroup {
        // Construction transfers the sorted membership list to the immutable layout.
        cycleIds = IntLists.unmodifiable(cycleIds);
    }

    static Int2ObjectMap<CraftingPlanRouteGroup> index(CraftingPlanGraph graph) {
        var diagnostic = new CraftingPlanRouteGroup(false, IntLists.emptyList());
        Int2ObjectMap<CraftingPlanRouteGroup> processes = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<CraftingPlanRouteGroup> edges = new Int2ObjectOpenHashMap<>();
        var groups = new Object2ObjectOpenHashMap<CraftingPlanRouteGroup, CraftingPlanRouteGroup>();
        for (var edge : graph.edges()) {
            int processId = switch (edge.role()) {
                case INPUT -> edge.source();
                case OUTPUT, REMAINDER -> edge.target();
                case DIAGNOSTIC -> -1;
            };
            CraftingPlanRouteGroup group = diagnostic;
            if (processId >= 0) {
                group = processes.computeIfAbsent(processId, id -> {
                    IntList cycles = new IntArrayList(((Process) graph.node(id)).cycleIds());
                    cycles.sort(IntComparators.NATURAL_COMPARATOR);
                    var key = new CraftingPlanRouteGroup(true, cycles);
                    return groups.computeIfAbsent(key, unused -> key);
                });
            }
            edges.put(edge.id(), group);
        }
        return edges;
    }
}
