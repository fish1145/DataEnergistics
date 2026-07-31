package com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission;

import appeng.api.networking.crafting.ICraftingPlan;

/**
 * Single plan-semantics boundary shared by every route that may submit work to a Trinity CPU.
 */
public interface TrinityPlanAdmission {

    /**
     * Submission route whose unsupported-plan behavior differs between explicit and opportunistic selection.
     */
    enum Route {
        /**
         * A caller named a Trinity CPU directly; incompatibility is a terminal rejection for that target.
         */
        EXPLICIT_TARGET,
        /**
         * Trinity is considered before AE2 or third-party automatic routing.
         */
        AUTOMATIC_SELECTION,
        /**
         * Trinity is considered after the original route reported no usable CPU.
         */
        FALLBACK,
        /**
         * A caller invoked the Trinity CPU implementation directly.
         */
        DIRECT_CPU
    }

    /**
     * Action the caller must take for one route.
     */
    enum Decision {
        /**
         * The complete plan semantics are supported and Trinity may attempt submission.
         */
        SUBMIT_TO_TRINITY,
        /**
         * The plan belongs to AE2 or another CPU implementation and routing must continue without Trinity.
         */
        DEFER_TO_ORIGINAL,
        /**
         * The caller explicitly selected or directly invoked Trinity with an unsupported plan.
         */
        REJECT_TRINITY
    }

    /**
     * CPU implementation family used by both confirmation filtering and final submission routing.
     */
    enum CpuFamily {
        /**
         * A Data Energistics Trinity CPU that understands {@link TrinityCpuExecutablePlan}.
         */
        TRINITY,
        /**
         * AE2-native and third-party CPUs whose execution contract is not owned by Trinity.
         */
        NON_TRINITY
    }

    /**
     * @return default allowlist-based admission policy
     */
    static TrinityPlanAdmission create() {
        return new TrinityPlanAdmissionImpl();
    }

    /**
     * Decides whether Trinity may own the plan on the specified submission route.
     *
     * @param plan  calculated crafting plan
     * @param route route attempting Trinity ownership
     * @return required routing action
     */
    Decision decide(ICraftingPlan plan, Route route);

    /**
     * Applies the plan ownership boundary to a concrete CPU family.
     *
     * <p>
     * Trinity-only plans can never reach another CPU. Unknown third-party extended plans remain eligible for their
     * own CPU family but are never admitted to Trinity.
     * </p>
     *
     * @param plan      calculated crafting plan
     * @param cpuFamily target CPU implementation family
     * @return whether that CPU family may receive the plan
     */
    boolean isCompatibleWith(ICraftingPlan plan, CpuFamily cpuFamily);
}
