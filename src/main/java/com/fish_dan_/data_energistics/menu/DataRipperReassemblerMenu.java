package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.OutputSlot;

import java.util.ArrayList;
import java.util.List;

public class DataRipperReassemblerMenu extends UpgradeableMenu<DataRipperReassemblerBlockEntity> implements IProgressProvider {

    private static final String ACTION_SET_AUTO_EXPORT = "set_auto_export";
    private static final String ACTION_SET_OUTPUT_SIDE = "set_output_side";
    public static final SlotSemantic KEY_INPUT = SlotSemantics.register("DATA_RIPPER_REASSEMBLER_KEY_INPUT", false);
    public static final SlotSemantic KEY_OUTPUT = SlotSemantics.register("DATA_RIPPER_REASSEMBLER_KEY_OUTPUT", false);
    public static final SlotSemantic FLUID_INPUT_B = SlotSemantics.register("DATA_RIPPER_REASSEMBLER_FLUID_INPUT_B", false);
    public static final SlotSemantic FLUID_OUTPUT_A = SlotSemantics.register("DATA_RIPPER_REASSEMBLER_FLUID_OUTPUT_A", false);
    public static final SlotSemantic FLUID_OUTPUT_B = SlotSemantics.register("DATA_RIPPER_REASSEMBLER_FLUID_OUTPUT_B", false);
    public static final SlotSemantic ITEM_OUTPUT_B = SlotSemantics.register("DATA_RIPPER_REASSEMBLER_OUTPUT_B", false);
    public static final SlotSemantic ITEM_OUTPUT_C = SlotSemantics.register("DATA_RIPPER_REASSEMBLER_OUTPUT_C", false);

    @GuiSync(840)
    public boolean online;
    @GuiSync(841)
    public String fluidInputAId = "";
    @GuiSync(842)
    public int fluidInputAAmount;
    @GuiSync(843)
    public String fluidInputBId = "";
    @GuiSync(844)
    public int fluidInputBAmount;
    @GuiSync(845)
    public String fluidOutputAId = "";
    @GuiSync(846)
    public int fluidOutputAAmount;
    @GuiSync(847)
    public String fluidOutputBId = "";
    @GuiSync(848)
    public int fluidOutputBAmount;
    @GuiSync(849)
    public String keyInputLabel = "";
    @GuiSync(850)
    public long keyInputAmount;
    @GuiSync(851)
    public String keyOutputLabel = "";
    @GuiSync(852)
    public long keyOutputAmount;
    @GuiSync(853)
    public int progress;
    @GuiSync(854)
    public int maxProgress = DataRipperReassemblerBlockEntity.MAX_PROGRESS;
    @GuiSync(855)
    public YesNo autoExport = YesNo.NO;
    @GuiSync(856)
    public int outputSidesMask = 63;

