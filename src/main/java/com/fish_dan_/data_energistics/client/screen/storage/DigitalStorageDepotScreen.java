package com.fish_dan_.data_energistics.client.screen.storage;

import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorAutoExportMode;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.key.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.widget.DigitalStorageDepotAutoExportButton;
import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.menu.storage.DigitalStorageDepotMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DigitalStorageDepotScreen extends UpgradeableScreen<DigitalStorageDepotMenu> {

    private final DigitalStorageDepotAutoExportButton autoExportButton;
    private final OutputSideActionButton outputSideButton;

    public DigitalStorageDepotScreen(DigitalStorageDepotMenu menu, Inventory playerInventory, Component title,
                                     ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.widgets.addOpenPriorityButton();
        this.autoExportButton = new DigitalStorageDepotAutoExportButton(this.menu::sendSetAutoExportMode);
        this.addToLeftToolbar(this.autoExportButton);
        this.outputSideButton = new OutputSideActionButton(button -> openOutputConfig());
        this.addToLeftToolbar(this.outputSideButton);
    }

    private void openOutputConfig() {
        if (this.menu.getHost() == null) {
            return;
        }

        this.switchToScreen(new DigitalStorageDepotOutputSideScreen(
                this,
                this.menu,
                this.menu.getHost(),
                DigitalStorageDepotOutputType.ITEMS));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.autoExportButton.setMode(this.menu.getAutoExportMode());
        this.outputSideButton.setVisibility(this.menu.getAutoExportMode() == DataExtractorAutoExportMode.CONTAINER);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && isEmptyGenericSlot(this.hoveredSlot)) {
            List<Component> tooltip = new ArrayList<>();
            int slotIndex = getGenericSlotIndex(this.hoveredSlot);
            if (isFluidSemantic(this.menu.getSlotSemantic(this.hoveredSlot))) {
                tooltip.add(Component.translatable("screen.data_energistics.data_reassembler.fluid.empty"));
                tooltip.add(Component.literal(this.menu.getFluidAmount(slotIndex) + " mB / " + this.menu.getFluidCapacity() + " mB")
                        .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            } else {
                tooltip.add(Component.translatable("screen.data_energistics.data_reassembler.key.empty"));
                tooltip.add(Component.literal(this.menu.getKeyAmount(slotIndex) + " / " + this.menu.getKeyCapacity())
                        .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            }
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        if (this.menu.getCarried().isEmpty() && isFilledGenericSlot(this.hoveredSlot)) {
            List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
            int slotIndex = getGenericSlotIndex(this.hoveredSlot);
            if (isFluidSemantic(this.menu.getSlotSemantic(this.hoveredSlot))) {
                tooltip.add(Component.literal(this.menu.getFluidAmount(slotIndex) + " mB / " + this.menu.getFluidCapacity() + " mB")
                        .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            } else {
                GenericStack stack = GenericStack.fromItemStack(this.hoveredSlot.getItem());
                long amount = stack != null ? stack.amount() : 0L;
                tooltip.add(Component.literal(amount + " / " + this.menu.getKeyCapacity())
                        .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            }
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        GenericStack stack = getDisplayedGenericStack(slot);
        if (stack != null) {
            CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiGraphics, slot.x, slot.y, stack.what());
            GenericStackDisplayHelper.renderSmallOverlay(
                    guiGraphics,
                    slot.x,
                    slot.y,
                    GenericStackDisplayHelper.formatCompactAmount(stack));
            return;
        }

        if (isStorageSlot(slot) && slot.hasItem()) {
            ItemStack displayStack = slot.getItem();
            guiGraphics.renderItem(displayStack, slot.x, slot.y);
            if (displayStack.getCount() > 1) {
                GenericStackDisplayHelper.renderSmallOverlay(
                        guiGraphics,
                        slot.x,
                        slot.y,
                        GenericStackDisplayHelper.formatCompactAmount(displayStack.getCount()));
            }
            return;
        }

        super.renderSlot(guiGraphics, slot);
    }

    private boolean isEmptyGenericSlot(@Nullable Slot slot) {
        return slot != null && slot.isActive() && slot.getItem().isEmpty() && isGenericSemantic(this.menu.getSlotSemantic(slot));
    }

    private boolean isFilledGenericSlot(@Nullable Slot slot) {
        return slot != null && slot.isActive() && !slot.getItem().isEmpty() && isGenericSemantic(this.menu.getSlotSemantic(slot));
    }

    private @Nullable GenericStack getDisplayedGenericStack(@Nullable Slot slot) {
        if (slot == null || !slot.isActive()) {
            return null;
        }

        SlotSemantic semantic = this.menu.getSlotSemantic(slot);
        int slotIndex = getGenericSlotIndex(slot);
        if (slotIndex < 0) {
            return null;
        }
        if (isFluidSemantic(semantic)) {
            GenericStack display = this.menu.getFluidDisplay(slotIndex);
            return display != null ? display : fluidStack(this.menu.getFluidId(slotIndex), this.menu.getFluidAmount(slotIndex));
        }
        if (isKeySemantic(semantic) && !slot.getItem().isEmpty()) {
            return GenericStack.fromItemStack(slot.getItem());
        }
        return null;
    }

    private boolean isGenericSemantic(SlotSemantic semantic) {
        return isFluidSemantic(semantic) || isKeySemantic(semantic);
    }

    private boolean isStorageSlot(@Nullable Slot slot) {
        if (slot == null || !slot.isActive()) {
            return false;
        }

        SlotSemantic semantic = this.menu.getSlotSemantic(slot);
        return semantic == SlotSemantics.STORAGE || semantic == DigitalStorageDepotMenu.STORAGE_ROW_2 || semantic == DigitalStorageDepotMenu.STORAGE_ROW_3;
    }

    private boolean isFluidSemantic(SlotSemantic semantic) {
        return semantic == DigitalStorageDepotMenu.FLUID || semantic == DigitalStorageDepotMenu.FLUID_2 || semantic == DigitalStorageDepotMenu.FLUID_3;
    }

    private boolean isKeySemantic(SlotSemantic semantic) {
        return semantic == DigitalStorageDepotMenu.KEY || semantic == DigitalStorageDepotMenu.KEY_2 || semantic == DigitalStorageDepotMenu.KEY_3;
    }

    private int getGenericSlotIndex(@Nullable Slot slot) {
        if (slot == null) {
            return -1;
        }

        SlotSemantic semantic = this.menu.getSlotSemantic(slot);
        if (semantic == DigitalStorageDepotMenu.FLUID || semantic == DigitalStorageDepotMenu.KEY) {
            return 0;
        }
        if (semantic == DigitalStorageDepotMenu.FLUID_2 || semantic == DigitalStorageDepotMenu.KEY_2) {
            return 1;
        }
        if (semantic == DigitalStorageDepotMenu.FLUID_3 || semantic == DigitalStorageDepotMenu.KEY_3) {
            return 2;
        }
        return -1;
    }

    private @Nullable GenericStack fluidStack(String fluidId, int amount) {
        if (fluidId == null || fluidId.isBlank() || amount <= 0) {
            return null;
        }

        var fluid = BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(fluidId)).orElse(null);
        if (fluid == null) {
            return null;
        }

        AEKey key = AEFluidKey.of(new FluidStack(fluid, amount));
        return key == null ? null : new GenericStack(key, amount);
    }
}
