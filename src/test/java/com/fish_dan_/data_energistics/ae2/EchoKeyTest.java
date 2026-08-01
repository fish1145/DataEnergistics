package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class EchoKeyTest {

    @Test
    void componentCodecRoundTripsTheStatelessSingletonWithoutPayload() {
        var encoded = EchoKey.CODEC.encodeStart(JsonOps.INSTANCE, EchoKey.of()).getOrThrow();
        EchoKey decoded = EchoKey.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(new JsonObject(), encoded);
        assertSame(EchoKey.of(), decoded);
    }

    @Test
    void nbtAndTypePacketPayloadsAreEmpty() {
        assertTrue(EchoKey.of().toTag(RegistryAccess.EMPTY).isEmpty());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.OTHER);
        try {
            EchoKey.of().writeToPacket(buffer);
            assertEquals(0, buffer.readableBytes());
            assertSame(EchoKey.of(), EchoKeyType.TYPE.readFromPacket(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void exposesEightUnitsPerByteAndOneUnitPerOperationAccounting() {
        assertEquals(8, EchoKeyType.TYPE.getAmountPerByte());
        assertEquals(1, EchoKeyType.TYPE.getAmountPerOperation());
    }

    @Test
    void centralizedCustomKeyCatalogIncludesEcho() {
        assertEquals(
                List.of(DataFlowKeyType.TYPE, DataKeyType.TYPE, EchoKeyType.TYPE),
                ModAE2Keys.types());
        assertEquals(
                List.of(DataFlowKey.of(), DataKey.of(), EchoKey.of()),
                ModAE2Keys.keys());
        assertTrue(ModAE2Keys.isCustomType(EchoKeyType.TYPE));
        assertTrue(ModAE2Keys.isCustomKey(EchoKey.of()));
        assertFalse(ModAE2Keys.isCustomType(null));
        assertFalse(ModAE2Keys.isCustomKey(null));
    }
}
