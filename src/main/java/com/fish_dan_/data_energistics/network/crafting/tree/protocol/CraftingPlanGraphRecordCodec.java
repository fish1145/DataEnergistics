package com.fish_dan_.data_energistics.network.crafting.tree.protocol;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Cycle;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Header;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Kind;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphCycle;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphEdge;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphHeader;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphNode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded typed graph record codec; collection lengths are checked before allocation or iteration. */
final class CraftingPlanGraphRecordCodec {
    private CraftingPlanGraphRecordCodec() {}

    static void write(RegistryFriendlyByteBuf buffer, CraftingPlanGraphRecord record) {
        switch (record) {
            case GraphHeader value -> {
                buffer.writeByte(0);
                Header header = value.header();
                buffer.writeVarInt(value.rootId());
                AEKey.STREAM_CODEC.encode(buffer, header.target());
                amount(buffer, header.requested());
                amount(buffer, header.bytes());
                buffer.writeVarInt(header.kind().ordinal());
                buffer.writeVarInt(header.quantityMode().ordinal());
                buffer.writeVarLong(header.planningNanos());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, header.diagnostic());
            }
            case GraphNode value -> {
                if (value.node() instanceof Material node) {
                    buffer.writeByte(1);
                    buffer.writeVarInt(node.id());
                    AEKey.STREAM_CODEC.encode(buffer, node.key());
                    amount(buffer, node.required());
                    amount(buffer, node.stored());
                    amount(buffer, node.crafting());
                    amount(buffer, node.missing());
                    amount(buffer, node.unresolved());
                    buffer.writeVarInt(node.inventoryUsageBasisPoints());
                } else if (value.node() instanceof Process node) {
                    buffer.writeByte(2);
                    buffer.writeVarInt(node.id());
                    buffer.writeVarInt(node.stageIndex());
                    buffer.writeUtf(node.patternIdentity());
                    buffer.writeVarInt(node.variantOrdinal());
                    AEKey.STREAM_CODEC.encode(buffer, node.primaryOutput());
                    amount(buffer, node.executions());
                    buffer.writeBoolean(node.estimated());
                    ids(buffer, node.cycleIds());
                }
            }
            case GraphEdge value -> {
                buffer.writeByte(3);
                Edge edge = value.edge();
                buffer.writeVarInt(edge.id());
                buffer.writeVarInt(edge.source());
                buffer.writeVarInt(edge.target());
                buffer.writeVarInt(edge.role().ordinal());
                amount(buffer, edge.amount());
            }
            case GraphCycle value -> {
                buffer.writeByte(4);
                Cycle cycle = value.cycle();
                buffer.writeVarInt(cycle.id());
                buffer.writeVarInt(cycle.ordinal());
                ids(buffer, cycle.nodeIds());
                ids(buffer, cycle.stageOrder());
                amount(buffer, cycle.repetitions());
                amounts(buffer, cycle.minimumSeed());
                amounts(buffer, cycle.netChange());
            }
        }
    }

    static CraftingPlanGraphRecord read(RegistryFriendlyByteBuf buffer) {
        return switch (buffer.readUnsignedByte()) {
            case 0 -> {
                int root = buffer.readVarInt();
                Header header = new Header(AEKey.STREAM_CODEC.decode(buffer), amount(buffer), amount(buffer),
                        enumeration(buffer, Kind.values()), CraftingQuantityMode.fromOrdinal(buffer.readVarInt()),
                        buffer.readVarLong(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer));
                yield new GraphHeader(header, root);
            }
            case 1 -> new GraphNode(new Material(buffer.readVarInt(), AEKey.STREAM_CODEC.decode(buffer),
                    amount(buffer), amount(buffer), amount(buffer), amount(buffer), amount(buffer), buffer.readVarInt()));
            case 2 -> new GraphNode(new Process(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(),
                    buffer.readVarInt(), AEKey.STREAM_CODEC.decode(buffer), amount(buffer), buffer.readBoolean(), ids(buffer)));
            case 3 -> new GraphEdge(new Edge(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    enumeration(buffer, Role.values()), amount(buffer)));
            case 4 -> new GraphCycle(new Cycle(buffer.readVarInt(), buffer.readVarInt(), ids(buffer), ids(buffer),
                    amount(buffer), amounts(buffer), amounts(buffer)));
            default -> throw new IllegalArgumentException("Unknown graph record type");
        };
    }

    private static <T> T enumeration(RegistryFriendlyByteBuf buffer, T[] values) {
        int index = buffer.readVarInt();
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("Invalid graph enum ordinal");
        return values[index];
    }

    private static void amount(RegistryFriendlyByteBuf buffer, BigInteger amount) {
        buffer.writeByteArray(TrinityBigIntegerEncoding.encode(amount, "plan graph amount"));
    }
    private static BigInteger amount(RegistryFriendlyByteBuf buffer) {
        return TrinityBigIntegerEncoding.decode(buffer.readByteArray(TrinityBigIntegerEncoding.MAX_BYTES), "plan graph amount");
    }
    private static int count(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > CraftingPlanGraphPayload.MAX_RECORDS || count > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid graph collection size");
        }
        return count;
    }
    private static void ids(RegistryFriendlyByteBuf buffer, List<Integer> ids) {
        buffer.writeVarInt(ids.size());
        ids.forEach(buffer::writeVarInt);
    }
    private static List<Integer> ids(RegistryFriendlyByteBuf buffer) {
        int count = count(buffer);
        List<Integer> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) ids.add(buffer.readVarInt());
        return ids;
    }
    private static void amounts(RegistryFriendlyByteBuf buffer, Map<AEKey, BigInteger> amounts) {
        buffer.writeVarInt(amounts.size());
        amounts.forEach((key, value) -> {
            AEKey.STREAM_CODEC.encode(buffer, key);
            amount(buffer, value);
        });
    }
    private static Map<AEKey, BigInteger> amounts(RegistryFriendlyByteBuf buffer) {
        int count = count(buffer);
        Map<AEKey, BigInteger> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            AEKey key = AEKey.STREAM_CODEC.decode(buffer);
            if (values.putIfAbsent(key, amount(buffer)) != null) {
                throw new IllegalArgumentException("Duplicate cycle amount key");
            }
        }
        return values;
    }
}
