package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Paging and value-comparison regressions only; no client widgets or visual assertions. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternViewportGameTest {

    private TrinityPatternViewportGameTest() {}

    @GameTest(template = "empty_5x5")
    public static void wheelStepsStayOneRowAtEveryCapacity(GameTestHelper helper) {
        for (int count : new int[] { 0, 71, 72, 73, 144, 72_000, 1_000_000, Integer.MAX_VALUE }) {
            int last = TrinityPatternViewport.maximumRow(count) * TrinityPatternCatalogView.COLUMN_COUNT;
            helper.assertTrue(TrinityPatternViewport.scrollRows(0, 1, count) == Math.min(9, last),
                    "One wheel notch must move one row, independent of catalog size");
            helper.assertTrue(TrinityPatternViewport.firstSlot(1.0F, count) == last,
                    "The thumb must reach the last row without float multiplication losing slots");
            helper.assertTrue(TrinityPatternViewport.scrollRows(last, 1, count) == last &&
                    TrinityPatternViewport.scrollRows(0, -1, count) == 0,
                    "Wheel movement must stop at both boundaries");
            helper.assertTrue(TrinityPatternViewport.scrollRows(last, -1, count) == Math.max(0, last - 9),
                    "Scrolling backward must also move exactly one row");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void delayedPagesDoNotRewindTheViewport(GameTestHelper helper) {
        var viewport = new TrinityPatternViewport();
        viewport.accept(page(0, 576, 7));
        viewport.setFirstSlot(9);
        helper.assertTrue(viewport.pattern(9) != null && viewport.pattern(80) == null,
                "The overlapping cached rows stay visible while the new row is unloaded");
        viewport.setFirstSlot(90);
        viewport.accept(page(9, 576, 7));
        helper.assertTrue(viewport.firstSlot() == 90 && viewport.requestedPage() == 90,
                "An earlier reply must not overwrite the latest drag target");
        viewport.accept(page(90, 576, 7));
        helper.assertTrue(viewport.pattern(90) != null, "The matching page must populate the requested viewport");
        viewport.accept(page(0, 100, 8));
        helper.assertTrue(viewport.firstSlot() == 36 && viewport.requestedPage() == 28,
                "A partial last row stays aligned while the wire protocol still requests a bounded full page");
        viewport.accept(page(28, 100, 8));
        helper.assertTrue(viewport.firstSlot() == 36 && viewport.pattern(99) != null && viewport.pattern(100) == null,
                "The catalog tail must be reachable without shifting columns or inventing slots");
        viewport.accept(page(0, 72, 9));
        helper.assertTrue(viewport.firstSlot() == 0 && viewport.pattern(90) == null,
                "A changed catalog must clamp the viewport and invalidate old cached slots");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void copiedPatternPagesCompareByContents(GameTestHelper helper) {
        ItemStack pattern = new ItemStack(Items.STONE);
        pattern.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        var first = new TrinityPatternCatalogView(1, 7, 144, 0, List.of(pattern));
        var copy = new TrinityPatternCatalogView(1, 7, 144, 0, List.of(pattern.copy()));
        helper.assertTrue(first.equals(copy) && first.hashCode() == copy.hashCode(),
                "Copying ItemStacks must not dirty an unchanged page on each sync tick");
        ItemStack renamed = pattern.copy();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("second"));
        helper.assertFalse(first.equals(new TrinityPatternCatalogView(1, 7, 144, 0, List.of(renamed))),
                "Changed pattern components must still synchronize");
        helper.assertFalse(first.equals(new TrinityPatternCatalogView(1, 7, 144, 0, List.of(pattern.copyWithCount(2)))),
                "Changed counts must still synchronize");
        helper.assertFalse(first.equals(new TrinityPatternCatalogView(1, 7, 144, 9, List.of(pattern))),
                "A different page position must still synchronize");
        helper.succeed();
    }

    private static TrinityPatternCatalogView page(int first, int count, long revision) {
        List<ItemStack> patterns = new ObjectArrayList<>();
        for (int index = 0; index < Math.min(TrinityPatternCatalogView.PAGE_SIZE, count - first); index++) {
            patterns.add(new ItemStack(Items.STONE, (first + index) % 64 + 1));
        }
        return new TrinityPatternCatalogView(1, revision, count, first, patterns);
    }
}
