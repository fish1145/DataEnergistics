package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Cycle;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Header;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Kind;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Node;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.Expansion;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import appeng.api.stacks.AEItemKey;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Numeric layout regressions; no rendering, client, optional mods or screenshot assertions. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class CraftingPlanGraphLayoutGameTest {

    private CraftingPlanGraphLayoutGameTest() {}

    @GameTest(template = "empty_5x5")
    public static void keepsUnevenDependencyBranchesTogether(GameTestHelper helper) {
        CraftingPlanGraph graph = graph(List.of(material(0, Items.DIAMOND), material(1, Items.IRON_INGOT),
                material(2, Items.GOLD_INGOT), material(3, Items.COAL), material(4, Items.STONE),
                material(5, Items.DIRT), material(6, Items.SAND), process(7, Items.DIAMOND),
                process(8, Items.IRON_INGOT), process(9, Items.GOLD_INGOT)),
                new int[][] { { 0, 7 }, { 7, 1 }, { 7, 2 }, { 1, 8 }, { 8, 3 },
                        { 2, 9 }, { 9, 4 }, { 9, 5 }, { 9, 6 } },
                List.of());
        for (boolean compact : new boolean[] { false, true }) {
            var view = new CraftingPlanGraphView(graph).visible(Expansion.empty(), false);
            Layout layout = CraftingPlanGraphLayout.layout(view, compact);
            var placed = index(layout);
            helper.assertTrue(Math.abs(center(placed.get(1)) - center(placed.get(3))) < 0.000001,
                    "A single-child chain must stay on one horizontal centerline");
            helper.assertTrue(center(placed.get(3)) < center(placed.get(4)),
                    "The first branch must not interleave with the second branch's leaves");
            for (var edge : view.edges()) helper.assertTrue(placed.get(edge.source()).x() < placed.get(edge.target()).x(),
                    "Dependencies must advance from the requested item to the right");
            assertNodesAndEdges(helper, graph, layout);
        }
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void sharedSupplyRemainsOneNodeAfterAllConsumers(GameTestHelper helper) {
        CraftingPlanGraph graph = graph(List.of(material(0, Items.DIAMOND), material(1, Items.IRON_INGOT),
                material(2, Items.GOLD_INGOT), material(3, Items.COAL), process(4, Items.DIAMOND),
                process(5, Items.IRON_INGOT), process(6, Items.GOLD_INGOT)),
                new int[][] { { 0, 4 }, { 4, 1 }, { 4, 2 }, { 1, 5 }, { 5, 3 }, { 2, 6 }, { 6, 3 } }, List.of());
        var projection = new CraftingPlanGraphView(graph);
        var view = projection.visible(Expansion.empty(), false);
        Layout layout = CraftingPlanGraphLayout.layout(view, false);
        var placed = index(layout);
        helper.assertTrue(placed.get(3).x() > placed.get(5).x() && placed.get(3).x() > placed.get(6).x(),
                "Shared supply must be to the right of both consumer processes");
        helper.assertTrue(layout.nodes().stream().filter(node -> node.id() == 3).count() == 1,
                "Shared material must not be copied into each branch");
        helper.assertTrue(layout.nodes().equals(CraftingPlanGraphLayout.layout(view, false).nodes()),
                "Repeated layout of the same view must be deterministic");
        var collapsed = projection.setCollapsed(Expansion.empty(), 1, true);
        var folded = CraftingPlanGraphLayout.layout(projection.visible(collapsed, false), false);
        helper.assertTrue(index(folded).containsKey(3), "Folding one branch must retain the other branch's shared supply");
        assertNodesAndEdges(helper, graph, layout);
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void keepsSamePatternSupplierAndSeedOutsideDeclaredLoop(GameTestHelper helper) {
        var diamond = AEItemKey.of(Items.DIAMOND);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        CraftingPlanGraph graph = graph(List.of(material(0, Items.DIAMOND), material(1, Items.IRON_INGOT),
                new Process(2, 0, "shared-pattern", 0, diamond, BigInteger.ONE, false, List.of()),
                new Process(3, 1, "shared-pattern", 0, diamond, BigInteger.TWO, false, List.of(0)),
                new Process(4, 2, "return-pattern", 0, iron, BigInteger.TWO, false, List.of(0))),
                new int[][] { { 0, 2 }, { 2, 1 }, { 0, 3 }, { 3, 1 }, { 1, 4 }, { 4, 0 } },
                List.of(new Cycle(0, 1, List.of(0, 1, 3, 4), List.of(1, 2), BigInteger.TWO,
                        Map.of(iron, BigInteger.ONE), Map.of())));
        var projection = new CraftingPlanGraphView(graph);
        var view = projection.visible(Expansion.empty(), false);
        Layout layout = CraftingPlanGraphLayout.layout(view, false);
        var nodes = index(layout);
        helper.assertFalse(nodes.get(2).viewNode().cyclic(), "The same-pattern prefix must not be absorbed into the loop");
        helper.assertFalse(nodes.get(1).viewNode().cyclic(), "The startup seed is an outside supply, not an in-loop material");
        helper.assertTrue(nodes.get(3).viewNode().cyclic() && nodes.get(4).viewNode().cyclic(),
                "Declared loop stages must remain grouped even when their materials are external boundaries");
        helper.assertTrue(nodes.get(1).x() > nodes.get(3).x() && nodes.get(1).x() > nodes.get(4).x(),
                "The loop must request its shared seed from an external supply column on the right");
        var folded = projection.visible(projection.setCollapsed(Expansion.empty(), 3, true), false);
        helper.assertTrue(folded.nodes().stream().anyMatch(node -> node.id() == 2),
                "Folding the loop must not hide the independently used same-pattern supplier");
        assertNodesAndEdges(helper, graph, layout);
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void retainsInternalCirculationButKeepsSeedAndProductOutside(GameTestHelper helper) {
        var diamond = AEItemKey.of(Items.DIAMOND);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        CraftingPlanGraph graph = graph(List.of(material(0, Items.DIAMOND), material(1, Items.IRON_INGOT),
                material(2, Items.GOLD_INGOT),
                new Process(3, 0, "loop-input", 0, gold, BigInteger.TWO, false, List.of(0)),
                new Process(4, 1, "loop-output", 0, diamond, BigInteger.TWO, false, List.of(0))),
                new int[][] { { 0, 4 }, { 4, 2 }, { 2, 3 }, { 3, 1 }, { 1, 4 } },
                List.of(new Cycle(0, 1, List.of(0, 1, 2, 3, 4), List.of(0, 1), BigInteger.TWO,
                        Map.of(iron, BigInteger.ONE), Map.of(diamond, BigInteger.TWO))));
        var view = new CraftingPlanGraphView(graph).visible(Expansion.empty(), false);
        Layout layout = CraftingPlanGraphLayout.layout(view, true);
        var nodes = index(layout);
        helper.assertFalse(nodes.get(0).viewNode().cyclic(), "Requested product must remain outside the loop");
        helper.assertFalse(nodes.get(1).viewNode().cyclic(), "Initial stock must remain an external supply");
        helper.assertTrue(nodes.get(2).viewNode().cyclic(), "A material used only for internal circulation belongs to the loop");
        helper.assertTrue(nodes.get(2).viewNode().componentId() == nodes.get(4).viewNode().componentId(),
                "Internal circulation must share the declared loop's component");
        assertNodesAndEdges(helper, graph, layout);
        helper.succeed();
    }

    private static Material material(int id, Item item) {
        return new Material(id, AEItemKey.of(item), BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE,
                BigInteger.ZERO, BigInteger.ZERO, 0);
    }

    private static Process process(int id, Item output) {
        return new Process(id, id, "layout/" + id, 0, AEItemKey.of(output), BigInteger.ONE, false, List.of());
    }

    private static CraftingPlanGraph graph(List<Node> nodes, int[][] connections, List<Cycle> cycles) {
        var byId = new Int2ObjectOpenHashMap<Node>();
        for (Node node : nodes) byId.put(node.id(), node);
        List<Edge> edges = new ObjectArrayList<>();
        for (int[] connection : connections) {
            Role role = byId.get(connection[0]) instanceof Process ? Role.INPUT : Role.OUTPUT;
            edges.add(new Edge(edges.size(), connection[0], connection[1], role, BigInteger.ONE));
        }
        Header header = new Header(AEItemKey.of(Items.DIAMOND), BigInteger.ONE, BigInteger.ZERO,
                Kind.EXACT, CraftingQuantityMode.NET_NEW, 0, Component.empty());
        return new CraftingPlanGraph(header, 0, nodes, edges, cycles);
    }

    private static Int2ObjectOpenHashMap<PlacedNode> index(Layout layout) {
        var result = new Int2ObjectOpenHashMap<PlacedNode>();
        for (PlacedNode node : layout.nodes()) result.put(node.id(), node);
        return result;
    }

    private static double center(PlacedNode node) {
        return node.y() + node.height() / 2;
    }

    private static void assertNodesAndEdges(GameTestHelper helper, CraftingPlanGraph graph, Layout layout) {
        var represented = new IntOpenHashSet();
        var edgeIds = new IntOpenHashSet();
        for (PlacedNode node : layout.nodes()) {
            helper.assertTrue(represented.add(node.id()), "A graph node was placed twice");
            if (node.embeddedProcessId() != null) represented.add(node.embeddedProcessId().intValue());
            helper.assertTrue(Double.isFinite(node.x()) && Double.isFinite(node.y()), "Node coordinates must be finite");
            for (PlacedNode other : layout.nodes()) {
                if (node.id() >= other.id()) continue;
                helper.assertTrue(node.x() + node.width() <= other.x() || other.x() + other.width() <= node.x() ||
                        node.y() + node.height() <= other.y() || other.y() + other.height() <= node.y(),
                        "Placed cards overlap");
            }
        }
        helper.assertTrue(represented.size() == graph.nodes().size(), "Layout lost a source node");
        for (var route : layout.edges()) edgeIds.addAll(route.originalEdgeIds());
        for (Edge edge : graph.edges()) {
            boolean embedded = layout.nodes().stream().anyMatch(node -> node.id() == edge.source() &&
                    node.embeddedProcessId() != null && node.embeddedProcessId().intValue() == edge.target());
            helper.assertTrue(embedded || edgeIds.contains(edge.id()), "Layout lost an original dependency edge");
        }
    }
}
