package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.physical;

import com.fish_dan_.data_energistics.blockentity.trinity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import appeng.api.inventories.InternalInventory;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;

/**
 * Native LDLib2 pattern grid for one physical core.
 *
 * <p>
 * Every tier keeps its real 72, 144, or 576-slot inventory. Each capacity is an exact number of nine-slot rows, and
 * the clipped content scrolls one whole row at a time.
 * </p>
 */
final class TrinityPatternCoreNativeGrid extends UIElement {

    private static final int COLUMN_COUNT = TrinityPatternCatalogView.COLUMN_COUNT;
    private static final int VISIBLE_ROW_COUNT = TrinityPatternCatalogView.ROW_COUNT;
    private static final int SLOT_SIZE = 18;
    private static final int VIEW_LEFT = 4;
    private static final int VIEW_TOP = 6;
    private static final IGuiTexture PATTERN_ROW_BACKGROUND = SpriteTexture
            .of("data_energistics:textures/guis/model/model.png");
    private final UIElement rowContent = new UIElement();
    private final Scroller.Vertical scrollbar;
    private final int totalRows;
    private final int maximumFirstRow;

    private TrinityPatternCoreNativeGrid(TrinityPatternCoreBlockEntity core, Scroller.Vertical scrollbar) {
        this.scrollbar = scrollbar;
        int capacity = core.patternCapacity();
        this.totalRows = rowsForCapacity(capacity);
        this.maximumFirstRow = Math.max(0, this.totalRows - VISIBLE_ROW_COUNT);
        setId(TrinityPatternCoreNbtLayout.CONTENT_ID + "_grid");
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(VIEW_LEFT)
                .top(VIEW_TOP)
                .width(COLUMN_COUNT * SLOT_SIZE)
                .height(VISIBLE_ROW_COUNT * SLOT_SIZE));

        this.rowContent.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(COLUMN_COUNT * SLOT_SIZE)
                .height(this.totalRows * SLOT_SIZE));
        addChild(this.rowContent);

        InternalInventory patterns = core.patternInventory();
        Container inventory = patterns.toContainer();
        for (int row = 0; row < this.totalRows; row++) {
            addRowBackground(row);
        }
        for (int index = 0; index < capacity; index++) {
            addPatternSlot(inventory, index);
        }

        configureScrollbar();
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0 && this.maximumFirstRow > 0) {
                float delta = this.scrollbar.getScrollerStyle().scrollDelta();
                this.scrollbar.scrollValue(event.deltaY > 0 ? -delta : delta);
                event.stopPropagation();
            }
        });
    }

    static TrinityPatternCoreNativeGrid create(TrinityPatternCoreBlockEntity core, Scroller.Vertical scrollbar) {
        return new TrinityPatternCoreNativeGrid(core, scrollbar);
    }

    private void addRowBackground(int row) {
        UIElement background = new UIElement();
        background.setAllowHitTest(false);
        background.getStyle().backgroundTexture(PATTERN_ROW_BACKGROUND);
        background.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(row * SLOT_SIZE)
                .width(COLUMN_COUNT * SLOT_SIZE)
                .height(SLOT_SIZE));
        this.rowContent.addChild(background);
    }

    private void addPatternSlot(Container inventory, int index) {
        int column = index % COLUMN_COUNT;
        int row = index / COLUMN_COUNT;
        ItemSlot slot = new ItemSlot(new Slot(inventory, index, 0, 0));
        slot.setId(TrinityPatternCoreNbtLayout.CONTENT_ID + "_slot_" + index);
        slot.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        slot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(column * SLOT_SIZE)
                .top(row * SLOT_SIZE)
                .width(SLOT_SIZE)
                .height(SLOT_SIZE));
        this.rowContent.addChild(slot);
    }

    private void configureScrollbar() {
        boolean scrollable = this.maximumFirstRow > 0;
        this.scrollbar.headButton.setDisplay(false);
        this.scrollbar.tailButton.setDisplay(false);
        this.scrollbar.layout(layout -> layout
                .gapRow(0)
                .gapColumn(0));
        this.scrollbar.setRange(0.0F, 1.0F);
        this.scrollbar.setScrollBarSize(100.0F * Math.min(1.0F, (float) VISIBLE_ROW_COUNT / this.totalRows));
        this.scrollbar.scrollerStyle(style -> style.scrollDelta(
                scrollable ? 1.0F / this.maximumFirstRow : 1.0F));
        this.scrollbar.setActive(scrollable);
        this.scrollbar.selfAndAllChildren().forEach(element -> element.setAllowHitTest(scrollable));
        this.scrollbar.setNormalizedValue(0.0F, false);
        this.scrollbar.setOnValueChanged(this::scrollToNormalizedRow);
        scrollToNormalizedRow(0.0F);
    }

    private void scrollToNormalizedRow(float normalizedValue) {
        int firstRow = Math.round(Math.clamp(normalizedValue, 0.0F, 1.0F) * this.maximumFirstRow);
        this.rowContent.layout(layout -> layout.top(-firstRow * SLOT_SIZE));
        if (this.maximumFirstRow > 0) {
            this.scrollbar.setNormalizedValue((float) firstRow / this.maximumFirstRow, false);
        }
    }

    private static int rowsForCapacity(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Pattern core capacity must be positive");
        }
        if (capacity % COLUMN_COUNT != 0) {
            throw new IllegalArgumentException("Pattern core capacity must contain complete nine-slot rows");
        }
        return capacity / COLUMN_COUNT;
    }
}
