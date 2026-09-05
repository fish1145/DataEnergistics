package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataRipperReassemblerBlockEntity;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
    public int itemOutputSidesMask = 63;
    @GuiSync(857)
    public int fluidOutputSidesMask = 63;
    @GuiSync(858)
    public int keyOutputSidesMask = 63;
    @GuiSync(886)
    public int itemInputPatternColor0;
    @GuiSync(887)
    public int itemInputPatternColor1;
    @GuiSync(888)
    public int itemInputPatternColor2;
    @GuiSync(889)
    public int itemInputPatternColor3;
    @GuiSync(890)
    public int itemInputPatternColor4;
    @GuiSync(891)
    public int itemInputPatternColor5;
    @GuiSync(892)
    public int itemInputPatternColor6;
    @GuiSync(893)
    public int itemInputPatternColor7;
    @GuiSync(894)
    public int itemInputPatternColor8;
    @GuiSync(895)
    public int fluidInputPatternColor0;
    @GuiSync(896)
    public int fluidInputPatternColor1;
    @GuiSync(897)
    public int keyInputPatternColor0;

    public DataRipperReassemblerMenu(int id, Inventory playerInventory, DataRipperReassemblerBlockEntity host) {
        this(DEMenus.DATA_RIPPER_REASSEMBLER.get(), id, playerInventory, host);
    }

    protected DataRipperReassemblerMenu(MenuType<?> menuType, int id, Inventory playerInventory, DataRipperReassemblerBlockEntity host) {
        super(menuType, id, playerInventory, host);
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
            syncInputPatternColors(host);
            this.progress = host.getProgress();
            this.maxProgress = host.getMaxProgress();
            this.autoExport = host.isAutoExportEnabled() ? YesNo.YES : YesNo.NO;
            this.itemOutputSidesMask = encodeOutputSides(host.getOutputSides(DigitalStorageDepotOutputType.ITEMS));
            this.fluidOutputSidesMask = encodeOutputSides(host.getOutputSides(DigitalStorageDepotOutputType.FLUIDS));
            this.keyOutputSidesMask = encodeOutputSides(host.getOutputSides(DigitalStorageDepotOutputType.KEYS));
        }
        super.broadcastChanges();
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getStorageInventory();
        for (int i = 0; i < this.getHost().getItemInputSlotCount(); i++) {
            this.addSlot(new ReassemblerItemSlot(storage, DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT + i), SlotSemantics.MACHINE_INPUT);
        }
        this.addSlot(new PatternInputSlot(this.getHost().getFluidMenuInventoryA(), 0, PatternInputType.FLUID, 0), SlotSemantics.STORAGE);
        this.addSlot(new PatternInputSlot(this.getHost().getFluidMenuInventoryB(), 0, PatternInputType.FLUID, 1), FLUID_INPUT_B);
        this.addSlot(new AppEngSlot(this.getHost().getFluidOutputMenuInventoryA(), 0), FLUID_OUTPUT_A);
        this.addSlot(new AppEngSlot(this.getHost().getFluidOutputMenuInventoryB(), 0), FLUID_OUTPUT_B);
        this.addSlot(new PatternInputSlot(this.getHost().getKeyMenuInventory(), 0, PatternInputType.KEY, 0), KEY_INPUT);
        this.addSlot(new AppEngSlot(this.getHost().getKeyOutputMenuInventory(), 0), KEY_OUTPUT);
        for (int i = 0; i < this.getHost().getItemOutputSlotCount(); i++) {
            SlotSemantic semantic = switch (i) {
                case 0 -> SlotSemantics.MACHINE_OUTPUT;
                case 1 -> ITEM_OUTPUT_B;
                case 2 -> ITEM_OUTPUT_C;
                default -> SlotSemantics.MACHINE_OUTPUT;
            };
            this.addSlot(new ReassemblerOutputSlot(storage, this.getHost().getItemOutputStartSlot() + i), semantic);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {}

    @Override
    protected int getQuickMovePriority(Slot slot) {
        if (this.getSlotSemantic(slot) == SlotSemantics.UPGRADE) {
            return 1;
        }
        return super.getQuickMovePriority(slot);
    }

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

    private void syncInputPatternColors(DataRipperReassemblerBlockEntity host) {
        this.itemInputPatternColor0 = host.getItemInputPatternColor(0);
        this.itemInputPatternColor1 = host.getItemInputPatternColor(1);
        this.itemInputPatternColor2 = host.getItemInputPatternColor(2);
        this.itemInputPatternColor3 = host.getItemInputPatternColor(3);
        this.itemInputPatternColor4 = host.getItemInputPatternColor(4);
        this.itemInputPatternColor5 = host.getItemInputPatternColor(5);
        this.itemInputPatternColor6 = host.getItemInputPatternColor(6);
        this.itemInputPatternColor7 = host.getItemInputPatternColor(7);
        this.itemInputPatternColor8 = host.getItemInputPatternColor(8);
        this.fluidInputPatternColor0 = host.getFluidInputPatternColor(0);
        this.fluidInputPatternColor1 = host.getFluidInputPatternColor(1);
        this.keyInputPatternColor0 = host.getKeyInputPatternColor(0);
    }

    public int getPatternInputColor(Slot slot) {
        if (!(slot instanceof PatternInputSlot patternInputSlot)) {
            return 0;
        }
        return switch (patternInputSlot.inputType) {
            case ITEM -> getItemInputPatternColor(patternInputSlot.inputSlot);
            case FLUID -> getFluidInputPatternColor(patternInputSlot.inputSlot);
            case KEY -> getKeyInputPatternColor(patternInputSlot.inputSlot);
        };
    }

    public int getItemInputPatternColor(int slot) {
        return switch (slot) {
            case 0 -> this.itemInputPatternColor0;
            case 1 -> this.itemInputPatternColor1;
            case 2 -> this.itemInputPatternColor2;
            case 3 -> this.itemInputPatternColor3;
            case 4 -> this.itemInputPatternColor4;
            case 5 -> this.itemInputPatternColor5;
            case 6 -> this.itemInputPatternColor6;
            case 7 -> this.itemInputPatternColor7;
            case 8 -> this.itemInputPatternColor8;
            default -> throw new IndexOutOfBoundsException("Invalid item input pattern color slot: " + slot);
        };
    }

    public int getFluidInputPatternColor(int slot) {
        return switch (slot) {
            case 0 -> this.fluidInputPatternColor0;
            case 1 -> this.fluidInputPatternColor1;
            default -> throw new IndexOutOfBoundsException("Invalid fluid input pattern color slot: " + slot);
        };
    }

    public int getKeyInputPatternColor(int slot) {
        if (slot != 0) {
            throw new IndexOutOfBoundsException("Invalid key input pattern color slot: " + slot);
        }
        return this.keyInputPatternColor0;
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
        return this.getHost().getKeyInputCapacity();
    }

    public long getKeyOutputCapacity() {
        return this.getHost().getKeyOutputCapacity();
    }

    public void sendSetAutoExport(boolean enabled) {
        sendClientAction(ACTION_SET_AUTO_EXPORT, enabled);
    }

    public YesNo getAutoExport() {
        return this.autoExport;
    }

    public List<Direction> getOutputSides(DigitalStorageDepotOutputType outputType) {
        int mask = switch (outputType) {
            case ITEMS -> this.itemOutputSidesMask;
            case FLUIDS -> this.fluidOutputSidesMask;
            case KEYS -> this.keyOutputSidesMask;
        };
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if ((mask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    public void sendSetOutputSide(DigitalStorageDepotOutputType outputType, Direction side, boolean enabled) {
        sendClientAction(ACTION_SET_OUTPUT_SIDE,
                outputType.getSerializedName() + ":" + side.getName() + ":" + enabled);
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

        int firstSeparator = payload.indexOf(':');
        int secondSeparator = firstSeparator < 0 ? -1 : payload.indexOf(':', firstSeparator + 1);
        if (firstSeparator <= 0 || secondSeparator <= firstSeparator + 1 || secondSeparator >= payload.length() - 1 || secondSeparator != payload.lastIndexOf(':')) {
            Data_Energistics.LOGGER.warn(
                    "Rejected malformed Data Reassembler output-side client action at {}",
                    this.getHost().getBlockPos());
            return;
        }

        DigitalStorageDepotOutputType outputType = DigitalStorageDepotOutputType.fromSerializedName(
                payload.substring(0, firstSeparator));
        if (outputType == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Reassembler output-side client action with an unknown content type at {}",
                    this.getHost().getBlockPos());
            return;
        }

        Direction side = Direction.byName(payload.substring(firstSeparator + 1, secondSeparator));
        if (side == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Reassembler output-side client action with an unknown direction at {}",
                    this.getHost().getBlockPos());
            return;
        }

        String enabledValue = payload.substring(secondSeparator + 1);
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
        this.getHost().setOutputSideEnabled(outputType, side, enabled);
        switch (outputType) {
            case ITEMS -> this.itemOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(outputType));
            case FLUIDS -> this.fluidOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(outputType));
            case KEYS -> this.keyOutputSidesMask = encodeOutputSides(this.getHost().getOutputSides(outputType));
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

    protected enum PatternInputType {
        ITEM,
        FLUID,
        KEY
    }

    protected static class PatternInputSlot extends AppEngSlot {

        private final PatternInputType inputType;
        private final int inputSlot;

        protected PatternInputSlot(InternalInventory inv, int invSlot, PatternInputType inputType, int inputSlot) {
            super(inv, invSlot);
            this.inputType = inputType;
            this.inputSlot = inputSlot;
        }
    }

    protected static final class ReassemblerItemSlot extends PatternInputSlot {

        protected ReassemblerItemSlot(InternalInventory inv, int invSlot) {
            super(inv, invSlot, PatternInputType.ITEM, invSlot);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.getMaxStackSize();
        }
    }

    protected static final class ReassemblerOutputSlot extends OutputSlot {

        protected ReassemblerOutputSlot(InternalInventory inv, int invSlot) {
            super(inv, invSlot, null);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.getMaxStackSize();
        }
    }
}
