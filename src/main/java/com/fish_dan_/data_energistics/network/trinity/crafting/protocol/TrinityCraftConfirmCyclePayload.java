package com.fish_dan_.data_energistics.network.trinity.crafting.protocol;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingExactShortage;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingUnresolvedDemand;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingExactPlanAmounts;
import com.fish_dan_.data_energistics.network.trinity.crafting.client.TrinityCraftConfirmCycleClientHandler;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.ExactPlanAmounts;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.ExactShortage;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.Header;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.InventoryUsage;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.Material;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCycleRecord.UnresolvedDemand;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * One bounded batch from an atomically published Trinity crafting confirmation summary.
 */
public final class TrinityCraftConfirmCyclePayload implements CustomPacketPayload {

    /** Maximum number of typed records carried by one packet. */
    public static final int MAX_RECORDS_PER_BATCH = 64;
    /** NeoForge payload identifier. */
    public static final Type<TrinityCraftConfirmCyclePayload> TYPE = new Type<>(Data_Energistics.id("trinity_craft_confirm_cycles"));
    /** Shared server/client stream codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityCraftConfirmCyclePayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityCraftConfirmCyclePayload::write, TrinityCraftConfirmCyclePayload::new);

    private static final int HEADER_RECORD = 0;
    private static final int MATERIAL_RECORD = 1;
    private static final int INVENTORY_USAGE_RECORD = 2;
    private static final int EXACT_SHORTAGE_RECORD = 3;
    private static final int UNRESOLVED_DEMAND_RECORD = 4;
    private static final int EXACT_PLAN_AMOUNTS_RECORD = 5;
    private static final int MAX_BIG_INTEGER_BYTES = 512;

    private final int containerId;
    private final long revision;
    private final int batchIndex;
    private final int batchCount;
    private final int totalRecordCount;
    private final List<TrinityCraftConfirmCycleRecord> records;

    private TrinityCraftConfirmCyclePayload(int containerId,
                                            long revision,
                                            int batchIndex,
                                            int batchCount,
                                            int totalRecordCount,
                                            List<TrinityCraftConfirmCycleRecord> records) {
        this.containerId = containerId;
        this.revision = revision;
        this.batchIndex = batchIndex;
        this.batchCount = batchCount;
        this.totalRecordCount = totalRecordCount;
        this.records = List.copyOf(records);
        validateMetadata();
    }

    private TrinityCraftConfirmCyclePayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readRecords(buffer));
    }

    /**
     * Flattens and splits one complete summary into deterministic protocol-sized batches.
     *
     * @param containerId owning confirmation menu
     * @param revision    synchronized planning revision
     * @param summary     complete immutable projection
     * @return immutable ordered packet sequence, including one empty packet for an empty summary
     */
    public static List<TrinityCraftConfirmCyclePayload> batches(int containerId,
                                                                long revision,
                                                                TrinityCraftingCycleSummary summary) {
        List<TrinityCraftConfirmCycleRecord> records = flatten(summary);
        int totalRecordCount = records.size();
        int batchCount = totalRecordCount == 0 ? 1 : ((totalRecordCount - 1) / MAX_RECORDS_PER_BATCH) + 1;
        ArrayList<TrinityCraftConfirmCyclePayload> payloads = new ArrayList<>(batchCount);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int fromIndex = batchIndex * MAX_RECORDS_PER_BATCH;
            int toIndex = Math.min(totalRecordCount, fromIndex + MAX_RECORDS_PER_BATCH);
            payloads.add(new TrinityCraftConfirmCyclePayload(
                    containerId,
                    revision,
                    batchIndex,
                    batchCount,
                    totalRecordCount,
                    records.subList(fromIndex, toIndex)));
        }
        return List.copyOf(payloads);
    }

    /** @return owning menu container id */
    public int containerId() {
        return this.containerId;
    }

    /** @return synchronized plan revision */
    public long revision() {
        return this.revision;
    }

    /** @return zero-based batch index */
    public int batchIndex() {
        return this.batchIndex;
    }

    /** @return complete batch count */
    public int batchCount() {
        return this.batchCount;
    }

    /** @return complete record count across all batches */
    public int totalRecordCount() {
        return this.totalRecordCount;
    }

    /** @return immutable records in this batch */
    public List<TrinityCraftConfirmCycleRecord> records() {
        return this.records;
    }

    @Override
    public Type<TrinityCraftConfirmCyclePayload> type() {
        return TYPE;
    }

    /** Schedules validation and delivery on the client game thread. */
    public static void handle(TrinityCraftConfirmCyclePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityCraftConfirmCycleClientHandler.receive(payload, context.player()));
    }

    private static List<TrinityCraftConfirmCycleRecord> flatten(TrinityCraftingCycleSummary summary) {
        int totalRecordCount = Math.addExact(
                Math.addExact(
                        Math.addExact(summary.cycles().size(), summary.inventoryUsageBasisPoints().size()),
                        summary.contributions().size()),
                Math.addExact(
                        Math.addExact(summary.exactShortages().size(), summary.unresolvedDemands().size()),
                        summary.exactPlanAmounts().size()));
        ArrayList<TrinityCraftConfirmCycleRecord> records = new ArrayList<>(totalRecordCount);
        summary.cycles().forEach(cycle -> records.add(new Header(cycle)));
        summary.inventoryUsageBasisPoints().forEach((key, basisPoints) -> records.add(new InventoryUsage(key, basisPoints)));
        summary.contributions().forEach(contribution -> records.add(new Material(contribution)));
        summary.exactShortages().forEach(shortage -> records.add(new ExactShortage(shortage)));
        summary.unresolvedDemands().forEach(unresolved -> records.add(new UnresolvedDemand(unresolved)));
        summary.exactPlanAmounts().forEach(amounts -> records.add(new ExactPlanAmounts(amounts)));
        return List.copyOf(records);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeVarLong(this.revision);
        buffer.writeVarInt(this.batchIndex);
        buffer.writeVarInt(this.batchCount);
        buffer.writeVarInt(this.totalRecordCount);
        buffer.writeVarInt(this.records.size());
        this.records.forEach(record -> writeRecord(buffer, record));
    }

    private static List<TrinityCraftConfirmCycleRecord> readRecords(RegistryFriendlyByteBuf buffer) {
        int recordCount = buffer.readVarInt();
        if (recordCount < 0 || recordCount > MAX_RECORDS_PER_BATCH) {
            throw new IllegalArgumentException("Trinity crafting confirmation batch record count is outside [0, " + MAX_RECORDS_PER_BATCH + "]: " + recordCount);
        }
        ArrayList<TrinityCraftConfirmCycleRecord> records = new ArrayList<>(recordCount);
        for (int index = 0; index < recordCount; index++) {
            records.add(readRecord(buffer));
        }
        return List.copyOf(records);
    }

    private static void writeRecord(RegistryFriendlyByteBuf buffer, TrinityCraftConfirmCycleRecord record) {
        switch (record) {
            case Header entry -> writeHeader(buffer, entry.value());
            case Material entry -> writeMaterial(buffer, entry.value());
            case InventoryUsage entry -> writeInventoryUsage(buffer, entry);
            case ExactShortage entry -> writeExactShortage(buffer, entry.value());
            case UnresolvedDemand entry -> writeUnresolvedDemand(buffer, entry.value());
            case ExactPlanAmounts entry -> writeExactPlanAmounts(buffer, entry.value());
        }
    }

    private static TrinityCraftConfirmCycleRecord readRecord(RegistryFriendlyByteBuf buffer) {
        return switch (buffer.readUnsignedByte()) {
            case HEADER_RECORD -> readHeader(buffer);
            case MATERIAL_RECORD -> readMaterial(buffer);
            case INVENTORY_USAGE_RECORD -> readInventoryUsage(buffer);
            case EXACT_SHORTAGE_RECORD -> readExactShortage(buffer);
            case UNRESOLVED_DEMAND_RECORD -> readUnresolvedDemand(buffer);
            case EXACT_PLAN_AMOUNTS_RECORD -> readExactPlanAmounts(buffer);
            default -> throw new IllegalArgumentException("Unknown Trinity crafting confirmation record type");
        };
    }

    private static void writeHeader(RegistryFriendlyByteBuf buffer, TrinityCraftingCycleHeader header) {
        buffer.writeByte(HEADER_RECORD);
        buffer.writeVarInt(header.blockIndex());
        buffer.writeVarInt(header.displayOrdinal());
        writeBigInteger(buffer, header.repetitions());
        writeBigInteger(buffer, header.patternExecutions());
        buffer.writeVarInt(header.stageCount());
        buffer.writeVarInt(header.patternTypeCount());
    }

    private static Header readHeader(RegistryFriendlyByteBuf buffer) {
        return new Header(new TrinityCraftingCycleHeader(
                buffer.readVarInt(),
                buffer.readVarInt(),
                readBigInteger(buffer),
                readBigInteger(buffer),
                buffer.readVarInt(),
                buffer.readVarInt()));
    }

    private static void writeMaterial(RegistryFriendlyByteBuf buffer,
                                      TrinityCraftingCycleMaterialContribution contribution) {
        buffer.writeByte(MATERIAL_RECORD);
        buffer.writeVarInt(contribution.blockIndex());
        buffer.writeVarInt(contribution.displayOrdinal());
        AEKey.STREAM_CODEC.encode(buffer, contribution.key());
        int roles = (contribution.input() ? 1 : 0) | (contribution.output() ? 2 : 0);
        buffer.writeByte(roles);
        writeBigInteger(buffer, contribution.minimumSeed());
        writeBigInteger(buffer, contribution.netChange());
    }

    private static Material readMaterial(RegistryFriendlyByteBuf buffer) {
        int blockIndex = buffer.readVarInt();
        int displayOrdinal = buffer.readVarInt();
        AEKey key = AEKey.STREAM_CODEC.decode(buffer);
        int roles = buffer.readUnsignedByte();
        if ((roles & ~3) != 0) {
            throw new IllegalArgumentException("Invalid Trinity crafting confirmation material roles: " + roles);
        }
        return new Material(new TrinityCraftingCycleMaterialContribution(
                blockIndex,
                displayOrdinal,
                key,
                (roles & 1) != 0,
                (roles & 2) != 0,
                readBigInteger(buffer),
                readBigInteger(buffer)));
    }

    private static void writeInventoryUsage(RegistryFriendlyByteBuf buffer, InventoryUsage entry) {
        buffer.writeByte(INVENTORY_USAGE_RECORD);
        AEKey.STREAM_CODEC.encode(buffer, entry.key());
        buffer.writeVarInt(entry.basisPoints());
    }

    private static InventoryUsage readInventoryUsage(RegistryFriendlyByteBuf buffer) {
        return new InventoryUsage(AEKey.STREAM_CODEC.decode(buffer), buffer.readVarInt());
    }

    private static void writeExactShortage(
                                           RegistryFriendlyByteBuf buffer,
                                           TrinityCraftingExactShortage shortage) {
        buffer.writeByte(EXACT_SHORTAGE_RECORD);
        AEKey.STREAM_CODEC.encode(buffer, shortage.key());
        writeBigInteger(buffer, shortage.required());
        writeBigInteger(buffer, shortage.available());
        writeBigInteger(buffer, shortage.missing());
    }

    private static ExactShortage readExactShortage(RegistryFriendlyByteBuf buffer) {
        return new ExactShortage(new TrinityCraftingExactShortage(
                AEKey.STREAM_CODEC.decode(buffer),
                readBigInteger(buffer),
                readBigInteger(buffer),
                readBigInteger(buffer)));
    }

    private static void writeUnresolvedDemand(
                                              RegistryFriendlyByteBuf buffer,
                                              TrinityCraftingUnresolvedDemand unresolved) {
        buffer.writeByte(UNRESOLVED_DEMAND_RECORD);
        AEKey.STREAM_CODEC.encode(buffer, unresolved.key());
        writeBigInteger(buffer, unresolved.amount());
    }

    private static UnresolvedDemand readUnresolvedDemand(RegistryFriendlyByteBuf buffer) {
        return new UnresolvedDemand(new TrinityCraftingUnresolvedDemand(
                AEKey.STREAM_CODEC.decode(buffer),
                readBigInteger(buffer)));
    }

    private static void writeExactPlanAmounts(
                                              RegistryFriendlyByteBuf buffer,
                                              TrinityCraftingExactPlanAmounts amounts) {
        buffer.writeByte(EXACT_PLAN_AMOUNTS_RECORD);
        AEKey.STREAM_CODEC.encode(buffer, amounts.key());
        writeBigInteger(buffer, amounts.missing());
        writeBigInteger(buffer, amounts.stored());
        writeBigInteger(buffer, amounts.crafting());
    }

    private static ExactPlanAmounts readExactPlanAmounts(RegistryFriendlyByteBuf buffer) {
        return new ExactPlanAmounts(new TrinityCraftingExactPlanAmounts(
                AEKey.STREAM_CODEC.decode(buffer),
                readBigInteger(buffer),
                readBigInteger(buffer),
                readBigInteger(buffer)));
    }

    private static void writeBigInteger(RegistryFriendlyByteBuf buffer, BigInteger value) {
        byte[] encoded = value.toByteArray();
        if (encoded.length > MAX_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException("Trinity crafting confirmation integer exceeds protocol limit: " + encoded.length);
        }
        buffer.writeByteArray(encoded);
    }

    private static BigInteger readBigInteger(RegistryFriendlyByteBuf buffer) {
        return new BigInteger(buffer.readByteArray(MAX_BIG_INTEGER_BYTES));
    }

    private void validateMetadata() {
        if (this.containerId < 0 || this.revision < 0L || this.batchCount <= 0 || this.batchIndex < 0 || this.batchIndex >= this.batchCount || this.totalRecordCount < 0) {
            throw new IllegalArgumentException("Invalid Trinity crafting confirmation batch metadata");
        }
        if (this.records.size() > MAX_RECORDS_PER_BATCH || this.records.size() > this.totalRecordCount) {
            throw new IllegalArgumentException("Invalid Trinity crafting confirmation batch size");
        }
        if (this.totalRecordCount == 0) {
            if (this.batchCount != 1) {
                throw new IllegalArgumentException("An empty Trinity crafting summary requires one empty batch");
            }
            return;
        }

        int minimumBatchCount = ((this.totalRecordCount - 1) / MAX_RECORDS_PER_BATCH) + 1;
        if (this.records.isEmpty() || this.batchCount < minimumBatchCount || this.batchCount > this.totalRecordCount) {
            throw new IllegalArgumentException("Trinity crafting confirmation batches cannot represent declared total");
        }
    }
}
