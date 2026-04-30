package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import com.fish_dan_.data_energistics.client.widget.DataExtractorDropRoutingButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.DataMimeticFieldMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DataMimeticFieldScreen extends UpgradeableScreen<DataMimeticFieldMenu> {
    private final DataExtractorToggleButton redstoneControlButton;
    private final DataExtractorDropRoutingButton dropRoutingButton;

    public DataMimeticFieldScreen(DataMimeticFieldMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.redstoneControlButton = new DataExtractorToggleButton(
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.data_mimetic_field.redstone_control",
                "button.data_energistics.data_mimetic_field.redstone_control.enabled",
                "button.data_energistics.data_mimetic_field.redstone_control.disabled",
                this.menu::sendSetRedstoneControlled
        );
        this.addToLeftToolbar(this.redstoneControlButton);

        this.dropRoutingButton = new DataExtractorDropRoutingButton(
                "button.data_energistics.data_mimetic_field.output_routing",
                "button.data_energistics.data_mimetic_field.output_routing.",
                this.menu::sendSetDropRoutingMode
        );
        this.addToLeftToolbar(this.dropRoutingButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.setTextContent("status", Component.translatable(
                this.menu.online
                        ? "screen.data_energistics.data_mimetic_field.status.online"
                        : "screen.data_energistics.data_mimetic_field.status.offline"
        ));
        this.redstoneControlButton.setState(this.menu.redstoneControlled);
        this.dropRoutingButton.setMode(this.menu.getDropRoutingMode());
    }
}
