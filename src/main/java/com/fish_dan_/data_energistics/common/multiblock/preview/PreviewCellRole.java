package com.fish_dan_.data_energistics.common.multiblock.preview;

/**
 * Business role of one projected cell after its mutable MDLib predicate has been snapshotted.
 */
public enum PreviewCellRole {

    /** Controller anchor rendered from the owner item and excluded from recipe inputs. */
    CONTROLLER,
    /** Required concrete structure material. */
    MATERIAL,
    /** Predicate that may select either air or a concrete structure material. */
    OPTIONAL,
    /** Required absence of a block. */
    AIR,
    /** Unconstrained cell retained only for source and layout diagnostics. */
    WILDCARD;

    /**
     * Returns whether a concrete selected candidate contributes one recipe input.
     */
    public boolean contributesMaterial() {
        return this == MATERIAL || this == OPTIONAL;
    }
}
