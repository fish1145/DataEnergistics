package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataAsynchronousProcessingFactoryBlockEntity;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.inventories.InternalInventory;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;

public final class DataAsynchronousProcessingFactoryMenu extends DataRipperReassemblerMenu {

    public static final SlotSemantic ITEM_INPUT_LEFT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_INPUT_LEFT", false);
    public static final SlotSemantic ITEM_INPUT_RIGHT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_INPUT_RIGHT", false);
    public static final SlotSemantic ITEM_INPUT_END = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_INPUT_END", false);
    public static final SlotSemantic FLUID_INPUT_LEFT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_FLUID_INPUT_LEFT", false);
    public static final SlotSemantic FLUID_INPUT_RIGHT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_FLUID_INPUT_RIGHT", false);
    public static final SlotSemantic KEY_INPUT_TOP = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_KEY_INPUT_TOP", false);
    public static final SlotSemantic KEY_INPUT_MIDDLE = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_KEY_INPUT_MIDDLE", false);
    public static final SlotSemantic KEY_INPUT_BOTTOM = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_KEY_INPUT_BOTTOM", false);
    public static final SlotSemantic FLUID_OUTPUT_LEFT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_FLUID_OUTPUT_LEFT", false);
    public static final SlotSemantic FLUID_OUTPUT_RIGHT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_FLUID_OUTPUT_RIGHT", false);
    public static final SlotSemantic KEY_OUTPUT_TOP = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_KEY_OUTPUT_TOP", false);
    public static final SlotSemantic KEY_OUTPUT_BOTTOM = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_KEY_OUTPUT_BOTTOM", false);
    public static final SlotSemantic ITEM_OUTPUT_LEFT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_OUTPUT_LEFT", false);
    public static final SlotSemantic ITEM_OUTPUT_RIGHT = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_OUTPUT_RIGHT", false);
    public static final SlotSemantic ITEM_OUTPUT_END = SlotSemantics.register("DATA_ASYNCHRONOUS_FACTORY_OUTPUT_END", false);

    @GuiSync(870)
    public String fluidInputCId = "";
    @GuiSync(871)
    public int fluidInputCAmount;
    @GuiSync(872)
    public String fluidInputDId = "";
    @GuiSync(873)
    public int fluidInputDAmount;
    @GuiSync(874)
    public String fluidInputEId = "";
    @GuiSync(875)
    public int fluidInputEAmount;
    @GuiSync(876)
    public String fluidInputFId = "";
    @GuiSync(877)
    public int fluidInputFAmount;
    @GuiSync(878)
    public String fluidOutputCId = "";
    @GuiSync(879)
    public int fluidOutputCAmount;
    @GuiSync(880)
    public String fluidOutputDId = "";
    @GuiSync(881)
    public int fluidOutputDAmount;
    @GuiSync(882)
    public int middleProgress;
    @GuiSync(883)
    public int middleMaxProgress = DataAsynchronousProcessingFactoryBlockEntity.MAX_PROGRESS;
    @GuiSync(884)
    public int rightProgress;
    @GuiSync(885)
    public int rightMaxProgress = DataAsynchronousProcessingFactoryBlockEntity.MAX_PROGRESS;

    private final IProgressProvider middleProgressProvider = new IProgressProvider() {

        @Override
        public int getCurrentProgress() {
            return DataAsynchronousProcessingFactoryMenu.this.middleProgress;
        }

        @Override
        public int getMaxProgress() {
            return DataAsynchronousProcessingFactoryMenu.this.middleMaxProgress;
        }
    };
    private final IProgressProvider rightProgressProvider = new IProgressProvider() {

        @Override
        public int getCurrentProgress() {
            return DataAsynchronousProcessingFactoryMenu.this.rightProgress;
        }

        @Override
        public int getMaxProgress() {
            return DataAsynchronousProcessingFactoryMenu.this.rightMaxProgress;
        }
    };

