package com.fish_dan_.data_energistics.integration.emi;

import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereRightClickRecipe;

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

import java.util.Arrays;
import java.util.List;

public final class RadixContainmentSphereRightClickEmiRecipe extends BasicEmiRecipe {

    private static final int WIDTH = 148;
    private static final int HEIGHT = 72;
    private static final int INPUT_ITEM_X = 8;
    private static final int INPUT_ITEM_Y = 26;
    private static final int INPUT_BLOCK_X = 61;
    private static final int INPUT_BLOCK_Y = 26;
    private static final int ARROW_LEFT_X = 30;
    private static final int ARROW_RIGHT_X = 88;
    private static final int ARROW_Y = 27;
    private static final int OUTPUT_X = 122;
    private static final int OUTPUT_Y = 26;

    public static final EmiRecipeCategory CATEGORY = TimeShiftEmiRecipe.CATEGORY;

    private final RadixContainmentSphereRightClickRecipe recipe;

    public RadixContainmentSphereRightClickEmiRecipe(RecipeHolder<RadixContainmentSphereRightClickRecipe> holder) {
        super(CATEGORY, holder.id(), WIDTH, HEIGHT);
        this.recipe = holder.value();
        this.inputs.add(getItemInput(this.recipe));
        this.inputs.add(EmiStack.of(new ItemStack(this.recipe.getInputBlock())));
        this.outputs.add(EmiStack.of(new ItemStack(this.recipe.getResultBlock())));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.inputs.get(0), INPUT_ITEM_X, INPUT_ITEM_Y);
        if (isRadixContainmentSphereInput(this.recipe)) {
            widgets.addTooltipText(
                    List.of(Component.translatable(
                            "recipe.data_energistics.radix_containment_sphere_right_click.preset",
                            this.recipe.getDataCost(), formatEnergy(this.recipe.getEnergyCost()))),
                    INPUT_ITEM_X, INPUT_ITEM_Y, 18, 18);
        }
        widgets.addSlot(this.inputs.get(1), INPUT_BLOCK_X, INPUT_BLOCK_Y).drawBack(false);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_LEFT_X, ARROW_Y);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_RIGHT_X, ARROW_Y);
        widgets.addSlot(this.outputs.get(0), OUTPUT_X, OUTPUT_Y).recipeContext(this);
        widgets.addText(
                Component.translatable("recipe.data_energistics.radix_containment_sphere_right_click.apply"),
                INPUT_BLOCK_X + 8,
                8,
                0x7E7E7E,
                false)
                .horizontalAlign(TextWidget.Alignment.CENTER);
    }

    private static EmiIngredient getItemInput(RadixContainmentSphereRightClickRecipe recipe) {
        ItemStack[] itemStacks = recipe.getItemIngredient().getItems();
        if (itemStacks.length == 1 && itemStacks[0].getItem() instanceof RadixContainmentSphereItem) {
            return EmiStack.of(RadixContainmentSphereItem.createConfiguredStack(recipe.getEnergyCost(), recipe.getDataCost()));
        }
        return EmiIngredient.of(Arrays.stream(itemStacks).map(EmiStack::of).toList());
    }

    private static boolean isRadixContainmentSphereInput(RadixContainmentSphereRightClickRecipe recipe) {
        return Arrays.stream(recipe.getItemIngredient().getItems())
                .anyMatch(stack -> stack.getItem() instanceof RadixContainmentSphereItem);
    }

    private static String formatEnergy(double energy) {
        if (energy == Math.rint(energy)) {
            return Long.toString((long) energy);
        }
        return Double.toString(energy);
    }
}
