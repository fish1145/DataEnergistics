package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.json.StructurePatternResolver;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loader backed by MDLib's GregTech-style JSON resolver.
 */
public final class MdlibJsonMultiBlockDefinitionLoader implements JsonMultiBlockDefinitionLoader {

    public static final String DIRECTORY = "multiblock";
    private static final String PREDICATES_PROPERTY = "predicates";
    private static final String AISLES_PROPERTY = "aisles";
    private static final String SLICES_PROPERTY = "slices";
    private static final String TYPE_PROPERTY = "type";
    private static final String PREDICATE_PROPERTY = "predicate";
    private static final String BLOCK_PROPERTY = "block";
    private static final String BLOCKS_PROPERTY = "blocks";
    private static final String BLOCK_STATES_PROPERTY = "block_states";
    private static final String BLOCKS_PREDICATE_TYPE = "blocks";
    private static final String BLOCK_STATES_PREDICATE_TYPE = "block_states";
    private static final String FALLBACK_BLOCK_PREDICATE_TYPE = "mdlib:blocks";
    private static final String FALLBACK_BLOCK_ID = "minecraft:air";
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
        JsonObject root = readRoot(reader, resourceId);
        JsonMultiBlockMetadata metadata = JsonMultiBlockMetadata.read(root, resourceId);
        return parseDefinition(resourceId, root, metadata);
    }

    private static JsonMultiBlockDefinition parseDefinition(ResourceLocation resourceId,
                                                            JsonObject root,
                                                            JsonMultiBlockMetadata metadata) {
        JsonMultiBlockStructureKey key = JsonMultiBlockResourceKeyResolver.resolve(resourceId);
        JsonObject patternRoot = root.deepCopy();
        patternRoot.remove(JsonMultiBlockMetadata.METADATA_PROPERTY);
        sanitizeBlockPredicates(resourceId, patternRoot);
        JsonMultiBlockCompartmentPredicate.registerType();
        JsonMultiBlockReplaceableCompartmentPredicate.registerType();
        applyCompartmentPredicates(resourceId, patternRoot, metadata.compartmentTypes());
        applyReplaceableCompartmentPredicates(resourceId, patternRoot, metadata.replaceableCompartmentTypes());
        BlockPattern pattern = StructurePatternResolver.parsePattern(new StringReader(GSON.toJson(patternRoot)));
        return new ResolvedJsonMultiBlockDefinition(
                key,
                pattern,
                metadata.displayNameTranslationKey(),
                metadata.compartmentTypes(),
                metadata.replaceableCompartmentTypes());
    }

    private static void applyCompartmentPredicates(ResourceLocation resourceId,
                                                   JsonObject root,
                                                   Map<String, CompartmentType> compartmentTypes) {
        if (compartmentTypes.isEmpty()) {
            return;
        }
        JsonObject predicates = getOrCreatePredicates(root, resourceId);
        for (Map.Entry<String, CompartmentType> entry : compartmentTypes.entrySet()) {
            String symbol = entry.getKey();
            if (!patternUsesSymbol(root, symbol)) {
                throw new IllegalArgumentException("JSON multiblock compartment symbol '" + symbol +
                        "' is not used by pattern: " + resourceId);
            }
            JsonObject compartmentPredicate = new JsonObject();
            compartmentPredicate.addProperty(TYPE_PROPERTY, JsonMultiBlockCompartmentPredicate.TYPE.toString());
            compartmentPredicate.addProperty("compartment", entry.getValue().id());
            JsonElement existingPredicate = predicates.get(symbol);
            if (existingPredicate != null && !existingPredicate.isJsonNull()) {
                if (!existingPredicate.isJsonObject()) {
                    throw new IllegalArgumentException("JSON multiblock predicate for compartment symbol '" + symbol +
                            "' must be an object: " + resourceId);
                }
                compartmentPredicate.add("predicate", existingPredicate.deepCopy());
            }
            predicates.add(symbol, compartmentPredicate);
        }
    }

    private static void applyReplaceableCompartmentPredicates(ResourceLocation resourceId,
                                                              JsonObject root,
                                                              Map<String, Set<CompartmentType>> replaceableCompartmentTypes) {
        if (replaceableCompartmentTypes.isEmpty()) {
            return;
        }
        JsonObject predicates = getOrCreatePredicates(root, resourceId);
        for (Map.Entry<String, Set<CompartmentType>> entry : replaceableCompartmentTypes.entrySet()) {
            String symbol = entry.getKey();
            if (!patternUsesSymbol(root, symbol)) {
                throw new IllegalArgumentException("JSON multiblock replaceable compartment symbol '" + symbol +
                        "' is not used by pattern: " + resourceId);
            }
            JsonElement existingPredicate = predicates.get(symbol);
            if (existingPredicate == null || existingPredicate.isJsonNull() || !existingPredicate.isJsonObject()) {
                throw new IllegalArgumentException("JSON multiblock replaceable compartment symbol '" + symbol +
                        "' requires an existing object predicate: " + resourceId);
            }

            JsonObject replaceablePredicate = new JsonObject();
            replaceablePredicate.addProperty(TYPE_PROPERTY, JsonMultiBlockReplaceableCompartmentPredicate.TYPE.toString());
            JsonArray compartments = new JsonArray();
            for (CompartmentType type : entry.getValue()) {
                compartments.add(type.id());
            }
            replaceablePredicate.add("compartments", compartments);
            replaceablePredicate.add(PREDICATE_PROPERTY, existingPredicate.deepCopy());
            predicates.add(symbol, replaceablePredicate);
        }
    }

    private static JsonObject getOrCreatePredicates(JsonObject root, ResourceLocation resourceId) {
        JsonElement predicatesElement = root.get(PREDICATES_PROPERTY);
        if (predicatesElement == null || predicatesElement.isJsonNull()) {
            JsonObject predicates = new JsonObject();
            root.add(PREDICATES_PROPERTY, predicates);
            return predicates;
        }
        if (!predicatesElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock predicates must be an object: " + resourceId);
        }
        return predicatesElement.getAsJsonObject();
    }

    private static boolean patternUsesSymbol(JsonObject root, String symbol) {
        char expected = symbol.charAt(0);
        JsonElement aislesElement = root.get(AISLES_PROPERTY);
        if (aislesElement == null || !aislesElement.isJsonArray()) {
            return false;
        }
        for (JsonElement unitElement : aislesElement.getAsJsonArray()) {
            if (!unitElement.isJsonObject()) {
                continue;
            }
            JsonElement slicesElement = unitElement.getAsJsonObject().get(SLICES_PROPERTY);
            if (slicesElement == null || !slicesElement.isJsonArray()) {
                continue;
            }
            for (JsonElement sliceElement : slicesElement.getAsJsonArray()) {
                if (!sliceElement.isJsonArray()) {
                    continue;
                }
                for (JsonElement rowElement : sliceElement.getAsJsonArray()) {
                    if (rowElement.isJsonPrimitive() &&
                            rowElement.getAsJsonPrimitive().isString() &&
                            rowElement.getAsString().indexOf(expected) >= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static JsonObject readRoot(Reader reader, ResourceLocation resourceId) {
        String source = readSource(reader, resourceId);
        validateUniqueCompartmentSymbols(source, resourceId);
        JsonObject root = JsonParser.parseString(source).getAsJsonObject();
        if (root == null) {
            throw new IllegalArgumentException("JSON multiblock root must be an object");
        }
        return root;
    }

    private static String readSource(Reader reader, ResourceLocation resourceId) {
        Objects.requireNonNull(reader, "reader");
        try {
            StringWriter writer = new StringWriter();
            reader.transferTo(writer);
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read JSON multiblock source: " + resourceId, exception);
        }
    }

    private static void validateUniqueCompartmentSymbols(String source, ResourceLocation resourceId) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String property = reader.nextName();
                if (JsonMultiBlockMetadata.METADATA_PROPERTY.equals(property) &&
                        reader.peek() == JsonToken.BEGIN_OBJECT) {
                    validateMetadataObject(reader, resourceId);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect JSON multiblock metadata: " + resourceId, exception);
        }
    }

    private static void validateMetadataObject(JsonReader reader, ResourceLocation resourceId) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String property = reader.nextName();
            if ("compartments".equals(property) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                validateCompartmentSymbols(reader, resourceId);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static void validateCompartmentSymbols(JsonReader reader, ResourceLocation resourceId) throws IOException {
        Set<String> symbols = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String symbol = reader.nextName();
            if (!symbols.add(symbol)) {
                throw new IllegalArgumentException("Duplicate JSON multiblock compartment symbol '" + symbol +
                        "': " + resourceId);
            }
            reader.skipValue();
        }
        reader.endObject();
    }

    private static void sanitizeBlockPredicates(ResourceLocation resourceId, JsonObject root) {
        JsonElement predicatesElement = root.get(PREDICATES_PROPERTY);
        if (predicatesElement == null || predicatesElement.isJsonNull() || !predicatesElement.isJsonObject()) {
            return;
        }
        JsonObject predicates = predicatesElement.getAsJsonObject();
        List<String> predicateKeys = List.copyOf(predicates.keySet());
        for (String predicateKey : predicateKeys) {
            JsonElement predicateElement = predicates.get(predicateKey);
            if (!predicateElement.isJsonObject()) {
                continue;
            }
            JsonObject predicate = predicateElement.getAsJsonObject();
            if (!isBlockBackedPredicate(predicate)) {
                continue;
            }
            List<String> missingBlockIds = missingBlockIds(predicate);
            if (missingBlockIds.isEmpty()) {
                continue;
            }
            JsonObject airPredicate = new JsonObject();
            airPredicate.addProperty(TYPE_PROPERTY, FALLBACK_BLOCK_PREDICATE_TYPE);
            airPredicate.addProperty(BLOCK_PROPERTY, FALLBACK_BLOCK_ID);
            predicates.add(predicateKey, airPredicate);
            for (String missingBlockId : missingBlockIds) {
                LOGGER.warn(
                        "JSON multiblock resource {} predicate '{}' references missing block id {}; replacing predicate with {}",
                        resourceId,
                        predicateKey,
                        missingBlockId,
                        FALLBACK_BLOCK_ID);
            }
        }
    }

    private static boolean isBlockBackedPredicate(JsonObject predicate) {
        JsonElement typeElement = predicate.get(TYPE_PROPERTY);
        if (typeElement == null || !typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
            return false;
        }
        ResourceLocation type = ResourceLocation.tryParse(typeElement.getAsString());
        return type != null &&
                (BLOCKS_PREDICATE_TYPE.equals(type.getPath()) || BLOCK_STATES_PREDICATE_TYPE.equals(type.getPath()));
    }

    private static List<String> missingBlockIds(JsonObject predicate) {
        List<String> missingBlockIds = new ArrayList<>();
        for (String blockId : blockIds(predicate)) {
            ResourceLocation id = ResourceLocation.tryParse(blockId);
            if (id == null || !blockExists(id)) {
                missingBlockIds.add(blockId);
            }
        }
        return List.copyOf(missingBlockIds);
    }

    private static List<String> blockIds(JsonObject predicate) {
        if (predicate.has(BLOCK_STATES_PROPERTY)) {
            JsonElement blockStatesElement = predicate.get(BLOCK_STATES_PROPERTY);
            if (!blockStatesElement.isJsonArray()) {
                return List.of();
            }
            List<String> blockIds = new ArrayList<>();
            JsonArray blockStates = blockStatesElement.getAsJsonArray();
            for (JsonElement blockStateElement : blockStates) {
                if (!blockStateElement.isJsonObject()) {
                    continue;
                }
                addBlockId(blockStateElement.getAsJsonObject(), blockIds);
            }
            return List.copyOf(blockIds);
        }
        if (predicate.has(BLOCKS_PROPERTY)) {
            JsonElement blocksElement = predicate.get(BLOCKS_PROPERTY);
            if (!blocksElement.isJsonArray()) {
                return List.of();
            }
            List<String> blockIds = new ArrayList<>();
            JsonArray blocks = blocksElement.getAsJsonArray();
            for (JsonElement blockElement : blocks) {
                if (blockElement.isJsonPrimitive() && blockElement.getAsJsonPrimitive().isString()) {
                    blockIds.add(blockElement.getAsString());
                }
            }
            return List.copyOf(blockIds);
        }
        JsonElement blockElement = predicate.get(BLOCK_PROPERTY);
        if (blockElement == null || !blockElement.isJsonPrimitive() || !blockElement.getAsJsonPrimitive().isString()) {
            return List.of();
        }
        return List.of(blockElement.getAsString());
    }

    private static void addBlockId(JsonObject object, List<String> blockIds) {
        JsonElement blockElement = object.get(BLOCK_PROPERTY);
        if (blockElement != null && blockElement.isJsonPrimitive() && blockElement.getAsJsonPrimitive().isString()) {
            blockIds.add(blockElement.getAsString());
        }
    }

    private static boolean blockExists(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        return id.equals(BuiltInRegistries.BLOCK.getKey(block));
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
