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
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.Tooltips;

import java.util.ArrayList;
import java.util.List;

public class DigitalConstructFlowerScreen extends AEBaseScreen<DigitalConstructFlowerMenu> {

    private static final int CPU_X = 102;
    private static final int CPU_Y = 80;
    private static final int TARGET_HOVER_X = CPU_X + 54;
    private static final int TARGET_HOVER_Y = CPU_Y;
    private static final int TARGET_HOVER_WIDTH = 13;
    private static final int TARGET_HOVER_HEIGHT = 21;
    private static final int TARGET_ICON_X = CPU_X + 56;
    private static final int TARGET_ICON_Y = CPU_Y + 6;
    private static final float TARGET_ICON_SCALE = 0.625F;
    private static final int STATUS_X = 6;
    private static final int STATUS_Y = 18;
    private static final int STATUS_WIDTH = 96;
    private static final int STATUS_HEIGHT = 88;
    private static final int FAILURE_SUMMARY_LENGTH = 18;

    private final Blitter cpuIdle;
    private final Blitter cpuTaskOverlay;

    public DigitalConstructFlowerScreen(DigitalConstructFlowerMenu menu, Inventory playerInventory, Component title,
                                        ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.cpuIdle = style.getImage("cpuIdle");
        this.cpuTaskOverlay = style.getImage("cpuTaskOverlay");
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent("dialog_title", Component.translatable("block.data_energistics.digital_construct_flower"));
        setTextContent("online", Component.translatable(
                this.menu.online ? "screen.data_energistics.status.online" : "screen.data_energistics.status.offline"));
        setTextContent("formed", Component.translatable(
                "screen.data_energistics.digital_construct_flower.formed",
                Component.translatable(this.menu.structureFormed ? "screen.data_energistics.digital_construct_flower.formed.yes" : "screen.data_energistics.digital_construct_flower.formed.no")));
        setTextContent("matched_blocks", Component.translatable(
                "screen.data_energistics.digital_construct_flower.matched_blocks",
                this.menu.matchedBlockCount));
        setTextContent("pattern_buffers", Component.translatable(
                "screen.data_energistics.digital_construct_flower.pattern_buffers",
                this.menu.patternBufferCount));
        setTextContent("last_failure", Component.translatable(
                "screen.data_energistics.digital_construct_flower.last_failure",
                getFailureSummary()));
        setTextContent("busy_cpus", Component.translatable(
                "screen.data_energistics.digital_construct_flower.busy_cpus",
                this.menu.busyCraftingCpuCount));
        setTextContent("storage_types", Component.translatable(
                "screen.data_energistics.digital_construct_flower.storage_types",
                this.menu.storedTypeCount));
        setTextContent("storage_amount", Component.translatable(
                "screen.data_energistics.digital_construct_flower.storage_amount",
                shortenDecimal(this.menu.storedAmountText)));
        setTextContent("molecular_status", Component.translatable(
                "screen.data_energistics.digital_construct_flower.molecular_status",
                getMolecularStatus()));
        setTextContent("cpu_partitions", Component.translatable(
                "screen.data_energistics.digital_construct_flower.cpu_partitions",
                this.menu.busyCpuPartitionCount,
                this.menu.cpuPartitionCount));
        setTextContent("cpu_storage", Component.translatable(
                "screen.data_energistics.digital_construct_flower.cpu_storage",
                shortenDecimal(Long.toString(this.menu.cpuStorageBytes))));
        setTextContent("cpu_coprocessors", Component.translatable(
                "screen.data_energistics.digital_construct_flower.cpu_coprocessors",
                this.menu.cpuCoProcessors));
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);

        this.cpuIdle.copy()
                .dest(offsetX + CPU_X, offsetY + CPU_Y)
                .blit(guiGraphics);
        if (this.menu.hasCraftingTarget()) {
            this.cpuTaskOverlay.copy()
                    .dest(offsetX + CPU_X, offsetY + CPU_Y)
                    .blit(guiGraphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

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

    private Component getFailureSummary() {
        if (!hasFailure()) {
            return Component.translatable("screen.data_energistics.digital_construct_flower.no_failure");
        }
        return MultiBlockFailureText.summarize(this.menu.lastFailureReason, FAILURE_SUMMARY_LENGTH);
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
