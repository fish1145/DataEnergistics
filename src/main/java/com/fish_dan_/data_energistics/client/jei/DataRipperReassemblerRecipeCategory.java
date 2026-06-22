package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.DataReassemblerLayout;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.core.AppEng;
import appeng.items.misc.WrappedGenericStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import java.util.Arrays;
import java.util.List;

public final class DataRipperReassemblerRecipeCategory extends AbstractRecipeCategory<DataRipperReassemblerRecipe> {

    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/data_reassembler.png");
    public static final RecipeType<DataRipperReassemblerRecipe> RECIPE_TYPE = RecipeType.create(Data_Energistics.MODID, "data_reassembler", DataRipperReassemblerRecipe.class);

    private final IDrawable background;
    private final IDrawableAnimated progress;

    public DataRipperReassemblerRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("recipe.data_energistics.data_reassembler"),
                guiHelper.createDrawableItemLike(ModBlocks.DATA_RIPPER_REASSEMBLER.get()),
                DataReassemblerLayout.RECIPE_WIDTH,
                DataReassemblerLayout.RECIPE_HEIGHT);
        this.background = guiHelper.createDrawable(TEXTURE, 11, 19,
                DataReassemblerLayout.RECIPE_WIDTH, DataReassemblerLayout.RECIPE_HEIGHT);
        this.progress = guiHelper.drawableBuilder(TEXTURE, 176, 0, 6, 18)
                .buildAnimated(40, IDrawableAnimated.StartDirection.BOTTOM, false);
    }

    @Override
    public void draw(DataRipperReassemblerRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.progress.draw(guiGraphics, DataReassemblerLayout.PROGRESS_X, DataReassemblerLayout.PROGRESS_Y);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DataRipperReassemblerRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.getItemInputs().size(); i++) {
            DataRipperReassemblerIngredient ingredient = recipe.getItemInputs().get(i);
            var pos = DataReassemblerLayout.jeiItemInput(i);
            builder.addInputSlot(pos.x(), pos.y()).addItemStacks(withCount(ingredient));
        }

        for (int i = 0; i < recipe.getItemOutputs().size() && i < DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS; i++) {
            var pos = DataReassemblerLayout.jeiItemOutput(i);
            builder.addOutputSlot(pos.x(), pos.y()).addItemStack(recipe.getItemOutputs().get(i).copy());
        }

        addGenericStackInputs(builder, recipe.getFluidInputs());
        addGenericStackOutputs(builder, recipe.getFluidOutputs());

        GenericStack keyInput = recipe.getKeyInput();
        if (keyInput != null) {
            var pos = DataReassemblerLayout.jeiKeyInput();
            builder.addInputSlot(pos.x(), pos.y())
                    .addItemStack(WrappedGenericStack.wrap(keyInput))
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, new NoCountItemRenderer(keyInput))
                    .addRichTooltipCallback((slotView, tooltip) -> {
                        tooltip.add(createKeyTooltip(keyInput));
                        tooltip.add(GenericStackDisplayHelper.createAmountTooltip(keyInput));
                    });
        }

        GenericStack keyOutput = recipe.getKeyOutput();
        if (keyOutput != null) {
            var pos = DataReassemblerLayout.jeiKeyOutput();
            builder.addOutputSlot(pos.x(), pos.y())
                    .addItemStack(WrappedGenericStack.wrap(keyOutput))
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, new NoCountItemRenderer(keyOutput))
                    .addRichTooltipCallback((slotView, tooltip) -> {
                        tooltip.add(createKeyTooltip(keyOutput));
                        tooltip.add(GenericStackDisplayHelper.createAmountTooltip(keyOutput));
                    });
        }
    }

    private static List<ItemStack> withCount(DataRipperReassemblerIngredient ingredient) {
        return Arrays.stream(ingredient.ingredient().getItems()).map(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(ingredient.count());
            return copy;
        }).toList();
    }

    private static Component createKeyTooltip(GenericStack keyInput) {
        return Component.empty()
                .append(keyInput.what().getDisplayName());
    }

    private static void addGenericStackInputs(IRecipeLayoutBuilder builder, List<GenericStack> stacks) {
        for (int i = 0; i < stacks.size() && i < DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS; i++) {
            GenericStack stack = stacks.get(i);
            var pos = DataReassemblerLayout.jeiFluidInput(i);
            addNativeFluidSlot(builder.addInputSlot(pos.x(), pos.y()), stack);
        }
    }

    private static void addGenericStackOutputs(IRecipeLayoutBuilder builder, List<GenericStack> stacks) {
        for (int i = 0; i < stacks.size() && i < DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS; i++) {
            GenericStack stack = stacks.get(i);
            var pos = DataReassemblerLayout.jeiFluidOutput(i);
            addNativeFluidSlot(builder.addOutputSlot(pos.x(), pos.y()), stack);
        }
    }

    private static void addNativeFluidSlot(IRecipeSlotBuilder slotBuilder, GenericStack stack) {
        if (!(stack.what() instanceof AEFluidKey fluidKey)) {
            slotBuilder.addItemStack(WrappedGenericStack.wrap(stack))
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, new NoCountItemRenderer(stack))
                    .addRichTooltipCallback((slotView, tooltip) -> {
                        tooltip.add(createKeyTooltip(stack));
                        tooltip.add(GenericStackDisplayHelper.createAmountTooltip(stack));
                    });
            return;
        }

        long amount = Math.max(1L, stack.amount());
        int renderAmount = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, amount));
        FluidStack fluidStack = fluidKey.toStack(renderAmount);

        slotBuilder
                .setFluidRenderer(Math.max(1000L, amount), false, 16, 16)
                .addIngredient(NeoForgeTypes.FLUID_STACK, fluidStack)
                .addRichTooltipCallback((slotView, tooltip) -> tooltip.add(GenericStackDisplayHelper.createAmountTooltip(stack)));
    }

    private record NoCountItemRenderer(GenericStack keyInput) implements IIngredientRenderer<ItemStack> {

        @Override
        public void render(GuiGraphics guiGraphics, ItemStack ingredient) {
            CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiGraphics, 0, 0, keyInput.what());
            GenericStackDisplayHelper.renderSmallOverlay(
                    guiGraphics,
                    0,
                    0,
                    GenericStackDisplayHelper.formatCompactAmount(keyInput));
        }

        @Override
        public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
            return ingredient.getTooltipLines(Item.TooltipContext.EMPTY, Minecraft.getInstance().player, tooltipFlag);
        }

        @Override
        public Font getFontRenderer(Minecraft minecraft, ItemStack ingredient) {
            return minecraft.font;
        }
    }
}
