package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.core.definitions.AEBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataCaptureBallCondenserCategory extends AbstractRecipeCategory<DataCaptureBallCondenserRecipe> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/condenser.png");
    private static final int REQUIRED_POWER = 131072;
    public static final RecipeType<DataCaptureBallCondenserRecipe> RECIPE_TYPE = RecipeType.create(Data_Energistics.MODID, "condenser_data_capture_ball", DataCaptureBallCondenserRecipe.class);

    private final IDrawableAnimated progress;
    private final IDrawable background;
    private final IDrawable trashBackground;
    private final IDrawable buttonBackground;

    public DataCaptureBallCondenserCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("block.ae2.condenser"),
                guiHelper.createDrawableItemLike(AEBlocks.CONDENSER),
                96,
                48);
        this.background = guiHelper.createDrawable(TEXTURE, 48, 25, 96, 48);
        this.progress = guiHelper.drawableBuilder(TEXTURE, 176, 0, 6, 18)
                .addPadding(0, 0, 72, 0)
                .buildAnimated(40, IDrawableAnimated.StartDirection.BOTTOM, false);
        this.trashBackground = new JeiIconDrawable(Icon.BACKGROUND_TRASH);
        this.buttonBackground = new JeiIconDrawable(Icon.TOOLBAR_BUTTON_BACKGROUND);
    }

    @Override
    public void draw(DataCaptureBallCondenserRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.progress.draw(guiGraphics);
        this.trashBackground.draw(guiGraphics, 3, 27);
        this.buttonBackground.draw(guiGraphics, 80, 26);
        DataEnergisticsIcon.getBlitter("CONDENSER_OUTPUT_DATA_CAPTURE_BALL")
                .dest(81, 27, 14, 14)
                .blit(guiGraphics);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DataCaptureBallCondenserRecipe recipe, IFocusGroup focuses) {
        builder.addOutputSlot(57, 27).addItemStack(DataCaptureBallItem.createChargedStack());
        builder.addSlot(RecipeIngredientRole.CATALYST, 53, 1)
                .addItemStacks(List.of(
                        DEItems.DATA_STORAGE_COMPONENT_16K.toStack(),
                        DEItems.DATA_STORAGE_COMPONENT_64K.toStack(),
                        DEItems.DATA_STORAGE_COMPONENT_256K.toStack(),
                        DEItems.DATA_STORAGE_COMPONENT_1M.toStack(),
                        DEItems.DATA_STORAGE_COMPONENT_4M.toStack(),
                        DEItems.DATA_STORAGE_COMPONENT_16M.toStack(),
                        DEItems.DATA_STORAGE_COMPONENT_64M.toStack(),
                        DEItems.DATA_STORAGE_COMPONENT_256M.toStack()));
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, DataCaptureBallCondenserRecipe recipe,
                           IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= 80 && mouseX < 96 && mouseY >= 26 && mouseY < 42) {
            tooltip.add(Component.translatable("item.data_energistics.data_capture_ball"));
            tooltip.add(Component.translatable("button.data_energistics.condenser_output.data_capture_ball.detail"));
            tooltip.add(Component.translatable("button.data_energistics.condenser_output.power", REQUIRED_POWER));
        }
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(DataCaptureBallCondenserRecipe recipe) {
        return Data_Energistics.id("condenser/data_capture_ball");
    }
}
