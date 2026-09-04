package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild;

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
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;
import java.util.function.LongFunction;

/**
 * Renders the fixed three-column automatic-build material viewport through its editor-authored scrollbar.
 */
final class AutoBuildMaterialGrid extends UIElement {

    private static final int COLUMN_COUNT = 3;
    private static final int VISIBLE_ROW_COUNT = 6;
    private static final int CELL_SIZE = 18;
    private static final int VISIBLE_ENTRY_COUNT = COLUMN_COUNT * VISIBLE_ROW_COUNT;

    private final ObjectList<MaterialEntry> entries = new ObjectArrayList<>(VISIBLE_ENTRY_COUNT);
    private final LongFunction<String> amountFormatter;
    private final IngredientIO recipeRole;
    private final Scroller.Vertical scrollbar;
    private ObjectList<PreviewMaterial> materials = ObjectLists.emptyList();
    private int firstVisibleRow;
    private boolean overflowing;

    AutoBuildMaterialGrid(String id,
                          AutoBuildComposition.Region geometry,
                          LongFunction<String> amountFormatter,
                          IngredientIO recipeRole,
                          Scroller.Vertical scrollbar) {
        if (recipeRole != IngredientIO.NONE && recipeRole != IngredientIO.INPUT) {
            throw new IllegalArgumentException("Automatic-build material grid only supports NONE and INPUT roles");
        }
        this.amountFormatter = amountFormatter;
        this.recipeRole = recipeRole;
        this.scrollbar = scrollbar;
        setId(id);
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(geometry.left())
                .top(geometry.top())
                .width(geometry.width())
                .height(geometry.height()));
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        for (int index = 0; index < VISIBLE_ENTRY_COUNT; index++) {
            MaterialEntry entry = createEntry(id, index);
            this.entries.add(entry);
            addChild(entry.root());
        }
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        AuthoredScrollerThumbSize.bind(scrollbar);
        scrollbar.setRange(0.0F, 1.0F);
        scrollbar.setOnValueChanged(ignored -> refreshFromScrollbar());
        updateScrollbar();
    }

    /**
     * Replaces the projected material snapshot while retaining the fixed editor-sized entry tree.
     */
    void setMaterials(List<PreviewMaterial> materials) {
        this.materials = ObjectLists.unmodifiable(new ObjectArrayList<>(materials));
        this.firstVisibleRow = Math.min(this.firstVisibleRow, maxFirstVisibleRow());
        updateScrollbar();
        refreshEntries();
    }

    private MaterialEntry createEntry(String idPrefix, int index) {
        int column = index % COLUMN_COUNT;
        int row = index / COLUMN_COUNT;
        UIElement root = new UIElement();
        root.setId(idPrefix + "_entry_" + index);
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(column * CELL_SIZE)
                .top(row * CELL_SIZE)
                .width(CELL_SIZE)
                .height(CELL_SIZE));

        ItemSlot slot = new MaterialItemSlot();
        slot.setId(root.getId() + "_slot");
        slot.setItem(ItemStack.EMPTY);
        if (this.recipeRole == IngredientIO.INPUT) {
            slot.xeiRecipeSlot(IngredientIO.INPUT, 1.0f);
        }
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
        refreshEntries();
    }

    private void updateScrollbar() {
        int maximum = maxFirstVisibleRow();
        this.overflowing = maximum > 0;
        this.firstVisibleRow = Math.min(this.firstVisibleRow, maximum);
        float normalized = maximum == 0 ? 0.0F : (float) this.firstVisibleRow / maximum;
        float scrollDelta = maximum == 0 ? 1.0F : 1.0F / maximum;
        this.scrollbar.scrollerStyle(style -> style.scrollDelta(scrollDelta));
        this.scrollbar.setNormalizedValue(normalized, false);
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

    private void activate(MaterialEntry entry, PreviewMaterial material) {
        entry.root().setVisible(true);
        int displayAmount = this.recipeRole == IngredientIO.INPUT ? xeiAmount(material) : 1;
        entry.slot().setItem(material.key().toStack(displayAmount));
        entry.amount().setText(Component.literal(this.amountFormatter.apply(material.amount())));
    }

    private static int xeiAmount(PreviewMaterial material) {
        if (material.amount() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "XEI material amount exceeds the supported int range: " + material.amount());
        }
        return (int) material.amount();
    }

    private static void deactivate(MaterialEntry entry) {
        entry.slot().setItem(ItemStack.EMPTY);
        entry.amount().setText(Component.empty());
        entry.root().setVisible(false);
    }

    private record MaterialEntry(UIElement root, ItemSlot slot, Label amount) {}

    /** Keeps the recipe count intact while the authored amount label owns visible quantity rendering. */
    private static final class MaterialItemSlot extends ItemSlot {

        @Override
        protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
            super.drawItemStack(guiContext, itemStack.getCount() == 1 ? itemStack : itemStack.copyWithCount(1));
        }
    }
}
