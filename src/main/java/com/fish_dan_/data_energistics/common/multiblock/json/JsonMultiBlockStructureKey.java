package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Names one JSON-backed multiblock structure by GTM-style machine id and structure name.
 *
 * @param machineId     machine id that owns the structure, such as {@code data_energistics:data_framework_column}
 * @param structureName single path segment identifying the named structure; {@code main} is the default structure
 */
public record JsonMultiBlockStructureKey(ResourceLocation machineId, String structureName) {

    public static final String DEFAULT_STRUCTURE_NAME = "main";

    public JsonMultiBlockStructureKey {
        machineId = Objects.requireNonNull(machineId, "machineId");
        structureName = requireStructureName(structureName);
    }

    public static JsonMultiBlockStructureKey main(ResourceLocation machineId) {
        return new JsonMultiBlockStructureKey(machineId, DEFAULT_STRUCTURE_NAME);
    }

    public boolean isMain() {
        return DEFAULT_STRUCTURE_NAME.equals(this.structureName);
    }

    public String serialized() {
        return this.machineId + "#" + this.structureName;
    }

    private static String requireStructureName(String structureName) {
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException("JSON multiblock structureName must not be blank");
        }
        if (structureName.indexOf('/') >= 0 || structureName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("JSON multiblock structureName must be one path segment: " + structureName);
        }
        for (int i = 0; i < structureName.length(); i++) {
            if (Character.isWhitespace(structureName.charAt(i))) {
                throw new IllegalArgumentException("JSON multiblock structureName must not contain whitespace: " + structureName);
            }
        }
        return structureName;
    }

    @Override
    public String toString() {
        return serialized();
    }
}
