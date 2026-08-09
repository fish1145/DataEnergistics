package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftIngredient;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftRecipe;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.Locale;

public final class TimeShiftEmiRecipe extends BasicEmiRecipe {

    private static final int WIDTH = 148;
    private static final int HEIGHT = 72;
    private static final int CENTER_Y = 36;
    private static final int SLOT_SIZE = 18;
    private static final int INPUT_X = 10;
    private static final int INPUT_Y = 10;
    private static final int ARROW_X = 62;
    private static final int TEXT_X = 40;
    private static final int TEXT_WIDTH = 68;
    private static final int OUTPUT_X = 112;

    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            Data_Energistics.id("world_interaction"),
            EmiStack.of(ModItems.DATA_CRYSTAL.get())) {

        @Override
        public Component getName() {
            return Component.translatable("recipe.data_energistics.time_shift.category");
        }
    };

    private final TimeShiftRecipe recipe;

    public TimeShiftEmiRecipe(RecipeHolder<TimeShiftRecipe> holder) {
        super(CATEGORY, holder.id(), WIDTH, HEIGHT);
        this.recipe = holder.value();

        for (TimeShiftIngredient ingredient : this.recipe.getItemInputs()) {
            this.inputs.add(EmiIngredient.of(ingredient.ingredient(), ingredient.count()));
        }
        for (ItemStack result : this.recipe.getResults()) {
            this.outputs.add(EmiStack.of(result.copy()));
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < this.inputs.size(); i++) {
            widgets.addSlot(this.inputs.get(i), inputWidgetX(i), inputWidgetY(this.inputs.size(), i));
        }

        int outputRows = visibleRows(this.outputs.size());
        int outputStartY = centeredSlotY(this.outputs.size());
        for (int i = 0; i < this.outputs.size(); i++) {
            int x = OUTPUT_X + i / outputRows * SLOT_SIZE;
            int y = outputStartY + i % outputRows * SLOT_SIZE;
            widgets.addSlot(this.outputs.get(i), x, y).recipeContext(this);
        }

        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_X, CENTER_Y - 8);

        widgets.addText(
                Component.translatable("recipe.data_energistics.time_shift"),
                TEXT_X + TEXT_WIDTH / 2,
                6,
                0x7E7E7E,
                false)
                .horizontalAlign(TextWidget.Alignment.CENTER);

        Component conditionText = Component.translatable(
                "recipe.data_energistics.time_shift.time." + this.recipe.getTimeCondition().getName());
        Component timeText = Component.translatable(
                "recipe.data_energistics.time_shift.duration",
                formatMinutes(this.recipe),
                conditionText);
        widgets.addText(timeText, TEXT_X + TEXT_WIDTH / 2, CENTER_Y + 26, 0x7E7E7E, false)
                .horizontalAlign(TextWidget.Alignment.CENTER);
    }

    private static String formatMinutes(TimeShiftRecipe recipe) {
        double minutes = recipe.getDurationMinutes();
        if (minutes == Math.rint(minutes)) {
            return Integer.toString((int) minutes);
        }

        return String.format(Locale.ROOT, "%.2f", minutes);
    }

    private static int centeredSlotY(int slotCount) {
        return CENTER_Y - visibleRows(slotCount) * SLOT_SIZE / 2;
    }

    private static int inputWidgetX(int index) {
        return inputContentX(index) - 1;
    }

    private static int inputWidgetY(int slotCount, int index) {
        return inputContentY(slotCount, index) - 1;
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
}
