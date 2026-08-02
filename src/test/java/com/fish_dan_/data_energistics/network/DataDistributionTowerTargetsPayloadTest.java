package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.blockentity.tower.network.TowerDeviceKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.TowerVirtualDeviceState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DataDistributionTowerTargetsPayloadTest {

    @Test
    void codecRoundTripPreservesEveryStructuredTargetField() {
        DataDistributionTowerTargetEntry entry = new DataDistributionTowerTargetEntry(
                ResourceLocation.parse("minecraft:diamond_block"),
                "Disabled AE target",
                3,
                ResourceLocation.parse("minecraft:the_nether"),
                new BlockPos(-12, 71, 4096),
                TargetKind.AE,
                TargetTransferMode.DISABLED,
                new TargetTransferInfo(
                        17,
                        true,
                        true,
                        4_000_000_000L,
                        8_000_000_000L,
                        true,
                        false,
                        24,
                        TowerVirtualDeviceState.WAITING_CHANNEL,
                        "CHANNEL_UNAVAILABLE",
                        ResourceLocation.parse("minecraft:overworld"),
                        new BlockPos(4, 65, 9),
                        new TowerDeviceKey(
                                ResourceLocation.parse("minecraft:the_nether"),
                                null,
                                2,
                                "example.LogicalDevice",
                                3)));
        DataDistributionTowerTargetsPayload original = new DataDistributionTowerTargetsPayload(
                42,
                9001L,
                0,
                1,
                1,
                List.of(entry));

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.OTHER);
        try {
            DataDistributionTowerTargetsPayload.STREAM_CODEC.encode(buffer, original);
            DataDistributionTowerTargetsPayload decoded = DataDistributionTowerTargetsPayload.STREAM_CODEC.decode(buffer);

            assertEquals(original, decoded);
            assertEquals(entry.itemId(), decoded.entries().getFirst().itemId());
            assertEquals(entry.displayName(), decoded.entries().getFirst().displayName());
            assertEquals(entry.dimensionId(), decoded.entries().getFirst().dimensionId());
            assertEquals(entry.pos(), decoded.entries().getFirst().pos());
            assertEquals(entry.kind(), decoded.entries().getFirst().kind());
            assertEquals(entry.transferMode(), decoded.entries().getFirst().transferMode());
            assertEquals(entry.transferInfo(), decoded.entries().getFirst().transferInfo());
            assertTrue(decoded.entries().getFirst().transferInfo().logicalDevice());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void seventyTargetsUseTwoBatchesAndPublishAtomically() {
        List<DataDistributionTowerTargetEntry> entries = entries(70);
        List<DataDistributionTowerTargetsPayload> payloads = DataDistributionTowerTargetsPayload.batches(7, 11L, entries);
        DataDistributionTowerTargetsAssembler assembler = new DataDistributionTowerTargetsAssembler();

        assertEquals(2, payloads.size());
        assertEquals(64, payloads.getFirst().entries().size());
        assertEquals(6, payloads.getLast().entries().size());
        assertEquals(70, payloads.getFirst().totalCount());
        assertTrue(assembler.accept(payloads.getFirst()).isEmpty());

        DataDistributionTowerTargetsSnapshot snapshot = assembler.accept(payloads.getLast()).orElseThrow();
        assertEquals(7, snapshot.containerId());
        assertEquals(11L, snapshot.revision());
        assertEquals(70, snapshot.totalCount());
        assertEquals(entries, snapshot.entries());
        assertEquals("Target 69", snapshot.entries().get(69).displayName());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.entries().add(entry(71)));
    }

    @Test
    void outOfOrderAndDuplicateBatchesRemainIdempotent() {
        List<DataDistributionTowerTargetEntry> entries = entries(70);
        List<DataDistributionTowerTargetsPayload> payloads = DataDistributionTowerTargetsPayload.batches(3, 20L, entries);
        DataDistributionTowerTargetsAssembler assembler = new DataDistributionTowerTargetsAssembler();

        assertTrue(assembler.accept(payloads.getLast()).isEmpty());
        assertTrue(assembler.accept(payloads.getLast()).isEmpty());
        DataDistributionTowerTargetsSnapshot snapshot = assembler.accept(payloads.getFirst()).orElseThrow();

        assertEquals(entries, snapshot.entries());
        assertTrue(assembler.accept(payloads.getFirst()).isEmpty());
        assertTrue(assembler.accept(payloads.getLast()).isEmpty());
    }

    @Test
    void newerRevisionSupersedesIncompleteWorkAndOlderRevisionIsDiscarded() {
        List<DataDistributionTowerTargetsPayload> newer = DataDistributionTowerTargetsPayload.batches(5, 30L, entries(70));
        DataDistributionTowerTargetsPayload older = DataDistributionTowerTargetsPayload.batches(5, 29L, List.of(entry(999))).getFirst();
        DataDistributionTowerTargetsAssembler assembler = new DataDistributionTowerTargetsAssembler();

        assertTrue(assembler.accept(newer.getLast()).isEmpty());
        assertTrue(assembler.accept(older).isEmpty());
        DataDistributionTowerTargetsSnapshot snapshot = assembler.accept(newer.getFirst()).orElseThrow();

        assertEquals(30L, snapshot.revision());
        assertEquals(entries(70), snapshot.entries());
    }

    @Test
    void missingBatchNeverPublishesAndContainersDoNotShareAssemblyState() {
        List<DataDistributionTowerTargetsPayload> firstContainer = DataDistributionTowerTargetsPayload.batches(1, 4L, entries(70));
        DataDistributionTowerTargetsPayload secondContainer = DataDistributionTowerTargetsPayload.batches(2, 4L, List.of(entry(200))).getFirst();
        DataDistributionTowerTargetsAssembler assembler = new DataDistributionTowerTargetsAssembler();

        assertTrue(assembler.accept(firstContainer.getFirst()).isEmpty());
        DataDistributionTowerTargetsSnapshot secondSnapshot = assembler.accept(secondContainer).orElseThrow();
        assertEquals(2, secondSnapshot.containerId());
        assertEquals(List.of(entry(200)), secondSnapshot.entries());

        DataDistributionTowerTargetsSnapshot firstSnapshot = assembler.accept(firstContainer.getLast()).orElseThrow();
        assertEquals(1, firstSnapshot.containerId());
        assertEquals(entries(70), firstSnapshot.entries());
    }

    @Test
    void emptySnapshotPublishesAndConflictingDuplicateBatchFailsFast() {
        DataDistributionTowerTargetsPayload empty = DataDistributionTowerTargetsPayload.batches(9, 1L, List.of()).getFirst();
        DataDistributionTowerTargetsAssembler assembler = new DataDistributionTowerTargetsAssembler();

        DataDistributionTowerTargetsSnapshot snapshot = assembler.accept(empty).orElseThrow();
        assertEquals(0, snapshot.totalCount());
        assertTrue(snapshot.entries().isEmpty());

        DataDistributionTowerTargetsPayload first = new DataDistributionTowerTargetsPayload(
                10, 2L, 0, 2, 2, List.of(entry(1)));
        DataDistributionTowerTargetsPayload conflicting = new DataDistributionTowerTargetsPayload(
                10, 2L, 0, 2, 2, List.of(entry(2)));
        assertTrue(assembler.accept(first).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(conflicting));
    }

    @Test
    void payloadRejectsOversizedBatch() {
        List<DataDistributionTowerTargetEntry> oversized = entries(65);

        assertThrows(IllegalArgumentException.class, () -> new DataDistributionTowerTargetsPayload(
                1,
                1L,
                0,
                1,
                65,
                oversized));
    }

    private static List<DataDistributionTowerTargetEntry> entries(int count) {
        ArrayList<DataDistributionTowerTargetEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(entry(index));
        }
        return List.copyOf(entries);
    }

    private static DataDistributionTowerTargetEntry entry(int index) {
        TargetKind kind = index % 2 == 0 ? TargetKind.AE : TargetKind.FE;
        TargetTransferMode mode = index % 3 == 0 ? TargetTransferMode.DISABLED : TargetTransferMode.AUTO;
        return new DataDistributionTowerTargetEntry(
                ResourceLocation.parse("minecraft:" + (index % 2 == 0 ? "stone" : "redstone_block")),
                "Target " + index,
                (index % 4) + 1,
                ResourceLocation.parse(index % 2 == 0 ? "minecraft:overworld" : "minecraft:the_end"),
                new BlockPos(index, 64 + index, -index),
                kind,
                mode,
                new TargetTransferInfo(
                        index,
                        kind == TargetKind.AE,
                        kind == TargetKind.FE,
                        index * 10_000L,
                        index * 20_000L,
                        index % 2 == 0,
                        index % 2 != 0));
    }
}
