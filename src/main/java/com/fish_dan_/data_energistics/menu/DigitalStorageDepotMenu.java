package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.DataExtractorAutoExportMode;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot.PlacableItemType;

import java.util.ArrayList;
import java.util.List;

public class DigitalStorageDepotMenu extends UpgradeableMenu<DigitalStorageDepotBlockEntity> {

    private static final String ACTION_SET_AUTO_EXPORT = "set_auto_export";
    private static final String ACTION_SET_OUTPUT_SIDE = "set_output_side";
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
    @GuiSync(912)
    public int autoExportModeOrdinal;
    @GuiSync(913)
    public int itemOutputSidesMask = 63;
    @GuiSync(914)
    public int fluidOutputSidesMask = 63;
    @GuiSync(915)
    public int keyOutputSidesMask = 63;

    public DigitalStorageDepotMenu(int id, Inventory playerInventory, DigitalStorageDepotBlockEntity host) {
        super(ModMenus.DIGITAL_STORAGE_DEPOT.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_AUTO_EXPORT, Integer.class, this::setAutoExportMode);
        registerClientAction(ACTION_SET_OUTPUT_SIDE, String.class, this::setOutputSide);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide() && this.getHost() != null) {
            for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
                syncFluid(this.getHost().getStoredFluid(i), i);
                GenericStack keyStack = this.getHost().getKeyStack(i);
                syncKeyAmount(keyStack == null ? 0L : keyStack.amount(), i);
            }
            this.autoExportModeOrdinal = this.getHost().getAutoExportMode().ordinal();
            this.itemOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(DigitalStorageDepotOutputType.ITEMS));
            this.fluidOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(DigitalStorageDepotOutputType.FLUIDS));
            this.keyOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(DigitalStorageDepotOutputType.KEYS));
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
            this.addSlot(new DepotStorageSlot(storage, i), getRowSemantic(i));
        }
    }

    @Override
    protected void setupUpgrades() {
        var upgrades = this.getHost().getUpgrades();
        var protectedUpgrades = new CapacityProtectedUpgradeInventory(upgrades);
        for (int i = 0; i < upgrades.size(); i++) {
            this.addSlot(new CapacityProtectedUpgradeSlot(protectedUpgrades, i), SlotSemantics.UPGRADE);
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
        return this.getHost().getKeyCapacity();
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

    public void sendSetAutoExportMode(DataExtractorAutoExportMode mode) {
        sendClientAction(ACTION_SET_AUTO_EXPORT, mode.ordinal());
    }

    public DataExtractorAutoExportMode getAutoExportMode() {
        return DataExtractorAutoExportMode.fromOrdinal(this.autoExportModeOrdinal);
    }

    public List<Direction> getOutputSides(DigitalStorageDepotOutputType outputType) {
        int mask = switch (outputType) {
            case ITEMS -> this.itemOutputSidesMask;
            case FLUIDS -> this.fluidOutputSidesMask;
            case KEYS -> this.keyOutputSidesMask;
        };

        ArrayList<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if ((mask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    public void sendSetOutputSide(DigitalStorageDepotOutputType outputType, Direction side, boolean enabled) {
        sendClientAction(ACTION_SET_OUTPUT_SIDE, outputType.getSerializedName() + ":" + side.getName() + ":" + enabled);
    }

    private void setAutoExportMode(Integer ordinal) {
        if (ordinal == null || this.getHost() == null) {
            return;
        }

        this.autoExportModeOrdinal = this.getHost()
                .setAutoExportMode(DataExtractorAutoExportMode.fromOrdinal(ordinal))
                .ordinal();
        broadcastChanges();
    }

    private void setOutputSide(String payload) {
        if (payload == null || this.getHost() == null) {
            return;
        }

        int firstSeparator = payload.indexOf(':');
        int secondSeparator = firstSeparator < 0 ? -1 : payload.indexOf(':', firstSeparator + 1);
        if (firstSeparator <= 0 || secondSeparator <= firstSeparator + 1 || secondSeparator >= payload.length() - 1) {
            return;
        }

        DigitalStorageDepotOutputType outputType = DigitalStorageDepotOutputType.fromSerializedName(payload.substring(0, firstSeparator));
        if (outputType == null) {
            return;
        }

        Direction side = Direction.byName(payload.substring(firstSeparator + 1, secondSeparator));
        if (side == null) {
            return;
        }

        boolean enabled = Boolean.parseBoolean(payload.substring(secondSeparator + 1));
        this.getHost().setOutputSideEnabled(outputType, side, enabled);
        switch (outputType) {
            case ITEMS -> this.itemOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(DigitalStorageDepotOutputType.ITEMS));
            case FLUIDS -> this.fluidOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(DigitalStorageDepotOutputType.FLUIDS));
            case KEYS -> this.keyOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(DigitalStorageDepotOutputType.KEYS));
        }
        broadcastChanges();
    }

    private int encodeOutputSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private boolean canExtractUpgradeSlot(int slot) {
        return this.getHost().canRemoveCapacityCard(slot);
    }

    private static final class DepotStorageSlot extends AppEngSlot {

        private DepotStorageSlot(InternalInventory inv, int invSlot) {
            super(inv, invSlot);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.getMaxStackSize();
        }
    }

    private final class CapacityProtectedUpgradeSlot extends RestrictedInputSlot {

        private final int invSlot;

        private CapacityProtectedUpgradeSlot(InternalInventory inv, int invSlot) {
            super(PlacableItemType.UPGRADES, inv, invSlot);
            this.invSlot = invSlot;
        }

        @Override
        public boolean mayPickup(Player player) {
            return canExtractUpgradeSlot(this.invSlot) && super.mayPickup(player);
        }
    }

    private final class CapacityProtectedUpgradeInventory implements InternalInventory {

        private final IUpgradeInventory delegate;

        private CapacityProtectedUpgradeInventory(IUpgradeInventory delegate) {
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return this.delegate.size();
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.delegate.getSlotLimit(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return this.delegate.getStackInSlot(slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            this.delegate.setItemDirect(slotIndex, stack);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.delegate.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return this.delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!canExtractUpgradeSlot(slot)) {
                return ItemStack.EMPTY;
            }
            return this.delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public void sendChangeNotification(int slot) {
            this.delegate.sendChangeNotification(slot);
        }
    }
}
