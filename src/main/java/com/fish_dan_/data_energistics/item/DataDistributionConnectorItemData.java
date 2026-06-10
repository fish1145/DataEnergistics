package com.fish_dan_.data_energistics.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DataDistributionConnectorItemData(String dimensionId, long towerPos, boolean selected) {

    public static final DataDistributionConnectorItemData EMPTY = new DataDistributionConnectorItemData("", 0L, false);

    public static final Codec<DataDistributionConnectorItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension_id", "").forGetter(DataDistributionConnectorItemData::dimensionId),
            Codec.LONG.optionalFieldOf("tower_pos", 0L).forGetter(DataDistributionConnectorItemData::towerPos),
            Codec.BOOL.optionalFieldOf("selected", false).forGetter(DataDistributionConnectorItemData::selected))
            .apply(instance, DataDistributionConnectorItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DataDistributionConnectorItemData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            DataDistributionConnectorItemData::dimensionId,
            ByteBufCodecs.VAR_LONG,
            DataDistributionConnectorItemData::towerPos,
            ByteBufCodecs.BOOL,
            DataDistributionConnectorItemData::selected,
            DataDistributionConnectorItemData::new);

    public DataDistributionConnectorItemData {
        dimensionId = dimensionId == null ? "" : dimensionId;
    }

    public boolean hasSelection() {
        return this.selected && !this.dimensionId.isEmpty();
    }

    public BlockPos getTowerPos() {
        return BlockPos.of(this.towerPos);
    }

    public DataDistributionConnectorItemData withTower(String dimensionId, BlockPos towerPos) {
        return new DataDistributionConnectorItemData(dimensionId, towerPos.asLong(), true);
    }

    public DataDistributionConnectorItemData clear() {
        return EMPTY;
    }
}
