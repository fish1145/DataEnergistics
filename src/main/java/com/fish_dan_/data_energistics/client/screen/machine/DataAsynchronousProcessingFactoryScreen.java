package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.menu.machine.DataAsynchronousProcessingFactoryMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;

public final class DataAsynchronousProcessingFactoryScreen
                                                           extends DataRipperReassemblerScreen<DataAsynchronousProcessingFactoryMenu> {

    private final ProgressBar middleProgressBar;
    private final ProgressBar rightProgressBar;

    public DataAsynchronousProcessingFactoryScreen(DataAsynchronousProcessingFactoryMenu menu,
                                                   Inventory playerInventory,
                                                   Component title,
                                                   ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.middleProgressBar = new ProgressBar(this.menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        this.rightProgressBar = new ProgressBar(this.menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        this.widgets.add("progressBarMiddle", this.middleProgressBar);
        this.widgets.add("progressBarRight", this.rightProgressBar);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        boolean visible = this.menu.getMaxProgress() > 0;
        this.middleProgressBar.visible = visible;
        this.rightProgressBar.visible = visible;
        if (!visible) {
            return;
        }

        int percent = this.menu.getCurrentProgress() * 100 / Math.max(1, this.menu.getMaxProgress());
        Component tooltip = Component.literal(percent + "%");
        this.middleProgressBar.setFullMsg(tooltip);
        this.rightProgressBar.setFullMsg(tooltip);
    }
}
