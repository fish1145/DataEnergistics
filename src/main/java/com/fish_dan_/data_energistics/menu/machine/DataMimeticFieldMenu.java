package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorDropRoutingMode;
import com.fish_dan_.data_energistics.blockentity.machine.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.inventories.InternalInventory;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.IOptionalSlotHost;
import appeng.menu.slot.OptionalRestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DataMimeticFieldMenu extends UpgradeableMenu<DataMimeticFieldBlockEntity> implements IOptionalSlotHost, IProgressProvider {

    private static final String ACTION_SET_REDSTONE_CONTROL = "set_redstone_control";
    private static final String ACTION_SET_AUTO_PULL_KEY_INPUT = "set_auto_pull_key_input";
    private static final String ACTION_SET_DROP_ROUTING_MODE = "set_drop_routing_mode";
    private static final String ACTION_SET_OUTPUT_SIDE = "set_output_side";
    public static final SlotSemantic EXTRA_STORAGE = SlotSemantics.register("DATA_MIMETIC_FIELD_EXTRA", false);
    public static final SlotSemantic KEY_INPUT = SlotSemantics.register("DATA_MIMETIC_FIELD_KEY_INPUT", false);

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
    @GuiSync(784)
    public int outputSidesMask = 63;
    @GuiSync(785)
    public long keyInputAmount;
    @GuiSync(786)
    public int workProgress;
    @GuiSync(787)
    public int workMaxProgress;
    @GuiSync(788)
    public boolean autoPullKeyInput;
    private int lastEnabledCapacitySlots = -1;

    public DataMimeticFieldMenu(int id, Inventory playerInventory, DataMimeticFieldBlockEntity host) {
        super(DEMenus.DATA_MIMETIC_FIELD.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_REDSTONE_CONTROL, Boolean.class, this::setRedstoneControlled);
        registerClientAction(ACTION_SET_AUTO_PULL_KEY_INPUT, Boolean.class, this::setAutoPullKeyInput);
        registerClientAction(ACTION_SET_DROP_ROUTING_MODE, Integer.class, this::setDropRoutingMode);
        registerClientAction(ACTION_SET_OUTPUT_SIDE, String.class, this::setOutputSide);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getInternalInventory();
        for (int i = 0; i < DataMimeticFieldBlockEntity.BASE_ACTIVE_SLOTS; i++) {
            this.addSlot(new CarrierSlot(storage, i), SlotSemantics.STORAGE);
        }
        for (int i = DataMimeticFieldBlockEntity.BASE_ACTIVE_SLOTS; i < DataMimeticFieldBlockEntity.SLOT_COUNT; i++) {
            this.addSlot(new OptionalCarrierSlot(storage, this, i, 0, this.getPlayerInventory()), EXTRA_STORAGE);
        }
        this.addSlot(new AppEngSlot(this.getHost().getKeyMenuInventory(), 0), KEY_INPUT);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            returnOverflowCarriersToPlayerIfNeeded();
            this.online = this.getHost().isOnline();
            this.redstoneControlled = this.getHost().isRedstoneControlled();
            this.autoPullKeyInput = this.getHost().isAutoPullKeyInput();
            this.currentPower = (int) Math.round(this.getHost().getAECurrentPower());
            this.maxPower = (int) Math.round(this.getHost().getAEMaxPower());
            this.dropRoutingModeOrdinal = this.getHost().getDropRoutingMode().ordinal();
            this.outputSidesMask = encodeOutputSides(this.getHost().getOutputSides());
            this.keyInputAmount = this.getHost().getKeyInputStack() == null ? 0 : this.getHost().getKeyInputStack().amount();
            this.workProgress = this.getHost().getWorkProgress();
            this.workMaxProgress = this.getHost().getWorkMaxProgress();
        }
        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // This menu only exposes upgrade slots and custom toggles.
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return idx < this.getHost().getInstalledCapacityCardCount();
    }

    public void sendSetRedstoneControlled(boolean enabled) {
        sendClientAction(ACTION_SET_REDSTONE_CONTROL, enabled);
    }

    public void sendSetAutoPullKeyInput(boolean enabled) {
        sendClientAction(ACTION_SET_AUTO_PULL_KEY_INPUT, enabled);
    }

    public void sendSetDropRoutingMode(DataExtractorDropRoutingMode mode) {
        sendClientAction(ACTION_SET_DROP_ROUTING_MODE, mode.ordinal());
    }

    public DataExtractorDropRoutingMode getDropRoutingMode() {
        return DataExtractorDropRoutingMode.fromOrdinal(this.dropRoutingModeOrdinal);
    }

    public List<Direction> getOutputSides() {
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if ((this.outputSidesMask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    public void sendSetOutputSide(Direction side, boolean enabled) {
        sendClientAction(ACTION_SET_OUTPUT_SIDE, side.getName() + ":" + enabled);
    }

    public long getKeyInputCapacity() {
        return DataMimeticFieldBlockEntity.KEY_INPUT_CAPACITY;
    }

    @Override
    public int getCurrentProgress() {
        return this.workProgress;
    }

    @Override
    public int getMaxProgress() {
        return this.workMaxProgress;
    }

    private void setRedstoneControlled(Boolean enabled) {
        if (enabled == null || this.getHost() == null) {
            return;
        }

        this.redstoneControlled = this.getHost().setRedstoneControlled(enabled);
        broadcastChanges();
    }

    private void setAutoPullKeyInput(Boolean enabled) {
        if (enabled == null || this.getHost() == null) {
            return;
        }

        this.autoPullKeyInput = this.getHost().setAutoPullKeyInput(enabled);
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

    private void setOutputSide(String payload) {
        if (payload == null || this.getHost() == null) {
            return;
        }

        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return;
        }

        Direction side = Direction.byName(payload.substring(0, separator));
        if (side == null) {
            return;
        }

        boolean enabled = Boolean.parseBoolean(payload.substring(separator + 1));
        this.getHost().setOutputSideEnabled(side, enabled);
        this.outputSidesMask = encodeOutputSides(this.getHost().getOutputSides());
        broadcastChanges();
    }

    private int encodeOutputSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private void returnOverflowCarriersToPlayerIfNeeded() {
        int enabledCapacitySlots = this.getHost().getInstalledCapacityCardCount();
        if (this.lastEnabledCapacitySlots == -1) {
            this.lastEnabledCapacitySlots = enabledCapacitySlots;
            return;
        }
        if (enabledCapacitySlots >= this.lastEnabledCapacitySlots) {
            this.lastEnabledCapacitySlots = enabledCapacitySlots;
            return;
        }

        for (ItemStack overflow : this.getHost().extractOverflowCarriers()) {
            this.getPlayerInventory().placeItemBackInInventory(overflow);
        }
        this.lastEnabledCapacitySlots = enabledCapacitySlots;
    }

    private static final class CarrierSlot extends RestrictedInputSlot {

        private CarrierSlot(InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return (stack.is(DEItems.MOB_DATA_CARRIER.get()) || stack.is(DEItems.ORE_DATA_CARRIER.get()) || stack.is(DEItems.CROP_DATA_CARRIER.get())) && super.mayPlace(stack);
        }
    }

    private static final class OptionalCarrierSlot extends OptionalRestrictedInputSlot {

        private OptionalCarrierSlot(InternalInventory inv, IOptionalSlotHost host, int invSlot, int group, Inventory playerInventory) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, host, invSlot, group, playerInventory);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return (stack.is(DEItems.MOB_DATA_CARRIER.get()) || stack.is(DEItems.ORE_DATA_CARRIER.get()) || stack.is(DEItems.CROP_DATA_CARRIER.get())) && super.mayPlace(stack);
        }
    }
}
