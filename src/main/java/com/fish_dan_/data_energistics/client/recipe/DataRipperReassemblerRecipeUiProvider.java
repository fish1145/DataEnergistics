package com.fish_dan_.data_energistics.client.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.DataReassemblerLayout;
import com.fish_dan_.data_energistics.client.DataReassemblerLayout.SlotPos;
import com.fish_dan_.data_energistics.client.ui.DataReassemblerProgressElement;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerIngredient;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.core.AppEng;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.ScrollDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.utils.IModularUIProvider;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.Arrays;
import java.util.List;

/**
 * Builds a fresh LDLib2 recipe UI for each viewer cache entry.
 */
public final class DataRipperReassemblerRecipeUiProvider
                                                         implements IModularUIProvider<DataRipperReassemblerRecipeView> {

    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/data_reassembler.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int BACKGROUND_U = 11;
    private static final int BACKGROUND_V = 19;
    private static final int CANDIDATE_CYCLE_TICKS = 20;
    private static final long PROGRESS_CYCLE_MILLIS = 2_000L;

    private final DataReassemblerRecipeIngredientAdapter ingredientAdapter;

    public DataRipperReassemblerRecipeUiProvider(DataReassemblerRecipeIngredientAdapter ingredientAdapter) {
        this.ingredientAdapter = ingredientAdapter;
    }

    @Override
    public ModularUI createModularUI(DataRipperReassemblerRecipeView recipe) {
        UIElement root = new RecipeRootElement();
        root.setId("data-reassembler-recipe");
        root.getLayout().width(DataReassemblerLayout.RECIPE_WIDTH);
        root.getLayout().height(DataReassemblerLayout.RECIPE_HEIGHT);
        root.style(style -> style.backgroundTexture(SpriteTexture.of(TEXTURE).setSprite(
                BACKGROUND_U,
                BACKGROUND_V,
                DataReassemblerLayout.RECIPE_WIDTH,
                DataReassemblerLayout.RECIPE_HEIGHT)));

        addProgress(root);
        addItemInputs(root, recipe);
        addFluidInputs(root, recipe);
        addGenericStack(root, "key-input", recipe.keyInput(), DataReassemblerLayout.recipeKeyInput(), IngredientIO.INPUT);
        addItemOutputs(root, recipe);
        addFluidOutputs(root, recipe);
        addGenericStack(root, "key-output", recipe.keyOutput(), DataReassemblerLayout.recipeKeyOutput(), IngredientIO.OUTPUT);

        return ModularUI.of(UI.of(root));
    }

    private void addProgress(UIElement root) {
        var progress = new DataReassemblerProgressElement(
                TEXTURE,
                176,
                0,
                DataReassemblerLayout.PROGRESS_WIDTH,
                DataReassemblerLayout.PROGRESS_HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                () -> (System.currentTimeMillis() % PROGRESS_CYCLE_MILLIS) / (double) PROGRESS_CYCLE_MILLIS);
        progress.setId("progress");
        place(progress, DataReassemblerLayout.PROGRESS_X, DataReassemblerLayout.PROGRESS_Y);
        root.addChild(progress);
    }

    private void addItemInputs(UIElement root, DataRipperReassemblerRecipeView recipe) {
        for (int index = 0; index < recipe.itemInputs().size(); index++) {
            DataRipperReassemblerIngredient input = recipe.itemInputs().get(index);
            List<ItemStack> candidates = Arrays.stream(input.ingredient().getItems())
                    .map(stack -> stack.copyWithCount(input.count()))
                    .toList();
            if (candidates.isEmpty()) {
                Data_Energistics.LOGGER.error(
                        "Data reassembler recipe {} item input {} has no display candidates",
                        recipe.id(),
                        index);
                throw new IllegalArgumentException("Data reassembler item input " + index + " has no display candidates");
            }
            addItem(root, "item-input-" + index, candidates, DataReassemblerLayout.recipeItemInput(index), IngredientIO.INPUT);
        }
    }

    private void addItemOutputs(UIElement root, DataRipperReassemblerRecipeView recipe) {
        List<ItemStack> outputs = recipe.itemOutputs();
        for (int index = 0; index < outputs.size(); index++) {
            addItem(
                    root,
                    "item-output-" + index,
                    List.of(outputs.get(index).copy()),
                    DataReassemblerLayout.recipeItemOutput(index),
                    IngredientIO.OUTPUT);
        }
    }

    private void addFluidInputs(UIElement root, DataRipperReassemblerRecipeView recipe) {
        for (int index = 0; index < recipe.fluidInputs().size(); index++) {
            addGenericStack(
                    root,
                    "fluid-input-" + index,
                    recipe.fluidInputs().get(index),
                    DataReassemblerLayout.recipeFluidInput(index),
                    IngredientIO.INPUT);
        }
    }

    private void addFluidOutputs(UIElement root, DataRipperReassemblerRecipeView recipe) {
        for (int index = 0; index < recipe.fluidOutputs().size(); index++) {
            addGenericStack(
                    root,
                    "fluid-output-" + index,
                    recipe.fluidOutputs().get(index),
                    DataReassemblerLayout.recipeFluidOutput(index),
                    IngredientIO.OUTPUT);
        }
    }

    private void addItem(UIElement root, String id, List<ItemStack> candidates, SlotPos pos, IngredientIO role) {
        ItemSlot slot = new ItemSlot();
        slot.setId(id);
        slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        place(slot, pos.x(), pos.y());
        slot.bindDataSource(ScrollDataSource.of(candidates).frequency(CANDIDATE_CYCLE_TICKS));
        this.ingredientAdapter.registerItemSlot(slot, role, candidates);
        root.addChild(slot);
    }

    private void addGenericStack(UIElement root,
                                 String id,
                                 GenericStack stack,
                                 SlotPos pos,
                                 IngredientIO role) {
        if (stack == null) {
            return;
        }
        var slot = new DataReassemblerGenericStackSlot(stack);
        slot.setId(id);
        place(slot, pos.x(), pos.y());
        this.ingredientAdapter.registerGenericStackSlot(slot, role, stack);
        root.addChild(slot);
    }

    private static void place(UIElement element, int x, int y) {
        element.getLayout().positionType(TaffyPosition.ABSOLUTE);
        element.getLayout().left(x);
        element.getLayout().top(y);
    }

    /** Commits LDLib2's buffered background before immediate child renderers draw into the framebuffer. */
    private static final class RecipeRootElement extends UIElement {

        @Override
        public void drawBackgroundTexture(GUIContext guiContext) {
            super.drawBackgroundTexture(guiContext);
            guiContext.graphics.flush();
        }
    }
}
