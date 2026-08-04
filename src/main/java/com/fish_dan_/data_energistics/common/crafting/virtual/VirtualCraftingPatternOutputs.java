package com.fish_dan_.data_energistics.common.crafting.virtual;

/**
 * Implemented by decoded patterns whose published outputs include dispatch-time virtual results.
 */
public interface VirtualCraftingPatternOutputs {

    /**
     * Returns the immutable output projection captured when this pattern was decoded.
     *
     * @return pattern output projection
     */
    VirtualCraftingOutputProjection dataEnergistics$virtualOutputProjection();
}
