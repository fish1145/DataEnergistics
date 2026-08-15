package com.fish_dan_.data_energistics.client.crafting.confirm.table;

import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.client.gui.GuiGraphics;

import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import java.util.List;

/**
 * Draws two-pixel cycle-membership bars beneath the visible cells of AE2's 3x5 confirmation table.
 */
public final class TrinityCraftConfirmCycleBarRenderer {

    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 19;
    private static final int COLUMN_COUNT = 3;
    private static final int ROW_COUNT = 5;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 22;
    private static final int CELL_BORDER = 1;
    private static final int BAR_HEIGHT = 2;

    private TrinityCraftConfirmCycleBarRenderer() {}

    /**
     * Draws only cycle members from the complete current summary while following AE2's current row offset.
     */
    public static void render(GuiGraphics graphics,
                              CraftingPlanSummary plan,
                              int scrollOffset,
                              TrinityCraftingCycleSummary summary) {
        List<CraftingPlanSummaryEntry> entries = plan.getEntries();
        for (int row = 0; row < ROW_COUNT; row++) {
            for (int column = 0; column < COLUMN_COUNT; column++) {
                int entryIndex = (row + scrollOffset) * COLUMN_COUNT + column;
                if (entryIndex >= entries.size()) {
                    break;
                }

                List<TrinityCraftingCycleMaterialContribution> contributions = summary.contributionsFor(entries.get(entryIndex).getWhat());
                int cellX = TABLE_X + column * (CELL_WIDTH + CELL_BORDER);
                int barY = TABLE_Y + row * (CELL_HEIGHT + CELL_BORDER) + CELL_HEIGHT - BAR_HEIGHT;
                for (TrinityCraftConfirmCycleBarLayout.Segment segment : TrinityCraftConfirmCycleBarLayout.segments(contributions, CELL_WIDTH)) {
                    graphics.fill(
                            cellX + segment.start(),
                            barY,
                            cellX + segment.end(),
                            barY + BAR_HEIGHT,
                            segment.color());
                }
            }
        }
    }
}
