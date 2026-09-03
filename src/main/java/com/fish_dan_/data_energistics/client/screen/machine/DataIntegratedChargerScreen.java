package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.key.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.widget.DataIntegratedChargerModeButton;
import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.menu.machine.DataIntegratedChargerMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DataIntegratedChargerScreen extends UpgradeableScreen<DataIntegratedChargerMenu> {

    private final ServerSettingToggleButton<YesNo> autoExportButton;
    private final OutputSideActionButton outputSidesButton;
    private final DataIntegratedChargerModeButton modeButton;
    private final ProgressBar progressBar;

    public DataIntegratedChargerScreen(DataIntegratedChargerMenu menu, Inventory playerInventory, Component title,
                                       ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.autoExportButton = new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO);
        this.addToLeftToolbar(this.autoExportButton);
        this.outputSidesButton = new OutputSideActionButton(button -> openOutputConfig());
        this.addToLeftToolbar(this.outputSidesButton);
        this.modeButton = new DataIntegratedChargerModeButton(this.menu::sendSetMachineMode);
        this.addToLeftToolbar(this.modeButton);
        this.progressBar = new ProgressBar(this.menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        this.widgets.add("progressBar", this.progressBar);
    }

    private void openOutputConfig() {
        if (this.menu.getHost() == null) {
            return;
        }

        this.switchToScreen(new DataIntegratedChargerOutputSideScreen(
                this,
                this.menu,
                this.menu.getHost()));
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
        boolean autoExportEnabled = this.autoExportButton.getCurrentValue() == YesNo.YES;
        this.outputSidesButton.setVisibility(autoExportEnabled);
        this.modeButton.setMode(this.menu.getMachineMode());
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && isFluidTankSlot(this.hoveredSlot)) {
            List<Component> tooltip = new ArrayList<>();
            if (getDisplayedFluid(this.hoveredSlot) == null) {
                tooltip.add(Component.translatable("screen.data_energistics.data_integrated_charger.fluid.empty"));
            } else {
                tooltip.addAll(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
            }
            tooltip.add(Component.literal(this.menu.fluidAmount + " mB / " + this.menu.getFluidCapacity() + " mB")
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        GenericStack fluid = getDisplayedFluid(slot);
        if (fluid != null) {
            CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiGraphics, slot.x, slot.y, fluid.what());
            GenericStackDisplayHelper.renderSmallOverlay(
                    guiGraphics,
                    slot.x,
                    slot.y,
                    GenericStackDisplayHelper.formatCompactAmount(fluid));
            return;
        }

        super.renderSlot(guiGraphics, slot);
    }

    @Override
    public @Nullable StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        // This mutable tank slot is an input target. Do not let recipe viewers consume a follow-up fill click as a
        // lookup for the fluid that was synced after the first transfer.
        if (isFluidTankSlot(this.hoveredSlot)) {
            return null;
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    private @Nullable GenericStack getDisplayedFluid(@Nullable Slot slot) {
        if (!isFluidTankSlot(slot)) {
            return null;
        }

        // The slot is backed by a ConfigMenuInventory and may expose an empty ItemStack for a
        // fluid key. Use the synchronized menu fields as the source of truth, just like the
        // reassembler screen does for all of its fluid slots.
        if (this.menu.fluidId == null || this.menu.fluidId.isBlank() || this.menu.fluidAmount <= 0) {
            return null;
        }

        var fluid = BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(this.menu.fluidId)).orElse(null);
        if (fluid == null) {
            return null;
        }

        AEKey key = AEFluidKey.of(new FluidStack(fluid, this.menu.fluidAmount));
        return key == null ? null : new GenericStack(key, this.menu.fluidAmount);
    }

    private boolean isFluidTankSlot(@Nullable Slot slot) {
        return slot != null && this.menu.getSlotSemantic(slot) == DataIntegratedChargerMenu.FLUID_TANK;
    }
}
