package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.MachineTargetId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CapacitySlicePlannerTest {

    private static final CraftingProviderId PROVIDER_ID = new CraftingProviderId(1L, 1L);

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void plansStableStartupFirstMaxMinSlices(Scenario scenario) {
        CapacitySlicePlan plan = CapacitySlicePlanner.create().plan(
                scenario.snapshots(),
                scenario.request(),
                scenario.physicalCallLimit(),
                scenario.cursor());

        List<ExpectedSlice> actual = plan.slices().stream()
                .map(slice -> new ExpectedSlice(
                        slice.target().route().stableIdentity(),
                        slice.logicalCrafts()))
                .toList();
        assertEquals(scenario.expected(), actual);
        assertEquals(scenario.expectedNextCursor(), plan.nextCursor());
        assertTrue(plan.slices().size() <= scenario.physicalCallLimit());
        BigInteger allocated = plan.slices().stream()
                .map(slice -> BigInteger.valueOf(slice.logicalCrafts()))
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertTrue(allocated.compareTo(scenario.request()) <= 0);
        assertThrows(UnsupportedOperationException.class, () -> plan.slices().clear());
    }

    private static Stream<Scenario> scenarios() {
        List<ProviderCapacitySnapshot> standard = knownSnapshots(4L, 4L, 2L, 8L, 0L, 1L);
        List<ProviderCapacitySnapshot> huge = knownSnapshots(Long.MAX_VALUE, Long.MAX_VALUE);
        BigInteger beyondHugeCapacity = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.TWO)
                .add(BigInteger.ONE);
        return Stream.of(
                new Scenario(
                        "documented fairness example",
                        standard,
                        BigInteger.valueOf(12L),
                        6,
                        0,
                        List.of(
                                expected(0, 3L),
                                expected(1, 3L),
                                expected(2, 2L),
                                expected(3, 3L),
                                expected(5, 1L)),
                        0),
                new Scenario(
                        "full known capacity",
                        standard,
                        BigInteger.valueOf(19L),
                        6,
                        0,
                        List.of(
                                expected(0, 4L),
                                expected(1, 4L),
                                expected(2, 2L),
                                expected(3, 8L),
                                expected(5, 1L)),
                        0),
                new Scenario(
                        "cursor rotates startup order",
                        standard,
                        BigInteger.valueOf(4L),
                        6,
                        3,
                        List.of(
                                expected(3, 1L),
                                expected(5, 1L),
                                expected(0, 1L),
                                expected(1, 1L)),
                        2),
                new Scenario(
                        "physical slots bound slice count",
                        standard,
                        BigInteger.valueOf(12L),
                        2,
                        0,
                        List.of(expected(0, 4L), expected(1, 4L)),
                        2),
                new Scenario(
                        "large cursor remains stable",
                        standard,
                        BigInteger.ONE,
                        1,
                        Integer.MAX_VALUE,
                        List.of(expected(1, 1L)),
                        2),
                new Scenario(
                        "uncertain and non-targeted routes stay single",
                        conservativeSnapshots(),
                        BigInteger.valueOf(12L),
                        6,
                        0,
                        List.of(
                                expected(0, 4L),
                                expected(1, 1L),
                                expected(2, 1L),
                                expected(3, 1L),
                                expected(5, 1L)),
                        0),
                new Scenario(
                        "BigInteger request does not overflow long water filling",
                        huge,
                        beyondHugeCapacity,
                        2,
                        0,
                        List.of(
                                expected(0, Long.MAX_VALUE),
                                expected(1, Long.MAX_VALUE)),
                        0),
                new Scenario(
                        "zero request preserves normalized cursor",
                        standard,
                        BigInteger.ZERO,
                        6,
                        4,
                        List.of(),
                        4));
    }

    private static List<ProviderCapacitySnapshot> knownSnapshots(long... capacities) {
        return IntStream.range(0, capacities.length)
                .mapToObj(index -> snapshot(
                        index,
                        ProviderRoutingMode.TARGETED,
                        new DispatchCapacity.Known(capacities[index]),
                        new DispatchCapacity.Known(capacities[index])))
                .toList();
    }

    private static List<ProviderCapacitySnapshot> conservativeSnapshots() {
        return List.of(
                snapshot(0, ProviderRoutingMode.TARGETED, known(4L), known(4L)),
                snapshot(1, ProviderRoutingMode.TARGETED, DispatchCapacity.Unknown.INSTANCE, known(4L)),
                snapshot(2, ProviderRoutingMode.AGGREGATE, known(2L), known(2L)),
                snapshot(3, ProviderRoutingMode.TARGETED, known(8L), DispatchCapacity.Unknown.INSTANCE),
                snapshot(4, ProviderRoutingMode.TARGETED, known(0L), known(0L)),
                snapshot(5, ProviderRoutingMode.UNKNOWN, known(1L), known(1L)));
    }

    private static DispatchCapacity.Known known(long capacity) {
        return new DispatchCapacity.Known(capacity);
    }

    private static ProviderCapacitySnapshot snapshot(
                                                     int index,
                                                     ProviderRoutingMode routingMode,
                                                     DispatchCapacity capacity,
                                                     DispatchCapacity maximumSingleBatch) {
        return new ProviderCapacitySnapshot(
                PROVIDER_ID,
                new CraftingDispatchTarget(route(index)),
                Optional.of(new MachineTargetId("machine-" + index)),
                "test-pattern",
                1L,
                1L,
                routingMode,
                capacity,
                maximumSingleBatch);
    }

    private static ExpectedSlice expected(int index, long logicalCrafts) {
        return new ExpectedSlice(route(index), logicalCrafts);
    }

    private static String route(int index) {
        return "target-" + index;
    }

    private record Scenario(
                            String name,
                            List<ProviderCapacitySnapshot> snapshots,
                            BigInteger request,
                            int physicalCallLimit,
                            int cursor,
                            List<ExpectedSlice> expected,
                            int expectedNextCursor) {

        @Override
        @NotNull
        public String toString() {
            return this.name;
        }
    }

    private record ExpectedSlice(String target, long logicalCrafts) {}
}
