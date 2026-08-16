package com.fish_dan_.data_energistics.menu.crafting;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.network.chat.Component;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

/**
 * Synchronized Trinity planning metadata shared by the confirmation menu and its client screen.
 */
public interface TrinityCraftConfirmMenuState {

    /**
     * @return quantity mode retained for initial planning, replan, and returning to the amount page
     */
    CraftingQuantityMode data_energistics$quantityMode();

    /**
     * @param quantityMode player selection transferred from the amount menu
     */
    void data_energistics$setQuantityMode(CraftingQuantityMode quantityMode);

    /**
     * Starts the same AE2 calculation used by the native confirmation menu without narrowing the request to an int.
     *
     * @param what     requested key
     * @param amount   positive requested amount
     * @param strategy AE2 missing-item strategy
     * @return whether a Grid was available and the calculation was started
     */
    boolean data_energistics$planJob(AEKey what, long amount, CalculationStrategy strategy);

    /**
     * @return synchronized revision identifying the current initial plan or replan attempt
     */
    long data_energistics$planRevision();

    /**
     * Atomically publishes a complete cycle summary assembled for the current revision.
     *
     * @param revision summary revision validated by the client payload boundary
     * @param summary  complete immutable cycle summary
     */
    void data_energistics$receiveCycleSummary(long revision, TrinityCraftingCycleSummary summary);

    /**
     * @return complete cycle summary for the synchronized revision, or {@code null} while unavailable
     */
    @Nullable
    TrinityCraftingCycleSummary data_energistics$cycleSummary();

    /**
     * @return whether the displayed plan and the server-side CPU eligibility pass belong to the same result
     */
    boolean data_energistics$isPlanReady();

    /**
     * @return complete Trinity planning duration measured by {@link System#nanoTime()}, in nanoseconds
     */
    long data_energistics$planningNanos();

    /**
     * @return whether the executable result can run only on Trinity CPUs
     */
    boolean data_energistics$isTrinityOnly();

    /**
     * @return whether expected materials may change through a legal dynamic variant during execution
     */
    boolean data_energistics$hasDynamicMaterialWarning();

    /**
     * @return whether a Trinity diagnostic accompanies the displayed plan
     */
    boolean data_energistics$hasDiagnostic();

    /**
     * @return whether the visible counters come from an AE2 simulation after Trinity planning failed
     */
    boolean data_energistics$isAe2FallbackEstimate();

    /**
     * @return synchronized player-facing diagnostic
     */
    Component data_energistics$diagnostic();
}
