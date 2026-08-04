package com.fish_dan_.data_energistics.common.multiblock;

import net.minecraft.network.chat.Component;

/**
 * Maps low-level multiblock diagnostics to player-facing text.
 */
public final class MultiBlockFailureText {

    private static final String BLOCK_PREDICATE_DID_NOT_MATCH = "Block predicate did not match";
    private static final String STRUCTURE_PATTERN_DID_NOT_MATCH = "Structure pattern did not match";
    private static final String BLOCK_PREDICATE_KEY = "text.data_energistics.multiblock.failure.block_predicate";
    private static final String STRUCTURE_PATTERN_KEY = "text.data_energistics.multiblock.failure.structure_pattern";
    private static final String TRINITY_MAIN_STRUCTURE_NOT_FORMED = "Main structure is not formed";
    private static final String TRINITY_PATTERN_CORE_MISSING_BLOCK_ENTITY = " has no matching block entity";
    private static final String TRINITY_PATTERN_CORE_REJECTED_STATE = " has rejected persisted state";
    private static final String TRINITY_PATTERN_CORE_MOUNTED_ELSEWHERE = " is already mounted by another active host";
    private static final String TRINITY_PATTERN_CORE_PREFIX = "Trinity pattern processing core";
    private static final String TRINITY_DUPLICATE_CORE_POSITION_PREFIX = "Duplicate Trinity pattern core position";
    private static final String TRINITY_CORE_CAPACITY_MISMATCH_PREFIX = "Trinity pattern core capacity mismatch";
    private static final String TRINITY_DUPLICATE_CORE_UUID_PREFIX = "Duplicate Trinity pattern core UUID";

    private MultiBlockFailureText() {}

    public static Component describe(String reason) {
        if (reason == null || reason.isBlank()) {
            return Component.empty();
        }
        return switch (reason) {
            case BLOCK_PREDICATE_DID_NOT_MATCH -> Component.translatable(BLOCK_PREDICATE_KEY);
            case STRUCTURE_PATTERN_DID_NOT_MATCH -> Component.translatable(STRUCTURE_PATTERN_KEY);
            default -> Component.literal(reason);
        };
    }

    /**
     * Maps every failure reachable from the Trinity Data Core UI without exposing internal English diagnostics.
     */
    public static Component describeTrinityDataCore(String reason) {
        if (reason == null || reason.isBlank()) {
            return Component.empty();
        }
        if (isKnownReason(reason)) {
            return describe(reason);
        }
        if (TRINITY_MAIN_STRUCTURE_NOT_FORMED.equals(reason)) {
            return Component.translatable("text.data_energistics.multiblock.failure.trinity.main_not_formed");
        }
        if (reason.startsWith(TRINITY_PATTERN_CORE_PREFIX) &&
                reason.endsWith(TRINITY_PATTERN_CORE_MISSING_BLOCK_ENTITY)) {
            return Component.translatable(
                    "text.data_energistics.multiblock.failure.trinity.pattern_core_missing_block_entity");
        }
        if (reason.startsWith(TRINITY_PATTERN_CORE_PREFIX) &&
                reason.endsWith(TRINITY_PATTERN_CORE_REJECTED_STATE)) {
            return Component.translatable(
                    "text.data_energistics.multiblock.failure.trinity.pattern_core_rejected_state");
        }
        if (reason.startsWith(TRINITY_PATTERN_CORE_PREFIX) &&
                reason.endsWith(TRINITY_PATTERN_CORE_MOUNTED_ELSEWHERE)) {
            return Component.translatable(
                    "text.data_energistics.multiblock.failure.trinity.pattern_core_mounted_elsewhere");
        }
        if (reason.startsWith(TRINITY_DUPLICATE_CORE_POSITION_PREFIX) ||
                reason.startsWith(TRINITY_CORE_CAPACITY_MISMATCH_PREFIX) ||
                reason.startsWith(TRINITY_DUPLICATE_CORE_UUID_PREFIX)) {
            return Component.translatable("text.data_energistics.multiblock.failure.trinity.pattern_catalog_invalid");
        }
        return Component.translatable("text.data_energistics.multiblock.failure.trinity.structure_validation");
    }

    public static Component summarize(String reason, int maxLength) {
        if (reason == null || reason.isBlank()) {
            return Component.empty();
        }
        if (isKnownReason(reason)) {
            return describe(reason);
        }
        return Component.literal(abbreviate(reason, maxLength));
    }

    private static boolean isKnownReason(String reason) {
        return BLOCK_PREDICATE_DID_NOT_MATCH.equals(reason) || STRUCTURE_PATTERN_DID_NOT_MATCH.equals(reason);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
