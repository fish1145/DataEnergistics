package com.fish_dan_.data_energistics.guideme;

import com.fish_dan_.data_energistics.common.DataReassemblerGuideLayout;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.core.AppEng;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.document.interaction.GuideTooltip;
import guideme.render.RenderContext;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class DataRipperReassemblerGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(
                ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get(),
                DataRipperReassemblerGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<DataRipperReassemblerRecipe> createRecipe(
                                                                                  RecipeHolder<DataRipperReassemblerRecipe> holder) {
        var recipe = holder.value();

        return LytStandardRecipeBox.builder()
                .title("Data Reassembler")
                .icon(ModBlocks.DATA_RIPPER_REASSEMBLER.get())
                .customBody(new RecipeBody(recipe))
                .build(holder);
    }

    private static Ingredient withCount(DataRipperReassemblerIngredient ingredient) {
        return Ingredient.of(Arrays.stream(ingredient.ingredient().getItems()).map(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(ingredient.count());
            return copy;
        }));
    }

    private static String formatSeconds(int ticks) {
        double seconds = ticks / 20.0D;
        if (seconds == Math.rint(seconds)) {
            return Integer.toString((int) seconds);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private static final class RecipeBody extends AbstractTexturedMachineGuideRecipeBody {

        private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/data_reassembler.png");
        private static final ResourceLocation PROGRESS_TEXTURE = AppEng.makeId("textures/guis/data_reassembler.png");

        private final DataRipperReassemblerRecipe recipe;

        private RecipeBody(DataRipperReassemblerRecipe recipe) {
            super(TEXTURE, 11, 19,
                    DataReassemblerGuideLayout.RECIPE_WIDTH, DataReassemblerGuideLayout.RECIPE_HEIGHT,
                    PROGRESS_TEXTURE,
                    DataReassemblerGuideLayout.PROGRESS_X, DataReassemblerGuideLayout.PROGRESS_Y,
                    DataReassemblerGuideLayout.PROGRESS_WIDTH, DataReassemblerGuideLayout.PROGRESS_HEIGHT);
            this.recipe = recipe;
        }

        @Override
        protected void renderBody(RenderContext context) {
            renderItemInputs(context);
            renderItemOutputs(context);
            renderFluidInputs(context);
            renderFluidOutputs(context);
            renderKeyInput(context);
            renderKeyOutput(context);
        }

        @Override
        protected List<Component> getProgressTooltipLines() {
            return List.of();
        }

        @Override
        protected Optional<GuideTooltip> getTooltipAt(float x, float y) {
            for (int i = 0; i < this.recipe.getItemInputs().size(); i++) {
                var pos = DataReassemblerGuideLayout.guideItemInput(i);
                Optional<GuideTooltip> tooltip = getItemTooltipIfHovered(
                        x,
                        y,
                        getDisplayedInputStack(this.recipe.getItemInputs().get(i)),
                        pos.x(),
                        pos.y());
                if (tooltip.isPresent()) {
                    return tooltip;
                }
            }

            for (int i = 0; i < this.recipe.getItemOutputs().size() && i < DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS; i++) {
                var pos = DataReassemblerGuideLayout.guideItemOutput(i);
                Optional<GuideTooltip> tooltip = getItemTooltipIfHovered(
                        x, y, this.recipe.getItemOutputs().get(i).copy(), pos.x(), pos.y());
                if (tooltip.isPresent()) {
                    return tooltip;
                }
            }

            for (int i = 0; i < this.recipe.getFluidInputs().size() && i < DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS; i++) {
                var pos = DataReassemblerGuideLayout.guideFluidInput(i);
                Optional<GuideTooltip> tooltip = getGenericTooltipIfHovered(
                        x, y, this.recipe.getFluidInputs().get(i), pos.x(), pos.y(), List.of());
                if (tooltip.isPresent()) {
                    return tooltip;
                }
            }

            for (int i = 0; i < this.recipe.getFluidOutputs().size() && i < DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS; i++) {
                var pos = DataReassemblerGuideLayout.guideFluidOutput(i);
                Optional<GuideTooltip> tooltip = getGenericTooltipIfHovered(
                        x, y, this.recipe.getFluidOutputs().get(i), pos.x(), pos.y(), List.of());
                if (tooltip.isPresent()) {
                    return tooltip;
                }
            }

            if (this.recipe.getKeyInput() != null) {
                var pos = DataReassemblerGuideLayout.guideKeyInput();
                Optional<GuideTooltip> tooltip = getGenericTooltipIfHovered(
                        x,
                        y,
                        this.recipe.getKeyInput(),
                        pos.x(),
                        pos.y(),
                        List.of());
                if (tooltip.isPresent()) {
                    return tooltip;
                }
            }

            if (this.recipe.getKeyOutput() != null) {
                var pos = DataReassemblerGuideLayout.guideKeyOutput();
                return getGenericTooltipIfHovered(
                        x,
                        y,
                        this.recipe.getKeyOutput(),
                        pos.x(),
                        pos.y(),
                        List.of());
            }

            return Optional.empty();
        }

        private void renderItemInputs(RenderContext context) {
            for (int i = 0; i < this.recipe.getItemInputs().size(); i++) {
                var pos = DataReassemblerGuideLayout.guideItemInput(i);
                renderItemStack(
                        context,
                        getDisplayedInputStack(this.recipe.getItemInputs().get(i)),
                        pos.x(),
                        pos.y());
            }
        }

        private void renderItemOutputs(RenderContext context) {
            for (int i = 0; i < this.recipe.getItemOutputs().size() && i < DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS; i++) {
                var pos = DataReassemblerGuideLayout.guideItemOutput(i);
                renderItemStack(context, this.recipe.getItemOutputs().get(i), pos.x(), pos.y());
            }
        }

        private void renderFluidInputs(RenderContext context) {
            for (int i = 0; i < this.recipe.getFluidInputs().size() && i < DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS; i++) {
                var pos = DataReassemblerGuideLayout.guideFluidInput(i);
                renderGenericStack(context, this.recipe.getFluidInputs().get(i), pos.x(), pos.y());
            }
        }

        private void renderFluidOutputs(RenderContext context) {
            for (int i = 0; i < this.recipe.getFluidOutputs().size() && i < DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS; i++) {
                var pos = DataReassemblerGuideLayout.guideFluidOutput(i);
                renderGenericStack(context, this.recipe.getFluidOutputs().get(i), pos.x(), pos.y());
            }
        }

        private void renderKeyInput(RenderContext context) {
            if (this.recipe.getKeyInput() != null) {
                var pos = DataReassemblerGuideLayout.guideKeyInput();
                renderGenericStack(context, this.recipe.getKeyInput(), pos.x(), pos.y());
                renderGenericStackAmount(context, this.recipe.getKeyInput(), pos.x(), pos.y(), formatCompactAmount(this.recipe.getKeyInput()));
            }
        }

        private void renderKeyOutput(RenderContext context) {
            if (this.recipe.getKeyOutput() != null) {
                var pos = DataReassemblerGuideLayout.guideKeyOutput();
                renderGenericStack(context, this.recipe.getKeyOutput(), pos.x(), pos.y());
                renderGenericStackAmount(context, this.recipe.getKeyOutput(), pos.x(), pos.y(), formatCompactAmount(this.recipe.getKeyOutput()));
            }
        }

        private ItemStack getDisplayedInputStack(DataRipperReassemblerIngredient ingredient) {
            return getDisplayedStack(withCount(ingredient).getItems());
        }
    }
}
