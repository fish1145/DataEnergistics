package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity.MachineMode;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.InternalInventory;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.OutputSlot;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class DataIntegratedChargerMenu extends UpgradeableMenu<DataIntegratedChargerBlockEntity> implements IProgressProvider {

    private static final String ACTION_SET_OUTPUT_SIDE = "set_output_side";
    private static final String ACTION_SET_MACHINE_MODE = "set_machine_mode";
    public static final SlotSemantic MACHINE_INPUT_LEFT = SlotSemantics.register("DATA_INTEGRATED_CHARGER_INPUT_LEFT", false);
    public static final SlotSemantic MACHINE_INPUT_MIDDLE = SlotSemantics.register("DATA_INTEGRATED_CHARGER_INPUT_MIDDLE", false);
    public static final SlotSemantic MACHINE_INPUT_RIGHT = SlotSemantics.register("DATA_INTEGRATED_CHARGER_INPUT_RIGHT", false);
    public static final SlotSemantic FLUID_TANK_1 = SlotSemantics.register("DATA_INTEGRATED_CHARGER_FLUID_1", false);
    public static final SlotSemantic FLUID_TANK_2 = SlotSemantics.register("DATA_INTEGRATED_CHARGER_FLUID_2", false);
    public static final SlotSemantic FLUID_TANK_3 = SlotSemantics.register("DATA_INTEGRATED_CHARGER_FLUID_3", false);

    @GuiSync(880)
    public YesNo autoExport = YesNo.NO;
    @GuiSync(881)
    public String fluidId0 = "";
    @GuiSync(882)
    public int fluidAmount0;
    @GuiSync(883)
    public String fluidId1 = "";
    @GuiSync(884)
    public int fluidAmount1;
    @GuiSync(885)
    public String fluidId2 = "";
    @GuiSync(886)
    public int fluidAmount2;
    @GuiSync(887)
    public int machineMode = MachineMode.POWDER.ordinal();
    @GuiSync(888)
    public int itemOutputSidesMask = 63;
    @GuiSync(889)
    public int progress;
    @GuiSync(890)
    public int maxProgress = DataIntegratedChargerBlockEntity.MAX_PROGRESS;

    public DataIntegratedChargerMenu(int id, Inventory playerInventory, DataIntegratedChargerBlockEntity host) {
        super(DEMenus.DATA_INTEGRATED_CHARGER.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_OUTPUT_SIDE, String.class, this::setOutputSide);
        registerClientAction(ACTION_SET_MACHINE_MODE, Integer.class, this::setMachineMode);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            var host = this.getHost();
            this.autoExport = host.isAutoExportEnabled() ? YesNo.YES : YesNo.NO;
            for (int tank = 0; tank < DataIntegratedChargerBlockEntity.FLUID_TANK_COUNT; tank++) {
                syncFluid(host.getFluidTank(tank).getFluid(), tank);
            }
            this.machineMode = host.getMachineMode().ordinal();
            this.itemOutputSidesMask = encodeOutputSides(host.getOutputSides(DigitalStorageDepotOutputType.ITEMS));
            this.progress = host.getProgress();
            this.maxProgress = host.getMaxProgress();
        }
        super.broadcastChanges();
    }

    @Override
    protected void setupInventorySlots() {
        InternalInventory storage = this.getHost().getStorageInventory();
        for (int slot = 0; slot < 3; slot++) {
            this.addSlot(new LargeStackSlot(storage, slot), MACHINE_INPUT_LEFT);
        }
        for (int slot = 3; slot < 6; slot++) {
            this.addSlot(new LargeStackSlot(storage, slot), MACHINE_INPUT_MIDDLE);
        }
        for (int slot = 6; slot < DataIntegratedChargerBlockEntity.ITEM_INPUT_SLOT_COUNT; slot++) {
            this.addSlot(new LargeStackSlot(storage, slot), MACHINE_INPUT_RIGHT);
        }
        for (int tank = 0; tank < DataIntegratedChargerBlockEntity.FLUID_TANK_COUNT; tank++) {
            this.addSlot(new AppEngSlot(this.getHost().getFluidMenuInventory(tank), 0), getFluidTankSemantic(tank));
        }
        for (int index = 0; index < DataIntegratedChargerBlockEntity.ITEM_OUTPUT_SLOT_COUNT; index++) {
            this.addSlot(new LargeOutputSlot(storage, DataIntegratedChargerBlockEntity.ITEM_OUTPUT_START_SLOT + index),
                    SlotSemantics.MACHINE_OUTPUT);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager configManager) {
        this.autoExport = configManager.getSetting(Settings.AUTO_EXPORT);
    }

    @Override
    protected int getQuickMovePriority(Slot slot) {
        if (this.getSlotSemantic(slot) == SlotSemantics.UPGRADE) {
            return 1;
        }
        return super.getQuickMovePriority(slot);
    }

    public YesNo getAutoExport() {
        return this.autoExport;
    }

    public int getFluidCapacity() {
        return this.getHost().getFluidCapacity();
    }

    public String getFluidId(int tank) {
        return switch (tank) {
            case 0 -> this.fluidId0;
            case 1 -> this.fluidId1;
            case 2 -> this.fluidId2;
            default -> "";
        };
    }

    public int getFluidAmount(int tank) {
        return switch (tank) {
            case 0 -> this.fluidAmount0;
            case 1 -> this.fluidAmount1;
            case 2 -> this.fluidAmount2;
            default -> 0;
        };
    }

    public MachineMode getMachineMode() {
        return MachineMode.fromOrdinal(this.machineMode);
    }

    public void sendSetMachineMode(MachineMode mode) {
        if (mode != null) {
            sendClientAction(ACTION_SET_MACHINE_MODE, mode.ordinal());
        }
    }

    @Override
    public int getCurrentProgress() {
        return this.progress;
    }

    @Override
    public int getMaxProgress() {
        return this.maxProgress;
    }

    public List<Direction> getOutputSides(DigitalStorageDepotOutputType outputType) {
        int sidesMask = outputType == DigitalStorageDepotOutputType.ITEMS ? this.itemOutputSidesMask : 0;
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if ((sidesMask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    public void sendSetOutputSide(DigitalStorageDepotOutputType outputType, Direction side, boolean enabled) {
        if (outputType != DigitalStorageDepotOutputType.ITEMS) {
            return;
        }
        sendClientAction(ACTION_SET_OUTPUT_SIDE,
                outputType.getSerializedName() + ":" + side.getName() + ":" + enabled);
    }

    private static SlotSemantic getFluidTankSemantic(int tank) {
        return switch (tank) {
            case 0 -> FLUID_TANK_1;
            case 1 -> FLUID_TANK_2;
            case 2 -> FLUID_TANK_3;
            default -> throw new IndexOutOfBoundsException("Invalid Data Integrated Charger fluid tank: " + tank);
        };
    }

    private void syncFluid(FluidStack fluid, int tank) {
        String fluidId = fluid.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        int fluidAmount = fluid.getAmount();
        switch (tank) {
            case 0 -> {
                this.fluidId0 = fluidId;
                this.fluidAmount0 = fluidAmount;
            }
            case 1 -> {
                this.fluidId1 = fluidId;
                this.fluidAmount1 = fluidAmount;
            }
            case 2 -> {
                this.fluidId2 = fluidId;
                this.fluidAmount2 = fluidAmount;
            }
            default -> throw new IndexOutOfBoundsException("Invalid Data Integrated Charger fluid tank: " + tank);
        }
    }

    private void setMachineMode(Integer ordinal) {
        if (ordinal == null) {
            return;
        }
        MachineMode mode = MachineMode.fromOrdinal(ordinal);
        this.getHost().setMachineMode(mode);
        this.machineMode = mode.ordinal();
        broadcastChanges();
    }

    private void setOutputSide(String payload) {
        if (payload == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Integrated Charger output-side client action without a payload at {}",
                    this.getHost().getBlockPos());
            return;
        }

        int firstSeparator = payload.indexOf(':');
        int secondSeparator = firstSeparator < 0 ? -1 : payload.indexOf(':', firstSeparator + 1);
        if (firstSeparator <= 0 || secondSeparator <= firstSeparator + 1 || secondSeparator >= payload.length() - 1 ||
                secondSeparator != payload.lastIndexOf(':')) {
            Data_Energistics.LOGGER.warn(
                    "Rejected malformed Data Integrated Charger output-side client action at {}",
                    this.getHost().getBlockPos());
            return;
        }
        DigitalStorageDepotOutputType outputType = DigitalStorageDepotOutputType.fromSerializedName(
                payload.substring(0, firstSeparator));
        if (outputType != DigitalStorageDepotOutputType.ITEMS) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Integrated Charger output-side client action with an unsupported content type at {}",
                    this.getHost().getBlockPos());
            return;
        }
        Direction side = Direction.byName(payload.substring(firstSeparator + 1, secondSeparator));
        if (side == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Integrated Charger output-side client action with an unknown direction at {}",
                    this.getHost().getBlockPos());
            return;
        }
        boolean enabled;
        String enabledValue = payload.substring(secondSeparator + 1);
        if ("true".equals(enabledValue)) {
            enabled = true;
        } else if ("false".equals(enabledValue)) {
            enabled = false;
        } else {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Integrated Charger output-side client action with a non-boolean value at {}",
                    this.getHost().getBlockPos());
            return;
        }

        this.getHost().setOutputSideEnabled(outputType, side, enabled);
        this.itemOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(outputType));
        broadcastChanges();
    }

    private static int encodeOutputSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private static final class LargeStackSlot extends AppEngSlot {

        private LargeStackSlot(InternalInventory inventory, int slot) {
            super(inventory, slot);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.getMaxStackSize();
        }
    }

    private static final class LargeOutputSlot extends OutputSlot {

        private LargeOutputSlot(InternalInventory inventory, int slot) {
            super(inventory, slot, null);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.getMaxStackSize();
        }
    }
}
