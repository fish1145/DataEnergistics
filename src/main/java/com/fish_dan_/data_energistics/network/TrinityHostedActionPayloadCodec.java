package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Strict bounded codec shared by Trinity hosted refund, auto-build, and response payloads. */
final class TrinityHostedActionPayloadCodec {

    static final int MAX_CONTAINER_ID = 1_000_000_000;
    private static final int MAX_RESOURCE_LOCATION_LENGTH = 256;
    private static final int MAX_STRUCTURE_NAME_LENGTH = 128;
    private static final int MAX_TIER_DOMAIN_LENGTH = 128;
    private static final int MAX_REPEAT_UNITS = 64;
    private static final int MAX_TIER_SELECTIONS = 64;
    private static final int MAX_CANDIDATE_SELECTIONS = 4_096;
    private static final int MAX_SELECTION_VALUE = 1_000_000;
    private static final long MAX_DEFINITION_REVISION = 1_000_000_000_000L;

    private TrinityHostedActionPayloadCodec() {}

    /** Reads a non-negative bounded menu identity. */
    static int readContainerId(RegistryFriendlyByteBuf buffer) {
        return readBoundedInt(buffer, "container id", 0, MAX_CONTAINER_ID);
    }

    /** Writes a previously validated menu identity. */
    static void writeContainerId(RegistryFriendlyByteBuf buffer, int containerId) {
        requireRange("container id", containerId, 0, MAX_CONTAINER_ID);
        buffer.writeVarInt(containerId);
    }

    /** Reads one required UUID used to bind a request or response to its exact menu host/session. */
    static UUID readUuid(RegistryFriendlyByteBuf buffer, String field) {
        UUID value = buffer.readUUID();
        if (value == null) {
            throw new IllegalArgumentException("Trinity hosted " + field + " cannot be null");
        }
        return value;
    }

