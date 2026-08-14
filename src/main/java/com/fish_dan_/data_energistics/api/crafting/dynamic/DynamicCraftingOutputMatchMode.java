package com.fish_dan_.data_energistics.api.crafting.dynamic;

/**
 * Supported matching policies for a pattern output whose runtime identity may differ from its declaration.
 */
public enum DynamicCraftingOutputMatchMode {

    /**
     * Matches item keys by their registered item while deliberately ignoring data components.
     *
     * <p>
     * The crafting runtime must retain the complete key returned by the provider. This mode only changes which
     * pending declaration may accept that key; it never rewrites the returned key to the declared template.
     * Ordinary outputs accepted this way remain eligible as same-item inputs only inside the job that produced
     * them. Final-request outputs remain isolated for delivery and are never consumed by downstream patterns.
     * </p>
     */
    SAME_ITEM
}
