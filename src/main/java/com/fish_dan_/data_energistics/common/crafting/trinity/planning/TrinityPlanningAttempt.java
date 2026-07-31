package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import org.jetbrains.annotations.Nullable;

/**
 * Exactly one successful executable plan or one explicit reason to fall back to AE2.
 *
 * @param plan       executable Trinity plan on success
 * @param diagnostic fallback reason on failure
 */
public record TrinityPlanningAttempt(
                                     @Nullable TrinityCraftingPlan plan,
                                     @Nullable TrinityPlanningDiagnostic diagnostic) {

    /** Enforces the mutually exclusive result contract at the planner boundary. */
    public TrinityPlanningAttempt {
        if ((plan == null) == (diagnostic == null)) {
            throw new IllegalArgumentException("A Trinity planning attempt requires exactly one result");
        }
        if (plan != null && plan.simulation()) {
            throw new IllegalArgumentException("A successful Trinity planning attempt must be executable");
        }
    }

    /**
     * @param plan validated executable plan
     * @return successful attempt
     */
    public static TrinityPlanningAttempt success(TrinityCraftingPlan plan) {
        return new TrinityPlanningAttempt(plan, null);
    }

    /**
     * @param diagnostic explicit fallback reason
     * @return failed attempt
     */
    public static TrinityPlanningAttempt failure(TrinityPlanningDiagnostic diagnostic) {
        return new TrinityPlanningAttempt(null, diagnostic);
    }

    /**
     * @return whether this attempt contains an executable Trinity plan
     */
    public boolean successful() {
        return this.plan != null;
    }
}
