package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Parsed metadata for a JSON multiblock definition.
 */
public final class JsonMultiBlockMetadata {

    public static final String METADATA_PROPERTY = "metadata";
    private static final String DISPLAY_NAME_PROPERTY = "display_name";

    private final Optional<String> displayNameTranslationKey;

    private JsonMultiBlockMetadata(Optional<String> displayNameTranslationKey) {
        this.displayNameTranslationKey = displayNameTranslationKey;
    }

    public static JsonMultiBlockMetadata read(JsonObject root, ResourceLocation resourceId) {
        if (!root.has(METADATA_PROPERTY)) {
            return new JsonMultiBlockMetadata(Optional.empty());
        }
        JsonElement metadataElement = root.get(METADATA_PROPERTY);
        if (!metadataElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock metadata must be an object: " + resourceId);
        }
        JsonObject metadata = metadataElement.getAsJsonObject();
        return new JsonMultiBlockMetadata(readDisplayNameTranslationKey(metadata, resourceId));
    }

    public Optional<String> displayNameTranslationKey() {
        return this.displayNameTranslationKey;
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
}
