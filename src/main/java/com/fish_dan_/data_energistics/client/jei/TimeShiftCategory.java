package com.fish_dan_.data_energistics.client.jei;

import appeng.core.definitions.AEItems;
import appeng.core.localization.ItemModText;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.TimeShiftRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class TimeShiftCategory extends AbstractRecipeCategory<RecipeHolder<TimeShiftRecipe>> {
    private static final int WIDTH = 140;
    private static final int HEIGHT = 80;
    private static final int CENTER_Y = 40;
    private static final int SLOT_SIZE = 18;
    private static final int INPUT_X = 16;
    private static final int ARROW_X = 58;
    private static final int TEXT_X = 35;
    private static final int TEXT_WIDTH = 70;
    private static final int OUTPUT_X = 106;

    public static final RecipeType<RecipeHolder<TimeShiftRecipe>> RECIPE_TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "time_shift"));

    public TimeShiftCategory(IGuiHelper guiHelper) {
        super(RECIPE_TYPE, ItemModText.TRANSFORM_CATEGORY.text(), guiHelper.createDrawableItemLike(AEItems.FLUIX_CRYSTAL), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<TimeShiftRecipe> holder, IFocusGroup focuses) {
        TimeShiftRecipe recipe = holder.value();

        int inputRows = visibleRows(recipe.getIngredients().size());
        int inputStartY = centeredSlotY(recipe.getIngredients().size());
        int inputIndex = 0;
        for (var ingredient : recipe.getIngredients()) {
            int x = INPUT_X + inputIndex / inputRows * SLOT_SIZE;
            int y = inputStartY + inputIndex % inputRows * SLOT_SIZE;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setStandardSlotBackground()
                    .addIngredients(ingredient);
            inputIndex++;
        }

        List<ItemStack> results = recipe.getResults();
        int outputRows = visibleRows(results.size());
        int outputStartY = centeredSlotY(results.size());
        int outputIndex = 0;
        for (ItemStack result : results) {
            int x = OUTPUT_X + outputIndex / outputRows * SLOT_SIZE;
            int y = outputStartY + outputIndex % outputRows * SLOT_SIZE;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .setStandardSlotBackground()
                    .addItemStack(result);
            outputIndex++;
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<TimeShiftRecipe> holder, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(ARROW_X, CENTER_Y - 8);

        Component titleText = Component.translatable("recipe.data_energistics.time_shift");
        builder.addText(List.of(titleText), TEXT_WIDTH, 16)
                .setPosition(TEXT_X, 8)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0x7E7E7E);

        Component conditionText = Component.translatable("recipe.data_energistics.time_shift.time." + holder.value().getTimeCondition().getName());
        Component timeText = Component.translatable("recipe.data_energistics.time_shift.duration", formatMinutes(holder.value()), conditionText);
        builder.addText(List.of(timeText), TEXT_WIDTH, 16)
                .setPosition(TEXT_X, CENTER_Y + 16)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0x7E7E7E);
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<TimeShiftRecipe> holder) {
        return holder.id();
    }

    private static String formatMinutes(TimeShiftRecipe recipe) {
        double minutes = recipe.getDurationMinutes();
        if (minutes == Math.rint(minutes)) {
            return Integer.toString((int) minutes);
        }

        return String.format("%.2f", minutes);
    }

    private static int centeredSlotY(int slotCount) {
        return CENTER_Y - visibleRows(slotCount) * SLOT_SIZE / 2;
    }

    private static int visibleRows(int slotCount) {
        return Math.min(Math.max(slotCount, 1), 3);
    }
}
