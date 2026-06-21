package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record AdaptiveWirelessConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {

    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_FACE = "Face";

    public boolean sameTarget(ResourceKey<Level> otherDim, BlockPos otherPos) {
        return this.dimension.equals(otherDim) && this.pos.equals(otherPos);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIM, this.dimension.location().toString());
        tag.putLong(TAG_POS, this.pos.asLong());
        tag.putInt(TAG_FACE, this.boundFace.get3DDataValue());
        return tag;
    }

    public static AdaptiveWirelessConnection fromTag(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(tag.getString(TAG_DIM)));
        BlockPos pos = BlockPos.of(tag.getLong(TAG_POS));
        Direction face = Direction.from3DDataValue(tag.getInt(TAG_FACE));
        return new AdaptiveWirelessConnection(dimension, pos, face);
    }
}
