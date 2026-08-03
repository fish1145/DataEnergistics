package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

/**
 * Distinguishes a reservoir miss from an unusable residual coordinate without conflating either with failure.
 *
 * @param kind  applicability disposition
 * @param basis populated only for {@link Kind#APPLICABLE}
 */
public record TrinityDeterministicApplicabilityResult(
                                                      Kind kind,
                                                      TrinityDeterministicBasis basis) {

    public static TrinityDeterministicApplicabilityResult skip() {
        return new TrinityDeterministicApplicabilityResult(Kind.SKIP_RESERVOIR, null);
    }

    public static TrinityDeterministicApplicabilityResult reject() {
        return new TrinityDeterministicApplicabilityResult(Kind.REJECT_RESERVOIR, null);
    }

    public static TrinityDeterministicApplicabilityResult applicable(TrinityDeterministicBasis basis) {
        return new TrinityDeterministicApplicabilityResult(Kind.APPLICABLE, basis);
    }

    public enum Kind {
        SKIP_RESERVOIR,
        REJECT_RESERVOIR,
        APPLICABLE
    }
}
