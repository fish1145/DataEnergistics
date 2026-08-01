package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exact Gaussian elimination over rational coefficients, followed by inventory-aware stable block ordering.
 */
final class TrinityDeterministicCycleSequenceImpl implements TrinityDeterministicCycleSequence {

    @Override
    public Optional<List<TrinityVariantFiring>> resolve(
                                                        TrinityStronglyConnectedComponent component,
                                                        AEKey target,
                                                        Map<AEKey, BigInteger> available) {
        if (component == null || !component.cyclic() || target == null || available == null) {
            throw new IllegalArgumentException("A deterministic Trinity cycle sequence requires complete inputs");
        }
        if (!component.keys().contains(target)) {
            throw new IllegalArgumentException("The deterministic Trinity cycle target must belong to its component");
        }

        Optional<List<TrinityPatternVariant>> deterministic = deterministicVariants(component);
        if (deterministic.isEmpty()) {
            return Optional.empty();
        }
        List<TrinityPatternVariant> variants = deterministic.orElseThrow();
        Optional<List<BigInteger>> ratio = solveMinimalPositiveRatio(component.keys(), variants, target);
        if (ratio.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(orderBlocks(variants, ratio.orElseThrow(), available));
    }

    private static Optional<List<TrinityPatternVariant>> deterministicVariants(
                                                                               TrinityStronglyConnectedComponent component) {
        LinkedHashSet<TrinityPatternVariant> selected = new LinkedHashSet<>();
        for (AEKey key : component.keys()) {
            List<TrinityPatternVariant> producers = component.cycleVariants().stream()
                    .filter(variant -> variant.outputs().containsKey(key))
                    .toList();
            if (producers.size() != 1) {
                return Optional.empty();
            }
            selected.add(producers.getFirst());
        }
        if (selected.size() != component.cycleVariants().size()) {
            return Optional.empty();
        }
        return Optional.of(selected.stream().sorted().toList());
    }

    private static Optional<List<BigInteger>> solveMinimalPositiveRatio(
                                                                        List<AEKey> internalKeys,
                                                                        List<TrinityPatternVariant> variants,
                                                                        AEKey target) {
        List<AEKey> balancedKeys = internalKeys.stream().filter(key -> !key.equals(target)).toList();
        Rational[][] matrix = new Rational[balancedKeys.size()][variants.size()];
        for (int row = 0; row < balancedKeys.size(); row++) {
            AEKey key = balancedKeys.get(row);
            for (int column = 0; column < variants.size(); column++) {
                matrix[row][column] = Rational.of(
                        variants.get(column).netChange().getOrDefault(key, BigInteger.ZERO));
            }
        }

        RowReduction reduction = reduce(matrix, variants.size());
        if (variants.size() - reduction.rank() != 1) {
            return Optional.empty();
        }
        int freeColumn = firstFreeColumn(variants.size(), reduction.pivotColumns());
        Rational[] solution = new Rational[variants.size()];
        Arrays.fill(solution, Rational.ZERO);
        solution[freeColumn] = Rational.ONE;
        for (int row = reduction.rank() - 1; row >= 0; row--) {
            int pivotColumn = reduction.pivotColumns().get(row);
            Rational sum = Rational.ZERO;
            for (int column = pivotColumn + 1; column < variants.size(); column++) {
                sum = sum.add(reduction.matrix()[row][column].multiply(solution[column]));
            }
            solution[pivotColumn] = sum.negate();
        }
        if (Arrays.stream(solution).anyMatch(value -> value.signum() <= 0)) {
            return Optional.empty();
        }

        BigInteger denominatorLcm = BigInteger.ONE;
        for (Rational value : solution) {
            denominatorLcm = lcm(denominatorLcm, value.denominator());
        }
        ArrayList<BigInteger> integers = new ArrayList<>(solution.length);
        BigInteger commonDivisor = BigInteger.ZERO;
        for (Rational value : solution) {
            BigInteger integer = value.numerator().multiply(denominatorLcm.divide(value.denominator()));
            integers.add(integer);
            commonDivisor = commonDivisor.equals(BigInteger.ZERO) ? integer.abs() : commonDivisor.gcd(integer.abs());
        }
        for (int index = 0; index < integers.size(); index++) {
            integers.set(index, integers.get(index).divide(commonDivisor));
        }
        if (!isProductiveExactCycle(internalKeys, variants, integers, target)) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(integers));
    }

    private static RowReduction reduce(Rational[][] source, int columns) {
        Rational[][] matrix = new Rational[source.length][columns];
        for (int row = 0; row < source.length; row++) {
            matrix[row] = Arrays.copyOf(source[row], columns);
        }
        ArrayList<Integer> pivots = new ArrayList<>();
        int pivotRow = 0;
        for (int column = 0; column < columns && pivotRow < matrix.length; column++) {
            int selectedRow = pivotRow;
            while (selectedRow < matrix.length && matrix[selectedRow][column].signum() == 0) {
                selectedRow++;
            }
            if (selectedRow == matrix.length) {
                continue;
            }
            Rational[] swap = matrix[pivotRow];
            matrix[pivotRow] = matrix[selectedRow];
            matrix[selectedRow] = swap;

            Rational pivot = matrix[pivotRow][column];
            for (int currentColumn = column; currentColumn < columns; currentColumn++) {
                matrix[pivotRow][currentColumn] = matrix[pivotRow][currentColumn].divide(pivot);
            }
            for (int row = 0; row < matrix.length; row++) {
                if (row == pivotRow || matrix[row][column].signum() == 0) {
                    continue;
                }
                Rational factor = matrix[row][column];
                for (int currentColumn = column; currentColumn < columns; currentColumn++) {
                    matrix[row][currentColumn] = matrix[row][currentColumn]
                            .subtract(factor.multiply(matrix[pivotRow][currentColumn]));
                }
            }
            pivots.add(column);
            pivotRow++;
        }
        return new RowReduction(matrix, List.copyOf(pivots), pivotRow);
    }

