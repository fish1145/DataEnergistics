package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildDefinitionBundle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded menu-opening codec for the server-issued Trinity automatic-build definition snapshot.
 */
public final class TrinityAutoBuildDefinitionBundleCodec {

    private TrinityAutoBuildDefinitionBundleCodec() {}

    /**
     * Reads the complete server definition snapshot and rejects duplicates or oversized sources.
     */
    public static TrinityAutoBuildDefinitionBundle read(RegistryFriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        if (revision < 0L) {
            throw new IllegalArgumentException("Trinity auto-build definition revision cannot be negative: " + revision);
        }
        int definitionCount = buffer.readVarInt();
        if (definitionCount < 1 || definitionCount > TrinityAutoBuildDefinitionBundle.MAX_DEFINITION_COUNT) {
            throw new IllegalArgumentException("Trinity auto-build definition bundle requires between 1 and " +
                    TrinityAutoBuildDefinitionBundle.MAX_DEFINITION_COUNT + " sources, got " + definitionCount);
        }
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        int totalBytes = 0;
        for (int index = 0; index < definitionCount; index++) {
            ResourceLocation definitionId = buffer.readResourceLocation();
            byte[] sourceBytes = buffer.readByteArray(TrinityAutoBuildDefinitionBundle.MAX_DEFINITION_BYTES);
            totalBytes = Math.addExact(totalBytes, sourceBytes.length);
            if (totalBytes > TrinityAutoBuildDefinitionBundle.MAX_TOTAL_DEFINITION_BYTES) {
                throw new IllegalArgumentException("Trinity auto-build definitions exceed " +
                        TrinityAutoBuildDefinitionBundle.MAX_TOTAL_DEFINITION_BYTES + " combined UTF-8 bytes");
            }
            String previous = sources.put(definitionId, decodeUtf8(sourceBytes, definitionId));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Trinity auto-build definition: " + definitionId);
            }
        }
        return new TrinityAutoBuildDefinitionBundle(revision, sources);
    }

    /**
     * Writes the already validated ordered definition snapshot.
     */
    public static void write(RegistryFriendlyByteBuf buffer, TrinityAutoBuildDefinitionBundle bundle) {
        buffer.writeVarLong(bundle.definitionRevision());
        buffer.writeVarInt(bundle.definitionSources().size());
        for (Map.Entry<ResourceLocation, String> entry : bundle.definitionSources().entrySet()) {
            buffer.writeResourceLocation(entry.getKey());
            byte[] sourceBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            buffer.writeByteArray(sourceBytes);
        }
    }

    private static String decodeUtf8(byte[] bytes, ResourceLocation definitionId) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "Trinity auto-build definition is not valid UTF-8: " + definitionId,
                    exception);
        }
    }
}
