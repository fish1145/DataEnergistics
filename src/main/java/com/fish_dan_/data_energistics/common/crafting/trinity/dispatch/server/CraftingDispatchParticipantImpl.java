package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCraftingRuntime;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * Prepared one-tick AE Grid participant that rotates runtimes only after a real provider call.
 */
public final class CraftingDispatchParticipantImpl implements CraftingDispatchParticipant {

    private final String diagnosticIdentity;
    private final List<TrinityDataCoreCraftingRuntime> runtimes;
    private final IEnergyService energyService;
    private final CraftingService craftingService;
    private final CraftingDispatchWindow dispatchWindow;
    private final CraftingDispatchBudget dispatchBudget;
    private final IntConsumer runtimeCursorCommit;
    private final Runnable tickCompletion;
    private final BiConsumer<String, RuntimeException> failureRecorder;
    private int runtimeCursor;

    /**
     * Captures immutable tick dependencies while leaving Grid-owned cursors and metrics behind callbacks.
     *
     * @param diagnosticIdentity  stable Grid identity for failure logs
     * @param runtimes            prepared runtime snapshot for this Grid tick
     * @param runtimeCursor       first runtime index retained from the last physical call
     * @param energyService       AE2 energy service for physical submissions
     * @param craftingService     AE2 crafting service for provider resolution
     * @param dispatchWindow      Grid-local physical and time budget
     * @param dispatchBudget      immutable Governor policy for this tick
     * @param runtimeCursorCommit callback that persists a physical-call successor cursor
     * @param tickCompletion      callback that completes Grid metrics exactly once
     * @param failureRecorder     callback that moves only this Grid into SAFE mode
     */
    public CraftingDispatchParticipantImpl(String diagnosticIdentity,
                                           List<TrinityDataCoreCraftingRuntime> runtimes,
                                           int runtimeCursor,
                                           IEnergyService energyService,
                                           CraftingService craftingService,
                                           CraftingDispatchWindow dispatchWindow,
                                           CraftingDispatchBudget dispatchBudget,
                                           IntConsumer runtimeCursorCommit,
                                           Runnable tickCompletion,
                                           BiConsumer<String, RuntimeException> failureRecorder) {
        if (diagnosticIdentity == null || diagnosticIdentity.isBlank()) {
            throw new IllegalArgumentException("Crafting dispatch participant identity is required");
        }
        if (runtimes == null || energyService == null || craftingService == null || dispatchWindow == null ||
                dispatchBudget == null || runtimeCursorCommit == null || tickCompletion == null ||
                failureRecorder == null) {
            throw new IllegalArgumentException("Crafting dispatch participant dependencies are required");
        }
        this.diagnosticIdentity = diagnosticIdentity;
        this.runtimes = List.copyOf(runtimes);
        this.runtimeCursor = this.runtimes.isEmpty() ? 0 : Math.floorMod(runtimeCursor, this.runtimes.size());
        this.energyService = energyService;
        this.craftingService = craftingService;
        this.dispatchWindow = dispatchWindow;
        this.dispatchBudget = dispatchBudget;
        this.runtimeCursorCommit = runtimeCursorCommit;
        this.tickCompletion = tickCompletion;
        this.failureRecorder = failureRecorder;
    }

    @Override
    public String diagnosticIdentity() {
        return this.diagnosticIdentity;
    }

    @Override
    public CraftingDispatchStepResult dispatchStep() {
        if (this.runtimes.isEmpty()) {
            return CraftingDispatchStepResult.IDLE;
        }
        boolean stateChanged = false;
        int start = Math.floorMod(this.runtimeCursor, this.runtimes.size());
        for (int offset = 0; offset < this.runtimes.size() && !this.dispatchWindow.isExhausted(); offset++) {
            int runtimeIndex = (start + offset) % this.runtimes.size();
            CraftingDispatchStepResult result = this.runtimes.get(runtimeIndex).dispatchStep(
                    this.energyService,
                    this.craftingService,
                    this.dispatchWindow,
                    this.dispatchBudget);
            stateChanged |= result.stateChanged();
            if (result.physicalAttempted()) {
                this.runtimeCursor = (runtimeIndex + 1) % this.runtimes.size();
                this.runtimeCursorCommit.accept(this.runtimeCursor);
                return new CraftingDispatchStepResult(
                        true,
                        stateChanged,
                        hasReadyWork(),
                        this.dispatchWindow.isExhausted());
            }
        }
        return new CraftingDispatchStepResult(
                false,
                stateChanged,
                hasReadyWork(),
                this.dispatchWindow.isExhausted());
    }

    @Override
    public void completeTick() {
        this.tickCompletion.run();
    }

    @Override
    public void recordUnexpectedFailure(String source, RuntimeException failure) {
        this.failureRecorder.accept(source, failure);
    }

    private boolean hasReadyWork() {
        for (TrinityDataCoreCraftingRuntime runtime : this.runtimes) {
            if (runtime.hasReadyDispatchWork()) {
                return true;
            }
        }
        return false;
    }
}
