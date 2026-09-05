package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;

/** Reusable hosted diagnostic strip with stable material-entry identities. */
public final class PreviewMaterialStrip extends ScrollerView {

    private static final String AMOUNT_KEY = "screen.data_energistics.multiblock_preview.material.amount";
    private static final int SLOT_SIZE = 18;
    private static final int ENTRY_GAP = 3;

    private final String idPrefix;
    private final ObjectList<MaterialEntry> entries = new ObjectArrayList<>();

    public PreviewMaterialStrip(String idPrefix) {
        if (idPrefix.isBlank()) {
            throw new IllegalArgumentException("Preview material strip id prefix cannot be blank");
        }
        this.idPrefix = idPrefix;
        setId(idPrefix + StructurePreviewPanel.MATERIALS_SUFFIX);
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scrollerStyle(style -> style
                .mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER)
                .scrollerViewStyle(0));
        viewPort(viewPort -> viewPort
                .layout(layout -> layout.paddingAll(0))
                .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        viewContainer(viewContainer -> viewContainer.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .height(SLOT_SIZE)));
    }

    /** Updates hosted diagnostics while retaining every existing entry instance. */
    public void setMaterials(List<PreviewMaterial> materials) {
        updateMaterials(materials);
    }

    private void updateMaterials(List<PreviewMaterial> materials) {
        while (this.entries.size() < materials.size()) {
            MaterialEntry entry = createEntry(this.entries.size());
            this.entries.add(entry);
            addScrollViewChild(entry.root());
        }
        for (int index = 0; index < materials.size(); index++) {
            activateEntry(this.entries.get(index), materials.get(index));
        }
        for (int index = materials.size(); index < this.entries.size(); index++) {
            deactivateEntry(this.entries.get(index));
        }
    }

    private MaterialEntry createEntry(int index) {
        UIElement entry = new UIElement();
        entry.setId(this.idPrefix + "_material_" + index);

        ItemSlot slot = new ItemSlot();
        slot.setId(entry.getId() + "_slot");
        slot.setItem(ItemStack.EMPTY);

        Label amount = new Label();
        amount.setId(entry.getId() + "_amount");
        amount.setOverflowVisible(false);
        amount.setText(Component.empty());
        amount.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.5f)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        entry.addChildren(slot, amount);
        MaterialEntry materialEntry = new MaterialEntry(entry, slot, amount);
        deactivateEntry(materialEntry);
        return materialEntry;
    }

    private static void activateEntry(MaterialEntry entry, PreviewMaterial material) {
        int labelWidth = Math.max(18, Long.toString(material.amount()).length() * 5 + 6);
        entry.root().setVisible(true);
        entry.slot().setVisible(true);
        entry.amount().setVisible(true);
        entry.root().layout(layout -> layout
                .width(SLOT_SIZE + labelWidth + ENTRY_GAP)
                .height(SLOT_SIZE));
        entry.slot().layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(SLOT_SIZE)
                .height(SLOT_SIZE));
        entry.amount().layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(SLOT_SIZE + 1)
                .top(0)
                .width(labelWidth)
                .height(SLOT_SIZE));
        entry.slot().setItem(material.key().toStack(1));
        entry.amount().setText(Component.translatable(AMOUNT_KEY, material.amount()));
    }

    private static void deactivateEntry(MaterialEntry entry) {
        entry.slot().setItem(ItemStack.EMPTY);
        entry.amount().setText(Component.empty());
        entry.root().setVisible(false);
        entry.slot().setVisible(false);
        entry.amount().setVisible(false);
        entry.root().layout(layout -> layout.width(0).height(0));
        entry.slot().layout(layout -> layout.width(0).height(0));
        entry.amount().layout(layout -> layout.width(0).height(0));
    }

    private record MaterialEntry(UIElement root, ItemSlot slot, Label amount) {}
}
