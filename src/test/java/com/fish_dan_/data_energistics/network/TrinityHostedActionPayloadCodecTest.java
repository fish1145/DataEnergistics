package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityHostedActionPayloadCodecTest {

    @Test
    void allPayloadsRoundTripAndConsumeTheirCompleteBuffers() {
        TrinityHostedRefundPayload refund = new TrinityHostedRefundPayload(41, 7L, 3L);
        RegistryFriendlyByteBuf refundBuffer = buffer();
        try {
            TrinityHostedRefundPayload.STREAM_CODEC.encode(refundBuffer, refund);
            assertEquals(refund, TrinityHostedRefundPayload.STREAM_CODEC.decode(refundBuffer));
            assertEquals(0, refundBuffer.readableBytes());
        } finally {
            refundBuffer.release();
        }

        TrinityHostedAutoBuildPayload autoBuild = new TrinityHostedAutoBuildPayload(
                42,
                9L,
                5L,
                submission(Map.of(), true));
        RegistryFriendlyByteBuf autoBuildBuffer = buffer();
        try {
            TrinityHostedAutoBuildPayload.STREAM_CODEC.encode(autoBuildBuffer, autoBuild);
            assertEquals(autoBuild, TrinityHostedAutoBuildPayload.STREAM_CODEC.decode(autoBuildBuffer));
            assertEquals(0, autoBuildBuffer.readableBytes());
        } finally {
            autoBuildBuffer.release();
        }

        TrinityHostedActionResponsePayload response = new TrinityHostedActionResponsePayload(
                42,
                new TrinityHostedActionResult(
                        TrinityDataCoreHostUiKeys.AUTO_BUILD,
                        9L,
                        5L,
                        TrinityHostedActionStatus.COMPLETED));
        RegistryFriendlyByteBuf responseBuffer = buffer();
        try {
            TrinityHostedActionResponsePayload.STREAM_CODEC.encode(responseBuffer, response);
            assertEquals(response, TrinityHostedActionResponsePayload.STREAM_CODEC.decode(responseBuffer));
            assertEquals(0, responseBuffer.readableBytes());
        } finally {
            responseBuffer.release();
        }
    }

    @Test
    void decodersRejectInvalidBoundsAndTrailingBytes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityHostedRefundPayload(1, 0L, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityHostedRefundPayload(1, 1L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityHostedRefundPayload(
                        TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID + 1,
                        1L,
                        1L));

        RegistryFriendlyByteBuf trailing = buffer();
        try {
            TrinityHostedRefundPayload.STREAM_CODEC.encode(
                    trailing,
                    new TrinityHostedRefundPayload(1, 1L, 1L));
            trailing.writeByte(99);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TrinityHostedRefundPayload.STREAM_CODEC.decode(trailing));
        } finally {
            trailing.release();
        }

        RegistryFriendlyByteBuf tooManyRepeats = autoBuildPrefix();
        try {
            tooManyRepeats.writeVarInt(65);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TrinityHostedAutoBuildPayload.STREAM_CODEC.decode(tooManyRepeats));
        } finally {
            tooManyRepeats.release();
        }
    }

    @Test
    void decoderRejectsDuplicateTierAndCandidateKeys() {
        RegistryFriendlyByteBuf duplicateTier = autoBuildPrefix();
        try {
            duplicateTier.writeVarInt(1);
            duplicateTier.writeVarInt(1);
            duplicateTier.writeVarInt(2);
            duplicateTier.writeUtf(TrinityAutoBuildBlockMap.STORAGE_CORE, 128);
            duplicateTier.writeVarInt(1);
            duplicateTier.writeUtf(TrinityAutoBuildBlockMap.STORAGE_CORE, 128);
            duplicateTier.writeVarInt(2);
            duplicateTier.writeVarInt(0);
            duplicateTier.writeBoolean(true);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TrinityHostedAutoBuildPayload.STREAM_CODEC.decode(duplicateTier));
        } finally {
            duplicateTier.release();
        }

        RegistryFriendlyByteBuf duplicateCandidate = autoBuildPrefix();
        try {
            duplicateCandidate.writeVarInt(1);
            duplicateCandidate.writeVarInt(1);
            duplicateCandidate.writeVarInt(1);
            duplicateCandidate.writeUtf(TrinityAutoBuildBlockMap.STORAGE_CORE, 128);
            duplicateCandidate.writeVarInt(1);
            duplicateCandidate.writeVarInt(2);
            writeCandidate(duplicateCandidate, 0, 1, 2, 0);
            writeCandidate(duplicateCandidate, 0, 1, 2, 1);
            duplicateCandidate.writeBoolean(true);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TrinityHostedAutoBuildPayload.STREAM_CODEC.decode(duplicateCandidate));
        } finally {
            duplicateCandidate.release();
        }
    }

    private static RegistryFriendlyByteBuf autoBuildPrefix() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeVarInt(1);
        buffer.writeVarLong(1L);
        buffer.writeVarLong(1L);
        buffer.writeUtf(ModVerticalMultiBlocks.trinityDataCoreId().toString(), 256);
        buffer.writeVarLong(0L);
        buffer.writeUtf(ModVerticalMultiBlocks.trinityDataCoreId().toString(), 256);
        buffer.writeUtf(JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME, 128);
        buffer.writeVarInt(0);
        return buffer;
    }

    private static void writeCandidate(RegistryFriendlyByteBuf buffer,
                                       int sourceLayer,
                                       int y,
                                       int x,
                                       int candidateIndex) {
        buffer.writeVarInt(sourceLayer);
        buffer.writeVarInt(y);
        buffer.writeVarInt(x);
        buffer.writeVarInt(candidateIndex);
    }

    private static TrinityAutoBuildSubmission submission(Map<PreviewPredicateKey, Integer> candidates,
                                                         boolean buildRequested) {
        return new TrinityAutoBuildSubmission(
                new ProjectionFingerprint(
                        ModVerticalMultiBlocks.trinityDataCoreId(),
                        0L,
                        ModVerticalMultiBlocks.trinityDataCoreMainKey(),
                        0,
                        List.of(1),
                        Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 1),
                        candidates),
                buildRequested);
    }

    /** Creates the registry-aware buffer used by production payload codecs. */
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
