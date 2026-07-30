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
     * Called after a named structure forms with the exact runtime identity retained for its later removal callback.
     */
    default void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                      String structureName,
                                                      VerticalMultiBlockContext<?> context,
                                                      long bindingEpoch) {
        verticalMultiBlock$addedToController(controller, structureName, context);
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

    /**
     * Called when a named structure becomes invalid with the runtime identity captured when it formed.
     */
    default void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller,
                                                          String structureName,
                                                          long bindingEpoch) {
        verticalMultiBlock$removedFromController(controller, structureName);
    }
}
