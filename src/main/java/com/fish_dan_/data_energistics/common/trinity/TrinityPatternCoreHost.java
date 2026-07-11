package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.world.entity.player.Player;

/**
 * Transient ownership contract between one formed Trinity crafting structure and its mounted pattern cores.
 *
 * <p>
 * The binding is deliberately not persisted: the host rebuilds it from a successful structure scan. It gives a
 * mounted core a direct path to the host's elected AE lease without turning the core into a multiblock compartment or
 * relying on a world scan.
 */
public interface TrinityPatternCoreHost {

    /**
     * Verifies that one exact core instance still belongs to the host's active catalog.
     *
     * @param core physical core requesting validation
     * @return whether the current catalog still owns that instance
     */
    boolean isPatternCoreMounted(TrinityPatternCore core);

    /**
     * Refunds one mounted core through the host's elected AE storage, then the player inventory and world fallback.
     *
     * @param core   exact mounted core whose queued state is being returned
     * @param player player requesting the operation
     * @return whether refundable queued state was committed and delivered
     */
    boolean tryRefundPatternCore(TrinityPatternCore core, Player player);

    /**
     * Withdraws publication when a bound core unloads or is removed before the next structure scan.
     *
     * @param core core instance that is no longer available
     */
    void onPatternCoreUnavailable(TrinityPatternCore core);
}
