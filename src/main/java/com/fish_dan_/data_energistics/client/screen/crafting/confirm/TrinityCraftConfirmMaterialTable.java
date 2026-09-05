package com.fish_dan_.data_energistics.client.screen.crafting.confirm;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmMaterialPresentation;
import com.fish_dan_.data_energistics.client.crafting.confirm.table.TrinityCraftConfirmCycleBarRenderer;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.ToIntFunction;

/** Fixed 3x6 material viewport fitted to the authored crafting-report background. */
final class TrinityCraftConfirmMaterialTable extends UIElement {

    private static final int COLUMN_COUNT = 3;
    private static final int VISIBLE_ROW_COUNT = 6;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 22;
    private static final int CELL_BORDER = 1;
    private static final int TABLE_WIDTH = COLUMN_COUNT * CELL_WIDTH + (COLUMN_COUNT - 1) * CELL_BORDER;
    private static final int TABLE_HEIGHT = VISIBLE_ROW_COUNT * CELL_HEIGHT +
            (VISIBLE_ROW_COUNT - 1) * CELL_BORDER;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final float TEXT_SCALE = 0.5F;
    private static final float INVERSE_TEXT_SCALE = 2.0F;
    private static final int LINE_SPACING = 1;

    private final Scroller.Vertical scrollbar;
    private final ToIntFunction<AEKey> selectedCycleOrdinal;
    private List<CraftingPlanSummaryEntry> entries = List.of();
    private @Nullable TrinityCraftingCycleSummary summary;
    private int firstVisibleRow;
    private boolean overflowing;

