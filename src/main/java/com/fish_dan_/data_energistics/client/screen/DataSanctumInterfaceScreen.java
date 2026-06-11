package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.menu.DataSanctumInterfaceMenu;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import appeng.core.localization.ButtonToolTips;

import java.util.ArrayList;
import java.util.List;

public class DataSanctumInterfaceScreen extends UpgradeableScreen<DataSanctumInterfaceMenu> {

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final List<Button> amountButtons = new ArrayList<>();

    public DataSanctumInterfaceScreen(DataSanctumInterfaceMenu menu, Inventory playerInventory, Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.fuzzyMode = new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        addToLeftToolbar(this.fuzzyMode);

        for (int i = 0; i < menu.getConfigSlots().size(); i++) {
            var button = new SetAmountButton(btn -> {
                int index = amountButtons.indexOf(btn);
                if (index >= 0 && index < this.menu.getConfigSlots().size()) {
                    this.menu.openSetAmountMenu(this.menu.getConfigSlots().get(index).getSlotIndex());
                }
            });
            button.setDisableBackground(true);
            button.setMessage(ButtonToolTips.InterfaceSetStockAmount.text());
            widgets.add("amtButton" + (i + 1), button);
            amountButtons.add(button);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.fuzzyMode.set(menu.getFuzzyMode());
        this.fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));

        var configSlots = this.menu.getConfigSlots();
        for (int i = 0; i < this.amountButtons.size(); i++) {
            this.amountButtons.get(i).visible = i < configSlots.size() && !configSlots.get(i).getItem().isEmpty();
        }
    }

    private static class SetAmountButton extends IconButton {

        private SetAmountButton(OnPress onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return isHoveredOrFocused() ? Icon.COG : Icon.COG_DISABLED;
        }
    }
}
