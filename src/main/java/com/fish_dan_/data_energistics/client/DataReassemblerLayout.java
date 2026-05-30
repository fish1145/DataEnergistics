package com.fish_dan_.data_energistics.client;

public final class DataReassemblerLayout {

    public static final int RECIPE_WIDTH = 162;
    public static final int RECIPE_HEIGHT = 58;
    public static final int SLOT_SPACING = 18;
    public static final int PROGRESS_X = 153;
    public static final int PROGRESS_Y = 20;
    public static final int PROGRESS_WIDTH = 6;
    public static final int PROGRESS_HEIGHT = 18;

    private static final int JEI_ITEM_INPUT_START_X = 8;
    private static final int JEI_ITEM_INPUT_START_Y = 3;
    private static final SlotPos[] JEI_ITEM_OUTPUTS = {
            new SlotPos(114, 3),
            new SlotPos(114, 21),
            new SlotPos(114, 39)
    };
    private static final SlotPos JEI_KEY_INPUT = new SlotPos(63, 21);
    private static final SlotPos JEI_KEY_OUTPUT = new SlotPos(132, 21);
    private static final SlotPos[] JEI_FLUID_INPUTS = {
            new SlotPos(63, 3),
            new SlotPos(63, 39)
    };
    private static final SlotPos[] JEI_FLUID_OUTPUTS = {
            new SlotPos(132, 3),
            new SlotPos(132, 39)
    };

    private static final int GUIDE_ITEM_INPUT_START_X = 7;
    private static final int GUIDE_ITEM_INPUT_START_Y = 2;
    private static final SlotPos[] GUIDE_ITEM_OUTPUTS = {
            new SlotPos(113, 2),
            new SlotPos(113, 20),
            new SlotPos(113, 38)
    };
    private static final SlotPos GUIDE_KEY_INPUT = new SlotPos(63, 21);
    private static final SlotPos GUIDE_KEY_OUTPUT = new SlotPos(132, 21);
    private static final SlotPos[] GUIDE_FLUID_INPUTS = {
            new SlotPos(63, 3),
            new SlotPos(63, 39)
    };
    private static final SlotPos[] GUIDE_FLUID_OUTPUTS = {
            new SlotPos(132, 3),
            new SlotPos(132, 39)
    };

    private static final int TERMINAL_SPECIAL_INPUT_X_OFFSET = 55;
    private static final int TERMINAL_SPECIAL_ROW_SPACING = 18;
    private static final int TERMINAL_SPECIAL_OUTPUT_X_OFFSET = 18;

    private DataReassemblerLayout() {}

    public static SlotPos jeiItemInput(int index) {
        return new SlotPos(
                JEI_ITEM_INPUT_START_X + index % 3 * SLOT_SPACING,
                JEI_ITEM_INPUT_START_Y + index / 3 * SLOT_SPACING);
    }

    public static SlotPos jeiItemOutput(int index) {
        return bounded(JEI_ITEM_OUTPUTS, index);
    }

    public static SlotPos jeiKeyInput() {
        return JEI_KEY_INPUT;
    }

    public static SlotPos jeiKeyOutput() {
        return JEI_KEY_OUTPUT;
    }

    public static SlotPos jeiFluidInput(int index) {
        return bounded(JEI_FLUID_INPUTS, index);
    }

    public static SlotPos jeiFluidOutput(int index) {
        return bounded(JEI_FLUID_OUTPUTS, index);
    }

    public static SlotPos emiItemInput(int index) {
        return jeiItemInput(index);
    }

    public static SlotPos emiItemOutput(int index) {
        return jeiItemOutput(index);
    }

    public static SlotPos emiKeyInput() {
        return jeiKeyInput();
    }

    public static SlotPos emiKeyOutput() {
        return jeiKeyOutput();
    }

    public static SlotPos emiFluidInput(int index) {
        return jeiFluidInput(index);
    }

    public static SlotPos emiFluidOutput(int index) {
        return jeiFluidOutput(index);
    }

    public static SlotPos guideItemInput(int index) {
        return new SlotPos(
                GUIDE_ITEM_INPUT_START_X + index % 3 * SLOT_SPACING,
                GUIDE_ITEM_INPUT_START_Y + index / 3 * SLOT_SPACING);
    }

    public static SlotPos guideItemOutput(int index) {
        return bounded(GUIDE_ITEM_OUTPUTS, index);
    }

    public static SlotPos guideKeyInput() {
        return GUIDE_KEY_INPUT;
    }

    public static SlotPos guideKeyOutput() {
        return GUIDE_KEY_OUTPUT;
    }

    public static SlotPos guideFluidInput(int index) {
        return bounded(GUIDE_FLUID_INPUTS, index);
    }

    public static SlotPos guideFluidOutput(int index) {
        return bounded(GUIDE_FLUID_OUTPUTS, index);
    }

    public static SlotPos terminalInputSpecial(int anchorX, int anchorY, int row) {
        return new SlotPos(anchorX + TERMINAL_SPECIAL_INPUT_X_OFFSET, anchorY + row * TERMINAL_SPECIAL_ROW_SPACING);
    }

    public static SlotPos terminalOutputSpecial(int anchorX, int anchorY) {
        return new SlotPos(anchorX + TERMINAL_SPECIAL_OUTPUT_X_OFFSET, anchorY);
    }

    private static SlotPos bounded(SlotPos[] positions, int index) {
        if (index < 0 || index >= positions.length) {
            throw new IndexOutOfBoundsException("No layout slot for index " + index);
        }
        return positions[index];
    }

    public record SlotPos(int x, int y) {}
}
