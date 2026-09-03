package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/** A request-private, bounded integer neighbourhood around a linear witness, not a restriction on the full plan. */
record TrinityIntegerCorrection(List<BigInteger> origin, IntList lowerBounds, IntList upperBounds) {

    private static final BigDecimal RADIUS = BigDecimal.valueOf(64);

    /** Translates a fresh ordinary model in place to small integer deltas, retaining every original constraint. */
    static @Nullable TrinityIntegerCorrection translate(ExpressionsBasedModel model, Optimisation.Result witness) {
        ObjectArrayList<BigInteger> origin = new ObjectArrayList<>(model.countVariables());
        IntArrayList lowerBounds = new IntArrayList(model.countVariables());
        IntArrayList upperBounds = new IntArrayList(model.countVariables());
        for (int index = 0; index < model.countVariables(); index++) {
            BigDecimal center = witness.get(index).setScale(0, RoundingMode.HALF_EVEN);
            Variable variable = model.getVariable(index);
            BigDecimal lower = variable.getLowerLimit().subtract(center).max(RADIUS.negate());
            BigDecimal upper = variable.getUpperLimit().subtract(center).min(RADIUS);
            if (lower.compareTo(upper) > 0) {
                return null;
            }
            origin.add(center.toBigIntegerExact());
            lowerBounds.add(lower.intValueExact());
            upperBounds.add(upper.intValueExact());
            variable.lower(lower).upper(upper);
        }
        for (Expression expression : model.getExpressions()) {
            BigInteger offset = BigInteger.ZERO;
            for (var term : expression.getLinearEntrySet()) {
                offset = offset.add(term.getValue().toBigIntegerExact().multiply(origin.get(term.getKey().index)));
            }
            BigDecimal shift = new BigDecimal(offset);
            BigDecimal lower = expression.getLowerLimit();
            BigDecimal upper = expression.getUpperLimit();
            if (lower != null) expression.lower(lower.subtract(shift));
            if (upper != null) expression.upper(upper.subtract(shift));
        }
        return new TrinityIntegerCorrection(origin, lowerBounds, upperBounds);
    }

    /** Checks the original delta domains before restoring coordinates; null rejects an out-of-domain candidate. */
    @Nullable
    List<BigInteger> restore(List<BigInteger> delta) {
        ObjectArrayList<BigInteger> restored = new ObjectArrayList<>(origin.size());
        for (int index = 0; index < origin.size(); index++) {
            BigInteger adjustment = delta.get(index);
            if (adjustment.compareTo(BigInteger.valueOf(lowerBounds.getInt(index))) < 0 ||
                    adjustment.compareTo(BigInteger.valueOf(upperBounds.getInt(index))) > 0) {
                return null;
            }
            restored.add(origin.get(index).add(adjustment));
        }
        return restored;
    }
}