    /** Writes one required UUID used to bind a request or response to its exact menu host/session. */
    static void writeUuid(RegistryFriendlyByteBuf buffer, UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Trinity hosted " + field + " cannot be null");
        }
        buffer.writeUUID(value);
    }

    /** Reads a positive bounded window generation. */
    static long readGeneration(RegistryFriendlyByteBuf buffer) {
        return readBoundedLong(buffer, "generation", 1L, TrinityHostedActionTicket.MAX_GENERATION);
    }

    /** Reads a positive bounded action sequence. */
    static long readSequence(RegistryFriendlyByteBuf buffer) {
        return readBoundedLong(buffer, "sequence", 1L, TrinityHostedActionTicket.MAX_SEQUENCE);
    }

    /** Writes a validated action sequence for a static menu action whose session identity is carried separately. */
    static void writeSequence(RegistryFriendlyByteBuf buffer, long sequence) {
        requireRange("sequence", sequence, 1L, TrinityHostedActionTicket.MAX_SEQUENCE);
        buffer.writeVarLong(sequence);
    }

    /** Writes a validated ticket suffix. */
    static void writeTicket(RegistryFriendlyByteBuf buffer, long generation, long sequence) {
        requireRange("generation", generation, 1L, TrinityHostedActionTicket.MAX_GENERATION);
        requireRange("sequence", sequence, 1L, TrinityHostedActionTicket.MAX_SEQUENCE);
        buffer.writeVarLong(generation);
        buffer.writeVarLong(sequence);
    }

    /** Reads the complete revision-bound auto-build selection without accepting duplicate map keys. */
    static TrinityAutoBuildSubmission readSubmission(RegistryFriendlyByteBuf buffer) {
        ResourceLocation controllerId = readResourceLocation(buffer, "controller id");
        long revision = readBoundedLong(
                buffer,
                "definition revision",
                0L,
                MAX_DEFINITION_REVISION);
        ResourceLocation structureMachineId = readResourceLocation(buffer, "structure machine id");
        String structureName = buffer.readUtf(MAX_STRUCTURE_NAME_LENGTH);
        int variantIndex = readBoundedInt(buffer, "variant index", 0, MAX_SELECTION_VALUE);

        int repeatCount = readCount(buffer, "repeat unit count", MAX_REPEAT_UNITS);
        List<Integer> repeats = new ArrayList<>(repeatCount);
        for (int index = 0; index < repeatCount; index++) {
            repeats.add(readBoundedInt(buffer, "repeat count", 1, MAX_SELECTION_VALUE));
        }

        int tierCount = readCount(buffer, "tier selection count", MAX_TIER_SELECTIONS);
        Map<String, Integer> tiers = new LinkedHashMap<>();
        for (int index = 0; index < tierCount; index++) {
            String domain = buffer.readUtf(MAX_TIER_DOMAIN_LENGTH);
            if (domain.isBlank()) {
                throw new IllegalArgumentException("Trinity hosted tier domain cannot be blank");
            }
            int value = readBoundedInt(buffer, "tier value", 1, MAX_SELECTION_VALUE);
            if (tiers.putIfAbsent(domain, value) != null) {
                throw new IllegalArgumentException("Duplicate Trinity hosted tier domain: " + domain);
            }
        }

        int candidateCount = readCount(buffer, "candidate selection count", MAX_CANDIDATE_SELECTIONS);
        Map<PreviewPredicateKey, Integer> candidates = new LinkedHashMap<>();
        for (int index = 0; index < candidateCount; index++) {
            PreviewPredicateKey key = new PreviewPredicateKey(
                    readBoundedInt(buffer, "candidate source layer", 0, MAX_SELECTION_VALUE),
                    readBoundedInt(buffer, "candidate y", 0, MAX_SELECTION_VALUE),
                    readBoundedInt(buffer, "candidate x", 0, MAX_SELECTION_VALUE));
            int value = readBoundedInt(buffer, "candidate index", 0, MAX_SELECTION_VALUE);
            if (candidates.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate Trinity hosted candidate key: " + key);
            }
        }
        boolean buildRequested = buffer.readBoolean();
        ProjectionFingerprint fingerprint = new ProjectionFingerprint(
                controllerId,
                revision,
                new JsonMultiBlockStructureKey(structureMachineId, structureName),
                variantIndex,
                repeats,
                tiers,
                candidates);
        return new TrinityAutoBuildSubmission(fingerprint, buildRequested);
    }

    /** Writes every recipe-affecting field in deterministic record order. */
    static void writeSubmission(RegistryFriendlyByteBuf buffer, TrinityAutoBuildSubmission submission) {
        ProjectionFingerprint fingerprint = submission.projectionFingerprint();
        writeResourceLocation(buffer, fingerprint.controllerId(), "controller id");
        requireRange("definition revision", fingerprint.definitionRevision(), 0L, MAX_DEFINITION_REVISION);
        buffer.writeVarLong(fingerprint.definitionRevision());
        writeResourceLocation(buffer, fingerprint.structureKey().machineId(), "structure machine id");
        if (fingerprint.structureKey().structureName().length() > MAX_STRUCTURE_NAME_LENGTH) {
            throw new IllegalArgumentException("Trinity hosted structure name exceeds " + MAX_STRUCTURE_NAME_LENGTH);
        }
        buffer.writeUtf(fingerprint.structureKey().structureName(), MAX_STRUCTURE_NAME_LENGTH);
        requireRange("variant index", fingerprint.variantIndex(), 0, MAX_SELECTION_VALUE);
        buffer.writeVarInt(fingerprint.variantIndex());

        writeCount(buffer, "repeat unit count", fingerprint.repeatCounts().size(), MAX_REPEAT_UNITS);
        for (int repeat : fingerprint.repeatCounts()) {
            requireRange("repeat count", repeat, 1, MAX_SELECTION_VALUE);
            buffer.writeVarInt(repeat);
        }
        writeCount(buffer, "tier selection count", fingerprint.tierSelections().size(), MAX_TIER_SELECTIONS);
        for (Map.Entry<String, Integer> tier : fingerprint.tierSelections().entrySet()) {
            if (tier.getKey().isBlank() || tier.getKey().length() > MAX_TIER_DOMAIN_LENGTH) {
                throw new IllegalArgumentException("Invalid Trinity hosted tier domain: " + tier.getKey());
            }
            buffer.writeUtf(tier.getKey(), MAX_TIER_DOMAIN_LENGTH);
            requireRange("tier value", tier.getValue(), 1, MAX_SELECTION_VALUE);
            buffer.writeVarInt(tier.getValue());
        }
        writeCount(
                buffer,
                "candidate selection count",
                fingerprint.candidateSelections().size(),
                MAX_CANDIDATE_SELECTIONS);
        for (Map.Entry<PreviewPredicateKey, Integer> candidate : fingerprint.candidateSelections().entrySet()) {
            PreviewPredicateKey key = candidate.getKey();
            requireRange("candidate source layer", key.sourceLayer(), 0, MAX_SELECTION_VALUE);
            requireRange("candidate y", key.y(), 0, MAX_SELECTION_VALUE);
            requireRange("candidate x", key.x(), 0, MAX_SELECTION_VALUE);
            requireRange("candidate index", candidate.getValue(), 0, MAX_SELECTION_VALUE);
            buffer.writeVarInt(key.sourceLayer());
            buffer.writeVarInt(key.y());
            buffer.writeVarInt(key.x());
            buffer.writeVarInt(candidate.getValue());
        }
        buffer.writeBoolean(submission.buildRequested());
    }

    /** Reads one exact response identity and bounded status. */
    static TrinityHostedActionResult readResult(RegistryFriendlyByteBuf buffer) {
        HostUiKey key = new HostUiKey(readResourceLocation(buffer, "host UI key"));
        long generation = readGeneration(buffer);
        long sequence = readSequence(buffer);
        TrinityHostedActionStatus status = TrinityHostedActionStatus.fromNetworkId(
                readBoundedInt(buffer, "status", 0, TrinityHostedActionStatus.values().length - 1));
        return new TrinityHostedActionResult(key, generation, sequence, status);
    }

    /** Writes the exact response identity echoed to the client. */
    static void writeResult(RegistryFriendlyByteBuf buffer, TrinityHostedActionResult result) {
        writeResourceLocation(buffer, result.key().id(), "host UI key");
        writeTicket(buffer, result.generation(), result.sequence());
        buffer.writeVarInt(result.status().networkId());
    }

    /** Rejects payload bytes not consumed by the declared schema. */
    static void requireFullyConsumed(RegistryFriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Trinity hosted action payload has " + buffer.readableBytes() +
                    " trailing bytes");
        }
    }

    private static ResourceLocation readResourceLocation(RegistryFriendlyByteBuf buffer, String field) {
        String value = buffer.readUtf(MAX_RESOURCE_LOCATION_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid Trinity hosted " + field + ": " + value);
        }
        return id;
    }

    private static void writeResourceLocation(RegistryFriendlyByteBuf buffer,
                                              ResourceLocation id,
                                              String field) {
        if (id == null || id.toString().length() > MAX_RESOURCE_LOCATION_LENGTH) {
            throw new IllegalArgumentException("Invalid Trinity hosted " + field + ": " + id);
        }
        buffer.writeUtf(id.toString(), MAX_RESOURCE_LOCATION_LENGTH);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, String field, int maximum) {
        return readBoundedInt(buffer, field, 0, maximum);
    }

    private static void writeCount(RegistryFriendlyByteBuf buffer, String field, int count, int maximum) {
        requireRange(field, count, 0, maximum);
        buffer.writeVarInt(count);
    }

    private static int readBoundedInt(RegistryFriendlyByteBuf buffer, String field, int minimum, int maximum) {
        int value = buffer.readVarInt();
        requireRange(field, value, minimum, maximum);
        return value;
    }

    private static long readBoundedLong(RegistryFriendlyByteBuf buffer, String field, long minimum, long maximum) {
        long value = buffer.readVarLong();
        requireRange(field, value, minimum, maximum);
        return value;
    }

    private static void requireRange(String field, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Trinity hosted " + field + " is outside [" + minimum + ", " +
                    maximum + "]: " + value);
        }
    }
}
