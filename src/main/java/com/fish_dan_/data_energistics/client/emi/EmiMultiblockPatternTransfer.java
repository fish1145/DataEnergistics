package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingMultiblockTransferTarget;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.util.ConfigInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves one live typed EMI recipe and checks it against the exact open AE2 encoding inventories.
 */
final class EmiMultiblockPatternTransfer {

    private EmiMultiblockPatternTransfer() {}

    /**
     * Reads the current typed view without consulting EMI's cached ingredient lists.
     */
    static LiveView resolve(EmiRecipe recipe) {
        if (recipe == null) {
            Data_Energistics.LOGGER.warn("EMI invoked multiblock recipe resolution with a null recipe");
            return LiveView.notApplicable();
        }
        if (!(recipe instanceof MultiblockRecipeViewSource source)) {
            return LiveView.notApplicable();
        }

        try {
            ResourceLocation registeredRecipeId = source.registeredRecipeId();
            MultiblockRecipeView view = source.currentRecipeView();
            if (!registeredRecipeId.equals(view.registeredRecipeId())) {
                Data_Energistics.LOGGER.debug(
                        "EMI multiblock source identity changed from {} to {}",
                        registeredRecipeId,
                        view.registeredRecipeId());
                return LiveView.rejected(Component.literal("The multiblock preview identity changed"));
            }
            ResourceLocation emiRecipeId = recipe.getId();
            ResourceLocation expectedEmiRecipeId = EmiMultiblockRecipeId.synthetic(registeredRecipeId);
            if (emiRecipeId != null && !expectedEmiRecipeId.equals(emiRecipeId)) {
                Data_Energistics.LOGGER.debug(
                        "EMI multiblock wrapper id {} does not match synthetic source id {}",
                        emiRecipeId,
                        expectedEmiRecipeId);
                return LiveView.rejected(Component.literal("The multiblock recipe identity is stale"));
            }
            return LiveView.ready(view);
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error("Unable to resolve the current EMI multiblock preview", failure);
            return LiveView.rejected(Component.literal("The multiblock preview changed; reopen the recipe"));
        }
    }

    /**
     * Checks the complete live view against the target's real slot counts and filters.
     */
    static TransferCheck validate(LiveView liveView, PatternEncodingMultiblockTransferTarget target) {
        if (!liveView.ready()) {
            return TransferCheck.rejected(liveView.error());
        }

        try {
            ConfigInventory inputs = target.data_energistics$getMultiblockTransferInputInventory();
            ConfigInventory outputs = target.data_energistics$getMultiblockTransferOutputInventory();
            MultiblockRecipeView view = liveView.view();
            if (view.inputs().size() > inputs.size()) {
                return TransferCheck.rejected(Component.literal(
                        "This multiblock needs " + view.inputs().size() + " input slots; the terminal has " + inputs.size()));
            }
            if (outputs.size() < 1) {
                return TransferCheck.rejected(Component.literal(
                        "This multiblock needs one output slot; the terminal has none"));
            }

            for (int slot = 0; slot < view.inputs().size(); slot++) {
                Component failure = validateMaterial("input", inputs, slot, view.inputs().get(slot));
                if (failure != null) {
                    return TransferCheck.rejected(failure);
                }
            }
            Component outputFailure = validateMaterial("output", outputs, 0, view.output());
            if (outputFailure != null) {
                return TransferCheck.rejected(outputFailure);
            }
            return TransferCheck.ready(view, target);
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error("Unable to validate the AE2 pattern terminal for EMI multiblock transfer",
                    failure);
            return TransferCheck.rejected(
                    Component.literal("The pattern terminal could not validate this multiblock recipe"));
        }
    }

    @Nullable
    private static Component validateMaterial(String role,
                                              ConfigInventory inventory,
                                              int slot,
                                              PreviewMaterial material) {
        if (!inventory.isAllowedIn(slot, material.key())) {
            return Component.literal("The pattern terminal rejects multiblock " + role + " slot " + (slot + 1));
        }
        long maximum = inventory.getMaxAmount(material.key());
        if (maximum <= 0L || material.amount() > maximum) {
            return Component.literal(
                    "Multiblock " + role + " slot " + (slot + 1) + " exceeds the terminal amount limit");
        }
        return null;
    }

    /**
     * One source resolution attempt. Applicable remains true for stale typed sources so EMI can show the rejection.
     */
    record LiveView(boolean applicable,
                    @Nullable MultiblockRecipeView view,
                    @Nullable Component error) {

        LiveView {
            if (!applicable && (view != null || error != null)) {
                throw new IllegalArgumentException("A non-applicable EMI recipe cannot carry multiblock state");
            }
            if (applicable && (view == null) == (error == null)) {
                throw new IllegalArgumentException("A typed EMI recipe must carry exactly one live view or error");
            }
        }

        boolean ready() {
            return this.view != null;
        }

        static LiveView notApplicable() {
            return new LiveView(false, null, null);
        }

        static LiveView ready(MultiblockRecipeView view) {
            return new LiveView(true, view, null);
        }

        static LiveView rejected(Component error) {
            return new LiveView(true, null, error);
        }
    }

    /**
     * Immutable preflight result used by canCraft, tooltip, and the one request-only craft action.
     */
    record TransferCheck(@Nullable MultiblockRecipeView view,
                         @Nullable PatternEncodingMultiblockTransferTarget target,
                         @Nullable Component error) {

        TransferCheck {
            if ((view == null) != (target == null)) {
                throw new IllegalArgumentException("EMI multiblock transfer view and target must be present together");
            }
            if (view != null && error != null) {
                throw new IllegalArgumentException("A ready EMI multiblock transfer cannot carry an error");
            }
        }

        boolean canTransfer() {
            return this.view != null;
        }

        boolean transfer() {
            if (!canTransfer()) {
                return false;
            }
            try {
                this.target.data_energistics$requestMultiblockTransfer(this.view);
                return true;
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error("Unable to request the current EMI multiblock pattern transfer",
                        failure);
                return false;
            }
        }

        static TransferCheck ready(MultiblockRecipeView view,
                                   PatternEncodingMultiblockTransferTarget target) {
            return new TransferCheck(view, target, null);
        }

        static TransferCheck rejected(@Nullable Component error) {
            return new TransferCheck(null, null, error);
        }
    }
}
