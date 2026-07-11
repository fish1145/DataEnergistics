package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.resources.ResourceLocation;

/**
 * Converts resource ids under {@code data/<namespace>/multiblock} into structure registry keys.
 */
public final class JsonMultiBlockResourceKeyResolver {

    private JsonMultiBlockResourceKeyResolver() {}

    public static JsonMultiBlockStructureKey resolve(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (path.isBlank()) {
            throw new IllegalArgumentException("JSON multiblock resource path must not be blank: " + resourceId);
        }
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            throw new IllegalArgumentException("JSON multiblock resource path contains an empty segment: " + resourceId);
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return JsonMultiBlockStructureKey.main(ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path));
        }

        String machinePath = path.substring(0, lastSlash);
        String structureName = path.substring(lastSlash + 1);
        return new JsonMultiBlockStructureKey(
                ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), machinePath),
                structureName);
    }
}
