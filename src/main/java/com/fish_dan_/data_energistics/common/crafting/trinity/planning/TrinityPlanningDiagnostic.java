package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable UI and log diagnostic retained when Trinity planning cannot produce an executable plan.
 *
 * @param code     stable programmatic reason
 * @param message  player-facing explanation
 * @param metadata deterministic structured details for logs and confirmation UI
 * @param detail   exact typed detail when Trinity has completely solved a diagnostic boundary
 */
public record TrinityPlanningDiagnostic(
                                        TrinityPlanningDiagnosticCode code,
                                        Component message,
                                        Map<String, String> metadata,
                                        Detail detail) {

    public TrinityPlanningDiagnostic(
                                     TrinityPlanningDiagnosticCode code,
                                     Component message,
                                     Map<String, String> metadata) {
        this(code, message, metadata, NoDetail.INSTANCE);
    }

    public TrinityPlanningDiagnostic(
                                     TrinityPlanningDiagnosticCode code,
                                     Component message,
                                     Map<String, String> metadata,
                                     InputShortage inputShortage) {
        this(code, message, metadata, (Detail) inputShortage);
    }

    /**
     * Isolates mutable component/map implementations from the retained planning result.
     */
    public TrinityPlanningDiagnostic {
        if (code == null || message == null || metadata == null || detail == null) {
            throw new IllegalArgumentException(
                    "A Trinity planning diagnostic requires code, message, metadata and detail state");
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

    /**
     * @return exact typed shortage when this diagnostic conclusively resolved an unavailable input
     */
    public Optional<InputShortage> inputShortage() {
        return this.detail instanceof InputShortage shortage ? Optional.of(shortage) : Optional.empty();
    }

    /**
     * Closed diagnostic-detail family keeps typed planner evidence separate from string log metadata.
     */
    public sealed interface Detail permits InputShortage, NoDetail {}

    private enum NoDetail implements Detail {
        INSTANCE
    }

    /**
     * Exact immutable shortage retained separately from localized text and string log metadata.
     *
     * @param key       immutable AE key that cannot meet the solved initial requirement
     * @param required  exact required amount
     * @param available exact amount captured at planning start
     * @param missing   exact positive difference between required and available
     */
    public record InputShortage(
                                AEKey key,
                                BigInteger required,
                                BigInteger available,
                                BigInteger missing)
            implements Detail {

        public InputShortage {
            if (key == null || required == null || available == null ||
                    required.signum() <= 0 || available.signum() < 0 ||
                    !required.subtract(available).equals(missing) || missing.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity input shortage must be exact and positive");
            }
        }
    }
}
