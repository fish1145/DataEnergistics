package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Validates and owns the retained planning result.
     */
    public TrinityPlanningDiagnostic {
        message = message.copy();
        Object2ObjectAVLTreeMap<String, String> orderedMetadata = new Object2ObjectAVLTreeMap<>();
        metadata.forEach((key, value) -> {
            if (key.isBlank()) {
                throw new IllegalArgumentException("Trinity planning diagnostic metadata must be named");
            }
            orderedMetadata.put(key, value);
        });
        metadata = Object2ObjectMaps.unmodifiable(orderedMetadata);
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
        if (translationKey.isBlank()) {
            throw new IllegalArgumentException("A Trinity planning diagnostic requires a translation key");
        }
        return new TrinityPlanningDiagnostic(code, Component.translatable(translationKey), Map.of());
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
        return switch (this.detail) {
            case PartialPlan partial -> Optional.of(partial);
            case CompositeEvidence evidence -> Optional.of(evidence.materials());
            default -> Optional.empty();
        };
    }

    /**
     * @return fully scheduled non-executable cycles retained by this diagnostic in stable component order
     */
    public List<TrinityCycleDiagnosticEvidence> cycleEvidence() {
        return this.detail instanceof CompositeEvidence evidence ? evidence.cycles() : List.of();
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
    public sealed interface Detail permits InputShortage, PartialPlan, CompositeEvidence, NoDetail {}

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
            if (required.signum() <= 0 || available.signum() < 0 ||
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
     * @param selectedFirings   actual selected variants and counts not represented by separate cycle evidence;
     *                          these retain input bindings but do not assert a complete executable schedule
     */
    public record PartialPlan(
                              Map<AEKey, BigInteger> usedItems,
                              Map<AEKey, BigInteger> emittedItems,
                              Map<AEKey, BigInteger> missingItems,
                              Map<AEKey, InputRequirement> inputRequirements,
                              List<TrinityVariantFiring> selectedFirings)
            implements Detail {

        public PartialPlan {
            usedItems = validatePositiveAmounts(usedItems, "used");
            emittedItems = validatePositiveAmounts(emittedItems, "emitted");
            missingItems = validatePositiveAmounts(missingItems, "missing");
            for (Map.Entry<AEKey, InputRequirement> requirement : inputRequirements.entrySet()) {
                if (!requirement.getValue().missing().equals(missingItems.get(requirement.getKey()))) {
                    throw new IllegalArgumentException(
                            "A Trinity exact input requirement must match its projected missing amount");
                }
            }
            inputRequirements = Collections.unmodifiableMap(inputRequirements);
            // Variant/count records are immutable. Reusing a retained snapshot does not copy this list again.
            selectedFirings = List.copyOf(selectedFirings);
        }

        private static Map<AEKey, BigInteger> validatePositiveAmounts(
                                                                      Map<AEKey, BigInteger> source,
                                                                      String role) {
            source.forEach((key, amount) -> {
                if (amount.signum() <= 0) {
                    throw new IllegalArgumentException("Trinity partial " + role + " amounts must be positive");
                }
            });
            return Collections.unmodifiableMap(source);
        }
    }

    /**
     * Immutable combination of the complete material projection and every cycle whose firing vector and compressed
     * execution order were independently verified.
     *
     * @param materials accumulated material view
     * @param cycles    fully proved diagnostic cycles
     */
    public record CompositeEvidence(
                                    PartialPlan materials,
                                    List<TrinityCycleDiagnosticEvidence> cycles)
            implements Detail {

        public CompositeEvidence {
            ObjectArrayList<TrinityCycleDiagnosticEvidence> ordered = new ObjectArrayList<>(cycles);
            ordered.sort(Comparator.comparingInt(TrinityCycleDiagnosticEvidence::componentIndex));
            IntSet components = new IntOpenHashSet();
            for (TrinityCycleDiagnosticEvidence cycle : ordered) {
                if (!components.add(cycle.componentIndex())) {
                    throw new IllegalArgumentException(
                            "Composite Trinity diagnostic evidence requires unique cycles");
                }
            }
            cycles = ObjectLists.unmodifiable(ordered);
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
