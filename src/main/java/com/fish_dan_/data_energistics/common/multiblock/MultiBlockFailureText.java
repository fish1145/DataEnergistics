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
