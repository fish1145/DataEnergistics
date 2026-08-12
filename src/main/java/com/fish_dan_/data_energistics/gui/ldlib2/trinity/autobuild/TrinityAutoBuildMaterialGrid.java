package com.fish_dan_.data_energistics.gui.ldlib2.trinity.autobuild;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the fixed three-column automatic-build material viewport through its editor-authored scrollbar.
 */
final class TrinityAutoBuildMaterialGrid extends UIElement {

    private static final int COLUMN_COUNT = 3;
    private static final int VISIBLE_ROW_COUNT = 6;
    private static final int CELL_SIZE = 18;
    private static final int VISIBLE_ENTRY_COUNT = COLUMN_COUNT * VISIBLE_ROW_COUNT;

    private final List<MaterialEntry> entries = new ArrayList<>(VISIBLE_ENTRY_COUNT);
    private List<PreviewMaterial> materials = List.of();
    @Nullable
    private Scroller.Vertical scrollbar;
    private int firstVisibleRow;
    private boolean overflowing;

    TrinityAutoBuildMaterialGrid(@NotNull String id) {
        setId(id);
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1)
                .top(2)
                .width(COLUMN_COUNT * CELL_SIZE)
                .height(VISIBLE_ROW_COUNT * CELL_SIZE));
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        for (int index = 0; index < VISIBLE_ENTRY_COUNT; index++) {
            MaterialEntry entry = createEntry(id, index);
            this.entries.add(entry);
            addChild(entry.root());
        }
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
    }

    /**
     * Binds the single scrollbar authored in the NBT instead of creating a second Java scrollbar.
     */
    void bindScrollbar(@NotNull Scroller.Vertical scrollbar) {
        if (this.scrollbar != null) {
            throw new IllegalStateException("Trinity automatic-build material grid already has a scrollbar");
        }
        this.scrollbar = scrollbar;
        scrollbar.setRange(0.0F, 1.0F);
        scrollbar.setOnValueChanged(ignored -> refreshFromScrollbar());
        scrollbar.scrollContainer.addEventListener(UIEvents.LAYOUT_CHANGED, ignored -> updateScrollbar());
        updateScrollbar();
    }

    /**
     * Replaces the projected material snapshot while retaining the fixed editor-sized entry tree.
     */
    void setMaterials(@NotNull List<PreviewMaterial> materials) {
        this.materials = List.copyOf(materials);
        this.firstVisibleRow = Math.min(this.firstVisibleRow, maxFirstVisibleRow());
        updateScrollbar();
        refreshEntries();
    }

    private MaterialEntry createEntry(String idPrefix, int index) {
        UIElement root = new UIElement();
        root.setId(idPrefix + "_entry_" + index);
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(index % COLUMN_COUNT * CELL_SIZE)
                .top(index / COLUMN_COUNT * CELL_SIZE)
                .width(CELL_SIZE)
                .height(CELL_SIZE));

        ItemSlot slot = new ItemSlot();
        slot.setId(root.getId() + "_slot");
        slot.setItem(ItemStack.EMPTY);
        slot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(CELL_SIZE)
                .height(CELL_SIZE));
        slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));

        Label amount = new Label();
        amount.setId(root.getId() + "_amount");
        amount.addClass("trinity-auto-build-material-amount");
        amount.setText(Component.empty());
        amount.setAllowHitTest(false);
        amount.setOverflowVisible(false);
        amount.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1)
                .top(10)
                .width(CELL_SIZE - 2)
                .height(7));

        root.addChildren(slot, amount);
        MaterialEntry entry = new MaterialEntry(root, slot, amount);
        deactivate(entry);
        return entry;
    }

    private void onMouseWheel(UIEvent event) {
        if (!this.overflowing || this.scrollbar == null || event.deltaY == 0.0F) {
            return;
        }
        this.scrollbar.scrollValue(event.deltaY > 0.0F ?
                -this.scrollbar.getScrollerStyle().scrollDelta() :
                this.scrollbar.getScrollerStyle().scrollDelta());
        event.stopPropagation();
    }

    private void refreshFromScrollbar() {
        if (this.scrollbar == null) {
            return;
        }
        int maximum = maxFirstVisibleRow();
        this.firstVisibleRow = maximum == 0 ? 0 : Math.round(this.scrollbar.getNormalizedValue() * maximum);
        refreshEntries();
    }

    private void updateScrollbar() {
        if (this.scrollbar == null) {
            return;
        }
        int totalRows = Math.max(1, Math.ceilDiv(this.materials.size(), COLUMN_COUNT));
        int maximum = maxFirstVisibleRow();
        this.overflowing = maximum > 0;
        this.firstVisibleRow = Math.min(this.firstVisibleRow, maximum);
        float normalized = maximum == 0 ? 0.0F : (float) this.firstVisibleRow / maximum;
        float scrollDelta = maximum == 0 ? 1.0F : 1.0F / maximum;
        float thumbPercent = Math.min(100.0F, VISIBLE_ROW_COUNT * 100.0F / totalRows);
        this.scrollbar.scrollerStyle(style -> style.scrollDelta(scrollDelta));
        this.scrollbar.setNormalizedValue(normalized, false);
        this.scrollbar.setScrollBarSize(thumbPercent);
        this.scrollbar.selfAndAllChildren()
                .forEach(element -> element.setAllowHitTest(this.overflowing));
    }

    private int maxFirstVisibleRow() {
        return Math.max(0, Math.ceilDiv(this.materials.size(), COLUMN_COUNT) - VISIBLE_ROW_COUNT);
    }

    private void refreshEntries() {
        for (int visibleIndex = 0; visibleIndex < this.entries.size(); visibleIndex++) {
            int row = visibleIndex / COLUMN_COUNT;
            int column = visibleIndex % COLUMN_COUNT;
            int materialIndex = (this.firstVisibleRow + row) * COLUMN_COUNT + column;
            MaterialEntry entry = this.entries.get(visibleIndex);
            if (materialIndex >= this.materials.size()) {
                deactivate(entry);
            } else {
                activate(entry, this.materials.get(materialIndex));
            }
        }
    }

    private static void activate(MaterialEntry entry, PreviewMaterial material) {
        entry.root().setVisible(true);
        entry.slot().setItem(material.key().toStack(1));
        entry.amount().setText(Component.literal(TrinityAmountFormatter.format(material.amount())));
    }

    private static void deactivate(MaterialEntry entry) {
        entry.slot().setItem(ItemStack.EMPTY);
        entry.amount().setText(Component.empty());
        entry.root().setVisible(false);
    }

    private record MaterialEntry(UIElement root, ItemSlot slot, Label amount) {}
}
