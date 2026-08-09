package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.emi.transfer.EmiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferTarget;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;

import java.util.List;

/**
 * EMI fill handler that transfers only the current typed multiblock view into AE2 processing-pattern configuration.
 */
public final class EmiMultiblockPatternTransferHandler<T extends PatternEncodingTermMenu>
                                                      implements EmiRecipeHandler<T> {

    private final Class<T> menuClass;
    private final EmiEncodePatternHandler<T> inventoryDelegate;

    /**
     * Binds the handler to one exact pattern-terminal menu type while retaining AE2's network inventory exposure.
     */
    public EmiMultiblockPatternTransferHandler(Class<T> menuClass) {
        this.menuClass = menuClass;
        this.inventoryDelegate = new EmiEncodePatternHandler<>(menuClass);
    }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        return this.inventoryDelegate.getInventory(screen);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return EmiMultiblockPatternTransfer.resolve(recipe).applicable();
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (isUnsupportedContext(context)) {
            return false;
        }
        return evaluate(recipe, context).canTransfer();
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (isUnsupportedContext(context)) {
            return List.of();
        }
        EmiMultiblockPatternTransfer.TransferCheck check = evaluate(recipe, context);
        Component error = check.error();
        if (error == null) {
            return List.of();
        }
        return List.of(ClientTooltipComponent.create(error.getVisualOrderText()));
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (isUnsupportedContext(context)) {
            return false;
        }
        EmiMultiblockPatternTransfer.TransferCheck check = evaluate(recipe, context);
        if (!check.canTransfer()) {
            return false;
        }
        PatternEncodingRankingContext rankingContext;
        try {
            rankingContext = EmiPatternTransferContextBridge.resolve(recipe);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Rejected EMI multiblock pattern transfer because its category/workstation context could not be resolved",
                    exception);
            PatternEncodingPreferencesClient.clearTransferredRecipeContext(context.getScreenHandler());
            return false;
        }
        if (!check.transfer()) {
            return false;
        }
        PatternEncodingPreferencesClient.captureTransferredProcessingRecipe(
                context.getScreenHandler(), rankingContext);
        return true;
    }

    private EmiMultiblockPatternTransfer.TransferCheck evaluate(EmiRecipe recipe, EmiCraftContext<T> context) {
        EmiMultiblockPatternTransfer.LiveView liveView = EmiMultiblockPatternTransfer.resolve(recipe);
        if (!liveView.applicable()) {
            return EmiMultiblockPatternTransfer.TransferCheck.rejected(null);
        }

        try {
            T menu = context.getScreenHandler();
            if (!this.menuClass.isInstance(menu)) {
                return EmiMultiblockPatternTransfer.TransferCheck.rejected(
                        Component.literal("The open menu is not the expected pattern terminal"));
            }
            if (!(menu instanceof PatternEncodingMultiblockTransferTarget target)) {
                return EmiMultiblockPatternTransfer.TransferCheck.rejected(
                        Component.literal("The open pattern terminal cannot accept multiblock recipes"));
            }
            return EmiMultiblockPatternTransfer.validate(liveView, target);
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error("Unable to inspect the open pattern terminal for EMI multiblock transfer",
                    failure);
            return EmiMultiblockPatternTransfer.TransferCheck.rejected(
                    Component.literal("The open pattern terminal is unavailable"));
        }
    }

    /**
     * Restricts pattern configuration to EMI's explicit recipe-page fill action.
     */
    private static boolean isUnsupportedContext(EmiCraftContext<?> context) {
        return context.getType() != EmiCraftContext.Type.FILL_BUTTON;
    }
}
