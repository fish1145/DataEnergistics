package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Raw auto-build staging declarations read from one JSON multiblock metadata object.
 *
 * <p>
 * Symbols deliberately refer to the surrounding predicate declarations instead of repeating block or item ids. The
 * loader resolves these declarations into immutable runtime candidates only after every predicate is available.
 * </p>
 */
final class JsonMultiBlockAutoBuildStagingMetadata {

    private static final String AUTO_BUILD_STAGING_PROPERTY = "auto_build_staging";
    private static final String BLOCK_SYMBOLS_PROPERTY = "block_symbols";
    private static final String REPLACEABLE_COMPARTMENT_SYMBOLS_PROPERTY = "replaceable_compartment_symbols";
    private static final String PHYSICAL_BLOCK_SYMBOLS_PROPERTY = "physical_block_symbols";
    private static final String PART_HOST_SYMBOLS_PROPERTY = "part_host_symbols";
    private static final JsonMultiBlockAutoBuildStagingMetadata NONE = new JsonMultiBlockAutoBuildStagingMetadata(
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of());

    private final Set<String> blockSymbols;
    private final Set<String> replaceableCompartmentSymbols;
    private final Set<String> physicalBlockSymbols;
    private final Set<String> partHostSymbols;

    private JsonMultiBlockAutoBuildStagingMetadata(Set<String> blockSymbols,
                                                   Set<String> replaceableCompartmentSymbols,
                                                   Set<String> physicalBlockSymbols,
                                                   Set<String> partHostSymbols) {
        this.blockSymbols = Set.copyOf(blockSymbols);
        this.replaceableCompartmentSymbols = Set.copyOf(replaceableCompartmentSymbols);
        this.physicalBlockSymbols = Set.copyOf(physicalBlockSymbols);
        this.partHostSymbols = Set.copyOf(partHostSymbols);
    }

    static JsonMultiBlockAutoBuildStagingMetadata none() {
        return NONE;
    }

    static JsonMultiBlockAutoBuildStagingMetadata read(JsonObject metadata, ResourceLocation resourceId) {
        if (!metadata.has(AUTO_BUILD_STAGING_PROPERTY)) {
            return NONE;
        }
        JsonElement stagingElement = metadata.get(AUTO_BUILD_STAGING_PROPERTY);
        if (!stagingElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock auto_build_staging must be an object: " + resourceId);
        }
        JsonObject staging = stagingElement.getAsJsonObject();
        Set<String> blockSymbols = readSymbols(staging, BLOCK_SYMBOLS_PROPERTY, resourceId);
        Set<String> replaceableCompartmentSymbols = readSymbols(
                staging,
                REPLACEABLE_COMPARTMENT_SYMBOLS_PROPERTY,
                resourceId);
        Set<String> physicalBlockSymbols = readSymbols(staging, PHYSICAL_BLOCK_SYMBOLS_PROPERTY, resourceId);
        Set<String> partHostSymbols = readSymbols(staging, PART_HOST_SYMBOLS_PROPERTY, resourceId);
        if (!blockSymbols.containsAll(physicalBlockSymbols)) {
            throw new IllegalArgumentException("JSON multiblock physical_block_symbols must be declared in block_symbols: " +
                    resourceId);
        }
        return new JsonMultiBlockAutoBuildStagingMetadata(
                blockSymbols,
                replaceableCompartmentSymbols,
                physicalBlockSymbols,
                partHostSymbols);
    }

    boolean isEmpty() {
        return this.blockSymbols.isEmpty() && this.replaceableCompartmentSymbols.isEmpty() &&
                this.partHostSymbols.isEmpty();
    }

    Set<String> blockSymbols() {
        return this.blockSymbols;
    }

    Set<String> replaceableCompartmentSymbols() {
        return this.replaceableCompartmentSymbols;
    }

    Set<String> physicalBlockSymbols() {
        return this.physicalBlockSymbols;
    }

    Set<String> partHostSymbols() {
        return this.partHostSymbols;
    }

    private static Set<String> readSymbols(JsonObject staging, String property, ResourceLocation resourceId) {
        if (!staging.has(property)) {
            return Set.of();
        }
        JsonElement symbolsElement = staging.get(property);
        if (!symbolsElement.isJsonArray()) {
            throw new IllegalArgumentException("JSON multiblock auto_build_staging." + property +
                    " must be an array: " + resourceId);
        }
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        for (JsonElement symbolElement : symbolsElement.getAsJsonArray()) {
            if (!symbolElement.isJsonPrimitive() || !symbolElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("JSON multiblock auto-build staging symbols must be strings: " +
                        resourceId);
            }
            String symbol = symbolElement.getAsString();
            if (symbol.isBlank() || symbol.length() != 1) {
                throw new IllegalArgumentException("JSON multiblock auto-build staging symbols must be one non-blank " +
                        "character: " + resourceId);
            }
            if (!symbols.add(symbol)) {
                throw new IllegalArgumentException("JSON multiblock auto-build staging symbol is declared more than once: '" +
                        symbol + "' in " + resourceId);
            }
        }
        return Set.copyOf(symbols);
    }
}
