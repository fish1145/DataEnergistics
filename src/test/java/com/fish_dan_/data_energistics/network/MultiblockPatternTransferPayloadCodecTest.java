package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.transfer.MultiblockPatternTransferRequest;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class MultiblockPatternTransferPayloadCodecTest {

    private static final String RECIPE_ID = "data_energistics:multiblock/trinity_data_core";
    private static final String CONTROLLER_ID = "data_energistics:trinity_data_core";
    private static final String STRUCTURE_NAME = "main";
    private static final String TIER_DOMAIN = "storage_core";

    @Test
    void streamCodecRoundTripPreservesTheCompleteRequestAndConsumesTheBuffer() {
        ResourceLocation controllerId = ResourceLocation.parse(CONTROLLER_ID);
        ProjectionFingerprint fingerprint = new ProjectionFingerprint(
                controllerId,
                73L,
                new JsonMultiBlockStructureKey(controllerId, STRUCTURE_NAME),
                2,
                List.of(3, 5),
                Map.of(TIER_DOMAIN, 4, "computation_core", 6),
                Map.of(new PreviewPredicateKey(0, 1, 2), 3, new PreviewPredicateKey(4, 5, 6), 7));
        MultiblockPatternTransferPayload original = new MultiblockPatternTransferPayload(
                41,
                ResourceLocation.parse(RECIPE_ID),
                fingerprint);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            MultiblockPatternTransferPayload.STREAM_CODEC.encode(buffer, original);

            assertEquals(original, MultiblockPatternTransferPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void streamCodecRejectsTrailingBytes() {
        MultiblockPatternTransferPayload original = validPayload();
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            MultiblockPatternTransferPayload.STREAM_CODEC.encode(buffer, original);
            buffer.writeByte(99);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> MultiblockPatternTransferPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsOversizedResourceLocationStructureAndTierStrings() {
        assertDecodeRejects(DecoderException.class, buffer -> {
            buffer.writeVarInt(1);
            buffer.writeUtf(
                    "data_energistics:" + "a".repeat(MultiblockPatternTransferPayloadCodec.MAX_RESOURCE_LOCATION_LENGTH),
                    Short.MAX_VALUE);
        });
        assertDecodeRejects(DecoderException.class, buffer -> {
            writeStructurePrefix(buffer, 1L);
            buffer.writeUtf(
                    "s".repeat(MultiblockPatternTransferPayloadCodec.MAX_STRUCTURE_NAME_LENGTH + 1),
                    Short.MAX_VALUE);
        });
        assertDecodeRejects(DecoderException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeUtf(
                    "t".repeat(MultiblockPatternTransferPayloadCodec.MAX_TIER_DOMAIN_LENGTH + 1),
                    Short.MAX_VALUE);
        });
    }

    @Test
    void decoderRejectsSelectionCountsOutsideTheirBounds() {
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(-1);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(MultiblockPatternTransferPayloadCodec.MAX_REPEAT_UNITS + 1);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(-1);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(MultiblockPatternTransferPayloadCodec.MAX_TIER_SELECTIONS + 1);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(-1);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(MultiblockPatternTransferPayloadCodec.MAX_CANDIDATE_SELECTIONS + 1);
        });
    }

    @Test
    void decoderRejectsDuplicateTierAndCandidateKeys() {
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(2);
            writeTier(buffer, TIER_DOMAIN, 1);
            writeTier(buffer, TIER_DOMAIN, 2);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeSelectionPrefix(buffer, 0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(2);
            writeCandidateFields(buffer, 1, 2, 3, 0);
            writeCandidateFields(buffer, 1, 2, 3, 1);
        });
    }

    @Test
    void decoderRejectsEveryNumericFieldOutsideItsLowerBound() {
        assertDecodeRejects(IllegalArgumentException.class, buffer -> buffer.writeVarInt(-1));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeRevisionPrefix(buffer);
            buffer.writeVarLong(-1L);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeStructurePrefix(buffer, 1L);
            buffer.writeUtf(STRUCTURE_NAME, Short.MAX_VALUE);
            buffer.writeVarInt(-1);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeRepeatValue(buffer, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeTierValue(buffer, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, -1, 0, 0, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, 0, -1, 0, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, 0, 0, -1, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, 0, 0, 0, -1));
    }

    @Test
    void decoderRejectsEveryNumericFieldOutsideItsUpperBound() {
        assertDecodeRejects(IllegalArgumentException.class, buffer -> buffer.writeVarInt(MultiblockPatternTransferRequest.MAX_CONTAINER_ID + 1));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeRevisionPrefix(buffer);
            buffer.writeVarLong(MultiblockPatternTransferPayloadCodec.MAX_DEFINITION_REVISION + 1L);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> {
            writeStructurePrefix(buffer, 1L);
            buffer.writeUtf(STRUCTURE_NAME, Short.MAX_VALUE);
            buffer.writeVarInt(MultiblockPatternTransferPayloadCodec.MAX_SELECTION_VALUE + 1);
        });
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeRepeatValue(buffer, MultiblockPatternTransferPayloadCodec.MAX_SELECTION_VALUE + 1));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeTierValue(buffer, MultiblockPatternTransferPayloadCodec.MAX_SELECTION_VALUE + 1));
        int tooLarge = MultiblockPatternTransferPayloadCodec.MAX_SELECTION_VALUE + 1;
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, tooLarge, 0, 0, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, 0, tooLarge, 0, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, 0, 0, tooLarge, 0));
        assertDecodeRejects(IllegalArgumentException.class, buffer -> writeCandidate(buffer, 0, 0, 0, tooLarge));
    }

    private static MultiblockPatternTransferPayload validPayload() {
        ResourceLocation controllerId = ResourceLocation.parse(CONTROLLER_ID);
        return new MultiblockPatternTransferPayload(
                1,
                ResourceLocation.parse(RECIPE_ID),
                new ProjectionFingerprint(
                        controllerId,
                        1L,
                        new JsonMultiBlockStructureKey(controllerId, STRUCTURE_NAME),
                        0,
                        List.of(1),
                        Map.of(TIER_DOMAIN, 1),
                        Map.of(new PreviewPredicateKey(0, 0, 0), 0)));
    }

    private static void writeRevisionPrefix(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(1);
        buffer.writeUtf(RECIPE_ID, Short.MAX_VALUE);
        buffer.writeUtf(CONTROLLER_ID, Short.MAX_VALUE);
    }

    private static void writeStructurePrefix(RegistryFriendlyByteBuf buffer, long revision) {
        writeRevisionPrefix(buffer);
        buffer.writeVarLong(revision);
        buffer.writeUtf(CONTROLLER_ID, Short.MAX_VALUE);
    }

    private static void writeSelectionPrefix(RegistryFriendlyByteBuf buffer, int variantIndex) {
        writeStructurePrefix(buffer, 1L);
        buffer.writeUtf(STRUCTURE_NAME, Short.MAX_VALUE);
        buffer.writeVarInt(variantIndex);
    }

    private static void writeRepeatValue(RegistryFriendlyByteBuf buffer, int value) {
        writeSelectionPrefix(buffer, 0);
        buffer.writeVarInt(1);
        buffer.writeVarInt(value);
    }

    private static void writeTierValue(RegistryFriendlyByteBuf buffer, int value) {
        writeSelectionPrefix(buffer, 0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        writeTier(buffer, TIER_DOMAIN, value);
    }

    private static void writeTier(RegistryFriendlyByteBuf buffer, String domain, int value) {
        buffer.writeUtf(domain, Short.MAX_VALUE);
        buffer.writeVarInt(value);
    }

    private static void writeCandidate(RegistryFriendlyByteBuf buffer,
                                       int sourceLayer,
                                       int y,
                                       int x,
                                       int candidateIndex) {
        writeSelectionPrefix(buffer, 0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        writeCandidateFields(buffer, sourceLayer, y, x, candidateIndex);
    }

    private static void writeCandidateFields(RegistryFriendlyByteBuf buffer,
                                             int sourceLayer,
                                             int y,
                                             int x,
                                             int candidateIndex) {
        buffer.writeVarInt(sourceLayer);
        buffer.writeVarInt(y);
        buffer.writeVarInt(x);
        buffer.writeVarInt(candidateIndex);
    }

    private static void assertDecodeRejects(
                                            Class<? extends Throwable> expectedType,
                                            Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            writer.accept(buffer);
            assertThrows(
                    expectedType,
                    () -> MultiblockPatternTransferPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /** Creates the registry-aware buffer used by the production payload codec. */
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
