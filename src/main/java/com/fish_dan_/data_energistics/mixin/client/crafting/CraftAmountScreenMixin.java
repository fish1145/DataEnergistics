package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.me.crafting.CraftAmountMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds one AE2-style NET_NEW / FINAL_TOTAL selector to the amount dialog's left toolbar.
 */
@Mixin(CraftAmountScreen.class)
public abstract class CraftAmountScreenMixin extends AEBaseScreen<CraftAmountMenu> {

    @Shadow
    @Final
    private Button next;

    @Shadow
    @Final
    private NumberEntryWidget amountToCraft;

    @Shadow
    private boolean amountInitialized;

    @Unique
    private ToggleButton dataEnergistics$quantityModeButton;

    protected CraftAmountScreenMixin(
                                     CraftAmountMenu menu,
                                     Inventory playerInventory,
                                     Component title,
                                     ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addQuantityModeButton(
                                                       CraftAmountMenu menu,
                                                       Inventory playerInventory,
                                                       Component title,
                                                       ScreenStyle style,
                                                       CallbackInfo ci) {
        TrinityCraftAmountMenuState state = (TrinityCraftAmountMenuState) menu;
        this.dataEnergistics$quantityModeButton = new ToggleButton(
                Icon.SCHEDULING_ROUND_ROBIN,
                Icon.SCHEDULING_RANDOM,
                netNew -> state.data_energistics$setQuantityMode(
                        netNew ? CraftingQuantityMode.NET_NEW : CraftingQuantityMode.FINAL_TOTAL));
        this.dataEnergistics$quantityModeButton.setTooltipOn(
                List.of(dataEnergistics$quantityModeLabel(CraftingQuantityMode.NET_NEW)));
        this.dataEnergistics$quantityModeButton.setTooltipOff(
                List.of(dataEnergistics$quantityModeLabel(CraftingQuantityMode.FINAL_TOTAL)));
        this.addToLeftToolbar(this.dataEnergistics$quantityModeButton);

        this.amountToCraft.setMaxValue(Long.MAX_VALUE);
        ((NumberEntryWidgetAccessor) this.amountToCraft)
                .dataEnergistics$textField()
                .setMaxLength(Long.toString(Long.MAX_VALUE).length());
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void dataEnergistics$initializeLongAmount(CallbackInfo ci) {
        if (this.amountInitialized) {
            return;
        }
        var whatToCraft = this.menu.getWhatToCraft();
        if (whatToCraft == null) {
            return;
        }

        this.amountToCraft.setType(NumberEntryType.of(whatToCraft.what()));
        this.amountToCraft.setLongValue(
                ((TrinityCraftAmountMenuState) this.menu).data_energistics$initialAmount());
        this.amountInitialized = true;
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncQuantityModeButton(CallbackInfo ci) {
        TrinityCraftAmountMenuState state = (TrinityCraftAmountMenuState) this.menu;
        this.dataEnergistics$quantityModeButton.setState(
                state.data_energistics$quantityMode() == CraftingQuantityMode.NET_NEW);
        this.next.active = this.amountToCraft.getLongValue().orElse(0L) > 0L;
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$confirmLongAmount(CallbackInfo ci) {
        long amount = this.amountToCraft.getLongValue().orElse(0L);
        if (amount > 0L) {
            ((TrinityCraftAmountMenuState) this.menu)
                    .data_energistics$confirm(amount, this.amountToCraft.startsWithEquals(), hasShiftDown());
        }
        ci.cancel();
    }

    @Unique
    private static Component dataEnergistics$quantityModeLabel(CraftingQuantityMode mode) {
        String modeKey = switch (mode) {
            case NET_NEW -> "gui.data_energistics.trinity_quantity.net_new";
            case FINAL_TOTAL -> "gui.data_energistics.trinity_quantity.final_total";
        };
        return Component.translatable(
                "gui.data_energistics.trinity_quantity.mode",
                Component.translatable(modeKey));
    }
}