    public DataAsynchronousProcessingFactoryMenu(int id, Inventory playerInventory,
                                                 DataAsynchronousProcessingFactoryBlockEntity host) {
        super(DEMenus.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), id, playerInventory, host);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            var host = this.getHost();
            this.middleProgress = host.getProgress(1);
            this.middleMaxProgress = host.getMaxProgress(1);
            this.rightProgress = host.getProgress(2);
            this.rightMaxProgress = host.getMaxProgress(2);
            syncExtraFluid(host.getFluidInput(2), 0);
            syncExtraFluid(host.getFluidInput(3), 1);
            syncExtraFluid(host.getFluidInput(4), 2);
            syncExtraFluid(host.getFluidInput(5), 3);
            syncExtraFluid(host.getFluidOutput(2), 4);
            syncExtraFluid(host.getFluidOutput(3), 5);
        }
        super.broadcastChanges();
    }

    public IProgressProvider getMiddleProgressProvider() {
        return this.middleProgressProvider;
    }

    public IProgressProvider getRightProgressProvider() {
        return this.rightProgressProvider;
    }

    @Override
    protected void setupInventorySlots() {
        var host = this.getHost();
        InternalInventory storage = host.getStorageInventory();
        for (int slot = 0; slot < host.getItemInputSlotCount(); slot++) {
            int column = slot % 7;
            SlotSemantic semantic = column < 3 ? ITEM_INPUT_LEFT : column < 6 ? ITEM_INPUT_RIGHT : ITEM_INPUT_END;
            this.addSlot(new ReassemblerItemSlot(storage, slot), semantic);
        }

        for (int slot = 0; slot < host.getFluidInputSlotCount(); slot++) {
            SlotSemantic semantic = slot % 2 == 0 ? FLUID_INPUT_LEFT : FLUID_INPUT_RIGHT;
            this.addSlot(new AppEngSlot(host.getFluidInputMenuInventory(slot), 0), semantic);
        }
        for (int slot = 0; slot < host.getKeyInputSlotCount(); slot++) {
            this.addSlot(new AppEngSlot(host.getKeyInputMenuInventory(slot), 0), getKeyInputSemantic(slot));
        }
        for (int slot = 0; slot < host.getFluidOutputSlotCount(); slot++) {
            SlotSemantic semantic = slot % 2 == 0 ? FLUID_OUTPUT_LEFT : FLUID_OUTPUT_RIGHT;
            this.addSlot(new AppEngSlot(host.getFluidOutputMenuInventory(slot), 0), semantic);
        }
        for (int slot = 0; slot < host.getKeyOutputSlotCount(); slot++) {
            this.addSlot(new AppEngSlot(host.getKeyOutputMenuInventory(slot), 0), getKeyOutputSemantic(slot));
        }

        for (int slot = 0; slot < host.getItemOutputSlotCount(); slot++) {
            int column = slot % 7;
            SlotSemantic semantic = column < 3 ? ITEM_OUTPUT_LEFT : column < 6 ? ITEM_OUTPUT_RIGHT : ITEM_OUTPUT_END;
            this.addSlot(new ReassemblerOutputSlot(storage, host.getItemOutputStartSlot() + slot), semantic);
        }
    }

    private void syncExtraFluid(FluidStack stack, int index) {
        String id = stack.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
        int amount = stack.getAmount();
        switch (index) {
            case 0 -> {
                this.fluidInputCId = id;
                this.fluidInputCAmount = amount;
            }
            case 1 -> {
                this.fluidInputDId = id;
                this.fluidInputDAmount = amount;
            }
            case 2 -> {
                this.fluidInputEId = id;
                this.fluidInputEAmount = amount;
            }
            case 3 -> {
                this.fluidInputFId = id;
                this.fluidInputFAmount = amount;
            }
            case 4 -> {
                this.fluidOutputCId = id;
                this.fluidOutputCAmount = amount;
            }
            case 5 -> {
                this.fluidOutputDId = id;
                this.fluidOutputDAmount = amount;
            }
            default -> throw new IndexOutOfBoundsException("Invalid factory fluid sync index: " + index);
        }
    }

    private static SlotSemantic getKeyInputSemantic(int slot) {
        return switch (slot) {
            case 0 -> KEY_INPUT_TOP;
            case 1 -> KEY_INPUT_MIDDLE;
            case 2 -> KEY_INPUT_BOTTOM;
            default -> throw new IndexOutOfBoundsException("Invalid factory key input slot: " + slot);
        };
    }

    private static SlotSemantic getKeyOutputSemantic(int slot) {
        return switch (slot) {
            case 0 -> KEY_OUTPUT_TOP;
            case 1 -> KEY_OUTPUT_BOTTOM;
            default -> throw new IndexOutOfBoundsException("Invalid factory key output slot: " + slot);
        };
    }
}
