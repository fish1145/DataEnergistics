package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.transfer.MultiblockPatternTransferRequest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict bounded codec for the complete multiblock-to-pattern-terminal request.
 */
final class MultiblockPatternTransferPayloadCodec {

    static final int MAX_RESOURCE_LOCATION_LENGTH = 256;
    static final int MAX_STRUCTURE_NAME_LENGTH = 128;
    static final int MAX_TIER_DOMAIN_LENGTH = 128;
    static final int MAX_REPEAT_UNITS = 64;
    static final int MAX_TIER_SELECTIONS = 64;
    static final int MAX_CANDIDATE_SELECTIONS = 4_096;
    static final int MAX_SELECTION_VALUE = 1_000_000;
    static final long MAX_DEFINITION_REVISION = 1_000_000_000_000L;

    private MultiblockPatternTransferPayloadCodec() {}

    /**
     * Reads one complete request without accepting duplicate map keys.
     */
    static MultiblockPatternTransferRequest readRequest(RegistryFriendlyByteBuf buffer) {
        int containerId = readBoundedInt(
                buffer,
                "container id",
                0,
                MultiblockPatternTransferRequest.MAX_CONTAINER_ID);
        ResourceLocation registeredRecipeId = readResourceLocation(buffer, "registered recipe id");
        ResourceLocation controllerId = readResourceLocation(buffer, "controller id");
        long revision = readBoundedLong(
                buffer,
                "definition revision",
                0L,
                MAX_DEFINITION_REVISION);
        ResourceLocation structureMachineId = readResourceLocation(buffer, "structure machine id");
        String structureName = buffer.readUtf(MAX_STRUCTURE_NAME_LENGTH);
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("Multiblock pattern transfer structure name cannot be blank");
        }
        int variantIndex = readBoundedInt(buffer, "variant index", 0, MAX_SELECTION_VALUE);

        int repeatCount = readCount(buffer, "repeat unit count", MAX_REPEAT_UNITS);
        List<Integer> repeatCounts = new ArrayList<>(repeatCount);
        for (int index = 0; index < repeatCount; index++) {
            repeatCounts.add(readBoundedInt(buffer, "repeat count", 1, MAX_SELECTION_VALUE));
        }

        int tierCount = readCount(buffer, "tier selection count", MAX_TIER_SELECTIONS);
        Map<String, Integer> tierSelections = new LinkedHashMap<>();
        for (int index = 0; index < tierCount; index++) {
            String domain = buffer.readUtf(MAX_TIER_DOMAIN_LENGTH);
            if (domain.isBlank()) {
                throw new IllegalArgumentException("Multiblock pattern transfer tier domain cannot be blank");
            }
            int value = readBoundedInt(buffer, "tier value", 1, MAX_SELECTION_VALUE);
            if (tierSelections.putIfAbsent(domain, value) != null) {
                throw new IllegalArgumentException("Duplicate multiblock pattern transfer tier domain: " + domain);
            }
        }

        int candidateCount = readCount(buffer, "candidate selection count", MAX_CANDIDATE_SELECTIONS);
        Map<PreviewPredicateKey, Integer> candidateSelections = new LinkedHashMap<>();
        for (int index = 0; index < candidateCount; index++) {
            PreviewPredicateKey key = new PreviewPredicateKey(
                    readBoundedInt(buffer, "candidate source layer", 0, MAX_SELECTION_VALUE),
                    readBoundedInt(buffer, "candidate y", 0, MAX_SELECTION_VALUE),
                    readBoundedInt(buffer, "candidate x", 0, MAX_SELECTION_VALUE));
            int value = readBoundedInt(buffer, "candidate index", 0, MAX_SELECTION_VALUE);
            if (candidateSelections.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate multiblock pattern transfer candidate key: " + key);
            }
        }

