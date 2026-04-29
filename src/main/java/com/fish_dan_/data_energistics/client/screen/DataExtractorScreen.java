package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ProgressBar.Direction;
import appeng.menu.SlotSemantics;
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
    private final ProgressBar collectionProgressBar;

    public DataExtractorScreen(DataExtractorMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
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
        this.setTextContent("damage", translate("damage", this.menu.damagePerCycle));
        this.setTextContent("data_flow", translate("data_flow", this.menu.dataFlowPerCycle));

        boolean hasProgress = this.menu.getMaxProgress() > 0;
        this.collectionProgressBar.visible = hasProgress;
        if (hasProgress) {
            int percent = this.menu.getCurrentProgress() * 100 / Math.max(1, this.menu.getMaxProgress());
            this.collectionProgressBar.setFullMsg(Component.literal(percent + "%"));
        }
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (this.menu.getSlotSemantic(slot) == SlotSemantics.MACHINE_INPUT
                && slot.isActive()
                && slot.getItem().isEmpty()) {
            DATA_CARRIER_SLOT_ICON.copy()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        }

        super.renderSlot(guiGraphics, slot);
    }

    private Component translate(String key, Object... args) {
        return Component.translatable("screen.data_energistics.data_extractor." + key, args);
    }
}
