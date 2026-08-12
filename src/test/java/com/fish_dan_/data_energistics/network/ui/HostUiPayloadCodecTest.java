package com.fish_dan_.data_energistics.network.ui;

import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiResponse;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiResponseStatus;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class HostUiPayloadCodecTest {

    private static final HostUiKey MAIN = new HostUiKey(
            ResourceLocation.fromNamespaceAndPath("data_energistics", "trinity/main"));

    @Test
    void requestAndResponseRoundTripConsumeTheirCompleteBuffers() {
        HostUiRequest request = new HostUiRequest(HostUiOperation.OPEN, MAIN, 37L);
        HostUiRequestPayload requestPayload = new HostUiRequestPayload(12, request);
        RegistryFriendlyByteBuf requestBuffer = buffer();
        try {
            HostUiRequestPayload.STREAM_CODEC.encode(requestBuffer, requestPayload);
            assertEquals(requestPayload, HostUiRequestPayload.STREAM_CODEC.decode(requestBuffer));
            assertEquals(0, requestBuffer.readableBytes());
        } finally {
            requestBuffer.release();
        }

        HostUiResponsePayload responsePayload = new HostUiResponsePayload(
                12,
                HostUiResponse.rejected(request, HostUiResponseStatus.MEMBERSHIP_MISMATCH));
        RegistryFriendlyByteBuf responseBuffer = buffer();
        try {
            HostUiResponsePayload.STREAM_CODEC.encode(responseBuffer, responsePayload);
            assertEquals(responsePayload, HostUiResponsePayload.STREAM_CODEC.decode(responseBuffer));
            assertEquals(0, responseBuffer.readableBytes());
        } finally {
            responseBuffer.release();
        }
    }

    @Test
    void requestDecodeRejectsUnknownOperationAndNonPositiveSequence() {
        RegistryFriendlyByteBuf unknownOperation = buffer();
        try {
            unknownOperation.writeVarInt(1);
            unknownOperation.writeVarInt(99);
            unknownOperation.writeResourceLocation(MAIN.id());
            unknownOperation.writeVarLong(1L);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> HostUiRequestPayload.STREAM_CODEC.decode(unknownOperation));
        } finally {
            unknownOperation.release();
        }

        RegistryFriendlyByteBuf invalidSequence = buffer();
        try {
            invalidSequence.writeVarInt(1);
            invalidSequence.writeVarInt(HostUiOperation.OPEN.networkId());
            invalidSequence.writeResourceLocation(MAIN.id());
            invalidSequence.writeVarLong(0L);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> HostUiRequestPayload.STREAM_CODEC.decode(invalidSequence));
        } finally {
            invalidSequence.release();
        }

        RegistryFriendlyByteBuf oversizedKey = buffer();
        try {
            oversizedKey.writeVarInt(1);
            oversizedKey.writeVarInt(HostUiOperation.OPEN.networkId());
            oversizedKey.writeUtf(
                    "data_energistics:" + "a".repeat(HostUiKey.MAX_NETWORK_LENGTH),
                    Short.MAX_VALUE);
            oversizedKey.writeVarLong(1L);
            assertThrows(
                    DecoderException.class,
                    () -> HostUiRequestPayload.STREAM_CODEC.decode(oversizedKey));
        } finally {
            oversizedKey.release();
        }
    }

    @Test
    void responseDecodeRejectsUnknownStatusAndEnvelopeRejectsNegativeContainer() {
        RegistryFriendlyByteBuf unknownStatus = buffer();
        try {
            unknownStatus.writeVarInt(1);
            unknownStatus.writeVarInt(HostUiOperation.CLOSE.networkId());
            unknownStatus.writeResourceLocation(MAIN.id());
            unknownStatus.writeVarLong(4L);
            unknownStatus.writeVarInt(99);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> HostUiResponsePayload.STREAM_CODEC.decode(unknownStatus));
        } finally {
            unknownStatus.release();
        }

        HostUiRequest request = new HostUiRequest(HostUiOperation.CLOSE, MAIN, 4L);
        assertThrows(IllegalArgumentException.class, () -> new HostUiRequestPayload(-1, request));
        assertThrows(
                IllegalArgumentException.class,
                () -> new HostUiResponsePayload(-1, HostUiResponse.accepted(request)));
    }

    /** Creates the same registry-aware buffer used by production payload codecs. */
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
