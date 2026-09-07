package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;

import appeng.api.stacks.AEItemKey;

import java.util.List;

/**
 * Bounded server-thread capture of complete input assignments and their deterministic successor
 * closure. Rules are queried with every exact slot present; a rule proved for one assignment is
 * never reused for a different assignment. Returned bindings contain values only.
 */
public final class ReusableInputPlanningExpansion {

    private ReusableInputPlanningExpansion() {}

    /** Complete capture or an explicit bound; partial state graphs must never be planned. */
    public sealed interface Result permits Captured, Stopped {}

    /**
     * @param bindings          exact complete assignments in stable capture order; list index is the expanded ordinal
     * @param hasReusableInputs whether at least one explicit rule was applicable
     */
    public record Captured(List<List<TrinityBoundPatternInput>> bindings, boolean hasReusableInputs) implements Result {

        public Captured {
            bindings = bindings.stream().map(List::copyOf).toList();
        }
    }

    /** Bounded capture rejection; callers must retain diagnostics rather than plan a truncated graph. */
    public record Stopped(Reason reason, int examinedBindings) implements Result {}

    /** Distinguishes configured capture bounds from cooperative cancellation. */
    public enum Reason {
        BINDING_LIMIT,
        DEADLINE,
        CANCELLED
    }

    /**
     * Captures candidates from encoded alternatives plus visible inventory item keys, and then exact
     * declared successors. New states require both a registered rule and the original IInput.isValid.
     * Visible counts are not treated as owned stock; normal request inventory capture performs access checks.
     *
     * @param context         server callback context; exactInputs supplies a complete initial grid but is not retained
     * @param inventoryStates visible physical item keys, sorted canonically before expansion
     * @param rules           frozen server-thread adapter lookup
     * @param maximumBindings maximum visited complete assignments, including rejected speculative inventory states
     * @param control         capture deadline/cancellation shared by the request
     * @return complete frozen capture or an explicit stop with no partial binding list
     */
    public static Result capture(ReusableInputContext context, List<AEItemKey> inventoryStates,
                                 ReusableInputRules rules, int maximumBindings, TrinityPlanningControl control) {
        if (maximumBindings <= 0) {
            throw new IllegalArgumentException("Reusable input capture requires a positive binding limit");
        }
        ReusableInputPlanningCursor cursor = new ReusableInputPlanningCursor(context, inventoryStates, rules, maximumBindings, control);
        Result result;
        do {
            result = cursor.advance(Long.MAX_VALUE, System::nanoTime);
        } while (result == null);
        return result;
    }
}
