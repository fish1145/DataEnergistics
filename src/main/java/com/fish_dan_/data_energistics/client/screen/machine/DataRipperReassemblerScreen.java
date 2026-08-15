package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;
import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.menu.machine.DataRipperReassemblerMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DataRipperReassemblerScreen extends UpgradeableScreen<DataRipperReassemblerMenu>
                                         implements GenericStackLookupScreen {

    private final ProgressBar progressBar;
    private final ServerSettingToggleButton<YesNo> autoExportButton;
    private final OutputSideActionButton outputSideButton;

    public DataRipperReassemblerScreen(DataRipperReassemblerMenu menu, Inventory playerInventory, Component title,
                                       ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.autoExportButton = new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO);
        this.addToLeftToolbar(this.autoExportButton);
        this.outputSideButton = new OutputSideActionButton(button -> openOutputConfig());
        this.addToLeftToolbar(this.outputSideButton);
        this.progressBar = new ProgressBar(this.menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        this.widgets.add("progressBar", this.progressBar);
    }

    private void openOutputConfig() {
        if (this.menu.getHost() == null) {
            return;
        }
        this.switchToScreen(new DataRipperReassemblerOutputSideScreen(
                this,
                this.menu,
                this.menu.getHost(),
                DigitalStorageDepotOutputType.ITEMS));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.progressBar.visible = this.menu.getMaxProgress() > 0;
        if (this.progressBar.visible) {
            int percent = this.menu.getCurrentProgress() * 100 / Math.max(1, this.menu.getMaxProgress());
            this.progressBar.setFullMsg(Component.literal(percent + "%"));
        }
        this.autoExportButton.set(this.menu.getAutoExport());
        this.outputSideButton.setVisibility(this.autoExportButton.getCurrentValue() == YesNo.YES);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && isEmptyGenericSlot(this.hoveredSlot)) {
            SlotSemantic semantic = this.menu.getSlotSemantic(this.hoveredSlot);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(getEmptySlotTooltip(semantic));
            tooltip.add(getAmountTooltip(semantic, 0));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        if (this.menu.getCarried().isEmpty() && isGenericStorageSlot(this.hoveredSlot)) {
            List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
            GenericStack stack = GenericStack.fromItemStack(this.hoveredSlot.getItem());
            long amount = stack != null ? stack.amount() : 0L;
            tooltip.add(getAmountTooltip(this.menu.getSlotSemantic(this.hoveredSlot), amount));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.isActive() && slot.getItem().isEmpty() && this.menu.getSlotSemantic(slot) == SlotSemantics.UPGRADE) {
            DataEnergisticsIcon.getBlitter("PLACEMENT_TOOLBOX")
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        }

        GenericStack genericStack = getDisplayedGenericStack(slot);
        if (genericStack != null) {
            renderGenericSlot(guiGraphics, slot, genericStack);
            return;
        }

        super.renderSlot(guiGraphics, slot);
    }

    @Override
    public @Nullable StackWithBounds dataEnergistics$getGenericStackUnderMouse(double mouseX, double mouseY) {
        GenericStack stack = getDisplayedGenericStack(this.hoveredSlot);
        if (stack == null) {
            return null;
        }
        return new StackWithBounds(
                stack,
                new Rect2i(this.leftPos + this.hoveredSlot.x, this.topPos + this.hoveredSlot.y, 16, 16));
    }

    private boolean isEmptyGenericSlot(@Nullable Slot slot) {
        if (slot == null || !slot.isActive() || !slot.getItem().isEmpty()) {
            return false;
        }

        return isGenericSemantic(this.menu.getSlotSemantic(slot));
    }

    private boolean isGenericStorageSlot(@Nullable Slot slot) {
        if (slot == null || !slot.isActive() || slot.getItem().isEmpty()) {
            return false;
        }

        return isGenericSemantic(this.menu.getSlotSemantic(slot));
    }

    private static boolean isGenericSemantic(SlotSemantic semantic) {
        return semantic == SlotSemantics.STORAGE ||
                semantic == DataRipperReassemblerMenu.FLUID_INPUT_B ||
                semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_A ||
                semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_B ||
                semantic == DataRipperReassemblerMenu.KEY_INPUT ||
                semantic == DataRipperReassemblerMenu.KEY_OUTPUT;
    }

    private Component getEmptySlotTooltip(SlotSemantic semantic) {
        if (semantic == DataRipperReassemblerMenu.KEY_INPUT || semantic == DataRipperReassemblerMenu.KEY_OUTPUT) {
            return Component.translatable("screen.data_energistics.data_reassembler.key.empty");
        }
        return Component.translatable("screen.data_energistics.data_reassembler.fluid.empty");
    }

    private Component getAmountTooltip(SlotSemantic semantic, long amount) {
        if (semantic == SlotSemantics.STORAGE || semantic == DataRipperReassemblerMenu.FLUID_INPUT_B) {
            return Component.literal(amount + " mB / " + this.menu.getFluidInputCapacity() + " mB")
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
        }
        if (semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_A ||
                semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_B) {
            return Component.literal(amount + " mB / " + this.menu.getFluidOutputCapacity() + " mB")
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
        }
        if (semantic == DataRipperReassemblerMenu.KEY_INPUT) {
            return Component.literal(amount + " / " + this.menu.getKeyInputCapacity())
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
        }
        return Component.literal(amount + " / " + this.menu.getKeyOutputCapacity())
                .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
    }

    private static void renderGenericSlot(GuiGraphics guiGraphics, Slot slot, GenericStack genericStack) {
        CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiGraphics, slot.x, slot.y, genericStack.what());
        GenericStackDisplayHelper.renderSmallOverlay(
                guiGraphics,
                slot.x,
                slot.y,
                GenericStackDisplayHelper.formatCompactAmount(genericStack));
    }

    private @Nullable GenericStack getDisplayedGenericStack(@Nullable Slot slot) {
        if (slot == null || !slot.isActive()) {
            return null;
        }

        SlotSemantic semantic = this.menu.getSlotSemantic(slot);
        if (semantic == SlotSemantics.STORAGE) {
            return fluidStack(this.menu.fluidInputAId, this.menu.fluidInputAAmount);
        }
        if (semantic == DataRipperReassemblerMenu.FLUID_INPUT_B) {
            return fluidStack(this.menu.fluidInputBId, this.menu.fluidInputBAmount);
        }
        if (semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_A) {
            return fluidStack(this.menu.fluidOutputAId, this.menu.fluidOutputAAmount);
        }
        if (semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_B) {
            return fluidStack(this.menu.fluidOutputBId, this.menu.fluidOutputBAmount);
        }
        if (semantic == DataRipperReassemblerMenu.KEY_INPUT || semantic == DataRipperReassemblerMenu.KEY_OUTPUT) {
            return GenericStack.fromItemStack(slot.getItem());
        }
        return null;
    }

    private static @Nullable GenericStack fluidStack(String fluidId, int amount) {
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
