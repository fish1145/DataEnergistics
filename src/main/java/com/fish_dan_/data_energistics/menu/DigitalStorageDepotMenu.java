package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;

public class DigitalStorageDepotMenu extends UpgradeableMenu<DigitalStorageDepotBlockEntity> {

    public static final SlotSemantic STORAGE_ROW_2 = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_ROW_2", false);
    public static final SlotSemantic STORAGE_ROW_3 = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_ROW_3", false);
    public static final SlotSemantic FLUID = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_FLUID", false);
    public static final SlotSemantic KEY = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_KEY", false);
    public static final SlotSemantic FLUID_2 = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_FLUID_2", false);
    public static final SlotSemantic FLUID_3 = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_FLUID_3", false);
    public static final SlotSemantic KEY_2 = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_KEY_2", false);
    public static final SlotSemantic KEY_3 = SlotSemantics.register("DIGITAL_STORAGE_DEPOT_KEY_3", false);

    @GuiSync(900)
    public String fluidId0 = "";
    @GuiSync(901)
    public int fluidAmount0;
    @GuiSync(902)
    public long keyAmount0;
    @GuiSync(909)
    public GenericStack fluidDisplay0;
    @GuiSync(903)
    public String fluidId1 = "";
    @GuiSync(904)
    public int fluidAmount1;
    @GuiSync(905)
    public long keyAmount1;
    @GuiSync(910)
    public GenericStack fluidDisplay1;
    @GuiSync(906)
    public String fluidId2 = "";
    @GuiSync(907)
    public int fluidAmount2;
    @GuiSync(908)
    public long keyAmount2;
    @GuiSync(911)
    public GenericStack fluidDisplay2;

    public DigitalStorageDepotMenu(int id, Inventory playerInventory, DigitalStorageDepotBlockEntity host) {
        super(ModMenus.DIGITAL_STORAGE_DEPOT.get(), id, playerInventory, host);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide() && this.getHost() != null) {
            for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
                syncFluid(this.getHost().getStoredFluid(i), i);
                GenericStack keyStack = this.getHost().getKeyStack(i);
                syncKeyAmount(keyStack == null ? 0L : keyStack.amount(), i);
            }
        }
        super.broadcastChanges();
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getStorageInventory();
        this.addSlot(new AppEngSlot(this.getHost().getFluidMenuInventory(0), 0), FLUID);
        this.addSlot(new AppEngSlot(this.getHost().getKeyMenuInventory(0), 0), KEY);
        this.addSlot(new AppEngSlot(this.getHost().getFluidMenuInventory(1), 0), FLUID_2);
        this.addSlot(new AppEngSlot(this.getHost().getKeyMenuInventory(1), 0), KEY_2);
        this.addSlot(new AppEngSlot(this.getHost().getFluidMenuInventory(2), 0), FLUID_3);
        this.addSlot(new AppEngSlot(this.getHost().getKeyMenuInventory(2), 0), KEY_3);
        for (int i = 0; i < DigitalStorageDepotBlockEntity.STORAGE_SLOTS; i++) {
            this.addSlot(new AppEngSlot(storage, i), getRowSemantic(i));
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // This menu only exposes storage and upgrade slots.
    }

    private SlotSemantic getRowSemantic(int slot) {
        if (slot < DigitalStorageDepotBlockEntity.STORAGE_COLUMNS) {
            return SlotSemantics.STORAGE;
        }
        if (slot < DigitalStorageDepotBlockEntity.STORAGE_COLUMNS * 2) {
            return STORAGE_ROW_2;
        }
        return STORAGE_ROW_3;
    }

    public int getFluidCapacity() {
        return this.getHost().getFluidCapacity();
    }

    public long getKeyCapacity() {
        return DigitalStorageDepotBlockEntity.KEY_CAPACITY;
    }

    public String getFluidId(int slot) {
        return switch (slot) {
            case 0 -> this.fluidId0;
            case 1 -> this.fluidId1;
            case 2 -> this.fluidId2;
            default -> "";
        };
    }

    public int getFluidAmount(int slot) {
        return switch (slot) {
            case 0 -> this.fluidAmount0;
            case 1 -> this.fluidAmount1;
            case 2 -> this.fluidAmount2;
            default -> 0;
        };
    }

    public long getKeyAmount(int slot) {
        return switch (slot) {
            case 0 -> this.keyAmount0;
            case 1 -> this.keyAmount1;
            case 2 -> this.keyAmount2;
            default -> 0L;
        };
    }

    public GenericStack getFluidDisplay(int slot) {
        return switch (slot) {
            case 0 -> this.fluidDisplay0;
            case 1 -> this.fluidDisplay1;
            case 2 -> this.fluidDisplay2;
            default -> null;
        };
    }

    private void syncFluid(FluidStack stack, int slot) {
        String id = stack.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
        int amount = stack.getAmount();
        GenericStack display = stack.isEmpty() ? null : new GenericStack(AEFluidKey.of(stack), amount);
        switch (slot) {
            case 0 -> {
                this.fluidId0 = id;
                this.fluidAmount0 = amount;
                this.fluidDisplay0 = display;
            }
            case 1 -> {
                this.fluidId1 = id;
                this.fluidAmount1 = amount;
                this.fluidDisplay1 = display;
            }
            case 2 -> {
                this.fluidId2 = id;
                this.fluidAmount2 = amount;
                this.fluidDisplay2 = display;
            }
        }
    }

    private void syncKeyAmount(long amount, int slot) {
        switch (slot) {
            case 0 -> this.keyAmount0 = amount;
            case 1 -> this.keyAmount1 = amount;
            case 2 -> this.keyAmount2 = amount;
        }
    }
}
