package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixInfeasibleException;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixModelLimitException;

import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Encodes non-negative logical values as base-2^15 digits and every linear relation as complete signed-carry
 * columns. It also derives the overflow-proof domain from the exact same normalized logical equations. Papadimitriou
 * (1981) proves that a feasible {@code m x n} system {@code Ax=b, x>=0} has a solution whose components are at most
 * {@code n*(m*a)^(2m+1)}, where {@code a=max(|A|,|b|)}. All inequalities here contribute their actual non-negative
 * slack variable before {@code m}, {@code n}, and {@code a} are measured.
 */
public final class TrinityRadixLinearEncoder {

    private static final BigInteger EXACT_ROW_LIMIT = BigInteger.ONE.shiftLeft(52).subtract(BigInteger.ONE);
    private static final int MAX_MODEL_VARIABLES = 100_000;
    private static final int MAX_MODEL_EXPRESSIONS = 100_000;

    private final TrinityRadixCodec codec;
    private final ExpressionsBasedModel model = new ExpressionsBasedModel();
    private final ArrayList<Variable> variables = new ArrayList<>();
    private final ArrayList<TrinityRadixColumnEquation> columnEquations = new ArrayList<>();
    private final ArrayList<TrinityRadixLogicalEquation> proofEquations = new ArrayList<>();
    private int expressionCount;
    private int carryIndex;

