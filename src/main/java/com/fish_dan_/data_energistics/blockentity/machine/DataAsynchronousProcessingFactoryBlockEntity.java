package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class DataAsynchronousProcessingFactoryBlockEntity extends DataRipperReassemblerBlockEntity {

    public static final int ITEM_INPUT_SLOT_COUNT = 18;
    public static final int ITEM_OUTPUT_SLOT_COUNT = 12;
    private static final String STORAGE_LAYOUT_VERSION_TAG = "storage_layout_version";
    private static final int STORAGE_LAYOUT_VERSION = 2;

    public DataAsynchronousProcessingFactoryBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_ASYNCHRONOUS_PROCESSING_FACTORY_BLOCK_ENTITY.get(),
                DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), blockPos, blockState);
    }

    @Override
    protected Block getMachineBlock() {
        return DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get();
    }

    @Override
    public int getItemInputSlotCount() {
        return ITEM_INPUT_SLOT_COUNT;
    }

    @Override
    public int getItemOutputSlotCount() {
        return ITEM_OUTPUT_SLOT_COUNT;
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        boolean migrateLegacyOutputSlots = !data.contains(STORAGE_LAYOUT_VERSION_TAG);
        super.loadTag(data, registries);
        if (!migrateLegacyOutputSlots) {
            return;
        }

        for (int slot = 0; slot < DataRipperReassemblerBlockEntity.ITEM_OUTPUT_SLOT_COUNT; slot++) {
            int legacyOutputSlot = DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT + slot;
            ItemStack output = this.getStorageInventory().getStackInSlot(legacyOutputSlot);
            if (output.isEmpty()) {
                continue;
            }

            this.getStorageInventory().setItemDirect(this.getItemOutputStartSlot() + slot, output.copy());
            this.getStorageInventory().setItemDirect(legacyOutputSlot, ItemStack.EMPTY);
        }
        this.setChanged();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putInt(STORAGE_LAYOUT_VERSION_TAG, STORAGE_LAYOUT_VERSION);
    }
}
