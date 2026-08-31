package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmPresentationState;
import com.fish_dan_.data_energistics.client.crafting.confirm.table.TrinityCraftConfirmCycleBarRenderer;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.client.util.TrinityDurationFormatter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Places synchronized Trinity metadata in the native dialog and draws cycle membership beneath its material cells.
 */
@Mixin(CraftConfirmScreen.class)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu>
                                              implements TrinityCraftConfirmPresentationState {

    @Shadow
    @Final
    private Button start;

    @Shadow
    @Final
    private Scrollbar scrollbar;

    @Unique
    private long dataEnergistics$cycleSelectionRevision = -1L;

    @Unique
    private int dataEnergistics$selectedCycleOrdinal;

    protected CraftConfirmScreenMixin(
                                      CraftConfirmMenu menu,
                                      Inventory playerInventory,
                                      Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "drawFG", at = @At("HEAD"))
    private void dataEnergistics$drawCycleMembershipBars(GuiGraphics guiGraphics,
                                                         int offsetX,
                                                         int offsetY,
                                                         int mouseX,
                                                         int mouseY,
                                                         CallbackInfo ci) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        dataEnergistics$refreshCycleSelection(state);
        CraftingPlanSummary plan = this.menu.getPlan();
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        if (!state.data_energistics$isPlanReady() || plan == null || summary == null) {
            return;
        }
        TrinityCraftConfirmCycleBarRenderer.render(
                guiGraphics,
                plan,
                this.scrollbar.getCurrentScroll(),
                summary,
                this.dataEnergistics$selectedCycleOrdinal);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void dataEnergistics$placePlanningMetadata(CallbackInfo ci) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        dataEnergistics$refreshCycleSelection(state);
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
        String planningTime = TrinityDurationFormatter.formatNanos(state.data_energistics$planningNanos());
        if (state.data_energistics$isAe2FallbackEstimate()) {
            this.setTextContent(
                    TEXT_ID_DIALOG_TITLE,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.ae2_fallback_title",
                            quantityMode,
                            bytes,
                            planningTime));
            this.setTextContent("cpu_status", state.data_energistics$diagnostic());
        } else if (state.data_energistics$isTrinityOnly()) {
            String titleKey = state.data_energistics$hasDynamicMaterialWarning() ?
                    "gui.data_energistics.trinity_planning.dynamic_title" :
                    "gui.data_energistics.trinity_planning.title";
            this.setTextContent(
                    TEXT_ID_DIALOG_TITLE,
                    Component.translatable(titleKey, quantityMode, bytes, planningTime));
            if (state.data_energistics$hasDiagnostic()) {
                this.setTextContent("cpu_status", state.data_energistics$diagnostic());
            }
        } else if (state.data_energistics$hasDiagnostic()) {
            this.setTextContent(
                    TEXT_ID_DIALOG_TITLE,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.diagnostic_title",
                            quantityMode,
                            planningTime));
            this.setTextContent("cpu_status", state.data_energistics$diagnostic());
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$switchSelectedCycle(int keyCode,
                                                     int scanCode,
                                                     int modifiers,
                                                     CallbackInfoReturnable<Boolean> cir) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        dataEnergistics$refreshCycleSelection(state);
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        if (summary == null || summary.cycles().size() <= 1) {
            return;
        }
        int cycleCount = summary.cycles().size();
        if (DEKeyMappings.PREVIOUS_TRINITY_CYCLE.matches(keyCode, scanCode)) {
            this.dataEnergistics$selectedCycleOrdinal = this.dataEnergistics$selectedCycleOrdinal <= 1 ?
                    cycleCount :
                    this.dataEnergistics$selectedCycleOrdinal - 1;
            cir.setReturnValue(true);
        } else if (DEKeyMappings.NEXT_TRINITY_CYCLE.matches(keyCode, scanCode)) {
            this.dataEnergistics$selectedCycleOrdinal = this.dataEnergistics$selectedCycleOrdinal >= cycleCount ?
                    1 :
                    this.dataEnergistics$selectedCycleOrdinal + 1;
            cir.setReturnValue(true);
        }
    }

    @Override
    public int data_energistics$selectedCycleOrdinal() {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        dataEnergistics$refreshCycleSelection(state);
        return this.dataEnergistics$selectedCycleOrdinal;
    }

    @Unique
    private void dataEnergistics$refreshCycleSelection(TrinityCraftConfirmMenuState state) {
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        long revision = state.data_energistics$planRevision();
        int cycleCount = summary == null ? 0 : summary.cycles().size();
        if (revision == this.dataEnergistics$cycleSelectionRevision &&
                this.dataEnergistics$selectedCycleOrdinal > 0 &&
                this.dataEnergistics$selectedCycleOrdinal <= cycleCount) {
            return;
        }
        this.dataEnergistics$cycleSelectionRevision = revision;
        this.dataEnergistics$selectedCycleOrdinal = 0;
        if (summary == null || summary.cycles().isEmpty()) {
            return;
        }
        for (var cycle : summary.cycles()) {
            boolean containsShortage = summary.exactShortages().stream().anyMatch(shortage -> summary
                    .contributionsFor(shortage.key())
                    .stream()
                    .anyMatch(contribution -> contribution.displayOrdinal() == cycle.displayOrdinal()));
            if (containsShortage) {
                this.dataEnergistics$selectedCycleOrdinal = cycle.displayOrdinal();
                return;
            }
        }
        this.dataEnergistics$selectedCycleOrdinal = 1;
    }
}
