package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.key.CustomKeyGuiRenderer;
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

import appeng.api.client.AEKeyRendering;
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

public class DataRipperReassemblerScreen<M extends DataRipperReassemblerMenu> extends UpgradeableScreen<M>
                                        implements GenericStackLookupScreen {

    private final ProgressBar progressBar;
    private final ServerSettingToggleButton<YesNo> autoExportButton;
    private final OutputSideActionButton outputSideButton;

    public DataRipperReassemblerScreen(M menu, Inventory playerInventory, Component title, ScreenStyle style) {
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
        this.switchToScreen(new DataRipperReassemblerOutputSideScreen<>(
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
        if (this.menu.getCarried().isEmpty() && isActiveGenericSlot(this.hoveredSlot)) {
            SlotSemantic semantic = this.menu.getSlotSemantic(this.hoveredSlot);
            GenericStack stack = getDisplayedGenericStack(this.hoveredSlot);
            if (stack == null) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(getEmptySlotTooltip(semantic));
                tooltip.add(getAmountTooltip(semantic, 0));
                this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
                return;
            }

            List<Component> tooltip = new ArrayList<>(AEKeyRendering.getTooltip(stack.what()));
            if (tooltip.isEmpty()) {
                tooltip.add(stack.what().getDisplayName());
            }
            tooltip.add(getAmountTooltip(semantic, stack.amount()));
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

        int patternInputColor = this.menu.getPatternInputColor(slot);
        GenericStack genericStack = getDisplayedGenericStack(slot);
        if (genericStack != null) {
            renderGenericSlot(guiGraphics, slot, genericStack);
            renderPatternInputMarker(guiGraphics, slot, patternInputColor);
            return;
        }

        if (patternInputColor != 0 && !slot.getItem().isEmpty()) {
            renderPatternInputBackground(guiGraphics, slot, patternInputColor);
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

    private boolean isActiveGenericSlot(@Nullable Slot slot) {
        if (slot == null || !slot.isActive()) {
            return false;
        }

        return isGenericSemantic(this.menu.getSlotSemantic(slot));
    }

    protected boolean isGenericSemantic(SlotSemantic semantic) {
        return semantic == SlotSemantics.STORAGE ||
                semantic == DataRipperReassemblerMenu.FLUID_INPUT_B ||
                semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_A ||
                semantic == DataRipperReassemblerMenu.FLUID_OUTPUT_B ||
                semantic == DataRipperReassemblerMenu.KEY_INPUT ||
                semantic == DataRipperReassemblerMenu.KEY_OUTPUT;
    }

    protected Component getEmptySlotTooltip(SlotSemantic semantic) {
        if (semantic == DataRipperReassemblerMenu.KEY_INPUT || semantic == DataRipperReassemblerMenu.KEY_OUTPUT) {
            return Component.translatable("screen.data_energistics.data_reassembler.key.empty");
        }
        return Component.translatable("screen.data_energistics.data_reassembler.fluid.empty");
    }

    protected Component getAmountTooltip(SlotSemantic semantic, long amount) {
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

    private static void renderPatternInputBackground(GuiGraphics guiGraphics, Slot slot, int color) {
        guiGraphics.fill(slot.x + 1, slot.y + 1, slot.x + 15, slot.y + 15, 0x50000000 | color);
    }

    private static void renderPatternInputMarker(GuiGraphics guiGraphics, Slot slot, int color) {
        if (color != 0) {
            guiGraphics.fill(slot.x + 1, slot.y + 1, slot.x + 5, slot.y + 5, 0xFF000000 | color);
        }
    }

    protected @Nullable GenericStack getDisplayedGenericStack(@Nullable Slot slot) {
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

    protected static @Nullable GenericStack fluidStack(String fluidId, int amount) {
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
