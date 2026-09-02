package com.fish_dan_.data_energistics.menu.crafting.tree.session;

/**
 * Internal AE2 confirmation bridge, not a plugin API. Client methods use the active menu's action channel;
 * restoration is server-thread-only and consumes ownership from a handoff token. No reflection is involved.
 */
public interface CraftingPlanSessionTransfer {

    /** Whether the synchronized network contains an active Trinity CPU, including busy CPUs. */
    boolean data_energistics$hasTrinityCpu();

    /** Whether the current plan has finished its graph projection (including an explicit projection error). */
    boolean data_energistics$isTreeReady();

    /** Requests a server-validated transfer from this active confirmation menu. */
    void data_energistics$openPlanTree();

    /** Restores a complete result without replanning; throws on invalid ownership or a pending calculation. */
    void data_energistics$adoptPlanTreeSession(CraftingPlanTreeSession session, Object handoffOwner);
}
