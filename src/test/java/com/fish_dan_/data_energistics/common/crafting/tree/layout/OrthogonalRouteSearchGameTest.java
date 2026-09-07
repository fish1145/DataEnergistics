package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Side;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.OrthogonalRoutingGraph.Port;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewNode;

import appeng.api.stacks.AEItemKey;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class OrthogonalRouteSearchGameTest {

    private OrthogonalRouteSearchGameTest() {}

    @GameTest(template = "empty_5x5")
    public static void replacesLongFacingChannelWithShorterVerticalPorts(GameTestHelper helper) {
        PlacedNode source = node(0, 0, 0, 40, 100);
        PlacedNode target = node(1, 100, 200, 40, 100);
        List<Port> sources = ports(source);
        List<Port> targets = ports(target);
        List<Port> all = new ObjectArrayList<>(sources);
        all.addAll(targets);
        var graph = new OrthogonalRoutingGraph(List.of(source, target), all);
        var reservations = new OrthogonalSegmentReservations(graph.x, graph.y);
        var search = new OrthogonalRouteSearch(graph, reservations);
        var group = new CraftingPlanRouteGroup(new CraftingPlanRouteGroup.Style(true, IntLists.emptyList()), 0);
        var channel = search.fixedChannel(sources.get(Side.RIGHT.ordinal()), targets.get(Side.LEFT.ordinal()), 70, group);
        helper.assertTrue(channel != null, "Fixture must offer a valid but longer facing-port channel");
        var shortest = search.route(sources, targets, group, channel, true);
        helper.assertTrue(Math.abs(shortest.metrics().length() - 200) < 0.000001,
                "The shorter bottom-to-top connection must replace the 260-unit facing channel");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void routesAroundObstacleByShortestClearBoundary(GameTestHelper helper) {
        PlacedNode source = node(0, 0, 0, 20, 20);
        PlacedNode target = node(1, 180, 0, 20, 20);
        PlacedNode obstacle = node(2, 80, -20, 20, 80);
        List<Port> sources = ports(source);
        List<Port> targets = ports(target);
        List<Port> all = new ObjectArrayList<>(sources);
        all.addAll(targets);
        var graph = new OrthogonalRoutingGraph(List.of(source, target, obstacle), all);
        var search = new OrthogonalRouteSearch(graph, new OrthogonalSegmentReservations(graph.x, graph.y));
        var group = new CraftingPlanRouteGroup(new CraftingPlanRouteGroup.Style(true, IntLists.emptyList()), 0);
        var shortest = search.route(sources, targets, group, null, false);
        helper.assertTrue(Math.abs(shortest.metrics().length() - 228) < 0.000001,
                "Routing must use the obstacle's upper clearance, not a distant outer channel");
        for (int index = 1; index < shortest.points().size(); index++) {
            var from = shortest.points().get(index - 1);
            var to = shortest.points().get(index);
            helper.assertTrue(Math.max(from.x(), to.x()) <= obstacle.x() || Math.min(from.x(), to.x()) >= obstacle.x() + obstacle.width() ||
                    Math.max(from.y(), to.y()) <= obstacle.y() || Math.min(from.y(), to.y()) >= obstacle.y() + obstacle.height(),
                    "Shortest routing must never cut through the intervening node");
        }
        helper.succeed();
    }

    private static List<Port> ports(PlacedNode node) {
        List<Port> result = new ObjectArrayList<>();
        for (Side side : Side.values()) result.add(OrthogonalRoutingGraph.port(node, side, 0.5));
        return result;
    }

    private static PlacedNode node(int id, double x, double y, double width, double height) {
        var material = new Material(id, AEItemKey.of(Items.STONE), BigInteger.ONE, BigInteger.ZERO,
                BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO, 0);
        return new PlacedNode(new ViewNode(id, material, null, id, false, false, false), x, y, width, height);
    }
}
