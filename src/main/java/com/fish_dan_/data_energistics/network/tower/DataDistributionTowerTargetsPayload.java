package com.fish_dan_.data_energistics.network.tower;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * One bounded batch in a complete Data Distribution Tower target snapshot.
 *
 * @param containerId menu container that requested the snapshot
 * @param revision    monotonically increasing server snapshot revision
 * @param batchIndex  zero-based index of this batch
 * @param batchCount  complete number of batches in the revision
 * @param totalCount  complete number of target rows in the revision
 * @param entries     ordered rows carried by this batch
 */
public record DataDistributionTowerTargetsPayload(int containerId,
                                                  long revision,
                                                  int batchIndex,
                                                  int batchCount,
                                                  int totalCount,
                                                  List<DataDistributionTowerTargetEntry> entries)
        implements CustomPacketPayload {

    /**
     * Maximum rows carried by one packet while allowing an unlimited number of batches.
     */
    public static final int MAX_ENTRIES_PER_BATCH = 64;
    /**
     * Payload identifier used by NeoForge registration.
     */
    public static final Type<DataDistributionTowerTargetsPayload> TYPE = new Type<>(Data_Energistics.id("data_distribution_tower_targets"));
    /**
     * Stream codec shared by the server sender and client receiver.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DataDistributionTowerTargetsPayload> STREAM_CODEC = CustomPacketPayload.codec(
            DataDistributionTowerTargetsPayload::write,
            DataDistributionTowerTargetsPayload::new);

    /**
     * Validates packet metadata and freezes its entry list.
     */
    public DataDistributionTowerTargetsPayload {
        entries = List.copyOf(entries);
        if (containerId < 0) {
            throw new IllegalArgumentException("Container id must be non-negative: " + containerId);
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Target batch revision must be non-negative: " + revision);
        }
        if (batchCount <= 0) {
            throw new IllegalArgumentException("Target batch count must be positive: " + batchCount);
        }
        if (batchIndex < 0 || batchIndex >= batchCount) {
            throw new IllegalArgumentException(
                    "Target batch index is outside [0, " + batchCount + "): " + batchIndex);
        }
        if (totalCount < 0) {
            throw new IllegalArgumentException("Target total count must be non-negative: " + totalCount);
        }
        if (entries.size() > MAX_ENTRIES_PER_BATCH) {
            throw new IllegalArgumentException("Target batch exceeds " + MAX_ENTRIES_PER_BATCH + " entries: " + entries.size());
        }

        if (totalCount == 0) {
            if (batchCount != 1 || !entries.isEmpty()) {
                throw new IllegalArgumentException("An empty target snapshot must contain one empty batch");
            }
        } else {
            int minimumBatchCount = ((totalCount - 1) / MAX_ENTRIES_PER_BATCH) + 1;
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("A non-empty target snapshot cannot contain an empty batch");
            }
            if (batchCount < minimumBatchCount || batchCount > totalCount) {
                throw new IllegalArgumentException("Target batch count cannot represent declared total: batches=" + batchCount + ", total=" + totalCount);
            }
        }
    }

    /**
     * Splits one complete target list into protocol-sized packets.
     *
     * @param containerId owning menu container
     * @param revision    monotonically increasing snapshot revision
     * @param entries     complete ordered target list
     * @return immutable packet sequence, including one empty packet for an empty list
     */
    public static List<DataDistributionTowerTargetsPayload> batches(
                                                                    int containerId,
                                                                    long revision,
                                                                    List<DataDistributionTowerTargetEntry> entries) {
        List<DataDistributionTowerTargetEntry> immutableEntries = List.copyOf(entries);
        int totalCount = immutableEntries.size();
        int batchCount = totalCount == 0 ? 1 : ((totalCount - 1) / MAX_ENTRIES_PER_BATCH) + 1;
        ArrayList<DataDistributionTowerTargetsPayload> payloads = new ArrayList<>(batchCount);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int fromIndex = batchIndex * MAX_ENTRIES_PER_BATCH;
            int toIndex = Math.min(totalCount, fromIndex + MAX_ENTRIES_PER_BATCH);
            payloads.add(new DataDistributionTowerTargetsPayload(
                    containerId,
                    revision,
                    batchIndex,
                    batchCount,
                    totalCount,
                    immutableEntries.subList(fromIndex, toIndex)));
        }
        return List.copyOf(payloads);
    }

    /**
     * Decodes one validated target batch from the network.
     *
     * @param buffer source packet buffer
     */
    private DataDistributionTowerTargetsPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readEntries(buffer));
    }

    /**
     * Encodes this batch for client delivery.
     *
     * @param buffer destination packet buffer
     */
    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeVarLong(this.revision);
        buffer.writeVarInt(this.batchIndex);
        buffer.writeVarInt(this.batchCount);
        buffer.writeVarInt(this.totalCount);
        buffer.writeVarInt(this.entries.size());
        for (DataDistributionTowerTargetEntry entry : this.entries) {
            entry.write(buffer);
        }
    }

    /**
     * Returns this payload's registered wire type.
     *
     * @return tower target payload type
     */
    @Override
    public Type<DataDistributionTowerTargetsPayload> type() {
        return TYPE;
    }

    /**
     * Schedules client-menu validation and atomic batch delivery.
     *
     * @param payload decoded target batch
     * @param context NeoForge payload context
     */
    public static void handle(DataDistributionTowerTargetsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DataDistributionTowerTargetsClientHandler.receive(payload, context.player()));
    }

    /**
     * Decodes a bounded target entry list before payload construction.
     *
     * @param buffer source packet buffer
     * @return immutable decoded batch entries
     */
    private static List<DataDistributionTowerTargetEntry> readEntries(RegistryFriendlyByteBuf buffer) {
        int entryCount = buffer.readVarInt();
        if (entryCount < 0 || entryCount > MAX_ENTRIES_PER_BATCH) {
            throw new IllegalArgumentException("Target payload entry count is outside [0, " + MAX_ENTRIES_PER_BATCH + "]: " + entryCount);
        }
        ArrayList<DataDistributionTowerTargetEntry> entries = new ArrayList<>(entryCount);
        for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
            entries.add(DataDistributionTowerTargetEntry.read(buffer));
        }
        return List.copyOf(entries);
    }
}