    TrinityCraftConfirmMaterialTable(Scroller.Vertical scrollbar,
                                     ToIntFunction<AEKey> selectedCycleOrdinal) {
        this.scrollbar = scrollbar;
        this.selectedCycleOrdinal = selectedCycleOrdinal;
        setId("trinity_craft_confirm_material_table");
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(15)
                .top(25)
                .width(TABLE_WIDTH)
                .height(TABLE_HEIGHT));
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        this.scrollbar.setRange(0.0F, 1.0F);
        this.scrollbar.setOnValueChanged(ignored -> refreshFromScrollbar());
        updateScrollbar();
    }

    void setPlan(@Nullable CraftingPlanSummary plan,
                 @Nullable TrinityCraftingCycleSummary summary) {
        List<CraftingPlanSummaryEntry> nextEntries = plan == null ? List.of() : plan.getEntries();
        boolean entryCountChanged = this.entries.size() != nextEntries.size();
        this.entries = nextEntries;
        this.summary = summary;
        if (entryCountChanged) {
            this.firstVisibleRow = Math.min(this.firstVisibleRow, maxFirstVisibleRow());
            updateScrollbar();
        }
    }

    void resetRevision() {
        this.firstVisibleRow = 0;
        updateScrollbar();
    }

    int firstVisibleRow() {
        return this.firstVisibleRow;
    }

    void restoreFirstVisibleRow(int firstVisibleRow) {
        this.firstVisibleRow = Math.clamp(firstVisibleRow, 0, maxFirstVisibleRow());
        updateScrollbar();
    }

    @Nullable
    CraftingPlanSummaryEntry entryAt(double mouseX, double mouseY) {
        int localX = (int) Math.floor(mouseX - getPositionX());
        int localY = (int) Math.floor(mouseY - getPositionY());
        if (localX < 0 || localY < 0 || localX >= TABLE_WIDTH || localY >= TABLE_HEIGHT) {
            return null;
        }
        int strideX = CELL_WIDTH + CELL_BORDER;
        int strideY = CELL_HEIGHT + CELL_BORDER;
        int column = localX / strideX;
        int row = localY / strideY;
        if (localX % strideX == CELL_WIDTH || localY % strideY == CELL_HEIGHT) {
            return null;
        }
        int index = (this.firstVisibleRow + row) * COLUMN_COUNT + column;
        return index < this.entries.size() ? this.entries.get(index) : null;
    }

    @Nullable
    StackWithBounds stackAt(double mouseX, double mouseY) {
        CraftingPlanSummaryEntry entry = entryAt(mouseX, mouseY);
        if (entry == null) {
            return null;
        }
        int localX = (int) Math.floor(mouseX - getPositionX());
        int localY = (int) Math.floor(mouseY - getPositionY());
        int column = localX / (CELL_WIDTH + CELL_BORDER);
        int row = localY / (CELL_HEIGHT + CELL_BORDER);
        Rect2i bounds = new Rect2i(
                Math.round(getPositionX()) + column * (CELL_WIDTH + CELL_BORDER),
                Math.round(getPositionY()) + row * (CELL_HEIGHT + CELL_BORDER),
                CELL_WIDTH,
                CELL_HEIGHT);
        return new StackWithBounds(new GenericStack(entry.getWhat(), 0), bounds);
    }

    List<Component> tooltip(CraftingPlanSummaryEntry entry) {
        return TrinityCraftConfirmMaterialPresentation.tooltip(
                entry,
                this.summary,
                this.selectedCycleOrdinal.applyAsInt(entry.getWhat()));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        GuiGraphics graphics = context.graphics;
        int originX = Math.round(getPositionX());
        int originY = Math.round(getPositionY());
        for (int row = 0; row < VISIBLE_ROW_COUNT; row++) {
            for (int column = 0; column < COLUMN_COUNT; column++) {
                int index = (this.firstVisibleRow + row) * COLUMN_COUNT + column;
                if (index >= this.entries.size()) {
                    break;
                }
                CraftingPlanSummaryEntry entry = this.entries.get(index);
                int cellX = originX + column * (CELL_WIDTH + CELL_BORDER);
                int cellY = originY + row * (CELL_HEIGHT + CELL_BORDER);
                drawEntry(graphics, minecraft, font, entry, cellX, cellY);
            }
        }
    }

    private void drawEntry(GuiGraphics graphics,
                           Minecraft minecraft,
                           Font font,
                           CraftingPlanSummaryEntry entry,
                           int cellX,
                           int cellY) {
        List<Component> lines = TrinityCraftConfirmMaterialPresentation.description(entry, this.summary);
        float lineHeight = font.lineHeight * TEXT_SCALE;
        float textHeight = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * LINE_SPACING;
        float textY = Math.round(cellY + (CELL_HEIGHT - textHeight) / 2.0F);
        int itemX = cellX + CELL_WIDTH - 19;

        graphics.pose().pushPose();
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        for (Component line : lines) {
            int width = font.width(line);
            graphics.drawString(
                    font,
                    line,
                    (int) ((itemX - 2 - width * TEXT_SCALE) * INVERSE_TEXT_SCALE),
                    (int) (textY * INVERSE_TEXT_SCALE),
                    TEXT_COLOR,
                    false);
            textY += lineHeight + LINE_SPACING;
        }
        graphics.pose().popPose();

        AEKeyRendering.drawInGui(minecraft, graphics, itemX, cellY + (CELL_HEIGHT - 16) / 2, entry.getWhat());
        int overlay = TrinityCraftConfirmMaterialPresentation.overlayColor(entry, this.summary);
        if (overlay != 0) {
            graphics.fill(cellX, cellY, cellX + CELL_WIDTH, cellY + CELL_HEIGHT, overlay);
        }
        if (this.summary != null) {
            TrinityCraftConfirmCycleBarRenderer.renderCell(
                    graphics,
                    cellX,
                    cellY,
                    CELL_WIDTH,
                    CELL_HEIGHT,
                    this.summary.contributionsFor(entry.getWhat()));
        }
    }

    private void onMouseWheel(UIEvent event) {
        if (!this.overflowing || event.deltaY == 0.0F) {
            return;
        }
        this.scrollbar.scrollValue(event.deltaY > 0.0F ?
                -this.scrollbar.getScrollerStyle().scrollDelta() :
                this.scrollbar.getScrollerStyle().scrollDelta());
        event.stopPropagation();
    }

    private void refreshFromScrollbar() {
        int maximum = maxFirstVisibleRow();
        this.firstVisibleRow = maximum == 0 ? 0 : Math.round(this.scrollbar.getNormalizedValue() * maximum);
    }

    private void updateScrollbar() {
        int maximum = maxFirstVisibleRow();
        this.overflowing = maximum > 0;
        this.firstVisibleRow = Math.min(this.firstVisibleRow, maximum);
        float normalized = maximum == 0 ? 0.0F : (float) this.firstVisibleRow / maximum;
        float scrollDelta = maximum == 0 ? 1.0F : 1.0F / maximum;
        this.scrollbar.scrollerStyle(style -> style.scrollDelta(scrollDelta));
        this.scrollbar.setNormalizedValue(normalized, false);
        this.scrollbar.setActive(this.overflowing);
        this.scrollbar.selfAndAllChildren().forEach(element -> element.setAllowHitTest(this.overflowing));
    }

    private int maxFirstVisibleRow() {
        return Math.max(0, Math.ceilDiv(this.entries.size(), COLUMN_COUNT) - VISIBLE_ROW_COUNT);
    }
}
