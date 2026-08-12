package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable high-density material strip with stable entry identities for recipe-viewer layouts.
 */
public final class PreviewMaterialStrip extends ScrollerView {

    private static final String AMOUNT_KEY = "screen.data_energistics.multiblock_preview.material.amount";
    private static final int SLOT_SIZE = 18;
    private static final int ENTRY_GAP = 3;

    private final String idPrefix;
    private final IngredientIO recipeRole;
    private final List<MaterialEntry> entries = new ArrayList<>();

    /**
     * Creates a hosted diagnostic strip that publishes no recipe-viewer role.
     *
     * @param idPrefix non-blank namespace unique within the owning UI
     */
    public PreviewMaterialStrip(String idPrefix) {
        this(idPrefix, IngredientIO.NONE);
    }

    /**
     * Creates a material strip whose role cannot change after slot listeners are registered.
     *
     * @param idPrefix   non-blank namespace unique within the owning UI
     * @param recipeRole NONE for hosted diagnostics or INPUT for XEI recipe slots
     */
    public PreviewMaterialStrip(String idPrefix, IngredientIO recipeRole) {
        if (idPrefix == null || idPrefix.isBlank()) {
            throw new IllegalArgumentException("Preview material strip id prefix cannot be null or blank");
        }
        if (recipeRole != IngredientIO.NONE && recipeRole != IngredientIO.INPUT) {
            throw new IllegalArgumentException("Preview material strip only supports NONE and INPUT roles");
        }
        this.idPrefix = idPrefix;
        this.recipeRole = recipeRole;
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

    /**
     * Updates hosted diagnostics while retaining every existing entry instance.
     */
    public void setMaterials(List<PreviewMaterial> materials) {
        if (this.recipeRole != IngredientIO.NONE) {
            throw new IllegalStateException("Recipe material strips must be updated through setRecipeInputs");
        }
        updateMaterials(materials);
    }

    /**
     * Updates XEI inputs while retaining every previously registered slot identity.
     *
     * @return whether the stable slot pool had to grow
     */
    public boolean setRecipeInputs(List<PreviewMaterial> materials) {
        if (this.recipeRole != IngredientIO.INPUT) {
            throw new IllegalStateException("Diagnostic material strips cannot publish recipe inputs");
        }
        return updateMaterials(materials);
    }

    private boolean updateMaterials(List<PreviewMaterial> materials) {
        validateMaterials(materials);
        boolean grew = false;
        while (this.entries.size() < materials.size()) {
            MaterialEntry entry = createEntry(this.entries.size());
            this.entries.add(entry);
            addScrollViewChild(entry.root());
            grew = true;
        }
        for (int index = 0; index < materials.size(); index++) {
            activateEntry(this.entries.get(index), materials.get(index));
        }
        for (int index = materials.size(); index < this.entries.size(); index++) {
            deactivateEntry(this.entries.get(index));
        }
        return grew;
    }

    private void validateMaterials(List<PreviewMaterial> materials) {
        if (materials == null) {
            throw new IllegalArgumentException("Preview material strip materials cannot be null");
        }
        for (PreviewMaterial material : materials) {
            if (material == null) {
                throw new IllegalArgumentException("Preview material strip cannot contain null materials");
            }
            if (this.recipeRole == IngredientIO.INPUT) {
                xeiAmount(material);
            }
        }
    }

    private MaterialEntry createEntry(int index) {
        UIElement entry = new UIElement();
        entry.setId(this.idPrefix + "_material_" + index);

        MaterialItemSlot slot = new MaterialItemSlot();
        slot.setId(entry.getId() + "_slot");
        slot.setItem(ItemStack.EMPTY);
        if (this.recipeRole == IngredientIO.INPUT) {
            slot.xeiRecipeSlot(IngredientIO.INPUT, 1.0f);
        }

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

    private void activateEntry(MaterialEntry entry, PreviewMaterial material) {
        int labelWidth = Math.max(18, Long.toString(material.amount()).length() * 5 + 6);
        int stackAmount = this.recipeRole == IngredientIO.INPUT ? xeiAmount(material) : 1;
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
        entry.slot().setItem(material.key().toStack(stackAmount));
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

    static int xeiAmount(PreviewMaterial material) {
        if (material.amount() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "XEI material amount exceeds the supported int range: " + material.amount());
        }
        return (int) material.amount();
    }

    IngredientIO recipeRole() {
        return this.recipeRole;
    }

    private record MaterialEntry(UIElement root, MaterialItemSlot slot, Label amount) {}

    /**
     * Leaves the stored recipe count untouched while the adjacent label owns amount rendering.
     */
    private static final class MaterialItemSlot extends ItemSlot {

        @Override
        protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
            super.drawItemStack(guiContext, itemStack.getCount() == 1 ? itemStack : itemStack.copyWithCount(1));
        }
    }
}
