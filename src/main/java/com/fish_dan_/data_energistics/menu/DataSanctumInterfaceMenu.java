package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.SetStockAmountMenu;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot.PlacableItemType;

import java.util.ArrayList;
import java.util.List;

public class DataSanctumInterfaceMenu extends UpgradeableMenu<DataSanctumBlockEntity> {

    public static final String ACTION_OPEN_SET_AMOUNT = "setAmount";
    public static final int CONFIG_SLOT_COUNT = 9;
    public static final int STOCK_SLOT_COUNT = 9;
    public static final int RETURN_SLOT_COUNT = 18;
    public static final SlotSemantic RETURN_ROW_1 = SlotSemantics.register("DATA_SANCTUM_INTERFACE_RETURN_ROW_1", false);
    public static final SlotSemantic RETURN_ROW_2 = SlotSemantics.register("DATA_SANCTUM_INTERFACE_RETURN_ROW_2", false);

    private List<Slot> configSlots;

    public DataSanctumInterfaceMenu(int id, Inventory playerInventory, DataSanctumBlockEntity host) {
        super(ModMenus.DATA_SANCTUM_INTERFACE.get(), id, playerInventory, host);
        registerClientAction(ACTION_OPEN_SET_AMOUNT, Integer.class, this::openSetAmountMenu);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getInterfaceLogic().getStorage().createMenuWrapper();
        var returnInventory = this.getHost().getReturnInventory().createMenuWrapper();
        for (int i = 0; i < Math.min(STOCK_SLOT_COUNT, storage.size()); i++) {
            this.addSlot(new AppEngSlot(storage, i), SlotSemantics.STORAGE);
        }
        for (int i = 0; i < Math.min(CONFIG_SLOT_COUNT, returnInventory.size()); i++) {
            this.addSlot(new AppEngSlot(returnInventory, i), RETURN_ROW_1);
        }
        for (int i = CONFIG_SLOT_COUNT; i < Math.min(RETURN_SLOT_COUNT, returnInventory.size()); i++) {
            this.addSlot(new AppEngSlot(returnInventory, i), RETURN_ROW_2);
        }
    }

    @Override
    protected void setupConfig() {
        this.configSlots = new ArrayList<>(CONFIG_SLOT_COUNT);
        var config = this.getHost().getInterfaceLogic().getConfig().createMenuWrapper();
        for (int i = 0; i < Math.min(CONFIG_SLOT_COUNT, config.size()); i++) {
            this.configSlots.add(this.addSlot(new FakeSlot(config, i), SlotSemantics.CONFIG));
        }
    }

    @Override
    protected void setupUpgrades() {
        var upgrades = this.getHost().getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            this.addSlot(new RestrictedInputSlot(PlacableItemType.UPGRADES, upgrades, i), SlotSemantics.UPGRADE);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
    }

    public List<Slot> getConfigSlots() {
        return this.configSlots != null ? this.configSlots : List.of();
    }

    public void openSetAmountMenu(int configSlot) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_SET_AMOUNT, configSlot);
            return;
        }

        var stack = getHost().getConfig().getStack(configSlot);
        if (stack != null) {
            SetStockAmountMenu.open((ServerPlayer) getPlayer(), getLocator(), configSlot, stack.what(), (int) stack.amount());
        }
    }
}
