package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenu;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenuHost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.Tooltips;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class DigitalConstructFlowerScreen extends AEBaseScreen<DigitalConstructFlowerMenu> {

    private static final int LABEL_COLOR = 0x080C1B;
    private static final int VALUE_COLOR = 0x9CD3FF;
    private static final int SUCCESS_COLOR = 0x62D96B;
    private static final int WARNING_COLOR = 0xFFB347;
    private static final int ERROR_COLOR = 0xFF6B6B;
    private static final int BUSY_COLOR = 0xFFE066;
    private static final int STATUS_X = 6;
    private static final int STATUS_Y = 19;
    private static final int STATUS_WIDTH = 73;
    private static final int STATUS_HEIGHT = 86;
    private static final int LEFT_TEXT_X = 10;
    private static final int LEFT_TEXT_Y = 23;
    private static final int LEFT_TEXT_WIDTH = 66;
    private static final int RIGHT_TEXT_X = 83;
    private static final int RIGHT_TEXT_WIDTH = 83;
    private static final int CPU_TEXT_Y = 23;
    private static final int STORAGE_TEXT_X = 81;
    private static final int STORAGE_TEXT_WIDTH = 87;
    private static final int STORAGE_TYPES_TEXT_Y = 74;
    private static final int STORAGE_AMOUNT_TEXT_Y = 83;
    private static final int CRAFTING_TEXT_Y = 96;
    private static final int CRAFTING_HOVER_X = 79;
    private static final int CRAFTING_HOVER_Y = 94;
    private static final int CRAFTING_HOVER_WIDTH = 91;
    private static final int CRAFTING_HOVER_HEIGHT = 12;
    private static final int LINE_HEIGHT = 10;
    private static final BigInteger UNIT_BASE = BigInteger.valueOf(1024L);
    private static final String[] COMPACT_UNITS = { "", "K", "M", "G", "T", "P", "E" };

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
    }

    private void drawStatusText(GuiGraphics guiGraphics) {
        drawStructureStatus(guiGraphics);
        drawCpuStatus(guiGraphics);
        drawStorageStatus(guiGraphics);
        drawCraftingStatus(guiGraphics);
    }

    private void drawStructureStatus(GuiGraphics guiGraphics) {
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.status_label", Component.translatable(
                this.menu.online ? "screen.data_energistics.digital_construct_flower.status_online" : "screen.data_energistics.digital_construct_flower.status_offline"), LEFT_TEXT_X, LEFT_TEXT_Y, statusColor(this.menu.online), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.formed_label", Component.translatable(
                this.menu.structureFormed ? "screen.data_energistics.digital_construct_flower.formed.yes" : "screen.data_energistics.digital_construct_flower.formed.no"), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT, statusColor(this.menu.structureFormed), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.matched_blocks_label", Component.literal(
                compactNumber(Integer.toString(this.menu.matchedBlockCount))), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 2, VALUE_COLOR,
                LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.pattern_buffers_label", Component.literal(
                compactNumber(Integer.toString(this.menu.patternBufferCount))), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 3, VALUE_COLOR,
                LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_structure_label", Component.translatable(
                this.menu.cpuStructureFormed ? "screen.data_energistics.digital_construct_flower.formed.yes" : "screen.data_energistics.digital_construct_flower.formed.no"), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 4,
                statusColor(this.menu.cpuStructureFormed), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.crafting_structure_label", Component.translatable(
                this.menu.craftingStructureFormed ? "screen.data_energistics.digital_construct_flower.formed.yes" : "screen.data_energistics.digital_construct_flower.formed.no"), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 5,
                statusColor(this.menu.craftingStructureFormed && this.menu.craftingPatternCapacity > 0), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.last_failure_label", getFailureSummary(),
                LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 6, hasAnyFailure() ? ERROR_COLOR : SUCCESS_COLOR, LEFT_TEXT_WIDTH);
    }

    private void drawCpuStatus(GuiGraphics guiGraphics) {
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_label", Component.literal(
                Integer.toString(this.menu.busyCraftingCpuCount)), RIGHT_TEXT_X, CPU_TEXT_Y,
                this.menu.busyCraftingCpuCount > 0 ? BUSY_COLOR : VALUE_COLOR, RIGHT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_partitions_label", Component.literal(
                this.menu.busyCpuPartitionCount + "/" + this.menu.cpuPartitionCount), RIGHT_TEXT_X, CPU_TEXT_Y + LINE_HEIGHT,
                this.menu.busyCpuPartitionCount > 0 ? BUSY_COLOR : VALUE_COLOR, RIGHT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_storage_label", Component.literal(
                compactNumber(Long.toString(this.menu.cpuStorageBytes))), RIGHT_TEXT_X, CPU_TEXT_Y + LINE_HEIGHT * 2, VALUE_COLOR,
                RIGHT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.cpu_coprocessors_label", Component.literal(
                compactNumber(Integer.toString(this.menu.cpuCoProcessors))), RIGHT_TEXT_X, CPU_TEXT_Y + LINE_HEIGHT * 3, VALUE_COLOR,
                RIGHT_TEXT_WIDTH);
    }

    private void drawStorageStatus(GuiGraphics guiGraphics) {
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.storage_types_label",
                Component.literal(formatCapacityPair(Integer.toString(this.menu.storedTypeCount), this.menu.storedTypeCapacityText)),
                STORAGE_TEXT_X, STORAGE_TYPES_TEXT_Y, VALUE_COLOR, STORAGE_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.storage_amount_label",
                Component.literal(formatCapacityPair(this.menu.storedAmountText, this.menu.storedAmountCapacityText)),
                STORAGE_TEXT_X, STORAGE_AMOUNT_TEXT_Y, VALUE_COLOR, STORAGE_TEXT_WIDTH);
    }

    private void drawCraftingStatus(GuiGraphics guiGraphics) {
        int statusColor = craftingUnavailable() ? ERROR_COLOR : this.menu.hasCraftingTarget() ? BUSY_COLOR : SUCCESS_COLOR;
        drawCenteredKeyValueText(guiGraphics, "screen.data_energistics.digital_construct_flower.molecular_label", getMolecularStatus(),
                RIGHT_TEXT_X, CRAFTING_TEXT_Y, statusColor, RIGHT_TEXT_WIDTH);
    }

    private static void drawKeyValueText(GuiGraphics guiGraphics, String labelKey, Component value, int x, int y, int valueColor,
                                         int maxWidth) {
        Component label = Component.translatable(labelKey);
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, label, x, y, LABEL_COLOR, false);
        int valueX = x + font.width(label);
        int valueWidth = Math.max(0, maxWidth - font.width(label));
        guiGraphics.drawString(font, trimToWidth(font, value.getString(), valueWidth), valueX, y, valueColor, false);
    }

    private static void drawCenteredKeyValueText(GuiGraphics guiGraphics, String labelKey, Component value, int x, int y,
                                                 int valueColor, int maxWidth) {
        Component label = Component.translatable(labelKey);
        Font font = Minecraft.getInstance().font;
        int labelWidth = font.width(label);
        int valueWidth = Math.max(0, maxWidth - font.width(label));
        String valueText = trimToWidth(font, value.getString(), valueWidth);
        int textX = x + (maxWidth - labelWidth - font.width(valueText)) / 2;
        guiGraphics.drawString(font, label, textX, y, LABEL_COLOR, false);
        int valueX = textX + labelWidth;
        guiGraphics.drawString(font, valueText, valueX, y, valueColor, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        GenericStack target = this.menu.getCraftingTarget();
        if (this.menu.getCarried().isEmpty() && !craftingUnavailable() && target != null && target.what() != null &&
                isMouseOverLocal(mouseX, mouseY, CRAFTING_HOVER_X, CRAFTING_HOVER_Y, CRAFTING_HOVER_WIDTH, CRAFTING_HOVER_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(target.what().getDisplayName());
            tooltip.add(GenericStackDisplayHelper.createAmountTooltip(target));
            tooltip.add(Component.translatable(
                    "screen.data_energistics.digital_construct_flower.busy_cpus",
                    this.menu.busyCraftingCpuCount).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            tooltip.add(Component.translatable(
                    "screen.data_energistics.digital_construct_flower.crafting_pattern_capacity",
                    this.menu.craftingPatternCapacity).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        if (this.menu.getCarried().isEmpty() && (hasFailure() || hasCpuFailure() || hasCraftingFailure()) &&
                isMouseOverLocal(mouseX, mouseY, STATUS_X, STATUS_Y, STATUS_WIDTH, STATUS_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            if (hasFailure()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.digital_construct_flower.last_failure",
                        MultiBlockFailureText.describe(this.menu.lastFailureReason)));
                if (!this.menu.lastFailurePosition.isBlank()) {
                    tooltip.add(Component.translatable(
                            "screen.data_energistics.digital_construct_flower.failure_position",
                            this.menu.lastFailurePosition).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
                }
            }
            if (hasCpuFailure()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.digital_construct_flower.cpu_failure",
                        MultiBlockFailureText.describe(this.menu.cpuLastFailureReason)));
                if (!this.menu.cpuLastFailurePosition.isBlank()) {
                    tooltip.add(Component.translatable(
                            "screen.data_energistics.digital_construct_flower.cpu_failure_position",
                            this.menu.cpuLastFailurePosition).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
                }
            }
            if (hasCraftingFailure()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.digital_construct_flower.crafting_failure",
                        MultiBlockFailureText.describe(this.menu.craftingLastFailureReason)));
                if (!this.menu.craftingLastFailurePosition.isBlank()) {
                    tooltip.add(Component.translatable(
                            "screen.data_energistics.digital_construct_flower.crafting_failure_position",
                            this.menu.craftingLastFailurePosition).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
                }
            }
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private Component getFailureSummary() {
        if (!hasFailure() && !hasCpuFailure() && !hasCraftingFailure()) {
            return Component.translatable("screen.data_energistics.digital_construct_flower.no_failure");
        }
        if (hasFailure()) {
            return MultiBlockFailureText.describe(this.menu.lastFailureReason);
        }
        if (hasCpuFailure()) {
            return MultiBlockFailureText.describe(this.menu.cpuLastFailureReason);
        }
        return MultiBlockFailureText.describe(this.menu.craftingLastFailureReason);
    }

    private Component getMolecularStatus() {
        if (craftingUnavailable()) {
            return Component.translatable("screen.data_energistics.digital_construct_flower.molecular_unavailable");
        }
        GenericStack target = this.menu.getCraftingTarget();
        if (target == null || target.what() == null) {
            return Component.translatable("screen.data_energistics.digital_construct_flower.molecular_idle");
        }
        return target.what().getDisplayName();
    }

    private static int statusColor(boolean ok) {
        return ok ? SUCCESS_COLOR : WARNING_COLOR;
    }

    private static String compactNumber(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }
        BigInteger amount = new BigInteger(value.trim());
        if (amount.signum() == 0) {
            return "0";
        }

        BigInteger absoluteAmount = amount.abs();
        BigInteger divisor = BigInteger.ONE;
        int unitIndex = 0;
        while (unitIndex < COMPACT_UNITS.length - 1 && absoluteAmount.compareTo(divisor.multiply(UNIT_BASE)) >= 0) {
            divisor = divisor.multiply(UNIT_BASE);
            unitIndex++;
        }
        if (unitIndex == 0) {
            return amount.toString();
        }

        BigInteger whole = absoluteAmount.divide(divisor);
        BigInteger fraction = absoluteAmount.remainder(divisor).multiply(BigInteger.TEN).divide(divisor);
        String sign = amount.signum() < 0 ? "-" : "";
        if (whole.compareTo(BigInteger.TEN) >= 0 || fraction.signum() == 0) {
            return sign + whole + COMPACT_UNITS[unitIndex];
        }
        return sign + whole + "." + fraction + COMPACT_UNITS[unitIndex];
    }

    private static String formatCapacityPair(String current, String capacity) {
        return compactCapacityNumber(current) + "/" + compactCapacityNumber(capacity);
    }

    private static String compactCapacityNumber(String value) {
        if (DigitalConstructFlowerMenuHost.UNLIMITED_STORAGE_CAPACITY.equals(value)) {
            return value;
        }
        return compactNumber(value);
    }

    private static String trimToWidth(Font font, String value, int maxWidth) {
        if (maxWidth <= 0 || value.isEmpty()) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return font.plainSubstrByWidth(value, maxWidth);
        }
        return font.plainSubstrByWidth(value, maxWidth - ellipsisWidth) + ellipsis;
    }

    private boolean hasFailure() {
        return this.menu.lastFailureReason != null && !this.menu.lastFailureReason.isBlank();
    }

    private boolean hasCpuFailure() {
        return this.menu.cpuLastFailureReason != null && !this.menu.cpuLastFailureReason.isBlank();
    }

    private boolean hasCraftingFailure() {
        return this.menu.craftingLastFailureReason != null && !this.menu.craftingLastFailureReason.isBlank();
    }

    private boolean hasAnyFailure() {
        return hasFailure() || hasCpuFailure() || hasCraftingFailure();
    }

    private boolean craftingUnavailable() {
        return !this.menu.craftingStructureFormed || this.menu.craftingPatternCapacity <= 0;
    }

    private boolean isMouseOverLocal(int mouseX, int mouseY, int x, int y, int width, int height) {
        int screenX = this.leftPos + x;
        int screenY = this.topPos + y;
        return mouseX >= screenX && mouseX < screenX + width && mouseY >= screenY && mouseY < screenY + height;
    }
}
