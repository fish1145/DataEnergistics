package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCpuContribution;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityOpenCpuStatusPayloadTest {

    @Test
    void codecRoundTripsStableHostAndCpuIdentity() {
        TrinityOpenCpuStatusPayload payload = new TrinityOpenCpuStatusPayload(
                73,
                UUID.fromString("bbad63ab-cf30-48dc-a72d-f690797bc4ac"),
                12);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            TrinityOpenCpuStatusPayload.STREAM_CODEC.encode(buffer, payload);

            assertEquals(payload, TrinityOpenCpuStatusPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecRejectsInvalidBoundsAndTrailingBytes() {
        UUID hostId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TrinityOpenCpuStatusPayload(-1, hostId, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrinityOpenCpuStatusPayload(1, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrinityOpenCpuStatusPayload(1, hostId, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityOpenCpuStatusPayload(
                        1,
                        hostId,
                        TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT + 1));

        RegistryFriendlyByteBuf trailing = buffer();
        try {
            TrinityOpenCpuStatusPayload.STREAM_CODEC.encode(
                    trailing,
                    new TrinityOpenCpuStatusPayload(1, hostId, 0));
            trailing.writeByte(99);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> TrinityOpenCpuStatusPayload.STREAM_CODEC.decode(trailing));
        } finally {
            trailing.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
