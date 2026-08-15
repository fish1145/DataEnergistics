package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorDropRoutingMode;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.key.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.client.widget.DataMimeticFieldOutputRoutingButton;
import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.menu.machine.DataMimeticFieldMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantics;

import java.util.ArrayList;
import java.util.List;

public class DataMimeticFieldScreen extends UpgradeableScreen<DataMimeticFieldMenu> {

    private final DataExtractorToggleButton redstoneControlButton;
    private final DataExtractorToggleButton autoPullKeyInputButton;
    private final DataMimeticFieldOutputRoutingButton dropRoutingButton;
    private final OutputSideActionButton outputSideButton;
    private final ProgressBar powerBar;

    public DataMimeticFieldScreen(DataMimeticFieldMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.redstoneControlButton = new DataExtractorToggleButton(
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.redstone_control",
                "button.data_energistics.redstone_control.enabled",
                "button.data_energistics.redstone_control.disabled",
                this.menu::sendSetRedstoneControlled);
        this.addToLeftToolbar(this.redstoneControlButton);

        this.autoPullKeyInputButton = new DataExtractorToggleButton(
                Icon.AUTO_EXPORT_ON,
                Icon.AUTO_EXPORT_OFF,
                "button.data_energistics.data_mimetic_field.auto_pull_key_input",
                "button.data_energistics.data_mimetic_field.auto_pull_key_input.enabled",
                "button.data_energistics.data_mimetic_field.auto_pull_key_input.disabled",
                this.menu::sendSetAutoPullKeyInput);
        this.addToLeftToolbar(this.autoPullKeyInputButton);

        this.dropRoutingButton = new DataMimeticFieldOutputRoutingButton(this.menu::sendSetDropRoutingMode);
        this.addToLeftToolbar(this.dropRoutingButton);

        this.outputSideButton = new OutputSideActionButton(button -> openOutputConfig());
        this.addToLeftToolbar(this.outputSideButton);

        this.powerBar = new ProgressBar(this.menu, style.getImage("powerBar"), ProgressBar.Direction.VERTICAL);
        widgets.add("powerBar", this.powerBar);
    }

    private void openOutputConfig() {
        if (this.menu.getHost() == null) {
            return;
        }

        this.switchToScreen(new DataMimeticFieldOutputSideScreen(
                this,
                this.menu.getHost(),
                this.menu.getOutputSides(),
                this.menu::sendSetOutputSide));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.redstoneControlButton.setState(this.menu.redstoneControlled);
        this.autoPullKeyInputButton.setState(this.menu.autoPullKeyInput);
        this.dropRoutingButton.setMode(this.menu.getDropRoutingMode());
        this.outputSideButton.setVisibility(this.menu.getDropRoutingMode() == DataExtractorDropRoutingMode.CONTAINER);
        this.powerBar.visible = this.menu.getMaxProgress() > 0;
        if (this.powerBar.visible) {
            int percent = this.menu.getCurrentProgress() * 100 / Math.max(1, this.menu.getMaxProgress());
            this.powerBar.setFullMsg(Component.literal(percent + "%"));
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && isEmptyKeySlot(this.hoveredSlot)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("screen.data_energistics.data_reassembler.key.empty"));
            tooltip.add(Component.literal(this.menu.keyInputAmount + " / " + this.menu.getKeyInputCapacity())
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        if (this.menu.getCarried().isEmpty() && isKeySlot(this.hoveredSlot)) {
            List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
            GenericStack stack = GenericStack.fromItemStack(this.hoveredSlot.getItem());
            long amount = stack != null ? stack.amount() : 0L;
            tooltip.add(Component.literal(amount + " / " + this.menu.getKeyInputCapacity())
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        GenericStack genericStack = getDisplayedKeyStack(slot);
        if (genericStack != null) {
            renderKeySlot(guiGraphics, slot, genericStack);
            return;
        }

        if (slot.isActive() && slot.getItem().isEmpty() && (this.menu.getSlotSemantic(slot) == SlotSemantics.STORAGE || this.menu.getSlotSemantic(slot) == DataMimeticFieldMenu.EXTRA_STORAGE)) {
            DataEnergisticsIcon.getBlitter("BACKGROUND_DATA_CARRIER_PATTERN")
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        }

        super.renderSlot(guiGraphics, slot);
    }

    private boolean isKeySlot(Slot slot) {
        return slot != null && slot.isActive() && this.menu.getSlotSemantic(slot) == DataMimeticFieldMenu.KEY_INPUT;
    }

    private boolean isEmptyKeySlot(Slot slot) {
        return isKeySlot(slot) && slot.getItem().isEmpty();
    }

    private GenericStack getDisplayedKeyStack(Slot slot) {
        if (!isKeySlot(slot) || slot.getItem().isEmpty()) {
            return null;
        }
        return GenericStack.fromItemStack(slot.getItem());
    }

    private void renderKeySlot(GuiGraphics guiGraphics, Slot slot, GenericStack stack) {
        CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiGraphics, slot.x, slot.y, stack.what());
        GenericStackDisplayHelper.renderSmallOverlay(
                guiGraphics,
                slot.x,
                slot.y,
                GenericStackDisplayHelper.formatCompactAmount(stack));
    }
}