    public DataRipperReassemblerMenu(int id, Inventory playerInventory, DataRipperReassemblerBlockEntity host) {
        super(ModMenus.DATA_RIPPER_REASSEMBLER.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_AUTO_EXPORT, Boolean.class, this::setAutoExportEnabled);
        registerClientAction(ACTION_SET_OUTPUT_SIDE, String.class, this::setOutputSide);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            var host = this.getHost();
            this.online = host.isOnline();
            syncFluid(host.getFluidInputA(), 0);
            syncFluid(host.getFluidInputB(), 1);
            syncFluid(host.getFluidOutputA(), 2);
            syncFluid(host.getFluidOutputB(), 3);
            var keyInput = host.getKeyInputStack();
            if (keyInput == null) {
                this.keyInputLabel = "";
                this.keyInputAmount = 0;
            } else {
                this.keyInputLabel = keyInput.what().getDisplayName().getString();
                this.keyInputAmount = keyInput.amount();
            }
            var keyOutput = host.getKeyOutputStack();
            if (keyOutput == null) {
                this.keyOutputLabel = "";
                this.keyOutputAmount = 0;
            } else {
                this.keyOutputLabel = keyOutput.what().getDisplayName().getString();
                this.keyOutputAmount = keyOutput.amount();
            }
            this.progress = host.getProgress();
            this.maxProgress = host.getMaxProgress();
            this.autoExport = host.isAutoExportEnabled() ? YesNo.YES : YesNo.NO;
            this.outputSidesMask = encodeOutputSides(host.getOutputSides());
        }
        super.broadcastChanges();
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getStorageInventory();
        for (int i = 0; i < DataRipperReassemblerBlockEntity.ITEM_INPUT_SLOT_COUNT; i++) {
            this.addSlot(new AppEngSlot(storage, DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT + i), SlotSemantics.MACHINE_INPUT);
        }
        this.addSlot(new AppEngSlot(this.getHost().getFluidMenuInventoryA(), 0), SlotSemantics.STORAGE);
        this.addSlot(new AppEngSlot(this.getHost().getFluidMenuInventoryB(), 0), FLUID_INPUT_B);
        this.addSlot(new AppEngSlot(this.getHost().getFluidOutputMenuInventoryA(), 0), FLUID_OUTPUT_A);
        this.addSlot(new AppEngSlot(this.getHost().getFluidOutputMenuInventoryB(), 0), FLUID_OUTPUT_B);
        this.addSlot(new AppEngSlot(this.getHost().getKeyMenuInventory(), 0), KEY_INPUT);
        this.addSlot(new AppEngSlot(this.getHost().getKeyOutputMenuInventory(), 0), KEY_OUTPUT);
        this.addSlot(new OutputSlot(storage, DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT, null), SlotSemantics.MACHINE_OUTPUT);
        this.addSlot(new OutputSlot(storage, DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT + 1, null), ITEM_OUTPUT_B);
        this.addSlot(new OutputSlot(storage, DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT + 2, null), ITEM_OUTPUT_C);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {}

    private void syncFluid(FluidStack stack, int index) {
        String id = stack.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
        int amount = stack.getAmount();
        switch (index) {
            case 0 -> {
                this.fluidInputAId = id;
                this.fluidInputAAmount = amount;
            }
            case 1 -> {
                this.fluidInputBId = id;
                this.fluidInputBAmount = amount;
            }
            case 2 -> {
                this.fluidOutputAId = id;
                this.fluidOutputAAmount = amount;
            }
            case 3 -> {
                this.fluidOutputBId = id;
                this.fluidOutputBAmount = amount;
            }
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

    public int getFluidInputCapacity() {
        return this.getHost().getFluidInputCapacity();
    }

    public int getFluidOutputCapacity() {
        return this.getHost().getFluidOutputCapacity();
    }

    public long getKeyInputCapacity() {
        return DataRipperReassemblerBlockEntity.KEY_INPUT_CAPACITY;
    }

    public long getKeyOutputCapacity() {
        return DataRipperReassemblerBlockEntity.KEY_OUTPUT_CAPACITY;
    }

    public void sendSetAutoExport(boolean enabled) {
        sendClientAction(ACTION_SET_AUTO_EXPORT, enabled);
    }

    public YesNo getAutoExport() {
        return this.autoExport;
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

    private void setAutoExportEnabled(Boolean enabled) {
        if (enabled == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Reassembler auto-export client action without a boolean payload at {}",
                    this.getHost().getBlockPos());
            return;
        }

        this.getHost().getConfigManager().putSetting(Settings.AUTO_EXPORT, enabled ? YesNo.YES : YesNo.NO);
        this.autoExport = enabled ? YesNo.YES : YesNo.NO;
        broadcastChanges();
    }

    private void setOutputSide(String payload) {
        if (payload == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Reassembler output-side client action without a payload at {}",
                    this.getHost().getBlockPos());
            return;
        }

        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1 || separator != payload.lastIndexOf(':')) {
            Data_Energistics.LOGGER.warn(
                    "Rejected malformed Data Reassembler output-side client action at {}",
                    this.getHost().getBlockPos());
            return;
        }

        Direction side = Direction.byName(payload.substring(0, separator));
        if (side == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Reassembler output-side client action with an unknown direction at {}",
                    this.getHost().getBlockPos());
            return;
        }

        String enabledValue = payload.substring(separator + 1);
        boolean enabled;
        if ("true".equals(enabledValue)) {
            enabled = true;
        } else if ("false".equals(enabledValue)) {
            enabled = false;
        } else {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Reassembler output-side client action with a non-boolean value for {} at {}",
                    side,
                    this.getHost().getBlockPos());
            return;
        }
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
}
