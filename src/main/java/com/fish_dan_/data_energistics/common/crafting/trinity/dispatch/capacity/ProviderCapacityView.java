package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import appeng.api.crafting.IPatternDetails;

import java.util.List;

/**
 * Compile-time compatibility boundary for capturing provider-owned capacity facts on the server thread.
 *
 * <p>
 * Implementations only translate facts already owned by the provider. They do not select counts, reserve targets,
 * mutate routing cursors, or submit crafting work. Returned snapshots and the containing list must be immutable.
 * </p>
 */
public interface ProviderCapacityView {

    /**
     * Captures every independently routable target for one exact pattern publication.
     *
     * @param providerId          current ID resolved from the grid publication index
     * @param patternDetails      exact live pattern queried on the server thread
     * @param patternIdentity     immutable signature copied into each returned snapshot
     * @param publicationRevision publication-index revision observed before capture
     * @param captureTick         current server tick used only for diagnostics and latency accounting
     * @return immutable snapshots; an empty list means the provider exposes no currently usable target
     */
    List<ProviderCapacitySnapshot> snapshotCapacity(
                                                    CraftingProviderId providerId,
                                                    IPatternDetails patternDetails,
                                                    String patternIdentity,
                                                    long publicationRevision,
                                                    long captureTick);
}
