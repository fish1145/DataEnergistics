package com.fish_dan_.data_energistics.network.crafting.tree.protocol;

/**
 * Menu-owned graph delivery boundary, avoiding a protocol dependency on concrete client or menu types.
 * Called only on the game thread for the currently open container. Implementations own one session/revision
 * assembler and must reject mismatched sessions; closing the menu discards that assembler and its partial data.
 */
public interface CraftingPlanGraphReceiver {
    /**
     * Accepts one non-null bounded batch on the game thread. May update this menu's assembly state only;
     * malformed or foreign-session data must not publish a partial graph or trigger crafting execution.
     */
    void receiveCraftingPlanGraph(CraftingPlanGraphPayload payload);
}
