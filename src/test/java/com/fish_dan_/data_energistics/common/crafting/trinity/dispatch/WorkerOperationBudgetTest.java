package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCpuProfile;

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

        budgets.getFirst().recordTickUsage(4);

        assertEquals(0, budgets.getFirst().availableOperations(3));
        for (int worker = 1; worker < budgets.size(); worker++) {
            assertEquals(4, budgets.get(worker).availableOperations(3));
        }
    }

    @Test
    void normalCoProcessorCountUsesThreeTickWindow() {
        WorkerOperationBudget budget = WorkerOperationBudget.create();

        assertEquals(6, budget.availableOperations(5));
        budget.recordTickUsage(1);
        assertEquals(5, budget.availableOperations(5));
        budget.recordTickUsage(2);
        assertEquals(3, budget.availableOperations(5));
        budget.recordTickUsage(3);
        assertEquals(0, budget.availableOperations(5));
        budget.recordTickUsage(0);
        assertEquals(1, budget.availableOperations(5));
    }

    @Test
    void maximumCoProcessorCountDoesNotOverflow() {
        WorkerOperationBudget budget = WorkerOperationBudget.create();

        assertEquals(Integer.MAX_VALUE, budget.availableOperations(Integer.MAX_VALUE));
        budget.recordTickUsage(6);
        assertEquals(Integer.MAX_VALUE - 5, budget.availableOperations(Integer.MAX_VALUE));
        budget.recordTickUsage(Integer.MAX_VALUE);
        assertEquals(0, budget.availableOperations(Integer.MAX_VALUE));
    }

    @Test
    void rejectsInvalidAccountingValues() {
        WorkerOperationBudget budget = WorkerOperationBudget.create();

        assertThrows(IllegalArgumentException.class, () -> budget.availableOperations(-1));
        assertThrows(IllegalArgumentException.class, () -> budget.recordTickUsage(-1));
    }
}
