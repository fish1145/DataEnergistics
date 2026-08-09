package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.List;

/**
 * Stateless AE resource produced when a Warden sonic boom crosses an online formation plane.
 */
public final class EchoKey extends AEKey {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "echo");
    public static final EchoKey INSTANCE = new EchoKey();
    public static final MapCodec<EchoKey> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final Codec<EchoKey> CODEC = MAP_CODEC.codec();

    private EchoKey() {}

    public static EchoKey of() {
        return INSTANCE;
    }

    @Override
    public AEKeyType getType() {
        return EchoKeyType.TYPE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    @Override
    public Object getPrimaryKey() {
        return ID;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {}

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("key." + Data_Energistics.MODID + ".echo");
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        if (amount > 0) {
            drops.add(GenericStack.wrapInItemStack(this, amount));
        }
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public ItemStack wrapForDisplayOrFilter() {
        return GenericStack.wrapInItemStack(this, 1);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EchoKey;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public String toString() {
        return "EchoKey{}";
    }
}
