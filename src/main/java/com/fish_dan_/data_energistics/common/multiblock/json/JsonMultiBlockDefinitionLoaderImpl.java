package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.json.StructurePatternResolver;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default loader implementation backed by MDLib's GregTech-style JSON resolver.
 */
public final class JsonMultiBlockDefinitionLoaderImpl implements JsonMultiBlockDefinitionLoader {

    public static final String DIRECTORY = "multiblock";
    private static final String METADATA_PROPERTY = "metadata";
    private static final String DISPLAY_NAME_PROPERTY = "display_name";
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final FileToIdConverter FILE_TO_ID = FileToIdConverter.json(DIRECTORY);

    @Override
    public Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> load(ResourceManager resourceManager) {
        Objects.requireNonNull(resourceManager, "resourceManager");
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = new LinkedHashMap<>();
        Map<JsonMultiBlockStructureKey, ResourceLocation> sources = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : FILE_TO_ID.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation resourceId = FILE_TO_ID.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                putDefinition(definitions, sources, parse(resourceId, reader), resourceId);
            } catch (IOException exception) {
                String message = "Could not read JSON multiblock resource " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            } catch (RuntimeException exception) {
                String message = "Could not parse JSON multiblock resource " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            }
        }
        return Map.copyOf(definitions);
    }

    @Override
    public Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> load(Map<ResourceLocation, String> resources) {
        Objects.requireNonNull(resources, "resources");
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = new LinkedHashMap<>();
        Map<JsonMultiBlockStructureKey, ResourceLocation> sources = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, String> entry : resources.entrySet()) {
            ResourceLocation resourceId = Objects.requireNonNull(entry.getKey(), "resourceId");
            String json = Objects.requireNonNull(entry.getValue(), "json");
            try (Reader reader = new StringReader(json)) {
                putDefinition(definitions, sources, parse(resourceId, reader), resourceId);
            } catch (RuntimeException exception) {
                String message = "Could not parse JSON multiblock resource " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            } catch (IOException exception) {
                String message = "Could not close JSON multiblock reader for " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            }
        }
        return Map.copyOf(definitions);
    }

    @Override
    public JsonMultiBlockDefinition parse(ResourceLocation resourceId, Reader reader) {
        JsonMultiBlockStructureKey key = JsonMultiBlockResourceKeyResolver.resolve(resourceId);
        JsonObject root = JsonParser.parseReader(Objects.requireNonNull(reader, "reader")).getAsJsonObject();
        Optional<String> displayNameTranslationKey = readDisplayNameTranslationKey(root, resourceId);
        JsonObject patternRoot = root.deepCopy();
        patternRoot.remove(METADATA_PROPERTY);
        BlockPattern pattern = StructurePatternResolver.parsePattern(new StringReader(GSON.toJson(patternRoot)));
        return new JsonMultiBlockDefinitionImpl(key, pattern, displayNameTranslationKey);
    }

    private static Optional<String> readDisplayNameTranslationKey(JsonObject root, ResourceLocation resourceId) {
        if (!root.has(METADATA_PROPERTY)) {
            return Optional.empty();
        }
        JsonElement metadataElement = root.get(METADATA_PROPERTY);
        if (!metadataElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock metadata must be an object: " + resourceId);
        }
        JsonObject metadata = metadataElement.getAsJsonObject();
        if (!metadata.has(DISPLAY_NAME_PROPERTY)) {
            return Optional.empty();
        }
        JsonElement displayNameElement = metadata.get(DISPLAY_NAME_PROPERTY);
        if (!displayNameElement.isJsonPrimitive() || !displayNameElement.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("JSON multiblock display_name must be a translation key string: " +
                    resourceId);
        }
        String displayNameTranslationKey = displayNameElement.getAsString();
        if (displayNameTranslationKey.isBlank()) {
            throw new IllegalArgumentException("JSON multiblock display_name must not be blank: " + resourceId);
        }
        return Optional.of(displayNameTranslationKey);
    }

    private static void putDefinition(Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions,
                                      Map<JsonMultiBlockStructureKey, ResourceLocation> sources,
                                      JsonMultiBlockDefinition definition,
                                      ResourceLocation resourceId) {
        JsonMultiBlockDefinition previous = definitions.putIfAbsent(definition.key(), definition);
        if (previous != null) {
            String message = "Duplicate JSON multiblock key " + definition.key() + " from " + resourceId +
                    ", already loaded from " + sources.get(definition.key());
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        sources.put(definition.key(), resourceId);
    }
}