    public TrinityRadixLinearEncoder(TrinityRadixCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("A Trinity radix encoder requires an exact codec");
        }
        this.codec = codec;
    }

    public TrinityRadixVariable addBounded(
                                           String name,
                                           BigInteger upperBound,
                                           boolean mathematicalUpperBound) {
        if (upperBound == null || upperBound.signum() < 0) {
            throw new IllegalArgumentException("A Trinity radix logical upper bound cannot be negative");
        }
        TrinityRadixDigits encodedUpper = this.codec.encode(upperBound);
        int width = encodedUpper.values().size();
        TrinityRadixVariable value = addUnsigned(name, width, upperBound);
        boolean lowerDigitsAreFull = encodedUpper.values()
                .subList(0, Math.max(0, width - 1))
                .stream()
                .allMatch(digit -> digit == TrinityRadixDigits.BASE - 1);
        if (!mathematicalUpperBound && lowerDigitsAreFull) {
            value.digits().get(width - 1).upper(encodedUpper.digit(width - 1));
            return value;
        }
        BigInteger fullWidthMaximum = BigInteger.valueOf(TrinityRadixDigits.BASE)
                .pow(width)
                .subtract(BigInteger.ONE);
        if (!upperBound.equals(fullWidthMaximum) || mathematicalUpperBound) {
            TrinityRadixVariable slack = addUnsigned(name + "_upper_slack", width, upperBound);
            addEquality(
                    name + "_upper",
                    Map.of(value, BigInteger.ONE, slack, BigInteger.ONE),
                    upperBound,
                    mathematicalUpperBound);
        }
        return value;
    }

    public TrinityRadixVariable addTotal(String name, Collection<TrinityRadixVariable> terms) {
        BigInteger upper = terms.stream()
                .map(TrinityRadixVariable::upperBound)
                .reduce(BigInteger.ZERO, BigInteger::add);
        TrinityRadixVariable total = addUnsigned(name, this.codec.encode(upper).values().size(), upper);
        LinkedHashMap<TrinityRadixVariable, BigInteger> equality = new LinkedHashMap<>();
        terms.forEach(variable -> equality.merge(variable, BigInteger.ONE, BigInteger::add));
        equality.put(total, BigInteger.ONE.negate());
        addEquality(name + "_sum", equality, BigInteger.ZERO, true);
        return total;
    }

    public void addGreaterOrEqual(
                                  String name,
                                  Map<TrinityRadixVariable, BigInteger> terms,
                                  BigInteger lowerBound) {
        if (lowerBound == null || lowerBound.signum() < 0) {
            throw new IllegalArgumentException("A Trinity radix constraint lower bound cannot be negative");
        }
        BigInteger maximum = maximumValue(terms);
        if (maximum.compareTo(lowerBound) < 0) {
            throw new TrinityRadixInfeasibleException(name);
        }
        TrinityRadixVariable slack = addUnsigned(
                name + "_slack",
                this.codec.encode(maximum.subtract(lowerBound)).values().size(),
                maximum.subtract(lowerBound));
        LinkedHashMap<TrinityRadixVariable, BigInteger> equality = new LinkedHashMap<>(terms);
        equality.put(slack, BigInteger.ONE.negate());
        addEquality(name, equality, lowerBound, true);
    }

    public void addLowerBound(String name, TrinityRadixVariable value, BigInteger lowerBound) {
        addGreaterOrEqual(name, Map.of(value, BigInteger.ONE), lowerBound);
    }

    /** Adds an exact signed logical relation before it is split into bounded radix carry columns. */
    public void addExact(
                         String name,
                         Map<TrinityRadixVariable, BigInteger> terms,
                         BigInteger value) {
        addEquality(name, terms, value, true);
    }

    public void addFixed(String name, TrinityRadixVariable value, BigInteger fixed) {
        if (fixed == null || fixed.signum() < 0 || fixed.compareTo(value.upperBound()) > 0) {
            throw new TrinityRadixInfeasibleException(name);
        }
        addEquality(name, Map.of(value, BigInteger.ONE), fixed, true);
    }

    public BigInteger proofUpperBound() {
        if (this.proofEquations.isEmpty()) {
            throw new IllegalStateException("A Trinity radix proof requires normalized logical equations");
        }
        LinkedHashSet<TrinityRadixVariable> proofVariables = new LinkedHashSet<>();
        BigInteger maximumMagnitude = BigInteger.ONE;
        for (TrinityRadixLogicalEquation equation : this.proofEquations) {
            proofVariables.addAll(equation.terms().keySet());
            maximumMagnitude = maximumMagnitude.max(equation.rightHandSide().abs());
            for (BigInteger coefficient : equation.terms().values()) {
                maximumMagnitude = maximumMagnitude.max(coefficient.abs());
            }
        }
        int rows = this.proofEquations.size();
        int columns = proofVariables.size();
        if (columns == 0) {
            throw new IllegalStateException("A Trinity radix proof requires logical variables");
        }
        BigInteger scaledMagnitude = maximumMagnitude.multiply(BigInteger.valueOf(rows));
        int exponent = Math.addExact(Math.multiplyExact(2, rows), 1);
        return scaledMagnitude.pow(exponent).multiply(BigInteger.valueOf(columns));
    }

    public ExpressionsBasedModel model() {
        return this.model;
    }

    public List<Variable> variables() {
        return List.copyOf(this.variables);
    }

    public List<TrinityRadixColumnEquation> columnEquations() {
        return List.copyOf(this.columnEquations);
    }

    public void checkSize() {
        if (this.variables.isEmpty() || this.columnEquations.isEmpty()) {
            throw new IllegalStateException("A Trinity radix model must contain variables and exact equations");
        }
    }

    private TrinityRadixVariable addUnsigned(String name, int width, BigInteger upperBound) {
        ArrayList<Variable> digits = new ArrayList<>(width);
        for (int digit = 0; digit < width; digit++) {
            Variable variable = addVariable(name + "_d" + digit)
                    .lower(BigInteger.ZERO)
                    .upper(TrinityRadixDigits.BASE - 1)
                    .integer();
            digits.add(variable);
        }
        return new TrinityRadixVariable(name, List.copyOf(digits), upperBound);
    }

    private void addEquality(
                             String name,
                             Map<TrinityRadixVariable, BigInteger> sourceTerms,
                             BigInteger rightHandSide,
                             boolean includeInProof) {
        LinkedHashMap<TrinityRadixVariable, BigInteger> terms = normalizedTerms(sourceTerms);
        if (rightHandSide == null || rightHandSide.signum() < 0) {
            throw new IllegalArgumentException("A Trinity radix equality RHS cannot be negative");
        }
        if (includeInProof) {
            this.proofEquations.add(new TrinityRadixLogicalEquation(
                    Collections.unmodifiableMap(new LinkedHashMap<>(terms)),
                    rightHandSide));
        }
        int columns = this.codec.encode(rightHandSide).values().size();
        LinkedHashMap<TrinityRadixVariable, TrinitySignedRadixDigits> coefficients = new LinkedHashMap<>();
        for (Map.Entry<TrinityRadixVariable, BigInteger> term : terms.entrySet()) {
            TrinitySignedRadixDigits encoded = this.codec.encodeSigned(term.getValue());
            coefficients.put(term.getKey(), encoded);
            columns = Math.max(
                    columns,
                    term.getKey().digits().size() + encoded.magnitude().values().size() - 1);
        }
        columns = Math.addExact(columns, 1);
        TrinityRadixDigits rhsDigits = this.codec.encode(rightHandSide, columns);
        Variable incomingCarry = null;
        TrinitySignedCarryBounds incomingBounds = new TrinitySignedCarryBounds(BigInteger.ZERO, BigInteger.ZERO);
        for (int column = 0; column < columns; column++) {
            LinkedHashMap<Variable, BigInteger> columnTerms = new LinkedHashMap<>();
            ArrayList<Integer> convolutionCoefficients = new ArrayList<>();
            addConvolutionTerms(column, coefficients, columnTerms, convolutionCoefficients);
            if (incomingCarry != null) {
                mergeCoefficient(columnTerms, incomingCarry, BigInteger.ONE);
            }
            TrinitySignedCarryBounds outgoingBounds = this.codec.nextCarryBounds(
                    convolutionCoefficients,
                    incomingBounds,
                    rhsDigits.digit(column));
            Variable outgoingCarry = null;
            if (column < columns - 1) {
                outgoingCarry = addVariable("carry_" + this.carryIndex++)
                        .lower(outgoingBounds.lowerBound())
                        .upper(outgoingBounds.upperBound())
                        .integer();
                mergeCoefficient(
                        columnTerms,
                        outgoingCarry,
                        BigInteger.valueOf(-TrinityRadixDigits.BASE));
            }
            addColumnExpression(name + "_c" + column, columnTerms, rhsDigits.digit(column));
            incomingCarry = outgoingCarry;
            incomingBounds = outgoingBounds;
        }
    }

    private static LinkedHashMap<TrinityRadixVariable, BigInteger> normalizedTerms(
                                                                                   Map<TrinityRadixVariable, BigInteger> source) {
        LinkedHashMap<TrinityRadixVariable, BigInteger> terms = new LinkedHashMap<>();
        source.forEach((variable, coefficient) -> {
            if (variable == null || coefficient == null) {
                throw new IllegalArgumentException("A Trinity radix equality term cannot be null");
            }
            if (coefficient.signum() != 0) {
                terms.merge(variable, coefficient, BigInteger::add);
            }
        });
        terms.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return terms;
    }

    private static void addConvolutionTerms(
                                            int column,
                                            Map<TrinityRadixVariable, TrinitySignedRadixDigits> coefficients,
                                            Map<Variable, BigInteger> columnTerms,
                                            List<Integer> convolutionCoefficients) {
        for (Map.Entry<TrinityRadixVariable, TrinitySignedRadixDigits> term : coefficients.entrySet()) {
            TrinityRadixVariable variable = term.getKey();
            TrinitySignedRadixDigits coefficient = term.getValue();
            for (int coefficientDigit = 0; coefficientDigit < coefficient.magnitude().values().size(); coefficientDigit++) {
                int valueDigit = column - coefficientDigit;
                if (valueDigit < 0 || valueDigit >= variable.digits().size()) {
                    continue;
                }
                int signedCoefficient = coefficient.signedCoefficient(coefficientDigit);
                if (signedCoefficient != 0) {
                    mergeCoefficient(
                            columnTerms,
                            variable.digits().get(valueDigit),
                            BigInteger.valueOf(signedCoefficient));
                    convolutionCoefficients.add(signedCoefficient);
                }
            }
        }
    }

    private void addColumnExpression(
                                     String name,
                                     Map<Variable, BigInteger> terms,
                                     int rightHandDigit) {
        BigInteger rowEnvelope = BigInteger.ZERO;
        for (Map.Entry<Variable, BigInteger> term : terms.entrySet()) {
            BigInteger lower = decimalInteger(term.getKey().getLowerLimit());
            BigInteger upper = decimalInteger(term.getKey().getUpperLimit());
            rowEnvelope = rowEnvelope.add(term.getValue().multiply(lower).abs()
                    .max(term.getValue().multiply(upper).abs()));
        }
        if (rowEnvelope.compareTo(EXACT_ROW_LIMIT) > 0) {
            throw new TrinityRadixModelLimitException(Map.of(
                    "reason", "numeric_envelope",
                    "expression", name,
                    "envelope", rowEnvelope.toString()));
        }
        Expression expression = addExpression(name);
        terms.forEach(expression::set);
        expression.level(rightHandDigit);
        this.columnEquations.add(new TrinityRadixColumnEquation(
                Collections.unmodifiableMap(new LinkedHashMap<>(terms)),
                BigInteger.valueOf(rightHandDigit)));
    }

    private Variable addVariable(String name) {
        if (this.variables.size() >= MAX_MODEL_VARIABLES) {
            throw new TrinityRadixModelLimitException(Map.of(
                    "reason", "variable_count",
                    "limit", Integer.toString(MAX_MODEL_VARIABLES)));
        }
        Variable variable = this.model.addVariable(name);
        this.variables.add(variable);
        return variable;
    }

    private Expression addExpression(String name) {
        if (this.expressionCount >= MAX_MODEL_EXPRESSIONS) {
            throw new TrinityRadixModelLimitException(Map.of(
                    "reason", "expression_count",
                    "limit", Integer.toString(MAX_MODEL_EXPRESSIONS)));
        }
        this.expressionCount = Math.addExact(this.expressionCount, 1);
        return this.model.addExpression(name);
    }

    private static BigInteger maximumValue(Map<TrinityRadixVariable, BigInteger> terms) {
        BigInteger maximum = BigInteger.ZERO;
        for (Map.Entry<TrinityRadixVariable, BigInteger> term : terms.entrySet()) {
            if (term.getValue().signum() > 0) {
                maximum = maximum.add(term.getValue().multiply(term.getKey().upperBound()));
            }
        }
        return maximum;
    }

    private static void mergeCoefficient(
                                         Map<Variable, BigInteger> terms,
                                         Variable variable,
                                         BigInteger coefficient) {
        terms.merge(variable, coefficient, BigInteger::add);
        if (terms.get(variable).signum() == 0) {
            terms.remove(variable);
        }
    }

    private static BigInteger decimalInteger(BigDecimal value) {
        if (value == null) {
            throw new TrinityRadixModelLimitException(Map.of("reason", "unbounded_radix_variable"));
        }
        return value.toBigIntegerExact();
    }
}
