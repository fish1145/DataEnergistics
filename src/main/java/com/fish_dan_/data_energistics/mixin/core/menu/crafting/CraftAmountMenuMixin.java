package com.fish_dan_.data_energistics.mixin.core.menu.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState.Confirmation;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_CONFIRM_LONG = "data_energistics$confirm_long";

    @Shadow
    @Nullable
    private AEKey whatToCraft;

    @GuiSync(791)
    @Unique
    public int dataEnergistics$quantityMode = DataEnergisticsConfiguration.INSTANCE.trinity.crafting.defaultQuantityMode.ordinal();

    @GuiSync(792)
    @Unique
    public long dataEnergistics$initialAmount = 1L;

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
        this.registerClientAction(
                DATA_ENERGISTICS_ACTION_CONFIRM_LONG,
                Confirmation.class,
                this::dataEnergistics$applyConfirmation);
    }

    @Inject(method = "setWhatToCraft", at = @At("RETURN"))
    private void dataEnergistics$rememberInitialAmount(AEKey whatToCraft, int initialAmount, CallbackInfo ci) {
        this.dataEnergistics$initialAmount = initialAmount;
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

    @Override
    public void data_energistics$confirm(long amount, boolean craftMissingAmount, boolean autoStart) {
        Confirmation confirmation = new Confirmation(amount, craftMissingAmount, autoStart);
        if (this.isClientSide()) {
            this.sendClientAction(DATA_ENERGISTICS_ACTION_CONFIRM_LONG, confirmation);
        } else {
            dataEnergistics$applyConfirmation(confirmation);
        }
    }

    @Override
    public long data_energistics$initialAmount() {
        return this.dataEnergistics$initialAmount;
    }

    @Override
    public void data_energistics$setInitialAmount(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("A crafting amount must be positive");
        }
        this.dataEnergistics$initialAmount = amount;
        if (!this.isClientSide()) {
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

    @Unique
    private void dataEnergistics$applyConfirmation(@Nullable Confirmation confirmation) {
        if (confirmation == null || confirmation.amount() <= 0L) {
            Data_Energistics.LOGGER.warn("Rejected invalid long-sized crafting confirmation {}", confirmation);
            return;
        }
        if (this.whatToCraft == null) {
            Data_Energistics.LOGGER.warn("Rejected a crafting confirmation before its requested key was synchronized");
            return;
        }

        long amount = confirmation.amount();
        boolean adjustMissing = data_energistics$quantityMode()
                .appliesAe2MissingAmountAdjustment(confirmation.craftMissingAmount());
        if (adjustMissing) {
            var actionHost = this.getActionHost();
            if (actionHost != null) {
                var node = actionHost.getActionableNode();
                if (node != null) {
                    long existingAmount = node.getGrid()
                            .getStorageService()
                            .getCachedInventory()
                            .get(this.whatToCraft);
                    amount = existingAmount >= amount ? 0L : amount - existingAmount;
                }
            }
        }

        var locator = this.getLocator();
        if (locator == null) {
            Data_Energistics.LOGGER.warn("Rejected a crafting confirmation because its menu locator is unavailable");
            return;
        }

        CraftAmountMenu self = (CraftAmountMenu) (Object) this;
        if (!(this.getPlayer() instanceof ServerPlayer player)) {
            Data_Energistics.LOGGER.error("Long-sized crafting confirmation reached a non-server player");
            return;
        }
        if (amount <= 0L) {
            self.getHost().returnToMainMenu(player, self);
            return;
        }

        MenuOpener.open(CraftConfirmMenu.TYPE, player, locator);
        if (!(player.containerMenu instanceof CraftConfirmMenu confirmationMenu)) {
            Data_Energistics.LOGGER.error("Craft confirmation menu did not open for a long-sized request");
            return;
        }

        confirmationMenu.setAutoStart(confirmation.autoStart());
        TrinityCraftConfirmMenuState confirmationState = (TrinityCraftConfirmMenuState) confirmationMenu;
        confirmationState.data_energistics$setQuantityMode(data_energistics$quantityMode());
        if (!confirmationState.data_energistics$planJob(
                this.whatToCraft,
                amount,
                CalculationStrategy.REPORT_MISSING_ITEMS)) {
            confirmationMenu.setValidMenu(false);
            return;
        }
        confirmationMenu.broadcastChanges();
    }
}
