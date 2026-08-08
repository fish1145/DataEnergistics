package com.fish_dan_.data_energistics.menu.common;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Identifies the exact recipe category and workstation candidates for provider ranking.
 *
 * <p>
 * The snapshot contains only canonical registry identifiers. It is therefore safe to persist and send over the
 * wire without retaining viewer, recipe, menu, or live registry objects.
 * </p>
 */
public record PatternEncodingRankingContext(@NotNull ResourceLocation categoryId,
                                            @NotNull List<@NotNull ResourceLocation> workstationIds) {

    /** Maximum number of workstation identifiers carried by one viewer snapshot. */
    public static final int MAX_WORKSTATION_IDS = 64;
    /** Maximum UTF-8 bytes accepted for one registry identifier on the wire and in persistence. */
    public static final int MAX_RESOURCE_LOCATION_BYTES = 256;
    /** Maximum UTF-8 bytes occupied by the complete context snapshot. */
    public static final int MAX_CONTEXT_BYTES = 16 * 1024;

    /** Canonicalizes and validates the immutable viewer snapshot. */
    public PatternEncodingRankingContext {
        validateResourceLocation(categoryId, "category id");
        if (workstationIds.size() > MAX_WORKSTATION_IDS) {
            throw new IllegalArgumentException(
                    "Pattern ranking workstation ids exceed " + MAX_WORKSTATION_IDS);
        }
        List<ResourceLocation> canonical = new ArrayList<>(workstationIds.size());
        for (ResourceLocation workstationId : workstationIds) {
            validateResourceLocation(workstationId, "workstation id");
            canonical.add(workstationId);
        }
        canonical.sort(Comparator.comparing(ResourceLocation::toString));
        List<ResourceLocation> unique = new ArrayList<>(canonical.size());
        ResourceLocation previous = null;
        for (ResourceLocation workstationId : canonical) {
            if (!workstationId.equals(previous)) {
                unique.add(workstationId);
                previous = workstationId;
            }
        }
        workstationIds = List.copyOf(unique);
        if (encodedByteLength(categoryId, workstationIds) > MAX_CONTEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Pattern ranking context exceeds " + MAX_CONTEXT_BYTES + " UTF-8 bytes");
        }
    }

    /** Creates a canonical context from an exact category and workstation collection. */
    public static @NotNull PatternEncodingRankingContext of(@NotNull ResourceLocation categoryId,
                                                            @NotNull Collection<@NotNull ResourceLocation> workstationIds) {
        return new PatternEncodingRankingContext(categoryId, new ArrayList<>(workstationIds));
    }

    private static int encodedByteLength(ResourceLocation categoryId, List<ResourceLocation> workstationIds) {
        int length = utf8Length(categoryId) + 1;
        for (ResourceLocation workstationId : workstationIds) {
            length += utf8Length(workstationId) + 1;
        }
        return length;
    }

    private static int utf8Length(ResourceLocation id) {
        return id.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static void validateResourceLocation(ResourceLocation id, String label) {
        if (utf8Length(id) > MAX_RESOURCE_LOCATION_BYTES) {
            throw new IllegalArgumentException(
                    "Pattern ranking " + label + " exceeds " + MAX_RESOURCE_LOCATION_BYTES + " UTF-8 bytes");
        }
    }
}
