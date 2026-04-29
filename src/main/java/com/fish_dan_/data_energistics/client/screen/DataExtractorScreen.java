package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import com.fish_dan_.data_energistics.menu.DataExtractorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DataExtractorScreen extends UpgradeableScreen<DataExtractorMenu> {
    public DataExtractorScreen(DataExtractorMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.setTextContent("status", Component.translatable(
                this.menu.online
                        ? "screen.data_energistics.data_extractor.status.online"
                        : "screen.data_energistics.data_extractor.status.offline"));
        this.setTextContent("damage", translate("damage", this.menu.damagePerTick));
        this.setTextContent("data_flow", translate("data_flow", this.menu.dataFlowPerTick));
        this.setTextContent("speed_cards", translate("speed_cards", this.menu.speedCardCount));
        this.setTextContent("capacity_cards", translate("capacity_cards", this.menu.capacityCardCount));
    }

    private Component translate(String key, Object... args) {
        return Component.translatable("screen.data_energistics.data_extractor." + key, args);
    }
}
