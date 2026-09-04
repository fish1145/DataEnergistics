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

/** Sharing identity: routes must reach the same projected destination and retain the same cycle/flow style. */
public record CraftingPlanRouteGroup(Style style, int destinationNodeId) {

    public boolean materialFlow() {
        return style.materialFlow();
    }

    /**
     * Shared visual facts, independent of destination, so splitting routes does not duplicate cycle lists or colors.
     */
    public record Style(boolean materialFlow, IntList cycleIds) {

        public Style {
            // Construction transfers the sorted membership list to the immutable layout.
            cycleIds = IntLists.unmodifiable(cycleIds);
        }
    }

    static Int2ObjectMap<Style> indexStyles(CraftingPlanGraph graph) {
        var diagnostic = new Style(false, IntLists.emptyList());
        Int2ObjectMap<Style> processes = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<Style> edges = new Int2ObjectOpenHashMap<>();
        var styles = new Object2ObjectOpenHashMap<Style, Style>();
        for (var edge : graph.edges()) {
            int processId = switch (edge.role()) {
                case INPUT -> edge.source();
                case OUTPUT, REMAINDER -> edge.target();
                case DIAGNOSTIC -> -1;
            };
            Style style = diagnostic;
            if (processId >= 0) {
                style = processes.computeIfAbsent(processId, id -> {
                    IntList cycles = new IntArrayList(((Process) graph.node(id)).cycleIds());
                    cycles.sort(IntComparators.NATURAL_COMPARATOR);
                    var key = new Style(true, cycles);
                    return styles.computeIfAbsent(key, unused -> key);
                });
            }
            edges.put(edge.id(), style);
        }
        return edges;
    }
}
