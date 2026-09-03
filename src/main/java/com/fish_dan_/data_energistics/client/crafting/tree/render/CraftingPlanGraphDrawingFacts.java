package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;

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
    private final Int2ObjectMap<CycleMark> cycles = new Int2ObjectOpenHashMap<>();

    public CraftingPlanGraphDrawingFacts(CraftingPlanGraph graph) {
        ObjectArrayList<CraftingPlanGraph.Cycle> orderedCycles = new ObjectArrayList<>(graph.cycles());
        orderedCycles.sort(Comparator.comparingInt(CraftingPlanGraph.Cycle::ordinal));
        for (var cycle : orderedCycles) {
            CycleMark mark = new CycleMark(cycle.id(), cycle.ordinal(), CraftingPlanGraphPalette.cycle(cycle.ordinal()));
            this.cycles.put(cycle.id(), mark);
            for (int node : cycle.nodeIds()) this.nodes.computeIfAbsent(node, unused -> new ObjectArrayList<>()).add(mark);
        }
        for (var entry : Int2ObjectMaps.fastIterable(this.nodes)) {
            this.labels.put(entry.getIntKey(), label(entry.getValue()));
            // The snapshot owns these lists; expose a read-only view without copying every membership list.
            entry.setValue(ObjectLists.unmodifiable(entry.getValue()));
        }
    }

    public List<CycleMark> node(int id) {
        return this.nodes.getOrDefault(id, ObjectLists.emptyList());
    }

    public String label(int id) {
        return this.labels.get(id);
    }

    public RouteStyle route(CraftingPlanRouteGroup group) {
        ObjectList<CycleMark> marks = new ObjectArrayList<>(group.cycleIds().size());
        for (int cycleId : group.cycleIds()) marks.add(this.cycles.get(cycleId));
        marks.sort(Comparator.comparingInt(CycleMark::ordinal));
        return new RouteStyle(ObjectLists.unmodifiable(marks), group.materialFlow());
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

        public int lineColor() {
            return this.materialFlow ? color(0) : CraftingPlanGraphPalette.DIAGNOSTIC;
        }

        public double lineOpacity() {
            return (lineColor() >>> 24) / 255D;
        }
    }
}
