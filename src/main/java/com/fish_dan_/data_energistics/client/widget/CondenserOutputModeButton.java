package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.accessor.condenser.CondenserMenuAccessor;
import com.fish_dan_.data_energistics.ae2.settings.CondenserOutputMode;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.api.config.CondenserOutput;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import java.util.List;

public class CondenserOutputModeButton extends IconButton {

    private static final String CUSTOM_OUTPUT_ICON = "CONDENSER_OUTPUT_RADIX_CONTAINMENT_SPHERE";

    private final CondenserMenuAccessor menu;
    private int modeIndex = CondenserOutputMode.TRASH;

    public CondenserOutputModeButton(CondenserMenuAccessor menu) {
        super(btn -> {});
        this.menu = menu;
    }

    public void setModeIndex(int modeIndex) {
        this.modeIndex = modeIndex;
    }

    @Override
    public void onPress() {
        int modeCount = CondenserOutputMode.getModeCount(Minecraft.getInstance().level);
        this.menu.dataEnergistics$setCondenserOutputMode((this.modeIndex + 1) % modeCount);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RecipeHolder<CondenserOutputRecipe> customRecipe = CondenserOutputMode.getCustomRecipe(Minecraft.getInstance().level, this.modeIndex);
        if (customRecipe == null) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        if (!this.visible) {
            return;
        }

        int yOffset = isHovered() ? 1 : 0;
        Icon background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

        background.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        DataEnergisticsIcon.getBlitter(CUSTOM_OUTPUT_ICON)
                .dest(getX(), getY() + 1 + yOffset)
                .zOffset(3)
                .blit(guiGraphics);
    }

    @Override
    protected Icon getIcon() {
        return switch (this.modeIndex) {
            case CondenserOutputMode.MATTER_BALLS -> Icon.CONDENSER_OUTPUT_MATTER_BALL;
            case CondenserOutputMode.SINGULARITY -> Icon.CONDENSER_OUTPUT_SINGULARITY;
            default -> Icon.CONDENSER_OUTPUT_TRASH;
        };
    }

    @Override
    public List<Component> getTooltipMessage() {
        RecipeHolder<CondenserOutputRecipe> customRecipe = CondenserOutputMode.getCustomRecipe(Minecraft.getInstance().level, this.modeIndex);
        if (customRecipe != null) {
            var recipe = customRecipe.value();
            return List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    recipe.getResult().getHoverName(),
                    Component.translatable("button.data_energistics.condenser_output.power", recipe.getRequiredPower()));
        }

        return switch (this.modeIndex) {
            case CondenserOutputMode.MATTER_BALLS -> List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    Component.translatable("button.data_energistics.condenser_output.matter_balls"),
                    Component.translatable(
                            "button.data_energistics.condenser_output.power",
                            CondenserOutput.MATTER_BALLS.requiredPower));
            case CondenserOutputMode.SINGULARITY -> List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    Component.translatable("button.data_energistics.condenser_output.singularity"),
                    Component.translatable(
                            "button.data_energistics.condenser_output.power",
                            CondenserOutput.SINGULARITY.requiredPower));
            default -> List.of(
                    Component.translatable("button.data_energistics.condenser_output.header"),
                    Component.translatable("button.data_energistics.condenser_output.trash"));
        };
    }
}
