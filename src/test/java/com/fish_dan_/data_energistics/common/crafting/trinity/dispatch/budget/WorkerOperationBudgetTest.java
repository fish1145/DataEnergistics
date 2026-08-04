package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget;

import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuProfile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class WorkerOperationBudgetTest {

    @Test
    void workerBudgetsRemainIndependentAtMaximumCapacity() {
        List<WorkerOperationBudget> budgets = new ArrayList<>(TrinityDataCoreCpuProfile.MAX_PARTITION_COUNT);
        for (int worker = 0; worker < TrinityDataCoreCpuProfile.MAX_PARTITION_COUNT; worker++) {
            budgets.add(WorkerOperationBudget.create());
        }

        budgets.getFirst().recordTickUsage(10L, 4);

        assertEquals(0, budgets.getFirst().availableOperations(3, 10L));
        assertEquals(4L, budgets.getFirst().recentOperations(10L));
        for (int worker = 1; worker < budgets.size(); worker++) {
            assertEquals(4, budgets.get(worker).availableOperations(3, 10L));
            assertEquals(0L, budgets.get(worker).recentOperations(10L));
        }
    }

    @Test
    void normalCoProcessorCountUsesThreeTickWindow() {
        WorkerOperationBudget budget = WorkerOperationBudget.create();

        assertEquals(6, budget.availableOperations(5, 20L));
        budget.recordTickUsage(20L, 1);
        assertEquals(5, budget.availableOperations(5, 20L));
        assertEquals(1L, budget.recentOperations(20L));
        budget.recordTickUsage(21L, 2);
        assertEquals(3, budget.availableOperations(5, 21L));
        assertEquals(3L, budget.recentOperations(21L));
        budget.recordTickUsage(22L, 3);
        assertEquals(0, budget.availableOperations(5, 22L));
        assertEquals(6L, budget.recentOperations(22L));
        budget.recordTickUsage(23L, 0);
        assertEquals(1, budget.availableOperations(5, 23L));
        assertEquals(5L, budget.recentOperations(23L));
    }

    @Test
    void skippedTicksAgeIdleWorkerLoadOutOfWindow() {
        WorkerOperationBudget budget = WorkerOperationBudget.create();

        budget.recordTickUsage(30L, 4);

        assertEquals(4L, budget.recentOperations(31L));
        assertEquals(4L, budget.recentOperations(32L));
        assertEquals(0L, budget.recentOperations(33L));
        assertEquals(4, budget.availableOperations(3, 40L));
    }

    @Test
    void maximumCoProcessorCountDoesNotOverflow() {
        WorkerOperationBudget budget = WorkerOperationBudget.create();

        assertEquals(Integer.MAX_VALUE, budget.availableOperations(Integer.MAX_VALUE, 100L));
        budget.recordTickUsage(100L, Integer.MAX_VALUE);
        assertEquals(1, budget.availableOperations(Integer.MAX_VALUE, 100L));
        budget.recordTickUsage(100L, 1);
        assertEquals(1L + Integer.MAX_VALUE, budget.recentOperations(100L));
        assertEquals(0, budget.availableOperations(Integer.MAX_VALUE, 101L));
    }

    @Test
    void rejectsInvalidAccountingValues() {
        WorkerOperationBudget budget = WorkerOperationBudget.create();

        assertThrows(IllegalArgumentException.class, () -> budget.availableOperations(-1, 0L));
        assertThrows(IllegalArgumentException.class, () -> budget.availableOperations(0, -1L));
        assertThrows(IllegalArgumentException.class, () -> budget.recordTickUsage(0L, -1));
        budget.recordTickUsage(10L, 1);
        budget.recordTickUsage(10L, 2);
        assertEquals(3L, budget.recentOperations(10L));
        assertThrows(IllegalArgumentException.class, () -> budget.recentOperations(9L));
    }
}
