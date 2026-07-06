package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.Tooltips;

import java.util.ArrayList;
import java.util.List;

public class DigitalConstructFlowerScreen extends AEBaseScreen<DigitalConstructFlowerMenu> {

    private static final int LABEL_COLOR = 0x080C1B;
    private static final int VALUE_COLOR = 0x005E83;
    private static final float LEFT_TEXT_SCALE = 0.9F;
    private static final int TARGET_HOVER_X = 156;
    private static final int TARGET_HOVER_Y = 96;
    private static final int TARGET_HOVER_WIDTH = 13;
    private static final int TARGET_HOVER_HEIGHT = 13;
    private static final int TARGET_ICON_X = 158;
    private static final int TARGET_ICON_Y = 97;
    private static final float TARGET_ICON_SCALE = 0.625F;
    private static final int STATUS_X = 6;
    private static final int STATUS_Y = 18;
    private static final int STATUS_WIDTH = 96;
    private static final int STATUS_HEIGHT = 88;

    public DigitalConstructFlowerScreen(DigitalConstructFlowerMenu menu, Inventory playerInventory, Component title,
                                        ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent("dialog_title", Component.translatable("block.data_energistics.digital_construct_flower"));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        drawStatusText(guiGraphics);

        GenericStack target = this.menu.getCraftingTarget();
        if (target == null || target.what() == null) {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(TARGET_ICON_X, TARGET_ICON_Y, 100.0F);
        guiGraphics.pose().scale(TARGET_ICON_SCALE, TARGET_ICON_SCALE, 1.0F);
        CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiGraphics, 0, 0, target.what());
        guiGraphics.pose().popPose();
    }

    private void drawStatusText(GuiGraphics guiGraphics) {
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_label", Component.literal(
                Integer.toString(this.menu.busyCraftingCpuCount)), 9, 24, LEFT_TEXT_SCALE);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_partitions_label", Component.literal(
                this.menu.busyCpuPartitionCount + "/" + this.menu.cpuPartitionCount), 9, 35, LEFT_TEXT_SCALE);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_storage_label", Component.literal(
                shortenDecimal(Long.toString(this.menu.cpuStorageBytes))), 9, 46, LEFT_TEXT_SCALE);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_coprocessors_label", Component.literal(
                Integer.toString(this.menu.cpuCoProcessors)), 9, 57, LEFT_TEXT_SCALE);

        drawRightKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.status_label", Component.translatable(
                this.menu.online ? "screen.data_energistics.digital_construct_flower.status_online" : "screen.data_energistics.digital_construct_flower.status_offline"), 107, 22);
        drawRightKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.formed_label", Component.translatable(
                this.menu.structureFormed ? "screen.data_energistics.digital_construct_flower.formed.yes" : "screen.data_energistics.digital_construct_flower.formed.no"), 107, 34);

        drawRightKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.storage_types_label", Component.literal(
                Integer.toString(this.menu.storedTypeCount)), 107, 54);
        drawRightKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.storage_amount_label", Component.literal(
                shortenDecimal(this.menu.storedAmountText)), 107, 66);

        drawRightKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.molecular_label", getMolecularStatus(), 107, 88);
    }

    private static void drawKeyValueText(GuiGraphics guiGraphics, String labelKey, Component value, int x, int y, float scale) {
        Component label = Component.translatable(labelKey);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, label, 0, 0, LABEL_COLOR, false);
        guiGraphics.drawString(font, value, font.width(label), 0, VALUE_COLOR, false);
        guiGraphics.pose().popPose();
    }

    private static void drawRightKeyValueText(GuiGraphics guiGraphics, String labelKey, Component value, int x, int y) {
        Component label = Component.translatable(labelKey);
        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, label, x, y, LABEL_COLOR, false);
        guiGraphics.drawString(font, value, x + font.width(label), y, VALUE_COLOR, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        GenericStack target = this.menu.getCraftingTarget();
        if (this.menu.getCarried().isEmpty() && target != null && target.what() != null &&
                isMouseOverLocal(mouseX, mouseY, TARGET_HOVER_X, TARGET_HOVER_Y, TARGET_HOVER_WIDTH, TARGET_HOVER_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(target.what().getDisplayName());
            tooltip.add(GenericStackDisplayHelper.createAmountTooltip(target));
            tooltip.add(Component.translatable(
                    "screen.data_energistics.digital_construct_flower.busy_cpus",
                    this.menu.busyCraftingCpuCount).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        if (this.menu.getCarried().isEmpty() && hasFailure() &&
                isMouseOverLocal(mouseX, mouseY, STATUS_X, STATUS_Y, STATUS_WIDTH, STATUS_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(
                    "screen.data_energistics.digital_construct_flower.last_failure",
                    MultiBlockFailureText.describe(this.menu.lastFailureReason)));
            if (!this.menu.lastFailurePosition.isBlank()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.digital_construct_flower.failure_position",
                        this.menu.lastFailurePosition).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            }
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private Component getMolecularStatus() {
        GenericStack target = this.menu.getCraftingTarget();
        if (target == null || target.what() == null) {
            return Component.translatable("screen.data_energistics.digital_construct_flower.molecular_idle");
        }
        return target.what().getDisplayName();
    }

    private static String shortenDecimal(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }
        if (value.length() <= 8) {
            return value;
        }
        return value.charAt(0) + "." + value.substring(1, 3) + "e" + (value.length() - 1);
    }

    private boolean hasFailure() {
        return this.menu.lastFailureReason != null && !this.menu.lastFailureReason.isBlank();
    }

    private boolean isMouseOverLocal(int mouseX, int mouseY, int x, int y, int width, int height) {
        int screenX = this.leftPos + x;
        int screenY = this.topPos + y;
        return mouseX >= screenX && mouseX < screenX + width && mouseY >= screenY && mouseY < screenY + height;
    }
}
