package com.fish_dan_.data_energistics.recipe.captureball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DataCaptureBallRightClickRecipeLogicTest {

    @Test
    void ordinaryItemCanRunZeroCostRecipe() {
        assertTrue(DataCaptureBallRightClickRecipeLogic.canRunOrdinaryItem(0L, 0.0D));
    }

    @Test
    void ordinaryItemCannotBypassCaptureBallCosts() {
        assertFalse(DataCaptureBallRightClickRecipeLogic.canRunOrdinaryItem(1L, 5_000.0D));
    }
}
