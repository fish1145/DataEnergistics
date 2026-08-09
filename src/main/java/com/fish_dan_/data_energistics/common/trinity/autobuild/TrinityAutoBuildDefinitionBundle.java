package com.fish_dan_.data_energistics.common.trinity.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.loading.MdlibJsonMultiBlockDefinitionLoader;
import com.fish_dan_.data_energistics.common.multiblock.json.registry.JsonMultiBlockDefinitionRegistrySnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.preview.TrinityMultiblockPreviewSpecFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-issued Trinity definition generation and the exact JSON sources used to render and submit automatic builds.
 *
 * <p>
 * A definition registry revision is deliberately local to one process. Sending the server's source snapshot together
 * with that revision lets a dedicated client build an equivalent preview without comparing its unrelated local
 * registry generation. A later server reload still invalidates the captured generation through the existing strict
 * submission check.
 * </p>
 *
 * @param definitionRevision server registry generation captured atomically for this menu lifecycle
 * @param definitionSources  ordered logical definition ids and their authoritative UTF-8 JSON sources
 */
public record TrinityAutoBuildDefinitionBundle(long definitionRevision,
                                               Map<ResourceLocation, String> definitionSources) {

    /**
     * Maximum UTF-8 bytes accepted for one Trinity definition in menu-opening data.
     */
    public static final int MAX_DEFINITION_BYTES = 256 * 1024;
    /**
     * Maximum combined UTF-8 bytes accepted for one complete menu definition snapshot.
     */
    public static final int MAX_TOTAL_DEFINITION_BYTES = 512 * 1024;
    /**
     * Upper bound for future named Trinity structures carried by one menu opening.
     */
    public static final int MAX_DEFINITION_COUNT = 16;

    /**
     * Copies and validates the exact bounded schema before it can reach a menu or network codec.
     */
    public TrinityAutoBuildDefinitionBundle {
        if (definitionRevision < 0L) {
            throw new IllegalArgumentException(
                    "Trinity auto-build definition revision cannot be negative: " + definitionRevision);
        }
        if (definitionSources.isEmpty() || definitionSources.size() > MAX_DEFINITION_COUNT) {
            throw new IllegalArgumentException("Trinity auto-build definition bundle requires between 1 and " +
                    MAX_DEFINITION_COUNT + " sources, got " + definitionSources.size());
        }
        LinkedHashMap<ResourceLocation, String> ordered = new LinkedHashMap<>();
        int totalBytes = 0;
        for (Map.Entry<ResourceLocation, String> entry : definitionSources.entrySet()) {
            ResourceLocation definitionId = entry.getKey();
            String source = entry.getValue();
            if (source.isBlank()) {
                throw new IllegalArgumentException("Empty Trinity auto-build definition source: " + definitionId);
            }
            int byteCount = source.getBytes(StandardCharsets.UTF_8).length;
            if (byteCount > MAX_DEFINITION_BYTES) {
                throw new IllegalArgumentException("Trinity auto-build definition exceeds " + MAX_DEFINITION_BYTES +
                        " UTF-8 bytes: " + definitionId);
            }
            totalBytes = Math.addExact(totalBytes, byteCount);
            ordered.put(definitionId, source);
        }
        if (totalBytes > MAX_TOTAL_DEFINITION_BYTES) {
            throw new IllegalArgumentException("Trinity auto-build definitions exceed " +
                    MAX_TOTAL_DEFINITION_BYTES + " combined UTF-8 bytes");
        }
        definitionSources = Collections.unmodifiableMap(ordered);
    }

    /**
     * Captures the top server resource for every structure referenced by the active Trinity preview specification.
     *
     * @param resourceManager current server datapack resource manager
     * @param previewSpec     active server preview specification
     * @return bounded authoritative source snapshot
     */
    public static TrinityAutoBuildDefinitionBundle capture(ResourceManager resourceManager,
                                                           MultiblockPreviewSpec previewSpec) {
        LinkedHashMap<ResourceLocation, String> sources = new LinkedHashMap<>();
        int totalBytes = 0;
        for (SubstructurePreviewSpec substructure : previewSpec.substructures()) {
            JsonMultiBlockStructureKey structureKey = substructure.definition().key();
            ResourceLocation definitionId = definitionId(structureKey);
            ResourceLocation resourceId = resourceId(definitionId);
            Resource resource = resourceManager.getResource(resourceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing active Trinity auto-build definition resource: " + resourceId));
            byte[] bytes;
            try (InputStream stream = resource.open()) {
                bytes = stream.readNBytes(MAX_DEFINITION_BYTES + 1);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not read active Trinity auto-build definition resource: " + resourceId,
                        exception);
            }
            if (bytes.length > MAX_DEFINITION_BYTES) {
                throw new IllegalStateException("Active Trinity auto-build definition exceeds " +
                        MAX_DEFINITION_BYTES + " UTF-8 bytes: " + resourceId);
            }
            totalBytes = Math.addExact(totalBytes, bytes.length);
            if (totalBytes > MAX_TOTAL_DEFINITION_BYTES) {
                throw new IllegalStateException("Active Trinity auto-build definitions exceed " +
                        MAX_TOTAL_DEFINITION_BYTES + " combined UTF-8 bytes");
            }
            sources.put(definitionId, decodeUtf8(bytes, resourceId));
        }
        return new TrinityAutoBuildDefinitionBundle(previewSpec.definitionRevision(), sources);
    }

    /**
     * Parses the synchronized sources into the same revision-bound preview model on either logical side.
     */
    public MultiblockPreviewSpec previewSpec() {
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = new MdlibJsonMultiBlockDefinitionLoader().load(this.definitionSources);
        return new TrinityMultiblockPreviewSpecFactory().create(
                new JsonMultiBlockDefinitionRegistrySnapshot(this.definitionRevision, definitions));
    }

    private static ResourceLocation resourceId(ResourceLocation definitionId) {
        return ResourceLocation.fromNamespaceAndPath(
                definitionId.getNamespace(),
                MdlibJsonMultiBlockDefinitionLoader.DIRECTORY + "/" + definitionId.getPath() + ".json");
    }

    private static ResourceLocation definitionId(JsonMultiBlockStructureKey structureKey) {
        ResourceLocation machineId = structureKey.machineId();
        return ResourceLocation.fromNamespaceAndPath(
                machineId.getNamespace(),
                machineId.getPath() + "/" + structureKey.structureName());
    }

    private static String decodeUtf8(byte[] bytes, ResourceLocation resourceId) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException(
                    "Active Trinity auto-build definition is not valid UTF-8: " + resourceId,
                    exception);
        }
    }
}
