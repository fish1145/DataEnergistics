package com.fish_dan_.data_energistics.common.multiblock.vertical;

/**
 * Checks whether one structure cell matches the world state at a position.
 *
 * @param <S> block state representation used by the caller
 */
@FunctionalInterface
public interface VerticalMultiBlockPredicate<S> {

    /**
     * Returns whether {@code state} satisfies this cell.
     *
     * @param state current world state
     * @param pos   absolute position being checked
     * @return true when the cell matches
     */
    boolean matches(S state, VerticalMultiBlockPos pos);

    static <S> VerticalMultiBlockPredicate<S> state(S expectedState) {
        return (state, pos) -> expectedState.equals(state);
    }
}
