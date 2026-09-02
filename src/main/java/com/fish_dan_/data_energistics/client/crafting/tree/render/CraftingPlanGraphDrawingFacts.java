package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/** Per-snapshot rendering facts; cycle membership comes from plan metadata, never visual SCC membership. */
public final class CraftingPlanGraphDrawingFacts {

    private final Int2ObjectMap<ObjectList<CycleMark>> nodes = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<String> labels = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<RouteStyle> edges = new Int2ObjectOpenHashMap<>();

    public CraftingPlanGraphDrawingFacts(CraftingPlanGraph graph) {
        Int2ObjectMap<CycleMark> cycles = new Int2ObjectOpenHashMap<>();
        ObjectArrayList<CraftingPlanGraph.Cycle> orderedCycles = new ObjectArrayList<>(graph.cycles());
        orderedCycles.sort(Comparator.comparingInt(CraftingPlanGraph.Cycle::ordinal));
        for (var cycle : orderedCycles) {
            CycleMark mark = new CycleMark(cycle.id(), cycle.ordinal(), CraftingPlanGraphPalette.cycle(cycle.ordinal()));
            cycles.put(cycle.id(), mark);
            for (int node : cycle.nodeIds()) this.nodes.computeIfAbsent(node, unused -> new ObjectArrayList<>()).add(mark);
        }
        for (var entry : Int2ObjectMaps.fastIterable(this.nodes)) {
            this.labels.put(entry.getIntKey(), label(entry.getValue()));
            // The snapshot owns these lists; expose a read-only view without copying every membership list.
            entry.setValue(ObjectLists.unmodifiable(entry.getValue()));
        }
        for (var edge : graph.edges()) {
            int processId = switch (edge.role()) {
                case INPUT -> edge.source();
                case OUTPUT, REMAINDER -> edge.target();
                case DIAGNOSTIC -> -1;
            };
            if (processId < 0) {
                this.edges.put(edge.id(), new RouteStyle(ObjectLists.emptyList(), false));
                continue;
            }
            Process process = (Process) graph.node(processId);
            ObjectList<CycleMark> marks = new ObjectArrayList<>(process.cycleIds().size());
            for (int cycleId : process.cycleIds()) marks.add(cycles.get(cycleId));
            marks.sort(Comparator.comparingInt(CycleMark::ordinal));
            this.edges.put(edge.id(), new RouteStyle(ObjectLists.unmodifiable(marks), true));
        }
    }

    public List<CycleMark> node(int id) {
        return this.nodes.getOrDefault(id, ObjectLists.emptyList());
    }

    public String label(int id) {
        return this.labels.get(id);
    }

    public RouteStyle route(List<Integer> originalEdgeIds) {
        if (originalEdgeIds.size() == 1) return this.edges.get(originalEdgeIds.getFirst().intValue());
        Int2ObjectMap<CycleMark> combined = new Int2ObjectOpenHashMap<>();
        boolean materialFlow = false;
        for (int edgeId : originalEdgeIds) {
            RouteStyle style = this.edges.get(edgeId);
            materialFlow |= style.materialFlow();
            for (CycleMark mark : style.cycles()) combined.put(mark.id(), mark);
        }
        ObjectList<CycleMark> marks = new ObjectArrayList<>(combined.values());
        marks.sort(Comparator.comparingInt(CycleMark::ordinal));
        return new RouteStyle(ObjectLists.unmodifiable(marks), materialFlow);
    }

    private static String label(List<CycleMark> marks) {
        StringJoiner label = new StringJoiner(", ", "↻ ", "");
        for (CycleMark mark : marks) label.add(Integer.toString(mark.ordinal()));
        return label.toString();
    }

    public record CycleMark(int id, int ordinal, int color) {}

    public record RouteStyle(List<CycleMark> cycles, boolean materialFlow) {

        public int color(int band) {
            return this.cycles.isEmpty() ? CraftingPlanGraphPalette.EDGE : this.cycles.get(band % this.cycles.size()).color();
        }
    }
}
