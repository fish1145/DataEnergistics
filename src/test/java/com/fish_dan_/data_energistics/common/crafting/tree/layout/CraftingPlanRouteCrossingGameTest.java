package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteCrossing.Underpass;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Run;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Segment;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;

/** Checks crossing geometry and line counts, without invoking any client renderer. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class CraftingPlanRouteCrossingGameTest {

    private CraftingPlanRouteCrossingGameTest() {}

    @GameTest(template = "empty_5x5")
    public static void sparseWireBridgesWholeDenseBundleInEitherOrientation(GameTestHelper helper) {
        for (boolean transposed : new boolean[] { false, true }) {
            List<Segment> segments = new ObjectArrayList<>();
            List<Run> runs = new ObjectArrayList<>();
            for (int coordinate : new int[] { -6, -2, 2, 6 }) {
                add(segments, runs, point(coordinate, -20, transposed), point(coordinate, 20, transposed));
            }
            add(segments, runs, point(-20, 0, transposed), point(20, 0, transposed));
            var crossings = CraftingPlanRouteCrossing.find(List.of(), List.of(), segments, runs);
            helper.assertTrue(crossings.size() == 1, "One sparse wire must span the bundle in a single bridge");
            var crossing = crossings.getFirst();
            helper.assertTrue(crossing.bridgeSegmentId() == 4 && crossing.underpasses().size() == 4,
                    "The one-wire side must cross over the four-wire side, regardless of orientation");
            helper.assertTrue(crossing.radius() > CraftingPlanRouteCrossing.MAX_RADIUS,
                    "A dense bundle needs one spanning bridge, not individual tiny arches");
            Point entry = crossing.point(0, -crossing.radius(), !transposed);
            helper.assertTrue(transposed ? entry.x() == 0 : entry.y() == 0,
                    "Bridge-local coordinates must map back to its owning graph segment");
        }
        ObjectList<Underpass> gaps = new ObjectArrayList<>(List.of(new Underpass(7, 2, 1.5), new Underpass(7, 0, 1.5)));
        var merged = CraftingPlanRouteCrossing.mergeUnderpasses(gaps);
        helper.assertTrue(merged.size() == 1 && merged.getFirst().x() == 1 && merged.getFirst().gapHalfWidth() == 2.5,
                "Overlapping cuts must merge rather than draw backwards through an earlier gap");
        helper.succeed();
    }

    private static Point point(double x, double y, boolean transposed) {
        return transposed ? new Point(y, x) : new Point(x, y);
    }

    private static void add(List<Segment> segments, List<Run> runs, Point from, Point to) {
        int id = segments.size();
        var group = new CraftingPlanRouteGroup(new CraftingPlanRouteGroup.Style(true, IntLists.emptyList()), id);
        segments.add(new Segment(from, to, group, IntLists.singleton(id)));
        runs.add(new Run(from, to, group, IntLists.singleton(id)));
    }
}
