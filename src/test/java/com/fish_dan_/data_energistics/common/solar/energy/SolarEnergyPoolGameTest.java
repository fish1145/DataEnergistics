package com.fish_dan_.data_energistics.common.solar.energy;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.config.Actionable;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class SolarEnergyPoolGameTest {

    private static final double TOLERANCE = 0.000001D;

    private SolarEnergyPoolGameTest() {}

    @TestHolder("solar_energy_pool_stores_and_extracts_across_cells_round_robin")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storesAndExtractsAcrossCellsRoundRobin(GameTestHelper helper) {
        TestCell first = new TestCell(0.0D, 10.0D);
        TestCell second = new TestCell(0.0D, 10.0D);
        SolarEnergyPool pool = new SolarEnergyPool(List.of(first, second));

        assertEquals(5.0D, pool.insert(5.0D, Actionable.MODULATE), "First insertion must be accepted");
        assertEquals(5.0D, first.stored(), "First insertion must start at the first cell");
        assertEquals(5.0D, pool.insert(5.0D, Actionable.MODULATE), "Second insertion must be accepted");
        assertEquals(5.0D, second.stored(), "Round-robin insertion must advance to the second cell");

        assertEquals(5.0D, pool.extract(5.0D, Actionable.MODULATE), "First extraction must be accepted");
        assertEquals(0.0D, first.stored(), "First extraction must start at the first cell");
        assertEquals(5.0D, pool.extract(5.0D, Actionable.MODULATE), "Second extraction must be accepted");
        assertEquals(0.0D, second.stored(), "Round-robin extraction must advance to the second cell");
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_returns_partial_accepted_amount")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void returnsPartialAcceptedAmount(GameTestHelper helper) {
        TestCell first = new TestCell(8.0D, 10.0D);
        TestCell second = new TestCell(4.0D, 5.0D);
        SolarEnergyPool pool = new SolarEnergyPool(List.of(first, second));

        assertEquals(3.0D, pool.insert(10.0D, Actionable.MODULATE), "Pool must return only the amount cells accepted");
        assertSnapshot(pool.snapshot(), 15.0D, 15.0D);
        assertEquals(15.0D, pool.extract(20.0D, Actionable.MODULATE), "Pool must return only available energy");
        assertSnapshot(pool.snapshot(), 0.0D, 15.0D);
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_simulation_preserves_cells_and_cursors")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void simulationPreservesCellsAndCursors(GameTestHelper helper) {
        TestCell first = new TestCell(0.0D, 10.0D);
        TestCell second = new TestCell(0.0D, 10.0D);
        SolarEnergyPool pool = new SolarEnergyPool(List.of(first, second));

        assertEquals(5.0D, pool.insert(5.0D, Actionable.SIMULATE), "Simulated insertion must report capacity");
        assertSnapshot(pool.snapshot(), 0.0D, 20.0D);
        assertEquals(5.0D, pool.insert(5.0D, Actionable.MODULATE), "Real insertion must remain possible");
        assertEquals(5.0D, first.stored(), "Simulation must not advance the insertion cursor");

        assertEquals(5.0D, pool.extract(5.0D, Actionable.SIMULATE), "Simulated extraction must report availability");
        assertEquals(5.0D, first.stored(), "Simulated extraction must not mutate a cell");
        assertEquals(5.0D, pool.extract(5.0D, Actionable.MODULATE), "Real extraction must remain possible");
        assertEquals(0.0D, first.stored(), "Simulation must not advance the extraction cursor");
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_rejects_invalid_amounts_and_cell_results")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsInvalidAmountsAndCellResults(GameTestHelper helper) {
        SolarEnergyPool pool = new SolarEnergyPool(List.of(new TestCell(0.0D, 10.0D)));

        expectException(
                IllegalArgumentException.class,
                () -> pool.insert(Double.NaN, Actionable.MODULATE),
                "Insertion must reject non-finite input");
        expectException(
                IllegalArgumentException.class,
                () -> pool.extract(-1.0D, Actionable.MODULATE),
                "Extraction must reject negative input");

        SolarEnergyPool invalidCellPool = new SolarEnergyPool(List.of(new InvalidCell()));
        expectException(
                IllegalStateException.class,
                () -> invalidCellPool.insert(1.0D, Actionable.MODULATE),
                "Pool must reject a cell accepting more than it was offered");
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_counts_repeated_cell_identity_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void countsRepeatedCellIdentityOnce(GameTestHelper helper) {
        TestCell cell = new TestCell(40.0D, 100.0D);
        SolarEnergyPool pool = new SolarEnergyPool(List.of(cell, cell));

        assertSnapshot(pool.snapshot(), 40.0D, 100.0D);
        assertEquals(40.0D, pool.extract(80.0D, Actionable.MODULATE), "Repeated identity must not duplicate energy");
        assertEquals(0.0D, cell.stored(), "The raw cell must be extracted exactly once");
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_supports_capacity_above_integer_range")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void supportsCapacityAboveIntegerRange(GameTestHelper helper) {
        TestCell first = new TestCell(2_000_000_000.0D, 2_000_000_000.0D);
        TestCell second = new TestCell(2_000_000_000.0D, 2_000_000_000.0D);
        SolarEnergyPool pool = new SolarEnergyPool(List.of(first, second));

        assertSnapshot(pool.snapshot(), 4_000_000_000.0D, 4_000_000_000.0D);
        assertEquals(
                3_000_000_000.0D,
                pool.extract(3_000_000_000.0D, Actionable.MODULATE),
                "Extraction must not truncate to an integer");
        assertEquals(1_000_000_000.0D, pool.snapshot().stored(), "One billion energy must remain");
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_moves_shrink_overflow_without_losing_source_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void movesShrinkOverflowWithoutLosingSourceRemainder(GameTestHelper helper) {
        TestCell source = new TestCell(100.0D, 100.0D);
        TestCell firstTarget = new TestCell(0.0D, 30.0D);
        TestCell secondTarget = new TestCell(0.0D, 20.0D);
        SolarEnergyPool pool = new SolarEnergyPool(List.of(source, firstTarget, secondTarget));

        assertEquals(
                50.0D,
                pool.transferToOtherCells(source, 60.0D),
                "Transfer must stop when all other cells are full");
        assertEquals(50.0D, source.stored(), "Unmoved shrink overflow must remain in the source");
        assertEquals(100.0D, pool.snapshot().stored(), "Transfer must conserve total stored energy");
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_reuses_cells_across_regrouping_without_copying_energy")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reusesCellsAcrossRegroupingWithoutCopyingEnergy(GameTestHelper helper) {
        TestCell first = new TestCell(40.0D, 50.0D);
        TestCell second = new TestCell(30.0D, 50.0D);
        TestCell third = new TestCell(20.0D, 50.0D);
        SolarEnergyPool original = new SolarEnergyPool(List.of(first, second));

        assertEquals(50.0D, original.extract(50.0D, Actionable.MODULATE), "Original grouping must extract raw shares");
        SolarEnergyPool regrouped = new SolarEnergyPool(List.of(second, third));
        SolarEnergyPool remainder = new SolarEnergyPool(List.of(first));

        assertEquals(
                40.0D,
                regrouped.snapshot().stored() + remainder.snapshot().stored(),
                "Regrouping must preserve the raw energy left in reused cells");
        assertEquals(20.0D, original.snapshot().stored(), "Existing pool views must observe the same raw cells");
        helper.succeed();
    }

    @TestHolder("solar_energy_pool_reports_actual_transfer_for_oversized_request")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reportsActualTransferForOversizedRequest(GameTestHelper helper) {
        SolarEnergyPool pool = new SolarEnergyPool(List.of(new TestCell(0.0D, 10.0D)));
        assertEquals(10.0D, pool.insert(Double.MAX_VALUE, Actionable.MODULATE),
                "Large requests must report accepted energy instead of subtracting nearly equal doubles");
        assertEquals(10.0D, pool.extract(Double.MAX_VALUE, Actionable.MODULATE),
                "Large extraction must report the actual removed amount");
        assertSnapshot(pool.snapshot(), 0.0D, 10.0D);
        helper.succeed();
    }

    private static void assertSnapshot(SolarEnergyPool.Snapshot snapshot, double stored, double capacity) {
        assertEquals(stored, snapshot.stored(), "Unexpected aggregate stored energy");
        assertEquals(capacity, snapshot.capacity(), "Unexpected aggregate capacity");
    }

    private static void assertEquals(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > TOLERANCE) {
            throw new GameTestAssertException(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expectException(Class<? extends RuntimeException> expected, Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            if (expected.isInstance(exception)) {
                return;
            }
            throw new GameTestAssertException(
                    message + ": expected " + expected.getSimpleName() + ", got " + exception.getClass().getSimpleName());
        }
        throw new GameTestAssertException(message + ": expected " + expected.getSimpleName());
    }

    private static final class TestCell implements SolarEnergyPool.Cell {

        private double stored;
        private final double capacity;

        private TestCell(double stored, double capacity) {
            this.stored = stored;
            this.capacity = capacity;
        }

        @Override
        public double stored() {
            return this.stored;
        }

        @Override
        public double capacity() {
            return this.capacity;
        }

        @Override
        public double insert(double amount, Actionable mode) {
            double accepted = Math.min(amount, this.capacity - this.stored);
            if (mode == Actionable.MODULATE) {
                this.stored += accepted;
            }
            return accepted;
        }

        @Override
        public double extract(double amount, Actionable mode) {
            double accepted = Math.min(amount, this.stored);
            if (mode == Actionable.MODULATE) {
                this.stored -= accepted;
            }
            return accepted;
        }
    }

    private static final class InvalidCell implements SolarEnergyPool.Cell {

        @Override
        public double stored() {
            return 0.0D;
        }

        @Override
        public double capacity() {
            return 10.0D;
        }

        @Override
        public double insert(double amount, Actionable mode) {
            return amount + 1.0D;
        }

        @Override
        public double extract(double amount, Actionable mode) {
            return 0.0D;
        }
    }
}
