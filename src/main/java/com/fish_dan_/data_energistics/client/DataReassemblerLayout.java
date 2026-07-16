package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;

public final class DataReassemblerLayout {

    public static final int RECIPE_WIDTH = 162;
    public static final int RECIPE_HEIGHT = 58;
    public static final int PROGRESS_X = 153;
    public static final int PROGRESS_Y = 20;
    public static final int PROGRESS_WIDTH = 6;
    public static final int PROGRESS_HEIGHT = 18;

    private static final int SLOT_SPACING = 18;
    private static final int RECIPE_ITEM_INPUT_START_X = 7;
    private static final int RECIPE_ITEM_INPUT_START_Y = 2;
    private static final SlotPos[] RECIPE_ITEM_OUTPUTS = {
            new SlotPos(113, 2),
            new SlotPos(113, 20),
            new SlotPos(113, 38)
    };
    private static final SlotPos RECIPE_KEY_INPUT = new SlotPos(62, 20);
    private static final SlotPos RECIPE_KEY_OUTPUT = new SlotPos(131, 20);
    private static final SlotPos[] RECIPE_FLUID_INPUTS = {
            new SlotPos(62, 2),
            new SlotPos(62, 38)
    };
    private static final SlotPos[] RECIPE_FLUID_OUTPUTS = {
            new SlotPos(131, 2),
            new SlotPos(131, 38)
    };

    private DataReassemblerLayout() {}

    public static SlotPos recipeItemInput(int index) {
        if (index < 0 || index >= DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS) {
            throw invalidSlotIndex("No recipe item input slot for index " + index);
        }
        return new SlotPos(
                RECIPE_ITEM_INPUT_START_X + index % 3 * SLOT_SPACING,
                RECIPE_ITEM_INPUT_START_Y + index / 3 * SLOT_SPACING);
    }

    public static SlotPos recipeItemOutput(int index) {
        return bounded(RECIPE_ITEM_OUTPUTS, index);
    }

    public static SlotPos recipeKeyInput() {
        return RECIPE_KEY_INPUT;
    }

    public static SlotPos recipeKeyOutput() {
        return RECIPE_KEY_OUTPUT;
    }

    public static SlotPos recipeFluidInput(int index) {
        return bounded(RECIPE_FLUID_INPUTS, index);
    }

    public static SlotPos recipeFluidOutput(int index) {
        return bounded(RECIPE_FLUID_OUTPUTS, index);
    }

    private static SlotPos bounded(SlotPos[] positions, int index) {
        if (index < 0 || index >= positions.length) {
            throw invalidSlotIndex("No layout slot for index " + index);
        }
        return positions[index];
    }

    private static IndexOutOfBoundsException invalidSlotIndex(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IndexOutOfBoundsException(message);
    }

    public record SlotPos(int x, int y) {}
}
