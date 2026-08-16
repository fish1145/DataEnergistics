package com.fish_dan_.data_energistics.ae2.key;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

import java.util.List;

/**
 * Common AE key space for the Data Flow, Echo and Celestial Energy resources exposed as Digitalization.
 */
public abstract sealed class DigitalizationKey extends AEKey permits CelestialEnergyKey, DataFlowKey, EchoKey {

    @Override
    public final AEKeyType getType() {
        return DigitalizationKeyType.TYPE;
    }

    @Override
    public final AEKey dropSecondary() {
        return this;
    }

    @Override
    public final CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DigitalizationKeyType.RESOURCE_FIELD, getId().toString());
        return tag;
    }

    @Override
    public final Object getPrimaryKey() {
        return getId();
    }

    @Override
    public final void writeToPacket(RegistryFriendlyByteBuf buffer) {
        DigitalizationKeyType.writeToPacket(buffer, this);
    }

    @Override
    public final void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        if (amount > 0) {
            drops.add(GenericStack.wrapInItemStack(this, amount));
        }
    }

    @Override
    public final boolean hasComponents() {
        return false;
    }

    @Override
    public final ItemStack wrapForDisplayOrFilter() {
        return GenericStack.wrapInItemStack(this, 1);
    }
}
