package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.modularmc.mdl.api.multiblock.StructureDir;
import com.modularmc.mdl.api.multiblock.util.RelativeDirection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed metadata for a JSON multiblock definition.
 */
public final class JsonMultiBlockMetadata {

    public static final String METADATA_PROPERTY = "metadata";
    private static final String DISPLAY_NAME_PROPERTY = "display_name";
    private static final String COMPARTMENTS_PROPERTY = "compartments";
    private static final String REPLACEABLE_COMPARTMENTS_PROPERTY = "replaceable_compartments";
    private static final String STRUCTURE_DIR_PROPERTY = "structure_dir";
    private static final String CHAR_DIRECTION_PROPERTY = "char";
    private static final String STRING_DIRECTION_PROPERTY = "string";
    private static final String AISLE_DIRECTION_PROPERTY = "aisle";
    private static final StructureDir DEFAULT_STRUCTURE_DIR = StructureDir.defaultDirs();

    private final Optional<String> displayNameTranslationKey;
    private final Map<String, CompartmentType> compartmentTypes;
    private final Map<String, Set<CompartmentType>> replaceableCompartmentTypes;
    private final StructureDir structureDir;

    private JsonMultiBlockMetadata(Optional<String> displayNameTranslationKey,
                                   Map<String, CompartmentType> compartmentTypes,
                                   Map<String, Set<CompartmentType>> replaceableCompartmentTypes,
                                   StructureDir structureDir) {
        this.displayNameTranslationKey = displayNameTranslationKey;
        this.compartmentTypes = Map.copyOf(compartmentTypes);
        this.replaceableCompartmentTypes = copyReplaceableCompartmentTypes(replaceableCompartmentTypes);
        this.structureDir = Objects.requireNonNull(structureDir, "structureDir");
    }

    public static JsonMultiBlockMetadata read(JsonObject root, ResourceLocation resourceId) {
        if (!root.has(METADATA_PROPERTY)) {
            return new JsonMultiBlockMetadata(Optional.empty(), Map.of(), Map.of(), DEFAULT_STRUCTURE_DIR);
        }
        JsonElement metadataElement = root.get(METADATA_PROPERTY);
        if (!metadataElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock metadata must be an object: " + resourceId);
        }
        JsonObject metadata = metadataElement.getAsJsonObject();
        return new JsonMultiBlockMetadata(
                readDisplayNameTranslationKey(metadata, resourceId),
                readCompartmentTypes(metadata, resourceId),
                readReplaceableCompartmentTypes(metadata, resourceId),
                readStructureDir(metadata, resourceId));
    }

    public Optional<String> displayNameTranslationKey() {
        return this.displayNameTranslationKey;
    }

    public Map<String, CompartmentType> compartmentTypes() {
        return this.compartmentTypes;
    }

    public Map<String, Set<CompartmentType>> replaceableCompartmentTypes() {
        return this.replaceableCompartmentTypes;
    }

    public StructureDir structureDir() {
        return this.structureDir;
    }

