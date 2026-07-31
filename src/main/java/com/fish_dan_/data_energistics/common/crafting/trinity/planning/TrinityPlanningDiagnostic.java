package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable UI and log diagnostic retained when Trinity planning cannot produce an executable plan.
 *
 * @param code     stable programmatic reason
 * @param message  player-facing explanation
 * @param metadata deterministic structured details for logs and confirmation UI
 */
public record TrinityPlanningDiagnostic(
                                        TrinityPlanningDiagnosticCode code,
                                        Component message,
                                        Map<String, String> metadata) {

    /**
     * Isolates mutable component/map implementations from the retained planning result.
     */
    public TrinityPlanningDiagnostic {
        if (code == null || message == null || metadata == null) {
            throw new IllegalArgumentException("A Trinity planning diagnostic requires code, message and metadata");
        }
        message = message.copy();
        TreeMap<String, String> orderedMetadata = new TreeMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                throw new IllegalArgumentException("Trinity planning diagnostic metadata must be named");
            }
            orderedMetadata.put(key, value);
        });
        metadata = Collections.unmodifiableMap(orderedMetadata);
    }

    /**
     * Creates a diagnostic without structured details.
     *
     * @param code   stable reason
     * @param detail player-facing detail
     * @return immutable diagnostic
     */
    public static TrinityPlanningDiagnostic of(TrinityPlanningDiagnosticCode code, String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("A Trinity planning diagnostic requires a detail");
        }
        return new TrinityPlanningDiagnostic(code, Component.literal(detail), Map.of());
    }

    /**
     * Prevents callers from mutating the retained component through a concrete mutable implementation.
     */
    @Override
    public Component message() {
        return this.message.copy();
    }
}
