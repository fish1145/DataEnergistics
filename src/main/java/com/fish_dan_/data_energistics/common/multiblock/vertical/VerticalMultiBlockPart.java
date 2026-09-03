package com.fish_dan_.data_energistics.common.multiblock.vertical;

/**
 * Receives lifecycle callbacks for a block entity participating in a vertical multiblock.
 *
 * <p>
 * Parts receive explicit bind and unbind events from the owning controller. This keeps capability aggregation and
 * visual state updates out of the scanner. Callbacks run on the server thread and always carry the named structure's
 * captured binding epoch; identity-aware parts must not let an old removal tear down a newer registration.
 */
public interface VerticalMultiBlockPart {

    /**
     * Called after a named structure forms with the exact runtime identity retained for its later removal callback.
     *
     * @param controller    non-null owning controller
     * @param structureName non-null matched structure name
     * @param context       non-null completed scan context belonging to that structure
     * @param bindingEpoch  runtime-issued identity for this binding, retained until removal
     */
    void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                              String structureName,
                                              VerticalMultiBlockContext<?> context,
                                              long bindingEpoch);

    /**
     * Called when a named structure becomes invalid with the runtime identity captured when it formed.
     *
     * @param controller    non-null controller that issued the binding
     * @param structureName non-null invalidated structure name
     * @param bindingEpoch  identity captured at formation, not the controller's newest epoch
     */
    void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller,
                                                  String structureName,
                                                  long bindingEpoch);
}
