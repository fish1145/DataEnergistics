package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Structured display and transfer state for one Data Distribution Tower target row.
 *
 * @param itemId       item rendered for the target
 * @param displayName  resolved target name shown by the menu
 * @param count        number of targets represented by this row
 * @param dimensionId  dimension containing the target
 * @param pos          target position used by focus and mode actions
 * @param kind         primary AE or FE display category
 * @param transferMode configured transfer mode, including the disabled state
 * @param transferInfo live AE and FE details shown for the target
 */
public record DataDistributionTowerTargetEntry(ResourceLocation itemId,
                                               String displayName,
                                               int count,
                                               ResourceLocation dimensionId,
                                               BlockPos pos,
                                               TargetKind kind,
                                               TargetTransferMode transferMode,
                                               TargetTransferInfo transferInfo) {

    /**
     * Maximum character count accepted for one target display name.
     */
    public static final int MAX_DISPLAY_NAME_LENGTH = 1024;

    /**
     * Validates and freezes one structured target entry.
     */
    public DataDistributionTowerTargetEntry {
        pos = pos.immutable();
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Target display name exceeds " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Target entry count must be positive: " + count);
        }
        if (transferInfo.channelConnections() < 0) {
            throw new IllegalArgumentException(
                    "Target channel connection count must be non-negative: " + transferInfo.channelConnections());
        }
        if (transferInfo.storedFe() < 0L || transferInfo.capacityFe() < 0L) {
            throw new IllegalArgumentException("Target FE amounts must be non-negative: stored=" + transferInfo.storedFe() + ", capacity=" + transferInfo.capacityFe());
        }
    }

    /**
     * Converts the tower's server-side display summary into its wire representation.
     *
     * @param summary complete target summary produced by the tower display resolver
     * @return immutable structured network entry
     */
    public static DataDistributionTowerTargetEntry fromSummary(BoundTargetSummary summary) {
        return new DataDistributionTowerTargetEntry(
                summary.itemId(),
                summary.displayName(),
                summary.count(),
                summary.dimensionId(),
                summary.pos(),
                summary.kind(),
                summary.transferMode(),
                summary.transferInfo());
    }

    /**
     * Decodes one target entry from a tower target batch.
     *
     * @param buffer source packet buffer
     * @return decoded and validated entry
     */
    static DataDistributionTowerTargetEntry read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation itemId = buffer.readResourceLocation();
        String displayName = buffer.readUtf(MAX_DISPLAY_NAME_LENGTH);
        int count = buffer.readVarInt();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        TargetKind kind = readEnum(buffer, TargetKind.values(), "target kind");
        TargetTransferMode transferMode = readEnum(buffer, TargetTransferMode.values(), "target transfer mode");
        TargetTransferInfo transferInfo = new TargetTransferInfo(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readBoolean(),
                buffer.readBoolean());
        return new DataDistributionTowerTargetEntry(
                itemId,
                displayName,
                count,
                dimensionId,
                pos,
                kind,
                transferMode,
                transferInfo);
    }

    /**
     * Encodes this entry into a tower target batch.
     *
     * @param buffer destination packet buffer
     */
    void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.itemId);
        buffer.writeUtf(this.displayName, MAX_DISPLAY_NAME_LENGTH);
        buffer.writeVarInt(this.count);
        buffer.writeResourceLocation(this.dimensionId);
        BlockPos.STREAM_CODEC.encode(buffer, this.pos);
        buffer.writeVarInt(this.kind.ordinal());
        buffer.writeVarInt(this.transferMode.ordinal());
        buffer.writeVarInt(this.transferInfo.channelConnections());
        buffer.writeBoolean(this.transferInfo.hasAeTarget());
        buffer.writeBoolean(this.transferInfo.hasEnergyTarget());
        buffer.writeVarLong(this.transferInfo.storedFe());
        buffer.writeVarLong(this.transferInfo.capacityFe());
        buffer.writeBoolean(this.transferInfo.canExtractFe());
        buffer.writeBoolean(this.transferInfo.canReceiveFe());
    }

    /**
     * Resolves a bounded enum ordinal from the packet.
     *
     * @param buffer packet buffer containing the ordinal
     * @param values enum constants in protocol order
     * @param label  field label used by validation failures
     * @param <E>    enum type being decoded
     * @return decoded enum constant
     */
    private static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buffer, E[] values, String label) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown " + label + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
