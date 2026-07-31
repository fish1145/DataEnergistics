package com.fish_dan_.data_energistics.menu.trinity;

import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;

import net.minecraft.world.entity.player.Player;

import appeng.api.storage.IPatternAccessTermMenuHost;
import appeng.helpers.patternprovider.PatternContainer;

/**
 * Defines the server-authoritative management boundary exposed by one Trinity ME access hatch.
 *
 * <p>
 * The menu depends on this interface so pattern visibility and refund execution remain tied to the exact hatch that
 * opened it instead of to every pattern provider on the shared AE grid.
 * </p>
 */
public interface TrinityAccessHatchMenuHost extends IPatternAccessTermMenuHost {

    /**
     * Verifies that the hatch block entity still occupies its original block and remains within interaction distance.
     *
     * @param player player whose open menu is being validated
     * @return whether the physical menu route is still current
     */
    boolean isAccessHatchMenuValid(Player player);

    /**
     * Verifies the full server-side management route, including the active grid node, current host lease and storage.
     *
     * @param player server player attempting a management action
     * @return whether a refund action may execute now
     */
    boolean isAccessHatchManagementAvailable(Player player);

    /**
     * Restricts AE2's grid-wide pattern scan to a partition currently mounted by this exact access hatch.
     *
     * @param container candidate grid pattern container
     * @return whether this hatch currently owns the candidate by identity
     */
    boolean isManagedPatternContainer(PatternContainer container);

    /**
     * Attempts to refund every installed pattern from the currently leased Trinity Data Core.
     *
     * @param player validated server player receiving the refund
     * @return precise server-authoritative action outcome
     */
    TrinityHostedActionStatus refundPatterns(Player player);

    /**
     * Attempts to refund queued inputs and pending outputs from the currently leased Trinity Data Core.
     *
     * @param player validated server player receiving the refund
     * @return precise server-authoritative action outcome
     */
    TrinityHostedActionStatus refundRetainedItems(Player player);
}
