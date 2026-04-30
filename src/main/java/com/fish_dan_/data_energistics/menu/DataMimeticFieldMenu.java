package com.fish_dan_.data_energistics.menu;

import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.IOptionalSlotHost;
import appeng.menu.slot.OptionalRestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorDropRoutingMode;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class DataMimeticFieldMenu extends UpgradeableMenu<DataMimeticFieldBlockEntity> implements IOptionalSlotHost {
    private static final String ACTION_SET_REDSTONE_CONTROL = "set_redstone_control";
    private static final String ACTION_SET_DROP_ROUTING_MODE = "set_drop_routing_mode";

    @GuiSync(779)
    public boolean online;
    @GuiSync(780)
    public boolean redstoneControlled;
    @GuiSync(781)
    public int currentPower;
    @GuiSync(782)
    public int maxPower;
    @GuiSync(783)
    public int dropRoutingModeOrdinal;

    public DataMimeticFieldMenu(int id, Inventory playerInventory, DataMimeticFieldBlockEntity host) {
        super(ModMenus.DATA_MIMETIC_FIELD.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_REDSTONE_CONTROL, Boolean.class, this::setRedstoneControlled);
        registerClientAction(ACTION_SET_DROP_ROUTING_MODE, Integer.class, this::setDropRoutingMode);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getInternalInventory();
        for (int i = 0; i < 9; i++) {
            this.addSlot(new CarrierSlot(storage, i), SlotSemantics.STORAGE);
        }
        for (int row = 1; row < DataMimeticFieldBlockEntity.MAX_ACTIVE_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                this.addSlot(new OptionalCarrierSlot(storage, this, slot, row - 1, this.getPlayerInventory()), SlotSemantics.STORAGE);
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            this.online = this.getHost().isOnline();
            this.redstoneControlled = this.getHost().isRedstoneControlled();
            this.currentPower = (int) Math.round(this.getHost().getAECurrentPower());
            this.maxPower = (int) Math.round(this.getHost().getAEMaxPower());
            this.dropRoutingModeOrdinal = this.getHost().getDropRoutingMode().ordinal();
        }
        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // This menu only exposes upgrade slots and custom toggles.
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return idx < Math.max(0, this.getHost().getActiveRows() - DataMimeticFieldBlockEntity.BASE_ACTIVE_ROWS);
    }

    public void sendSetRedstoneControlled(boolean enabled) {
        sendClientAction(ACTION_SET_REDSTONE_CONTROL, enabled);
    }

    public void sendSetDropRoutingMode(DataExtractorDropRoutingMode mode) {
        sendClientAction(ACTION_SET_DROP_ROUTING_MODE, mode.ordinal());
    }

    public DataExtractorDropRoutingMode getDropRoutingMode() {
        return DataExtractorDropRoutingMode.fromOrdinal(this.dropRoutingModeOrdinal);
    }

    private void setRedstoneControlled(Boolean enabled) {
        if (enabled == null || this.getHost() == null) {
            return;
        }

        this.redstoneControlled = this.getHost().setRedstoneControlled(enabled);
        broadcastChanges();
    }

    private void setDropRoutingMode(Integer ordinal) {
        if (ordinal == null || this.getHost() == null) {
            return;
        }

        this.dropRoutingModeOrdinal = this.getHost()
                .setDropRoutingMode(DataExtractorDropRoutingMode.fromOrdinal(ordinal))
                .ordinal();
        broadcastChanges();
    }

    private static final class CarrierSlot extends RestrictedInputSlot {
        private CarrierSlot(appeng.api.inventories.InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return (stack.is(ModItems.BIOLOGY_DATA_CARRIER.get()) || stack.is(ModItems.ORE_DATA_CARRIER.get()))
                    && super.mayPlace(stack);
        }
    }

    private static final class OptionalCarrierSlot extends OptionalRestrictedInputSlot {
        private OptionalCarrierSlot(appeng.api.inventories.InternalInventory inv, IOptionalSlotHost host, int invSlot, int group, Inventory playerInventory) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, host, invSlot, group, playerInventory);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return (stack.is(ModItems.BIOLOGY_DATA_CARRIER.get()) || stack.is(ModItems.ORE_DATA_CARRIER.get()))
                    && super.mayPlace(stack);
        }
    }
}
