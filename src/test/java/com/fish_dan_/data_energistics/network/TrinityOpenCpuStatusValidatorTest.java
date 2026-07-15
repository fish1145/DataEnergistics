package com.fish_dan_.data_energistics.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.CPU_NOT_ON_GRID;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.MISSING_ACTIVE_LEASE;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.MISSING_GRID;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.NONE;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.UNFORMED_STRUCTURE;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.UNKNOWN_CPU;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.WRONG_CONTAINER;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.WRONG_HOST;
import static com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusValidator.Rejection.WRONG_MENU;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityOpenCpuStatusValidatorTest {

    private static final UUID HOST_ID = UUID.fromString("96758744-4ed5-454a-ab65-a6583bc45801");

    @Test
    void resolvesTheSamePublishedCpuLeaseAndGridObjects() {
        Cpu reserve = new Cpu(0);
        Cpu worker = new Cpu(4);
        Hatch hatch = new Hatch("lease-owner");
        Grid grid = new Grid(List.of(reserve, worker));

        TrinityOpenCpuStatusValidator.Resolution<Cpu, Hatch, Grid> resolution = resolve(
                HOST_ID,
                true,
                4,
                List.of(reserve, worker),
                hatch,
                grid);

        assertTrue(resolution.isResolved());
        assertEquals(NONE, resolution.rejection());
        assertSame(worker, resolution.cpu());
        assertSame(hatch, resolution.hatch());
        assertSame(grid, resolution.grid());
    }

    @Test
    void rejectsWrongContainerAndMenuBeforeHostResolution() {
        assertEquals(WRONG_CONTAINER, TrinityOpenCpuStatusValidator.validateMenu(6, 7, true));
        assertEquals(WRONG_MENU, TrinityOpenCpuStatusValidator.validateMenu(7, 7, false));
        assertEquals(NONE, TrinityOpenCpuStatusValidator.validateMenu(7, 7, true));
    }

    @Test
    void rejectsWrongHostUnformedStructureAndStaleCpu() {
        Cpu cpu = new Cpu(2);
        Hatch hatch = new Hatch("lease-owner");
        Grid grid = new Grid(List.of(cpu));

        assertRejected(WRONG_HOST, resolve(UUID.randomUUID(), true, 2, List.of(cpu), hatch, grid));
        assertRejected(UNFORMED_STRUCTURE, resolve(HOST_ID, false, 2, List.of(cpu), hatch, grid));
        assertRejected(UNKNOWN_CPU, resolve(HOST_ID, true, 3, List.of(cpu), hatch, grid));
    }

    @Test
    void rejectsMissingLeaseDisconnectedGridAndGridMembershipChanges() {
        Cpu cpu = new Cpu(2);
        Hatch hatch = new Hatch("lease-owner");
        Grid grid = new Grid(List.of(cpu));

        assertRejected(MISSING_ACTIVE_LEASE, resolve(HOST_ID, true, 2, List.of(cpu), null, grid));
        assertRejected(MISSING_GRID, resolve(HOST_ID, true, 2, List.of(cpu), hatch, null));
        assertRejected(CPU_NOT_ON_GRID, resolve(
                HOST_ID,
                true,
                2,
                List.of(cpu),
                hatch,
                new Grid(List.of())));
    }

    @Test
    void duplicatePublishedStableNumbersFailFast() {
        Cpu first = new Cpu(1);
        Cpu duplicate = new Cpu(1);
        Hatch hatch = new Hatch("lease-owner");
        Grid grid = new Grid(List.of(first));

        assertThrows(
                IllegalStateException.class,
                () -> resolve(HOST_ID, true, 1, List.of(first, duplicate), hatch, grid));
    }

    private static TrinityOpenCpuStatusValidator.Resolution<Cpu, Hatch, Grid> resolve(
                                                                                      UUID requestedHostId,
                                                                                      boolean formed,
                                                                                      int cpuNumber,
                                                                                      List<Cpu> publishedCpus,
                                                                                      Hatch hatch,
                                                                                      Grid grid) {
        return TrinityOpenCpuStatusValidator.resolve(
                requestedHostId,
                cpuNumber,
                HOST_ID,
                formed,
                publishedCpus,
                Cpu::number,
                () -> hatch,
                ignored -> grid,
                (currentGrid, cpu) -> currentGrid.cpus().contains(cpu));
    }

    private static void assertRejected(
                                       TrinityOpenCpuStatusValidator.Rejection rejection,
                                       TrinityOpenCpuStatusValidator.Resolution<Cpu, Hatch, Grid> resolution) {
        assertFalse(resolution.isResolved());
        assertEquals(rejection, resolution.rejection());
        assertEquals(null, resolution.cpu());
        assertEquals(null, resolution.hatch());
        assertEquals(null, resolution.grid());
    }

    private record Cpu(int number) {}

    private record Hatch(String id) {}

    private record Grid(List<Cpu> cpus) {}
}
