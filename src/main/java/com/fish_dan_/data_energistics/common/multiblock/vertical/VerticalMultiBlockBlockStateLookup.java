package com.fish_dan_.data_energistics.common.multiblock.vertical;

/**
 * Provides the block state at a world position while scanning a vertical multiblock.
 *
 * <p>
 * The scanner only needs read access to the current structure area. This interface exists so the matching logic can
 * be tested without booting a Minecraft world, while production callers can adapt it to {@code Level#getBlockState}.
 *
 * @param <S> block state representation used by the caller
 */
@FunctionalInterface
public interface VerticalMultiBlockBlockStateLookup<S> {

    /**
     * Returns the state at {@code pos}.
     *
     * <p>
     * Implementations must return a non-null state. A missing block should be represented by the caller's normal
     * empty/air state so predicates can fail explicitly.
     *
     * @param pos absolute position being inspected
     * @return block state at the inspected position
     */
    S get(VerticalMultiBlockPos pos);
}
