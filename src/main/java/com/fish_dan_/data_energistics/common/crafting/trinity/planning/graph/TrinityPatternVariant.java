package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One immutable, fully bound transition in the Trinity crafting hypergraph.
 *
 * @param patternIdentity     stable parent pattern semantics
 * @param primaryOutput       primary output used to resolve the live provider pattern on the server thread
 * @param ordinal             deterministic Cartesian binding ordinal for that pattern
 * @param alternativeOrdinals selected alternative index for every ordered input slot
 * @param inputs              exact per-firing consumption
 * @param declaredOutputs     exact pattern-declared outputs, excluding input remainders
 * @param outputs             exact declared outputs plus remaining keys
 * @param netChange           exact signed {@code outputs - inputs}
 */
public record TrinityPatternVariant(
                                    TrinityPatternIdentity patternIdentity,
                                    AEKey primaryOutput,
                                    int ordinal,
                                    List<Integer> alternativeOrdinals,
                                    List<TrinityBoundPatternInput> bindings,
                                    Map<AEKey, BigInteger> inputs,
                                    Map<AEKey, BigInteger> declaredOutputs,
                                    Map<AEKey, BigInteger> outputs,
                                    Map<AEKey, BigInteger> netChange)
        implements Comparable<TrinityPatternVariant> {

    /**
     * Copies all planner values and verifies the retained transition equation.
     */
    public TrinityPatternVariant {
        if (ordinal < 0 || alternativeOrdinals.size() != bindings.size()) {
            throw new IllegalArgumentException("A Trinity pattern variant requires one legal binding per input slot");
        }
        alternativeOrdinals = List.copyOf(alternativeOrdinals);
        bindings = List.copyOf(bindings);
        for (int slot = 0; slot < bindings.size(); slot++) {
            TrinityBoundPatternInput binding = bindings.get(slot);
            int alternative = alternativeOrdinals.get(slot);
            if (binding.slotIndex() != slot ||
                    binding.alternativeIndex() != alternative) {
                throw new IllegalArgumentException("A Trinity pattern variant binding order is inconsistent");
            }
        }
        inputs = copyPositive(inputs, "inputs");
        declaredOutputs = copyPositive(declaredOutputs, "declared outputs");
        outputs = copyPositive(outputs, "outputs");
        if (!declaredOutputs.containsKey(primaryOutput)) {
            throw new IllegalArgumentException("A Trinity pattern variant must retain its primary output");
        }
        for (Map.Entry<AEKey, BigInteger> entry : declaredOutputs.entrySet()) {
            BigInteger total = outputs.get(entry.getKey());
            if (total == null || total.compareTo(entry.getValue()) < 0) {
                throw new IllegalArgumentException(
                        "Trinity declared outputs must be contained in the complete transition outputs");
            }
        }
        netChange = copySignedNonZero(netChange);
        if (!netChange.equals(calculateNetChange(inputs, outputs))) {
            throw new IllegalArgumentException("A Trinity pattern variant net change must equal outputs minus inputs");
        }
    }

    /**
     * Constructs one exact transition from its selected bindings and declared pattern outputs.
     *
     * @param patternIdentity     stable parent pattern semantics
     * @param primaryOutput       primary declared pattern output
     * @param ordinal             stable Cartesian binding ordinal
     * @param alternativeOrdinals selected alternative indexes
     * @param bindings            selected immutable bindings
     * @param declaredOutputs     immutable declared pattern outputs
     * @return validated exact transition
     */
    public static TrinityPatternVariant create(TrinityPatternIdentity patternIdentity,
                                               AEKey primaryOutput,
                                               int ordinal,
                                               List<Integer> alternativeOrdinals,
                                               List<TrinityBoundPatternInput> bindings,
                                               List<GenericStack> declaredOutputs) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> inputs = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> declared = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityBoundPatternInput binding : bindings) {
            merge(inputs, binding.template().what(), binding.consumedAmount());
            if (binding.remainingKey() != null) {
                merge(outputs, binding.remainingKey(), binding.remainingAmount());
            }
        }
        for (GenericStack output : declaredOutputs) {
            if (output == null || output.what() == null || output.amount() <= 0L) {
                throw new IllegalArgumentException("A Trinity pattern variant cannot contain an invalid output");
            }
            BigInteger amount = BigInteger.valueOf(output.amount());
            merge(declared, output.what(), amount);
            merge(outputs, output.what(), amount);
        }
        return new TrinityPatternVariant(
                patternIdentity,
                primaryOutput,
                ordinal,
                alternativeOrdinals,
                bindings,
                inputs,
                declared,
                outputs,
                calculateNetChange(inputs, outputs));
    }

    @Override
    public int compareTo(TrinityPatternVariant other) {
        int patternOrder = this.patternIdentity.compareTo(other.patternIdentity);
        return patternOrder != 0 ? patternOrder : Integer.compare(this.ordinal, other.ordinal);
    }

    private static Map<AEKey, BigInteger> copyPositive(Map<AEKey, BigInteger> source, String role) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity pattern variant " + role + " must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedNonZero(Map<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("Trinity pattern variant net change must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> calculateNetChange(Map<AEKey, BigInteger> inputs,
                                                             Map<AEKey, BigInteger> outputs) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        inputs.forEach((key, amount) -> merge(net, key, amount.negate()));
        outputs.forEach((key, amount) -> merge(net, key, amount));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(net);
    }

    private static void merge(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        amounts.merge(key, amount, BigInteger::add);
    }
}