    private static int firstFreeColumn(int columns, List<Integer> pivotColumns) {
        Set<Integer> pivots = Set.copyOf(pivotColumns);
        for (int column = 0; column < columns; column++) {
            if (!pivots.contains(column)) {
                return column;
            }
        }
        throw new IllegalStateException("A one-dimensional Trinity cycle basis has no free column");
    }

    private static boolean isProductiveExactCycle(
                                                  List<AEKey> internalKeys,
                                                  List<TrinityPatternVariant> variants,
                                                  List<BigInteger> ratio,
                                                  AEKey target) {
        for (AEKey key : internalKeys) {
            BigInteger net = BigInteger.ZERO;
            for (int index = 0; index < variants.size(); index++) {
                net = net.add(variants.get(index)
                        .netChange()
                        .getOrDefault(key, BigInteger.ZERO)
                        .multiply(ratio.get(index)));
            }
            if (key.equals(target)) {
                if (net.signum() <= 0) {
                    return false;
                }
            } else if (net.signum() != 0) {
                return false;
            }
        }
        return true;
    }

    private static List<TrinityVariantFiring> orderBlocks(
                                                          List<TrinityPatternVariant> variants,
                                                          List<BigInteger> ratio,
                                                          Map<AEKey, BigInteger> available) {
        ArrayList<TrinityVariantFiring> remaining = new ArrayList<>(variants.size());
        for (int index = 0; index < variants.size(); index++) {
            remaining.add(new TrinityVariantFiring(variants.get(index), ratio.get(index)));
        }
        LinkedHashMap<AEKey, BigInteger> balances = copyAvailable(available);
        ArrayList<TrinityVariantFiring> ordered = new ArrayList<>(remaining.size());
        while (!remaining.isEmpty()) {
            TrinityVariantFiring selected = remaining.stream()
                    .filter(firing -> hasInputs(balances, requiredAtStart(firing)))
                    .findFirst()
                    .orElse(remaining.getFirst());
            remaining.remove(selected);
            ordered.add(selected);
            selected.variant().netChange().forEach((key, amount) -> balances.merge(
                    key,
                    amount.multiply(selected.count()),
                    BigInteger::add));
        }
        return List.copyOf(ordered);
    }

    private static Map<AEKey, BigInteger> requiredAtStart(TrinityVariantFiring firing) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        firing.variant().inputs().forEach((key, input) -> {
            BigInteger net = firing.variant().netChange().getOrDefault(key, BigInteger.ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(firing.count().subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return Collections.unmodifiableMap(required);
    }

    private static boolean hasInputs(Map<AEKey, BigInteger> balances, Map<AEKey, BigInteger> required) {
        return required.entrySet().stream().allMatch(entry -> balances
                .getOrDefault(entry.getKey(), BigInteger.ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static LinkedHashMap<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity deterministic-cycle inventory cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return copied;
    }

    private static BigInteger lcm(BigInteger first, BigInteger second) {
        return first.divide(first.gcd(second)).multiply(second);
    }

    private record RowReduction(Rational[][] matrix, List<Integer> pivotColumns, int rank) {}

    /**
     * Canonical exact fraction used only while deriving the primitive cycle basis.
     */
    private record Rational(BigInteger numerator, BigInteger denominator) {

        private static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
        private static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);

        private Rational {
            if (denominator.signum() == 0) {
                throw new ArithmeticException("A Trinity cycle ratio cannot divide by zero");
            }
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor);
            denominator = denominator.divide(divisor);
        }

        private static Rational of(BigInteger value) {
            return value.signum() == 0 ? ZERO : new Rational(value, BigInteger.ONE);
        }

        private Rational add(Rational other) {
            return new Rational(
                    this.numerator.multiply(other.denominator).add(other.numerator.multiply(this.denominator)),
                    this.denominator.multiply(other.denominator));
        }

        private Rational subtract(Rational other) {
            return this.add(other.negate());
        }

        private Rational multiply(Rational other) {
            if (this.signum() == 0 || other.signum() == 0) {
                return ZERO;
            }
            return new Rational(
                    this.numerator.multiply(other.numerator),
                    this.denominator.multiply(other.denominator));
        }

        private Rational divide(Rational other) {
            if (other.signum() == 0) {
                throw new ArithmeticException("A Trinity cycle pivot cannot be zero");
            }
            return new Rational(
                    this.numerator.multiply(other.denominator),
                    this.denominator.multiply(other.numerator));
        }

        private Rational negate() {
            return this.signum() == 0 ? ZERO : new Rational(this.numerator.negate(), this.denominator);
        }

        private int signum() {
            return this.numerator.signum();
        }
    }
}
