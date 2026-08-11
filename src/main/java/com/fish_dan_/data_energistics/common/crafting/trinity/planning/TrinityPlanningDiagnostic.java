package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
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
     * Creates a player-visible diagnostic whose message is resolved through the active language.
     *
     * @param code           stable reason
     * @param translationKey player-facing translation key
     * @return immutable diagnostic
     */
    public static TrinityPlanningDiagnostic ofTranslationKey(TrinityPlanningDiagnosticCode code,
                                                             String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("A Trinity planning diagnostic requires a translation key");
        }
        return new TrinityPlanningDiagnostic(code, Component.translatable(translationKey), Map.of());
    }

    /**
     * Creates an exact literal diagnostic for tests and non-player-facing internal boundaries.
     *
     * @param code   stable reason
     * @param detail non-localized detail
     * @return immutable diagnostic
     */
    public static TrinityPlanningDiagnostic ofLiteral(TrinityPlanningDiagnosticCode code, String detail) {
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
     * @return the material projection accumulated before planning stopped
     */
    public Optional<PartialPlan> partialPlan() {
        return this.detail instanceof PartialPlan partial ? Optional.of(partial) : Optional.empty();
    }

    /**
     * Retains the diagnostic identity and message while attaching typed planner evidence.
     */
    public TrinityPlanningDiagnostic withDetail(Detail value) {
        return new TrinityPlanningDiagnostic(this.code, this.message, this.metadata, value);
    }

    /**
     * Closed diagnostic-detail family keeps typed planner evidence separate from string log metadata.
     */
    public sealed interface Detail permits InputShortage, PartialPlan, NoDetail {}

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

    /**
     * Immutable material view accumulated along the selected planning branch before a terminal boundary was reached.
     *
     * @param usedItems         network inventory already reserved by the partial branch
     * @param emittedItems      outputs of recipe firings already selected by the partial branch
     * @param missingItems      positive demands that remained unresolved when planning stopped
     * @param inputRequirements exact external-input allocations proven for the retained branch
     */
    public record PartialPlan(
                              Map<AEKey, BigInteger> usedItems,
                              Map<AEKey, BigInteger> emittedItems,
                              Map<AEKey, BigInteger> missingItems,
                              Map<AEKey, InputRequirement> inputRequirements)
            implements Detail {

        public PartialPlan(
                           Map<AEKey, BigInteger> usedItems,
                           Map<AEKey, BigInteger> emittedItems,
                           Map<AEKey, BigInteger> missingItems) {
            this(usedItems, emittedItems, missingItems, Map.of());
        }

        public PartialPlan {
            usedItems = copyPositiveAmounts(usedItems, "used");
            emittedItems = copyPositiveAmounts(emittedItems, "emitted");
            missingItems = copyPositiveAmounts(missingItems, "missing");
            Map<AEKey, BigInteger> copiedMissingItems = missingItems;
            LinkedHashMap<AEKey, InputRequirement> copiedRequirements = new LinkedHashMap<>();
            inputRequirements.forEach((key, requirement) -> {
                if (!requirement.missing().equals(copiedMissingItems.get(key))) {
                    throw new IllegalArgumentException(
                            "A Trinity exact input requirement must match its projected missing amount");
                }
                copiedRequirements.put(key, requirement);
            });
            inputRequirements = Collections.unmodifiableMap(copiedRequirements);
        }

        private static Map<AEKey, BigInteger> copyPositiveAmounts(
                                                                  Map<AEKey, BigInteger> source,
                                                                  String role) {
            LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
            source.forEach((key, amount) -> {
                if (key == null || amount == null || amount.signum() <= 0) {
                    throw new IllegalArgumentException("Trinity partial " + role + " amounts must be positive");
                }
                copied.put(key, amount);
            });
            return Collections.unmodifiableMap(copied);
        }
    }

    /**
     * Exact allocation result for one external input after the complete selected route has been propagated.
     *
     * @param required  total amount required by the selected route
     * @param available amount actually allocated from the captured network inventory
     * @param missing   positive amount still absent from that inventory
     */
    public record InputRequirement(
                                   BigInteger required,
                                   BigInteger available,
                                   BigInteger missing) {

        public InputRequirement {
            if (required.signum() <= 0 || available.signum() < 0 || missing.signum() <= 0 ||
                    !required.equals(available.add(missing))) {
                throw new IllegalArgumentException(
                        "A Trinity input requirement must preserve required = available + missing");
            }
        }
    }
}