    private static Optional<String> readDisplayNameTranslationKey(JsonObject metadata, ResourceLocation resourceId) {
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

    private static Map<String, CompartmentType> readCompartmentTypes(JsonObject metadata, ResourceLocation resourceId) {
        if (!metadata.has(COMPARTMENTS_PROPERTY)) {
            return Map.of();
        }
        JsonElement compartmentsElement = metadata.get(COMPARTMENTS_PROPERTY);
        if (!compartmentsElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock compartments metadata must be an object: " + resourceId);
        }
        JsonObject compartments = compartmentsElement.getAsJsonObject();
        LinkedHashMap<String, CompartmentType> types = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : compartments.entrySet()) {
            String symbol = entry.getKey();
            if (symbol == null || symbol.isBlank() || symbol.length() != 1) {
                throw new IllegalArgumentException("JSON multiblock compartment symbol must be exactly one non-blank character: " + resourceId);
            }
            JsonElement typeElement = entry.getValue();
            if (!typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("JSON multiblock compartment type must be a string for symbol '" + symbol + "': " + resourceId);
            }
            String typeId = typeElement.getAsString();
            CompartmentType type = CompartmentType.byId(typeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown JSON multiblock compartment type '" + typeId + "' for symbol '" + symbol + "': " + resourceId));
            types.put(symbol, type);
        }
        return Map.copyOf(types);
    }

    private static Map<String, Set<CompartmentType>> readReplaceableCompartmentTypes(JsonObject metadata,
                                                                                     ResourceLocation resourceId) {
        if (!metadata.has(REPLACEABLE_COMPARTMENTS_PROPERTY)) {
            return Map.of();
        }
        JsonElement replaceableElement = metadata.get(REPLACEABLE_COMPARTMENTS_PROPERTY);
        if (!replaceableElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock replaceable_compartments metadata must be an object: " +
                    resourceId);
        }
        JsonObject replaceable = replaceableElement.getAsJsonObject();
        LinkedHashMap<String, Set<CompartmentType>> types = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : replaceable.entrySet()) {
            String symbol = entry.getKey();
            validateSymbol(symbol, "replaceable compartment", resourceId);
            if (!entry.getValue().isJsonArray()) {
                throw new IllegalArgumentException("JSON multiblock replaceable compartment types must be an array for symbol '" +
                        symbol + "': " + resourceId);
            }
            LinkedHashMap<String, CompartmentType> symbolTypes = new LinkedHashMap<>();
            for (JsonElement typeElement : entry.getValue().getAsJsonArray()) {
                if (!typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("JSON multiblock replaceable compartment type must be a string for symbol '" +
                            symbol + "': " + resourceId);
                }
                String typeId = typeElement.getAsString();
                CompartmentType type = CompartmentType.byId(typeId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown JSON multiblock replaceable compartment type '" +
                                typeId + "' for symbol '" + symbol + "': " + resourceId));
                symbolTypes.put(type.id(), type);
            }
            if (symbolTypes.isEmpty()) {
                throw new IllegalArgumentException("JSON multiblock replaceable compartment symbol '" + symbol +
                        "' must allow at least one type: " + resourceId);
            }
            types.put(symbol, Set.copyOf(symbolTypes.values()));
        }
        return copyReplaceableCompartmentTypes(types);
    }

    private static StructureDir readStructureDir(JsonObject metadata, ResourceLocation resourceId) {
        if (!metadata.has(STRUCTURE_DIR_PROPERTY)) {
            return DEFAULT_STRUCTURE_DIR;
        }
        JsonElement structureDirElement = metadata.get(STRUCTURE_DIR_PROPERTY);
        if (!structureDirElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock structure_dir metadata must be an object: " +
                    resourceId);
        }
        JsonObject structureDir = structureDirElement.getAsJsonObject();
        RelativeDirection charDir = readRelativeDirection(structureDir, CHAR_DIRECTION_PROPERTY, resourceId);
        RelativeDirection stringDir = readRelativeDirection(structureDir, STRING_DIRECTION_PROPERTY, resourceId);
        RelativeDirection aisleDir = readRelativeDirection(structureDir, AISLE_DIRECTION_PROPERTY, resourceId);
        validateDistinctStructureAxes(charDir, stringDir, aisleDir, resourceId);
        return new StructureDir(charDir, stringDir, aisleDir);
    }

    private static RelativeDirection readRelativeDirection(JsonObject structureDir,
                                                           String property,
                                                           ResourceLocation resourceId) {
        JsonElement directionElement = structureDir.get(property);
        if (directionElement == null || directionElement.isJsonNull()) {
            throw new IllegalArgumentException("JSON multiblock structure_dir is missing direction '" + property +
                    "': " + resourceId);
        }
        if (!directionElement.isJsonPrimitive() || !directionElement.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("JSON multiblock structure_dir direction '" + property +
                    "' must be a string: " + resourceId);
        }
        String directionName = directionElement.getAsString();
        if (directionName.isBlank()) {
            throw new IllegalArgumentException("JSON multiblock structure_dir direction '" + property +
                    "' must not be blank: " + resourceId);
        }
        for (RelativeDirection direction : RelativeDirection.values()) {
            if (direction.getSerializedName().equalsIgnoreCase(directionName)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown JSON multiblock structure_dir direction '" + directionName +
                "' for '" + property + "': " + resourceId);
    }

    private static void validateDistinctStructureAxes(RelativeDirection charDir,
                                                      RelativeDirection stringDir,
                                                      RelativeDirection aisleDir,
                                                      ResourceLocation resourceId) {
        if (charDir.isSameAxis(stringDir) || charDir.isSameAxis(aisleDir) || stringDir.isSameAxis(aisleDir)) {
            throw new IllegalArgumentException("JSON multiblock structure_dir directions must use three distinct axes: " +
                    resourceId);
        }
    }

    private static void validateSymbol(String symbol, String label, ResourceLocation resourceId) {
        if (symbol == null || symbol.isBlank() || symbol.length() != 1) {
            throw new IllegalArgumentException("JSON multiblock " + label +
                    " symbol must be exactly one non-blank character: " + resourceId);
        }
    }

    private static Map<String, Set<CompartmentType>> copyReplaceableCompartmentTypes(Map<String, Set<CompartmentType>> source) {
        LinkedHashMap<String, Set<CompartmentType>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<CompartmentType>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
