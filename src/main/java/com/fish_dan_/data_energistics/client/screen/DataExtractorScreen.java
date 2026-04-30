package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ProgressBar.Direction;
import appeng.menu.SlotSemantics;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.DataExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class DataExtractorScreen extends UpgradeableScreen<DataExtractorMenu> {
    private static final Blitter DATA_CARRIER_SLOT_ICON = Blitter.texture(
            Icon.TEXTURE,
            Icon.TEXTURE_WIDTH,
            Icon.TEXTURE_HEIGHT
    ).src(240, 240, 16, 16);
    private static final Blitter SWORD_SLOT_ICON = Blitter.texture(
            Icon.TEXTURE,
            Icon.TEXTURE_WIDTH,
            Icon.TEXTURE_HEIGHT
    ).src(224, 240, 16, 16);
    private static final Blitter ORE_SLOT_ICON = Blitter.texture(
            Icon.TEXTURE,
            Icon.TEXTURE_WIDTH,
            Icon.TEXTURE_HEIGHT
    ).src(240, 224, 16, 16);
    private final DataExtractorToggleButton redstoneControlButton;
    private final DataExtractorToggleButton rangeVisibleButton;
    private final ProgressBar collectionProgressBar;

    public DataExtractorScreen(DataExtractorMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.redstoneControlButton = new DataExtractorToggleButton(
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.data_extractor.redstone_control",
                "button.data_energistics.data_extractor.redstone_control.enabled",
                "button.data_energistics.data_extractor.redstone_control.disabled",
                this.menu::sendSetRedstoneControlled
        );
        this.addToLeftToolbar(this.redstoneControlButton);

        this.rangeVisibleButton = new DataExtractorToggleButton(
                Icon.PATTERN_TERMINAL_ALL,
                Icon.PATTERN_TERMINAL_VISIBLE,
                "button.data_energistics.data_extractor.range_visible",
                "button.data_energistics.data_extractor.range_visible.enabled",
                "button.data_energistics.data_extractor.range_visible.disabled",
                this.menu::sendSetRangeVisible
        );
        this.addToLeftToolbar(this.rangeVisibleButton);

        this.collectionProgressBar = new ProgressBar(this.menu, style.getImage("progressBar"), Direction.VERTICAL);
        widgets.add("progressBar", this.collectionProgressBar);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.setTextContent("status", Component.translatable(
                this.menu.online
                        ? "screen.data_energistics.data_extractor.status.online"
                        : "screen.data_energistics.data_extractor.status.offline"));
        this.setTextContent("damage", translate("damage", this.menu.damagePerCycle, this.menu.workIntervalSeconds));
        this.setTextContent("data_flow", translate("data_flow", this.menu.dataFlowPerCycle, this.menu.workIntervalSeconds));
        this.setTextContent("targets", translate("targets", this.menu.targetCount, this.menu.targetLimit));
        this.redstoneControlButton.setState(this.menu.redstoneControlled);
        this.rangeVisibleButton.setState(this.menu.rangeVisible);

        boolean hasProgress = this.menu.getMaxProgress() > 0;
        this.collectionProgressBar.visible = hasProgress;
        if (hasProgress) {
            int percent = this.menu.getCurrentProgress() * 100 / Math.max(1, this.menu.getMaxProgress());
            this.collectionProgressBar.setFullMsg(Component.literal(percent + "%"));
        }
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.isActive() && slot.getItem().isEmpty()) {
            if (this.menu.getSlotSemantic(slot) == SlotSemantics.MACHINE_INPUT) {
                DATA_CARRIER_SLOT_ICON.copy()
                        .dest(slot.x, slot.y)
                        .blit(guiGraphics);
            } else if (this.menu.getSlotSemantic(slot) == DataExtractorMenu.SWORD_INPUT) {
                SWORD_SLOT_ICON.copy()
                        .dest(slot.x, slot.y)
                        .blit(guiGraphics);
            } else if (this.menu.getSlotSemantic(slot) == DataExtractorMenu.ORE_INPUT) {
                ORE_SLOT_ICON.copy()
                        .dest(slot.x, slot.y)
                        .blit(guiGraphics);
            }
        }

        super.renderSlot(guiGraphics, slot);
    }

    private Component translate(String key, Object... args) {
        return Component.translatable("screen.data_energistics.data_extractor." + key, args);
    }
}