        ProjectionFingerprint fingerprint = new ProjectionFingerprint(
                controllerId,
                revision,
                new JsonMultiBlockStructureKey(structureMachineId, structureName),
                variantIndex,
                repeatCounts,
                tierSelections,
                candidateSelections);
        return new MultiblockPatternTransferRequest(containerId, registeredRecipeId, fingerprint);
    }

    /**
     * Writes every request field in deterministic fingerprint order.
     */
    static void writeRequest(RegistryFriendlyByteBuf buffer, MultiblockPatternTransferRequest request) {
        validateRequest(request);
        buffer.writeVarInt(request.containerId());
        writeResourceLocation(buffer, request.registeredRecipeId(), "registered recipe id");

        ProjectionFingerprint fingerprint = request.projectionFingerprint();
        writeResourceLocation(buffer, fingerprint.controllerId(), "controller id");
        buffer.writeVarLong(fingerprint.definitionRevision());
        writeResourceLocation(buffer, fingerprint.structureKey().machineId(), "structure machine id");
        buffer.writeUtf(fingerprint.structureKey().structureName(), MAX_STRUCTURE_NAME_LENGTH);
        buffer.writeVarInt(fingerprint.variantIndex());

        buffer.writeVarInt(fingerprint.repeatCounts().size());
        for (int repeatCount : fingerprint.repeatCounts()) {
            buffer.writeVarInt(repeatCount);
        }
        buffer.writeVarInt(fingerprint.tierSelections().size());
        for (Map.Entry<String, Integer> tier : fingerprint.tierSelections().entrySet()) {
            buffer.writeUtf(tier.getKey(), MAX_TIER_DOMAIN_LENGTH);
            buffer.writeVarInt(tier.getValue());
        }
        buffer.writeVarInt(fingerprint.candidateSelections().size());
        for (Map.Entry<PreviewPredicateKey, Integer> candidate : fingerprint.candidateSelections().entrySet()) {
            PreviewPredicateKey key = candidate.getKey();
            buffer.writeVarInt(key.sourceLayer());
            buffer.writeVarInt(key.y());
            buffer.writeVarInt(key.x());
            buffer.writeVarInt(candidate.getValue());
        }
    }

    /**
     * Rejects outgoing state that cannot be represented by the bounded schema.
     */
    static void validateRequest(MultiblockPatternTransferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer request cannot be null");
        }
        requireRange(
                "container id",
                request.containerId(),
                0,
                MultiblockPatternTransferRequest.MAX_CONTAINER_ID);
        validateResourceLocation(request.registeredRecipeId(), "registered recipe id");

        ProjectionFingerprint fingerprint = request.projectionFingerprint();
        validateResourceLocation(fingerprint.controllerId(), "controller id");
        requireRange(
                "definition revision",
                fingerprint.definitionRevision(),
                0L,
                MAX_DEFINITION_REVISION);
        validateResourceLocation(fingerprint.structureKey().machineId(), "structure machine id");
        String structureName = fingerprint.structureKey().structureName();
        if (structureName.isBlank() || structureName.length() > MAX_STRUCTURE_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid multiblock pattern transfer structure name: " + structureName);
        }
        requireRange("variant index", fingerprint.variantIndex(), 0, MAX_SELECTION_VALUE);

        requireRange("repeat unit count", fingerprint.repeatCounts().size(), 0, MAX_REPEAT_UNITS);
        for (int repeatCount : fingerprint.repeatCounts()) {
            requireRange("repeat count", repeatCount, 1, MAX_SELECTION_VALUE);
        }
        requireRange("tier selection count", fingerprint.tierSelections().size(), 0, MAX_TIER_SELECTIONS);
        for (Map.Entry<String, Integer> tier : fingerprint.tierSelections().entrySet()) {
            if (tier.getKey().isBlank() || tier.getKey().length() > MAX_TIER_DOMAIN_LENGTH) {
                throw new IllegalArgumentException("Invalid multiblock pattern transfer tier domain: " + tier.getKey());
            }
            requireRange("tier value", tier.getValue(), 1, MAX_SELECTION_VALUE);
        }
        requireRange(
                "candidate selection count",
                fingerprint.candidateSelections().size(),
                0,
                MAX_CANDIDATE_SELECTIONS);
        for (Map.Entry<PreviewPredicateKey, Integer> candidate : fingerprint.candidateSelections().entrySet()) {
            PreviewPredicateKey key = candidate.getKey();
            requireRange("candidate source layer", key.sourceLayer(), 0, MAX_SELECTION_VALUE);
            requireRange("candidate y", key.y(), 0, MAX_SELECTION_VALUE);
            requireRange("candidate x", key.x(), 0, MAX_SELECTION_VALUE);
            requireRange("candidate index", candidate.getValue(), 0, MAX_SELECTION_VALUE);
        }
    }

    /**
     * Rejects payload bytes not consumed by the declared schema.
     */
    static void requireFullyConsumed(RegistryFriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Multiblock pattern transfer payload has " + buffer.readableBytes() +
                    " trailing bytes");
        }
    }

    private static ResourceLocation readResourceLocation(RegistryFriendlyByteBuf buffer, String field) {
        String value = buffer.readUtf(MAX_RESOURCE_LOCATION_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid multiblock pattern transfer " + field + ": " + value);
        }
        return id;
    }

    private static void writeResourceLocation(RegistryFriendlyByteBuf buffer,
                                              ResourceLocation id,
                                              String field) {
        validateResourceLocation(id, field);
        buffer.writeUtf(id.toString(), MAX_RESOURCE_LOCATION_LENGTH);
    }

    private static void validateResourceLocation(ResourceLocation id, String field) {
        if (id == null || id.toString().length() > MAX_RESOURCE_LOCATION_LENGTH) {
            throw new IllegalArgumentException("Invalid multiblock pattern transfer " + field + ": " + id);
        }
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, String field, int maximum) {
        return readBoundedInt(buffer, field, 0, maximum);
    }

    private static int readBoundedInt(RegistryFriendlyByteBuf buffer,
                                      String field,
                                      int minimum,
                                      int maximum) {
        int value = buffer.readVarInt();
        requireRange(field, value, minimum, maximum);
        return value;
    }

    private static long readBoundedLong(RegistryFriendlyByteBuf buffer,
                                        String field,
                                        long minimum,
                                        long maximum) {
        long value = buffer.readVarLong();
        requireRange(field, value, minimum, maximum);
        return value;
    }

    private static void requireRange(String field, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Multiblock pattern transfer " + field + " is outside [" + minimum +
                    ", " + maximum + "]: " + value);
        }
    }
}
