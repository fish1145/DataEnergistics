package com.fish_dan_.data_energistics.mixin.core.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds request-scoped quantity semantics without changing AE2's original confirmation packet.
 */
@Mixin(CraftAmountMenu.class)
public abstract class CraftAmountMenuMixin extends AEBaseMenu implements TrinityCraftAmountMenuState {

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_QUANTITY_MODE = "data_energistics$set_quantity_mode";

    @GuiSync(791)
    @Unique
    public int dataEnergistics$quantityMode = DataEnergisticsConfiguration.INSTANCE.trinityCrafting().defaultQuantityMode().ordinal();

    protected CraftAmountMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$registerQuantityModeAction(
                                                            int id,
                                                            Inventory ip,
                                                            ISubMenuHost host,
                                                            CallbackInfo ci) {
        this.registerClientAction(
                DATA_ENERGISTICS_ACTION_SET_QUANTITY_MODE,
                Integer.class,
                this::dataEnergistics$applyQuantityMode);
    }

    @ModifyVariable(method = "confirm", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean dataEnergistics$preserveFinalTotalAmount(boolean craftMissingAmount) {
        return data_energistics$quantityMode().appliesAe2MissingAmountAdjustment(craftMissingAmount);
    }

    @WrapOperation(
                   method = "confirm",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/menu/me/crafting/CraftConfirmMenu;planJob(Lappeng/api/stacks/AEKey;ILappeng/api/networking/crafting/CalculationStrategy;)Z"))
    private boolean dataEnergistics$transferQuantityMode(
                                                         CraftConfirmMenu confirmation,
                                                         AEKey what,
                                                         int amount,
                                                         CalculationStrategy strategy,
                                                         Operation<Boolean> original) {
        ((TrinityCraftConfirmMenuState) confirmation)
                .data_energistics$setQuantityMode(data_energistics$quantityMode());
        return original.call(confirmation, what, amount, strategy);
    }

    @Override
    public CraftingQuantityMode data_energistics$quantityMode() {
        return CraftingQuantityMode.fromOrdinal(this.dataEnergistics$quantityMode);
    }

    @Override
    public void data_energistics$setQuantityMode(CraftingQuantityMode quantityMode) {
        this.dataEnergistics$quantityMode = quantityMode.ordinal();
        if (this.isClientSide()) {
            this.sendClientAction(DATA_ENERGISTICS_ACTION_SET_QUANTITY_MODE, quantityMode.ordinal());
        } else {
            this.broadcastChanges();
        }
    }

    @Unique
    private void dataEnergistics$applyQuantityMode(Integer ordinal) {
        if (ordinal < 0 || ordinal >= CraftingQuantityMode.values().length) {
            Data_Energistics.LOGGER.warn("Rejected invalid Trinity quantity mode client action {}", ordinal);
            return;
        }
        this.dataEnergistics$quantityMode = CraftingQuantityMode.fromOrdinal(ordinal).ordinal();
        this.broadcastChanges();
    }
}
