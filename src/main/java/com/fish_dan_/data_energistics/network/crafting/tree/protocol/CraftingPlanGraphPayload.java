package com.fish_dan_.data_energistics.network.crafting.tree.protocol;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphCycle;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphEdge;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphHeader;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphNode;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One bounded revision-scoped typed batch. No batch independently changes crafting or CPU state. */
public record CraftingPlanGraphPayload(int containerId, UUID sessionId, long revision, int batchIndex,
                                      int batchCount, int totalRecords, int totalBytes, int encodedBytes,
                                      List<CraftingPlanGraphRecord> records) implements CustomPacketPayload {
    public static final int RECORDS_PER_BATCH = 64;
    public static final int MAX_RECORDS = 262144;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_BATCH_BYTES = 512 * 1024;
    public static final Type<CraftingPlanGraphPayload> TYPE = new Type<>(Data_Energistics.id("crafting_plan_graph"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingPlanGraphPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CraftingPlanGraphPayload::write, CraftingPlanGraphPayload::read);

    public CraftingPlanGraphPayload {
        Objects.requireNonNull(sessionId);
        validateMetadata(containerId, revision, batchIndex, batchCount, totalRecords, totalBytes);
        records = List.copyOf(records);
        if (records.isEmpty() || records.size() > RECORDS_PER_BATCH || records.size() > totalRecords
                || encodedBytes <= 0 || encodedBytes > MAX_BATCH_BYTES || encodedBytes > totalBytes) {
            throw new IllegalArgumentException("Invalid graph batch size");
        }
    }

    public static List<CraftingPlanGraphPayload> batches(int containerId, UUID sessionId, long revision,
                                                        CraftingPlanGraph graph, RegistryAccess registries) {
        long count = 1L + graph.nodes().size() + graph.edges().size() + graph.cycles().size();
        if (count > MAX_RECORDS) throw new IllegalArgumentException("Crafting graph exceeds record limit");
        List<CraftingPlanGraphRecord> all = new ArrayList<>((int) count);
        all.add(new GraphHeader(graph.header(), graph.rootId()));
        graph.nodes().forEach(node -> all.add(new GraphNode(node)));
        graph.edges().forEach(edge -> all.add(new GraphEdge(edge)));
        graph.cycles().forEach(cycle -> all.add(new GraphCycle(cycle)));
        List<List<CraftingPlanGraphRecord>> groups = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        List<CraftingPlanGraphRecord> group = new ArrayList<>();
        int size = 0;
        int total = 0;
        RegistryFriendlyByteBuf scratch = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MAX_BATCH_BYTES), registries);
        try {
            for (CraftingPlanGraphRecord record : all) {
                scratch.clear();
                CraftingPlanGraphRecordCodec.write(scratch, record);
                int bytes = scratch.readableBytes();
                total = Math.addExact(total, bytes);
                if (total > MAX_BYTES) throw new IllegalArgumentException("Crafting graph exceeds byte limit");
                if (!group.isEmpty() && (group.size() == RECORDS_PER_BATCH || size + bytes > MAX_BATCH_BYTES)) {
                    groups.add(List.copyOf(group));
                    sizes.add(size);
                    group.clear();
                    size = 0;
                }
                group.add(record);
                size += bytes;
            }
        } finally {
            scratch.release();
        }
        if (!group.isEmpty()) {
            groups.add(List.copyOf(group));
            sizes.add(size);
        }
        List<CraftingPlanGraphPayload> batches = new ArrayList<>(groups.size());
        for (int index = 0; index < groups.size(); index++) {
            batches.add(new CraftingPlanGraphPayload(containerId, sessionId, revision, index, groups.size(),
                    (int) count, total, sizes.get(index), groups.get(index)));
        }
        return List.copyOf(batches);
    }

    private static void validateMetadata(int container, long revision, int index, int batches, int records, int bytes) {
        if (container < 0 || revision < 0 || records < 2 || records > MAX_RECORDS || bytes <= 0 || bytes > MAX_BYTES
                || batches <= 0 || batches > records || (long) batches * RECORDS_PER_BATCH < records
                || index < 0 || index >= batches || bytes < records) {
            throw new IllegalArgumentException("Invalid graph transfer metadata");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeUUID(this.sessionId);
        buffer.writeVarLong(this.revision);
        buffer.writeVarInt(this.batchIndex);
        buffer.writeVarInt(this.batchCount);
        buffer.writeVarInt(this.totalRecords);
        buffer.writeVarInt(this.totalBytes);
        buffer.writeVarInt(this.records.size());
        buffer.writeVarInt(this.encodedBytes);
        int start = buffer.writerIndex();
        this.records.forEach(record -> CraftingPlanGraphRecordCodec.write(buffer, record));
        if (buffer.writerIndex() - start != this.encodedBytes) {
            throw new IllegalArgumentException("Graph batch encoded byte size changed");
        }
    }

    private static CraftingPlanGraphPayload read(RegistryFriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        UUID session = buffer.readUUID();
        long revision = buffer.readVarLong();
        int index = buffer.readVarInt();
        int batches = buffer.readVarInt();
        int totalRecords = buffer.readVarInt();
        int totalBytes = buffer.readVarInt();
        validateMetadata(container, revision, index, batches, totalRecords, totalBytes);
        int count = buffer.readVarInt();
        int bytes = buffer.readVarInt();
        if (count <= 0 || count > RECORDS_PER_BATCH || count > totalRecords || bytes < count
                || bytes > MAX_BATCH_BYTES || bytes > totalBytes || bytes > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid graph batch body length");
        }
        RegistryFriendlyByteBuf body = new RegistryFriendlyByteBuf(buffer.readSlice(bytes), buffer.registryAccess());
        List<CraftingPlanGraphRecord> records = new ArrayList<>(count);
        for (int record = 0; record < count; record++) records.add(CraftingPlanGraphRecordCodec.read(body));
        if (body.isReadable()) throw new IllegalArgumentException("Trailing graph record bytes");
        return new CraftingPlanGraphPayload(container, session, revision, index, batches, totalRecords, totalBytes, bytes, records);
    }

    @Override
    public Type<CraftingPlanGraphPayload> type() { return TYPE; }

    public static void handle(CraftingPlanGraphPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var menu = context.player().containerMenu;
            if (menu.containerId == payload.containerId() && menu instanceof CraftingPlanGraphReceiver receiver) {
                receiver.receiveCraftingPlanGraph(payload);
            }
        });
    }
}
