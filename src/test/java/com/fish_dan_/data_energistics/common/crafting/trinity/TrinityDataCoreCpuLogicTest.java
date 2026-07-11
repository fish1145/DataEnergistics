package com.fish_dan_.data_energistics.common.crafting.trinity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityDataCoreCpuLogicTest {

    @Test
    void normalCoProcessorCountUsesThreeTickWindow() {
        assertEquals(6, TrinityDataCoreCpuLogic.operationBudget(5, new int[] { 0, 0, 0 }));
        assertEquals(3, TrinityDataCoreCpuLogic.operationBudget(5, new int[] { 1, 2, 0 }));
    }

    @Test
    void maximumCoProcessorCountStillProvidesDispatchOperations() {
        assertEquals(
                Integer.MAX_VALUE,
                TrinityDataCoreCpuLogic.operationBudget(Integer.MAX_VALUE, new int[] { 0, 0, 0 }));
        assertEquals(
                Integer.MAX_VALUE - 5,
                TrinityDataCoreCpuLogic.operationBudget(Integer.MAX_VALUE, new int[] { 1, 2, 3 }));
        assertEquals(
                1,
                TrinityDataCoreCpuLogic.operationBudget(
                        Integer.MAX_VALUE,
                        new int[] { Integer.MAX_VALUE, 0, 0 }));
        assertEquals(
                0,
                TrinityDataCoreCpuLogic.operationBudget(
                        Integer.MAX_VALUE,
                        new int[] { Integer.MAX_VALUE, 1, 0 }));
    }

    @Test
    void dispatchBudgetNeverBecomesNegative() {
        assertEquals(0, TrinityDataCoreCpuLogic.operationBudget(2, new int[] { 1, 1, 1 }));
        assertEquals(0, TrinityDataCoreCpuLogic.operationBudget(2, new int[] { Integer.MAX_VALUE, 0, 0 }));
    }

    @Test
    void dispatchBudgetRejectsInvalidAccountingState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityDataCoreCpuLogic.operationBudget(-1, new int[] { 0, 0, 0 }));
        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityDataCoreCpuLogic.operationBudget(0, new int[] { 0, 0 }));
        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityDataCoreCpuLogic.operationBudget(0, new int[] { 0, -1, 0 }));
    }
}
