package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmPresentationState;
import com.fish_dan_.data_energistics.client.crafting.tree.CraftingPlanTreeEntry;
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

import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

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

    @Shadow
    @Final
    private CraftConfirmTableRenderer table;

    @Unique
    private long dataEnergistics$cycleSelectionRevision = -1L;

    @Unique
    private @Nullable AEKey dataEnergistics$hoveredCycleKey;

    @Unique
    private int dataEnergistics$selectedRelatedCycleIndex;

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
        dataEnergistics$refreshCyclePage(state);
        CraftingPlanSummary plan = this.menu.getPlan();
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        if (!state.data_energistics$isPlanReady() || plan == null || summary == null) {
            return;
        }
        TrinityCraftConfirmCycleBarRenderer.render(
                guiGraphics,
                plan,
                this.scrollbar.getCurrentScroll(),
                summary);
    }

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void dataEnergistics$updateHoveredCycleKey(GuiGraphics guiGraphics,
                                                       int offsetX,
                                                       int offsetY,
                                                       int mouseX,
                                                       int mouseY,
                                                       CallbackInfo ci) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        dataEnergistics$refreshCyclePage(state);
        var hovered = this.table.getHoveredStack();
        dataEnergistics$setHoveredCycleKey(
                state,
                hovered == null ? null : hovered.stack().what());
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void dataEnergistics$placePlanningMetadata(CallbackInfo ci) {
        CraftingPlanTreeEntry.refresh((CraftConfirmScreen) (Object) this);
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        dataEnergistics$refreshCyclePage(state);
        this.start.active = this.start.active && state.data_energistics$isPlanReady();
        var plan = this.menu.getPlan();
        if (plan == null) {
            return;
        }

        Component quantityMode = Component.translatable(state.data_energistics$quantityMode() ==
                CraftingQuantityMode.NET_NEW ?
                        "gui.data_energistics.trinity_quantity.net_new" :
                        "gui.data_energistics.trinity_quantity.final_total");
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        String bytes = summary == null ?
                TrinityAmountFormatter.format(plan.getUsedBytes()) :
                summary.exactBytes()
                        .map(TrinityAmountFormatter::format)
                        .orElseGet(() -> TrinityAmountFormatter.format(plan.getUsedBytes()));
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
        dataEnergistics$refreshCyclePage(state);
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        AEKey hoveredKey = this.dataEnergistics$hoveredCycleKey;
        if (summary == null || hoveredKey == null) {
            return;
        }
        int cycleCount = summary.contributionsFor(hoveredKey).size();
        if (cycleCount <= 1) {
            return;
        }
        if (DEKeyMappings.PREVIOUS_TRINITY_CYCLE.matches(keyCode, scanCode)) {
            this.dataEnergistics$selectedRelatedCycleIndex = this.dataEnergistics$selectedRelatedCycleIndex <= 0 ?
                    cycleCount - 1 :
                    this.dataEnergistics$selectedRelatedCycleIndex - 1;
            cir.setReturnValue(true);
        } else if (DEKeyMappings.NEXT_TRINITY_CYCLE.matches(keyCode, scanCode)) {
            this.dataEnergistics$selectedRelatedCycleIndex = (this.dataEnergistics$selectedRelatedCycleIndex + 1) % cycleCount;
            cir.setReturnValue(true);
        }
    }

    @Override
    public int data_energistics$selectedCycleOrdinal(AEKey key) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        dataEnergistics$refreshCyclePage(state);
        dataEnergistics$setHoveredCycleKey(state, key);
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        if (summary == null) {
            return 0;
        }
        var contributions = summary.contributionsFor(key);
        if (contributions.isEmpty()) {
            return 0;
        }
        if (this.dataEnergistics$selectedRelatedCycleIndex >= contributions.size()) {
            this.dataEnergistics$selectedRelatedCycleIndex = 0;
        }
        return contributions.get(this.dataEnergistics$selectedRelatedCycleIndex).displayOrdinal();
    }

    @Unique
    private void dataEnergistics$refreshCyclePage(TrinityCraftConfirmMenuState state) {
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        long revision = state.data_energistics$planRevision();
        if (revision != this.dataEnergistics$cycleSelectionRevision || summary == null) {
            this.dataEnergistics$cycleSelectionRevision = revision;
            this.dataEnergistics$hoveredCycleKey = null;
            this.dataEnergistics$selectedRelatedCycleIndex = 0;
        } else if (this.dataEnergistics$hoveredCycleKey != null) {
            int relatedCycleCount = summary.contributionsFor(this.dataEnergistics$hoveredCycleKey).size();
            if (relatedCycleCount == 0) {
                this.dataEnergistics$hoveredCycleKey = null;
                this.dataEnergistics$selectedRelatedCycleIndex = 0;
            } else if (this.dataEnergistics$selectedRelatedCycleIndex >= relatedCycleCount) {
                this.dataEnergistics$selectedRelatedCycleIndex = 0;
            }
        }
    }

    @Unique
    private void dataEnergistics$setHoveredCycleKey(
                                                    TrinityCraftConfirmMenuState state,
                                                    @Nullable AEKey key) {
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        AEKey nextKey = summary == null || key == null || summary.contributionsFor(key).isEmpty() ? null : key;
        if (!Objects.equals(this.dataEnergistics$hoveredCycleKey, nextKey)) {
            this.dataEnergistics$hoveredCycleKey = nextKey;
            this.dataEnergistics$selectedRelatedCycleIndex = 0;
        }
    }
}
