package com.fish_dan_.data_energistics.common.multiblock.vertical;

/**
 * Runtime marker for a block entity participating in a vertical multiblock.
 *
 * <p>
 * Parts receive explicit bind and unbind events from the owning controller. This keeps capability aggregation and
 * visual state updates out of the scanner.
 */
public interface VerticalMultiBlockPart {

    /**
     * Called after a structure formed and this part was matched.
     *
     * @param controller owning controller
     * @param context    completed scan context
     */
    void verticalMultiBlock$addedToController(VerticalMultiBlockController controller, VerticalMultiBlockContext<?> context);

    /**
     * Called after a named structure formed and this part was matched.
     *
     * @param controller    owning controller
     * @param structureName formed structure name from the matched definition
     * @param context       completed scan context
     */
    default void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                      String structureName,
                                                      VerticalMultiBlockContext<?> context) {
        verticalMultiBlock$addedToController(controller, context);
    }

    /**
     * Called when the owning structure becomes invalid.
     *
     * @param controller previous owning controller
     */
    void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller);

    /**
     * Called when the owning named structure becomes invalid.
     *
     * @param controller    previous owning controller
     * @param structureName invalidated structure name from the previous runtime state
     */
    default void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller, String structureName) {
        verticalMultiBlock$removedFromController(controller);
    }
}
