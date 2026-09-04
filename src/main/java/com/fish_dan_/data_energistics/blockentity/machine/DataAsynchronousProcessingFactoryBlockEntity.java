package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.integration.recipe.ExternalFactoryRecipeCatalog;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public final class DataAsynchronousProcessingFactoryBlockEntity extends DataRipperReassemblerBlockEntity {

    public static final int PROCESSING_CHANNEL_COUNT = 3;
    public static final int ITEM_INPUT_SLOT_COUNT = 21;
    public static final int ITEM_OUTPUT_SLOT_COUNT = 14;
    public static final int FLUID_INPUT_SLOT_COUNT = 6;
    public static final int FLUID_OUTPUT_SLOT_COUNT = 4;
    public static final int KEY_INPUT_SLOT_COUNT = 3;
    public static final int KEY_OUTPUT_SLOT_COUNT = 2;
    private static final String STORAGE_LAYOUT_VERSION_TAG = "storage_layout_version";
    private static final int STORAGE_LAYOUT_VERSION = 3;
    private final ExternalFactoryRecipeCatalog externalRecipeCatalog = new ExternalFactoryRecipeCatalog();

    public DataAsynchronousProcessingFactoryBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_ASYNCHRONOUS_PROCESSING_FACTORY_BLOCK_ENTITY.get(),
                DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), blockPos, blockState);
    }

    @Override
    protected Block getMachineBlock() {
        return DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get();
    }

    @Override
    protected boolean usesPatternInputColors() {
        return true;
    }

    @Override
    protected Iterable<RecipeHolder<DataRipperReassemblerRecipe>> getAdditionalProcessingRecipes(Level level) {
        return this.externalRecipeCatalog.recipes(level);
    }

    @Nullable
    @Override
    protected RecipeHolder<DataRipperReassemblerRecipe> getAdditionalProcessingRecipeById(Level level,
                                                                                          ResourceLocation recipeId) {
        return this.externalRecipeCatalog.recipeById(level, recipeId);
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
    protected int getProcessingChannelCount() {
        return PROCESSING_CHANNEL_COUNT;
    }

    @Override
    protected int getItemInputStartSlotForChannel(int channel) {
        return ITEM_INPUT_START_SLOT;
    }

    @Override
    protected int getItemInputSlotCountForChannel(int channel) {
        return ITEM_INPUT_SLOT_COUNT;
    }

    @Override
    protected int getFluidInputStartSlotForChannel(int channel) {
        return 0;
    }

    @Override
    protected int getFluidInputSlotCountForChannel(int channel) {
        return FLUID_INPUT_SLOT_COUNT;
    }

    @Override
    protected int getKeyInputStartSlotForChannel(int channel) {
        return 0;
    }

    @Override
    protected int getKeyInputSlotCountForChannel(int channel) {
        return KEY_INPUT_SLOT_COUNT;
    }

    @Override
    public int getFluidInputSlotCount() {
        return FLUID_INPUT_SLOT_COUNT;
    }

    @Override
    public int getFluidOutputSlotCount() {
        return FLUID_OUTPUT_SLOT_COUNT;
    }

    @Override
    public int getKeyInputSlotCount() {
        return KEY_INPUT_SLOT_COUNT;
    }

    @Override
    public int getKeyOutputSlotCount() {
        return KEY_OUTPUT_SLOT_COUNT;
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        int storageLayoutVersion = data.getInt(STORAGE_LAYOUT_VERSION_TAG);
        if (storageLayoutVersion != STORAGE_LAYOUT_VERSION) {
            throw new IllegalArgumentException("Unsupported asynchronous factory storage layout: " + storageLayoutVersion);
        }
        super.loadTag(data, registries);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putInt(STORAGE_LAYOUT_VERSION_TAG, STORAGE_LAYOUT_VERSION);
    }
}
