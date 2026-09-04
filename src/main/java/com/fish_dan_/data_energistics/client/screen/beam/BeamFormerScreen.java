package com.fish_dan_.data_energistics.client.screen.beam;

import com.fish_dan_.data_energistics.menu.beam.BeamFormerMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;

public final class BeamFormerScreen extends UpgradeableScreen<BeamFormerMenu> {

    public BeamFormerScreen(BeamFormerMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        String status = this.menu.faulted ? "faulted" : this.menu.online ? "online" : "offline";
        setTextContent("status", Component.translatable("screen.data_energistics.beam.status." + status));
        setTextContent("range", Component.translatable("screen.data_energistics.beam.range", this.menu.range));
        setTextContent("visibility", Component.translatable("screen.data_energistics.beam.visibility." +
                (this.menu.hidden ? "hidden" : "visible")));
    }
}
