package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Server-thread request boundary shared by initial planning and CPU remaining-work capture. */
public interface ReusableInputGraphCaptureAccess {

    /**
     * Captures a complete immutable graph in bounded server ticks before any background calculation.
     * Additional item states belong to the CPU's local inventory; network-visible states are added by
     * the service. These keys declare candidates, not quantities or extraction authority. Completion
     * callbacks run on the server thread; cancelling the future stops pending capture.
     */
    CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> data_energistics$captureReusableGraph(
                                                                                                                  ServerLevel level, IActionSource source, AEKey target, List<AEItemKey> additionalInventoryStates,
                                                                                                                  TrinityPlanningLimits limits);
}
