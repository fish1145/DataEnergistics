package com.fish_dan_.data_energistics.menu;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCraftingStatusSelectionTest {

    private static final UUID HOST_ID = UUID.fromString("36fbf05f-869d-41d1-89b6-367c6e80eb62");

    @Test
    void acceptsOnlyTheExactHostRuntimePublishedCpuAndGridMembership() {
        Object runtime = new Object();
        Cpu cpu = new Cpu(7);
        Grid grid = new Grid(List.of(cpu));

        assertTrue(matches(HOST_ID, runtime, cpu, HOST_ID, runtime, List.of(cpu), grid));
        assertFalse(matches(UUID.randomUUID(), runtime, cpu, HOST_ID, runtime, List.of(cpu), grid));
        assertFalse(matches(HOST_ID, runtime, cpu, HOST_ID, new Object(), List.of(cpu), grid));
        assertFalse(matches(HOST_ID, runtime, cpu, HOST_ID, runtime, List.of(), grid));
        assertFalse(matches(HOST_ID, runtime, cpu, HOST_ID, runtime, List.of(cpu), new Grid(List.of())));
        assertFalse(matches(HOST_ID, runtime, cpu, HOST_ID, runtime, List.of(cpu), null));
    }

    @Test
    void equalButDifferentCpuObjectsDoNotSatisfyPublicationIdentity() {
        Object runtime = new Object();
        Cpu expected = new Cpu(4);
        Cpu equalReplacement = new Cpu(4);

        assertFalse(matches(
                HOST_ID,
                runtime,
                expected,
                HOST_ID,
                runtime,
                List.of(equalReplacement),
                new Grid(List.of(equalReplacement))));
    }

    @Test
    void malformedPublishedCpuStateFailsFast() {
        Object runtime = new Object();
        Cpu cpu = new Cpu(1);
        Grid grid = new Grid(List.of(cpu));

        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityCraftingStatusSelection.matchesCurrentIdentity(
                        HOST_ID,
                        runtime,
                        cpu,
                        HOST_ID,
                        runtime,
                        null,
                        grid,
                        (currentGrid, targetCpu) -> currentGrid.cpus().contains(targetCpu)));
        assertThrows(
                IllegalStateException.class,
                () -> TrinityCraftingStatusSelection.matchesCurrentIdentity(
                        HOST_ID,
                        runtime,
                        cpu,
                        HOST_ID,
                        runtime,
                        Arrays.asList((Cpu) null),
                        grid,
                        (currentGrid, targetCpu) -> currentGrid.cpus().contains(targetCpu)));
    }

    private static boolean matches(UUID expectedHostId,
                                   Object expectedRuntime,
                                   Cpu expectedCpu,
                                   UUID currentHostId,
                                   Object currentRuntime,
                                   List<Cpu> publishedCpus,
                                   Grid grid) {
        return TrinityCraftingStatusSelection.matchesCurrentIdentity(
                expectedHostId,
                expectedRuntime,
                expectedCpu,
                currentHostId,
                currentRuntime,
                publishedCpus,
                grid,
                (currentGrid, targetCpu) -> currentGrid.cpus().contains(targetCpu));
    }

    private record Cpu(int number) {}

    private record Grid(List<Cpu> cpus) {}
}
