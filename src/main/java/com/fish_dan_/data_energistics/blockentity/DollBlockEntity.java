package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class DollBlockEntity extends BlockEntity implements Nameable {

    private static final String CUSTOM_NAME_TAG = "CustomName";
    private static final Component DEFAULT_NAME = Component.translatable("block.data_energistics.fish_dan_");

    @Nullable
    private Component customName;

    public DollBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.FISH_DAN_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public Component getName() {
        return customName == null ? DEFAULT_NAME : customName;
    }

    @Override
    public @Nullable Component getCustomName() {
        return customName;
    }

    public void setCustomName(@Nullable Component customName) {
        this.customName = customName;
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(CUSTOM_NAME_TAG, Tag.TAG_STRING)) {
            customName = Component.Serializer.fromJson(tag.getString(CUSTOM_NAME_TAG), registries);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (customName != null) {
            tag.putString(CUSTOM_NAME_TAG, Component.Serializer.toJson(customName, registries));
        }
    }
}
