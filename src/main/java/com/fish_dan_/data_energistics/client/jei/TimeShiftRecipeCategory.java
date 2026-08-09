package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.recipe.captureball.DataCaptureBallRightClickRecipe;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftIngredient;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftRecipe;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TimeShiftRecipeCategory extends AbstractRecipeCategory<WorldInteractionJeiRecipe> {

    private static final int WIDTH = 148;
    private static final int HEIGHT = 72;
    private static final int CENTER_Y = 36;
    private static final int SLOT_SIZE = 18;
    private static final int INPUT_X = 10;
    private static final int INPUT_Y = 10;
    private static final int ARROW_X = 62;
    private static final int OUTPUT_X = 112;
    private static final int TEXT_X = 40;
    private static final int TEXT_WIDTH = 68;
    private static final int TEXT_COLOR = 0x7E7E7E;
    private static final int RIGHT_CLICK_ITEM_X = 8;
    private static final int RIGHT_CLICK_ITEM_Y = 26;
    private static final int RIGHT_CLICK_BLOCK_X = 61;
    private static final int RIGHT_CLICK_BLOCK_Y = 26;
    private static final int RIGHT_CLICK_ARROW_LEFT_X = 30;
    private static final int RIGHT_CLICK_ARROW_RIGHT_X = 88;
    private static final int RIGHT_CLICK_ARROW_Y = 27;
    private static final int RIGHT_CLICK_OUTPUT_X = 122;
    private static final int RIGHT_CLICK_OUTPUT_Y = 26;

    public static final RecipeType<WorldInteractionJeiRecipe> RECIPE_TYPE = RecipeType.create(Data_Energistics.MODID, "world_interaction", WorldInteractionJeiRecipe.class);

    private final IDrawable background;
    private final IDrawable rightClickBackground;
    private final IDrawableStatic slotDrawable;
    private final IDrawableStatic recipeArrow;

    public TimeShiftRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("recipe.data_energistics.time_shift.category"),
                guiHelper.createDrawableItemLike(DEItems.DATA_CRYSTAL.get()),
                WIDTH,
                HEIGHT);
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.rightClickBackground = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.slotDrawable = guiHelper.getSlotDrawable();
        this.recipeArrow = guiHelper.getRecipeArrow();
    }

    @Override
    public void draw(WorldInteractionJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        switch (recipe) {
            case WorldInteractionJeiRecipe.TimeShiftView timeShiftView -> drawTimeShift(timeShiftView.holder().value(), guiGraphics);
            case WorldInteractionJeiRecipe.RightClickView rightClickView -> drawRightClick(rightClickView.holder().value(), guiGraphics);
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorldInteractionJeiRecipe recipe, IFocusGroup focuses) {
        switch (recipe) {
            case WorldInteractionJeiRecipe.TimeShiftView timeShiftView -> setTimeShiftRecipe(builder, timeShiftView.holder().value());
            case WorldInteractionJeiRecipe.RightClickView rightClickView -> setRightClickRecipe(builder, rightClickView.holder().value());
        }
    }

    private void drawTimeShift(TimeShiftRecipe recipe, GuiGraphics guiGraphics) {
        this.background.draw(guiGraphics);
        drawSlotFrames(guiGraphics, recipe);

        Font font = Minecraft.getInstance().font;
        drawCenteredString(
                guiGraphics,
                font,
                Component.translatable("recipe.data_energistics.time_shift"),
                TEXT_X + TEXT_WIDTH / 2,
                6);
        this.recipeArrow.draw(guiGraphics, ARROW_X, CENTER_Y - 8);

        Component conditionText = Component.translatable(
                "recipe.data_energistics.time_shift.time." + recipe.getTimeCondition().getName());
        Component timeText = Component.translatable(
                "recipe.data_energistics.time_shift.duration",
                formatMinutes(recipe),
                conditionText);
        drawCenteredString(guiGraphics, font, timeText, TEXT_X + TEXT_WIDTH / 2, CENTER_Y + 26);
    }

    private void setTimeShiftRecipe(IRecipeLayoutBuilder builder, TimeShiftRecipe recipe) {
        for (int i = 0; i < recipe.getItemInputs().size(); i++) {
            int x = inputContentX(i);
            int y = inputContentY(recipe.getItemInputs().size(), i);
            builder.addInputSlot(x, y).addItemStacks(withCount(recipe.getItemInputs().get(i)));
        }

        int outputRows = visibleRows(recipe.getResults().size());
        int outputStartY = centeredSlotY(recipe.getResults().size());
        for (int i = 0; i < recipe.getResults().size(); i++) {
            int x = OUTPUT_X + i / outputRows * SLOT_SIZE;
            int y = outputStartY + i % outputRows * SLOT_SIZE;
            builder.addOutputSlot(x, y).addItemStack(recipe.getResults().get(i).copy());
        }
    }

    private void drawRightClick(DataCaptureBallRightClickRecipe recipe, GuiGraphics guiGraphics) {
        this.rightClickBackground.draw(guiGraphics);
        this.slotDrawable.draw(guiGraphics, RIGHT_CLICK_ITEM_X - 1, RIGHT_CLICK_ITEM_Y - 1);
        this.slotDrawable.draw(guiGraphics, RIGHT_CLICK_OUTPUT_X - 1, RIGHT_CLICK_OUTPUT_Y - 1);
        this.recipeArrow.draw(guiGraphics, RIGHT_CLICK_ARROW_LEFT_X, RIGHT_CLICK_ARROW_Y);
        this.recipeArrow.draw(guiGraphics, RIGHT_CLICK_ARROW_RIGHT_X, RIGHT_CLICK_ARROW_Y);

        Font font = Minecraft.getInstance().font;
        drawCenteredString(guiGraphics, font,
                Component.translatable("recipe.data_energistics.data_capture_ball_right_click.apply"),
                RIGHT_CLICK_BLOCK_X + 8,
                8);
    }

    private void setRightClickRecipe(IRecipeLayoutBuilder builder, DataCaptureBallRightClickRecipe recipe) {
        var itemSlot = builder.addInputSlot(RIGHT_CLICK_ITEM_X, RIGHT_CLICK_ITEM_Y)
                .addItemStacks(getItemInputStacks(recipe));
        if (isDataCaptureBallInput(recipe)) {
            itemSlot.addRichTooltipCallback((slotView, tooltip) -> tooltip.add(Component.translatable(
                    "recipe.data_energistics.data_capture_ball_right_click.preset",
                    recipe.getDataCost(), formatEnergy(recipe.getEnergyCost()))));
        }
        builder.addInputSlot(RIGHT_CLICK_BLOCK_X, RIGHT_CLICK_BLOCK_Y).addItemStack(new ItemStack(recipe.getInputBlock()));
        builder.addOutputSlot(RIGHT_CLICK_OUTPUT_X, RIGHT_CLICK_OUTPUT_Y).addItemStack(new ItemStack(recipe.getResultBlock()));
    }

    private static List<ItemStack> getItemInputStacks(DataCaptureBallRightClickRecipe recipe) {
        ItemStack[] itemStacks = recipe.getItemIngredient().getItems();
        if (itemStacks.length == 1 && itemStacks[0].getItem() instanceof DataCaptureBallItem) {
            return List.of(DataCaptureBallItem.createConfiguredStack(recipe.getEnergyCost(), recipe.getDataCost()));
        }
        return Arrays.stream(itemStacks).map(ItemStack::copy).toList();
    }

    private static boolean isDataCaptureBallInput(DataCaptureBallRightClickRecipe recipe) {
        return Arrays.stream(recipe.getItemIngredient().getItems())
                .anyMatch(stack -> stack.getItem() instanceof DataCaptureBallItem);
    }

    private void drawSlotFrames(GuiGraphics guiGraphics, TimeShiftRecipe recipe) {
        for (int i = 0; i < recipe.getItemInputs().size(); i++) {
            int x = inputContentX(i);
            int y = inputContentY(recipe.getItemInputs().size(), i);
            this.slotDrawable.draw(guiGraphics, x - 1, y - 1);
        }

        int outputRows = visibleRows(recipe.getResults().size());
        int outputStartY = centeredSlotY(recipe.getResults().size());
        for (int i = 0; i < recipe.getResults().size(); i++) {
            int x = OUTPUT_X + i / outputRows * SLOT_SIZE;
            int y = outputStartY + i % outputRows * SLOT_SIZE;
            this.slotDrawable.draw(guiGraphics, x - 1, y - 1);
        }
    }

    private static void drawCenteredString(GuiGraphics guiGraphics, Font font, Component text, int centerX, int y) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, TEXT_COLOR, false);
    }

    private static String formatMinutes(TimeShiftRecipe recipe) {
        double minutes = recipe.getDurationMinutes();
        return formatMinutes(minutes);
    }

    private static String formatMinutes(double minutes) {
        if (minutes == Math.rint(minutes)) {
            return Integer.toString((int) minutes);
        }

        return String.format(Locale.ROOT, "%.2f", minutes);
    }

    private static String formatEnergy(double energy) {
        if (energy == Math.rint(energy)) {
            return Long.toString((long) energy);
        }

        return Double.toString(energy);
    }

    private static int centeredSlotY(int slotCount) {
        return CENTER_Y - visibleRows(slotCount) * SLOT_SIZE / 2;
    }

    private static int inputContentX(int index) {
        return INPUT_X + index / 3 * SLOT_SIZE;
    }

    private static int inputContentY(int slotCount, int index) {
        return inputStartY(slotCount) + index % 3 * SLOT_SIZE;
    }

    private static int inputStartY(int slotCount) {
        return INPUT_Y + (slotCount < 3 ? 9 * (3 - slotCount) : 0);
    }

    private static int visibleRows(int slotCount) {
        return Math.min(Math.max(slotCount, 1), 3);
    }

    private static List<ItemStack> withCount(TimeShiftIngredient ingredient) {
        return Arrays.stream(ingredient.ingredient().getItems()).map(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(ingredient.count());
            return copy;
        }).toList();
    }
}
