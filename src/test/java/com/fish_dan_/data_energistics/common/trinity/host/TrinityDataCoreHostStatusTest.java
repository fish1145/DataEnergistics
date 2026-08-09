package com.fish_dan_.data_energistics.common.trinity.host;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreHostStatus.StructureStatus;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.connection.ConnectionType;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrinityDataCoreHostStatusTest {

    private static final UUID HOST_ID = UUID.fromString("3a63e81f-360d-46b1-b00f-c2fe61f1811a");

    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecRoundTripPreservesAllHostAndStructureFields() {
        TrinityDataCoreHostStatus expected = populatedStatus();

        JsonElement encoded = TrinityDataCoreHostStatus.CODEC
                .encodeStart(JsonOps.INSTANCE, expected)
                .getOrThrow();
        TrinityDataCoreHostStatus decoded = TrinityDataCoreHostStatus.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(expected, decoded);
    }

    @Test
    void streamCodecRoundTripConsumesTheCompleteSnapshot() {
        TrinityDataCoreHostStatus expected = populatedStatus();
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            TrinityDataCoreHostStatus.STREAM_CODEC.encode(buffer, expected);
            TrinityDataCoreHostStatus decoded = TrinityDataCoreHostStatus.STREAM_CODEC.decode(buffer);

            assertEquals(expected, decoded);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void emptySnapshotIsCompleteAndHasNoFailure() {
        TrinityDataCoreHostStatus empty = TrinityDataCoreHostStatus.EMPTY;

        assertEquals(Optional.empty(), empty.hostId());
        assertFalse(empty.online());
        assertEquals(StructureStatus.EMPTY, empty.mainStructure());
        assertEquals(StructureStatus.EMPTY, empty.cpuStructure());
        assertEquals(StructureStatus.EMPTY, empty.craftingStructure());
        assertEquals(Optional.empty(), empty.craftingTarget());
        assertFalse(empty.hasAnyFailure());
    }

    @Test
    void validationRejectsIncoherentCountsAndFailurePositions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StructureStatus(false, -1, "", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StructureStatus(false, 0, "", "1, 2, 3"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreHostStatus(
                        Optional.empty(),
                        true,
                        StructureStatus.EMPTY,
                        StructureStatus.EMPTY,
                        StructureStatus.EMPTY,
                        0,
                        2,
                        3,
                        0L,
                        0,
                        Optional.empty()));
    }

    private static TrinityDataCoreHostStatus populatedStatus() {
        TrinityDataCoreHostStatus status = new TrinityDataCoreHostStatus(
                Optional.of(HOST_ID),
                true,
                new StructureStatus(true, 72, "", ""),
                new StructureStatus(true, 24, "CPU casing mismatch", "12, 64, -8"),
                new StructureStatus(false, 18, "Crafting core missing", "13, 65, -8"),
                3,
                8,
                2,
                4_194_304L,
                16,
                Optional.of(Component.literal("Quantum Processor")));
        assertTrue(status.hasAnyFailure());
        return status;
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
