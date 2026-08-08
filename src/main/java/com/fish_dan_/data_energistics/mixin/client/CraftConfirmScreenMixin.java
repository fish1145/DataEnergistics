package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftConfirmMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Places synchronized Trinity ownership and fallback diagnostics in the confirmation dialog's native text slots.
 */
@Mixin(CraftConfirmScreen.class)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {

    @Shadow
    @Final
    private Button start;

    protected CraftConfirmScreenMixin(
                                      CraftConfirmMenu menu,
                                      Inventory playerInventory,
                                      Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void dataEnergistics$placePlanningMetadata(CallbackInfo ci) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        this.start.active = this.start.active && state.data_energistics$isPlanReady();
        var plan = this.menu.getPlan();
        if (plan == null) {
            return;
        }

        Component quantityMode = Component.translatable(state.data_energistics$quantityMode() ==
                CraftingQuantityMode.NET_NEW ?
                        "gui.data_energistics.trinity_quantity.net_new" :
                        "gui.data_energistics.trinity_quantity.final_total");
        String bytes = TrinityAmountFormatter.format(plan.getUsedBytes());
        if (state.data_energistics$isAe2FallbackEstimate()) {
            String planningMillis = dataEnergistics$formatPlanningMillis(state.data_energistics$planningNanos());
            this.setTextContent(
                    TEXT_ID_DIALOG_TITLE,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.ae2_fallback_title",
                            quantityMode,
                            bytes,
                            planningMillis));
            this.setTextContent("cpu_status", state.data_energistics$diagnostic());
        } else if (state.data_energistics$isTrinityOnly()) {
            String titleKey = state.data_energistics$hasDynamicMaterialWarning() ?
                    "gui.data_energistics.trinity_planning.dynamic_title" :
                    "gui.data_energistics.trinity_planning.title";
            String planningMillis = dataEnergistics$formatPlanningMillis(state.data_energistics$planningNanos());
            this.setTextContent(
                    TEXT_ID_DIALOG_TITLE,
                    Component.translatable(titleKey, quantityMode, bytes, planningMillis));
            if (state.data_energistics$hasDiagnostic()) {
                this.setTextContent("cpu_status", state.data_energistics$diagnostic());
            }
        } else if (state.data_energistics$hasDiagnostic()) {
            this.setTextContent(
                    TEXT_ID_DIALOG_TITLE,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.diagnostic_title",
                            quantityMode));
            this.setTextContent("cpu_status", state.data_energistics$diagnostic());
        }
    }

    @Unique
    private static String dataEnergistics$formatPlanningMillis(long planningNanos) {
        BigDecimal roundedMillis = BigDecimal.valueOf(planningNanos, 6).setScale(1, RoundingMode.HALF_EVEN);
        if (planningNanos > 0L && roundedMillis.signum() == 0) {
            return "<0.1";
        }
        return roundedMillis.stripTrailingZeros().toPlainString();
    }
}
