package com.fish_dan_.data_energistics.menu.patternprovider;

import org.jspecify.annotations.Nullable;

/** Opaque menu-action target that keeps an exact leaf bound to its synchronized aggregate group. */
public record PatternProviderLeafActionTarget(long groupId, long leafId) {

    private static final char SEPARATOR = '\n';

    public PatternProviderLeafActionTarget {
        if (groupId <= 0L || leafId <= 0L) {
            throw new IllegalArgumentException("Pattern provider leaf action ids must be positive");
        }
    }

    public String encode() {
        return this.groupId + String.valueOf(SEPARATOR) + this.leafId;
    }

    public String encodeRename(String name) {
        return encode() + SEPARATOR + name;
    }

    @Nullable
    public static PatternProviderLeafActionTarget decode(@Nullable String payload) {
        if (payload == null) {
            return null;
        }
        int separator = payload.indexOf(SEPARATOR);
        if (separator <= 0 || separator == payload.length() - 1 ||
                payload.indexOf(SEPARATOR, separator + 1) >= 0) {
            return null;
        }
        return parse(payload.substring(0, separator), payload.substring(separator + 1));
    }

    @Nullable
    public static Rename decodeRename(@Nullable String payload) {
        if (payload == null) {
            return null;
        }
        int first = payload.indexOf(SEPARATOR);
        int second = first < 0 ? -1 : payload.indexOf(SEPARATOR, first + 1);
        if (first <= 0 || second <= first || payload.indexOf(SEPARATOR, second + 1) >= 0) {
            return null;
        }
        PatternProviderLeafActionTarget target = parse(
                payload.substring(0, first), payload.substring(first + 1, second));
        return target == null ? null : new Rename(target, payload.substring(second + 1));
    }

    @Nullable
    private static PatternProviderLeafActionTarget parse(String groupId, String leafId) {
        try {
            return new PatternProviderLeafActionTarget(Long.parseLong(groupId), Long.parseLong(leafId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record Rename(PatternProviderLeafActionTarget target, String name) {}
}
