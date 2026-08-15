package com.fish_dan_.data_energistics.network.trinity;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Performs the ordered, side-effect-free checks required before an AE crafting-status menu may target a Trinity CPU.
 */
public final class TrinityOpenCpuStatusValidator {

    private TrinityOpenCpuStatusValidator() {}

    /** Checks the replay-sensitive container identity before any host state is inspected. */
    public static Rejection validateMenu(int requestedContainerId,
                                         int currentContainerId,
                                         boolean trinityMenu) {
        if (requestedContainerId != currentContainerId) {
            return Rejection.WRONG_CONTAINER;
        }
        return trinityMenu ? Rejection.NONE : Rejection.WRONG_MENU;
    }

    /**
     * Resolves the exact published CPU, lease hatch and grid while preserving the reason for every rejected state.
     */
    public static <C, H, G> Resolution<C, H, G> resolve(
                                                        UUID requestedHostId,
                                                        int requestedCpuNumber,
                                                        UUID currentHostId,
                                                        boolean structureFormed,
                                                        List<C> publishedCpus,
                                                        ToIntFunction<C> cpuNumber,
                                                        Supplier<@Nullable H> activeLeaseHatch,
                                                        Function<H, @Nullable G> connectedGrid,
                                                        BiPredicate<G, C> gridContainsCpu) {
        if (requestedHostId == null || currentHostId == null || publishedCpus == null || cpuNumber == null ||
                activeLeaseHatch == null || connectedGrid == null || gridContainsCpu == null) {
            throw new IllegalArgumentException("Trinity CPU status validation collaborators cannot be null");
        }
        if (!requestedHostId.equals(currentHostId)) {
            return Resolution.rejected(Rejection.WRONG_HOST);
        }
        if (!structureFormed) {
            return Resolution.rejected(Rejection.UNFORMED_STRUCTURE);
        }

        C selectedCpu = null;
        for (C cpu : publishedCpus) {
            if (cpu == null) {
                throw new IllegalStateException("Published Trinity CPU collection contains null");
            }
            if (cpuNumber.applyAsInt(cpu) != requestedCpuNumber) {
                continue;
            }
            if (selectedCpu != null) {
                throw new IllegalStateException("Duplicate published Trinity CPU number: " + requestedCpuNumber);
            }
            selectedCpu = cpu;
        }
        if (selectedCpu == null) {
            return Resolution.rejected(Rejection.UNKNOWN_CPU);
        }

        H hatch = activeLeaseHatch.get();
        if (hatch == null) {
            return Resolution.rejected(Rejection.MISSING_ACTIVE_LEASE);
        }
        G grid = connectedGrid.apply(hatch);
        if (grid == null) {
            return Resolution.rejected(Rejection.MISSING_GRID);
        }
        if (!gridContainsCpu.test(grid, selectedCpu)) {
            return Resolution.rejected(Rejection.CPU_NOT_ON_GRID);
        }
        return Resolution.resolved(selectedCpu, hatch, grid);
    }

    /** Stable rejection categories used by production logging and direct validation tests. */
    public enum Rejection {
        NONE,
        WRONG_CONTAINER,
        WRONG_MENU,
        INVALID_MENU,
        WRONG_HOST,
        UNFORMED_STRUCTURE,
        UNKNOWN_CPU,
        MISSING_ACTIVE_LEASE,
        MISSING_GRID,
        CPU_NOT_ON_GRID
    }

    /** Exact objects proven to belong to the same current host publication and AE grid. */
    public record Resolution<C, H, G>(Rejection rejection,
                                      @Nullable C cpu,
                                      @Nullable H hatch,
                                      @Nullable G grid) {

        private static <C, H, G> Resolution<C, H, G> rejected(Rejection rejection) {
            return new Resolution<>(rejection, null, null, null);
        }

        private static <C, H, G> Resolution<C, H, G> resolved(C cpu, H hatch, G grid) {
            return new Resolution<>(Rejection.NONE, cpu, hatch, grid);
        }

        /** Returns whether every validation stage resolved its exact current object. */
        public boolean isResolved() {
            return this.rejection == Rejection.NONE;
        }
    }
}
