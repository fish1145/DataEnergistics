package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parsed metadata for a JSON multiblock definition.
 */
public final class JsonMultiBlockMetadata {

    public static final String METADATA_PROPERTY = "metadata";
    private static final String DISPLAY_NAME_PROPERTY = "display_name";
    private static final String COMPARTMENTS_PROPERTY = "compartments";

    private final Optional<String> displayNameTranslationKey;
    private final Map<String, CompartmentType> compartmentTypes;

    private JsonMultiBlockMetadata(Optional<String> displayNameTranslationKey,
                                   Map<String, CompartmentType> compartmentTypes) {
        this.displayNameTranslationKey = displayNameTranslationKey;
        this.compartmentTypes = Map.copyOf(compartmentTypes);
    }

    public static JsonMultiBlockMetadata read(JsonObject root, ResourceLocation resourceId) {
        if (!root.has(METADATA_PROPERTY)) {
            return new JsonMultiBlockMetadata(Optional.empty(), Map.of());
        }
        JsonElement metadataElement = root.get(METADATA_PROPERTY);
        if (!metadataElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock metadata must be an object: " + resourceId);
        }
        JsonObject metadata = metadataElement.getAsJsonObject();
        return new JsonMultiBlockMetadata(
                readDisplayNameTranslationKey(metadata, resourceId),
                readCompartmentTypes(metadata, resourceId));
    }

    public Optional<String> displayNameTranslationKey() {
        return this.displayNameTranslationKey;
    }

    public Map<String, CompartmentType> compartmentTypes() {
        return this.compartmentTypes;
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
}
