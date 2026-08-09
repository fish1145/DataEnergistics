package com.fish_dan_.data_energistics.mixin.advancedae;

import com.fish_dan_.data_energistics.accessor.PatternProviderHostAccessor;
import com.fish_dan_.data_energistics.accessor.PatternProviderMenuAccessor;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot.PlacableItemType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvPatternProviderMenu.class)
public abstract class AdvancedAePatternProviderMenuMixin extends AEBaseMenu implements PatternProviderMenuAccessor {

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_REDSTONE_TUNING_MODE = "dataEnergistics$setRedstoneTuningMode";
    @Unique
    private AdvPatternProviderLogicHost dataEnergistics$host;

    @GuiSync(921)
    @Unique
    public boolean dataEnergistics$hasRedstoneTuningCard;

    @GuiSync(922)
    @Unique
    public int dataEnergistics$redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH.ordinal();

    protected AdvancedAePatternProviderMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V",
            at = @At("RETURN"))
    private void dataEnergistics$initEnhancements(int id, Inventory playerInventory,
                                                  AdvPatternProviderLogicHost host, CallbackInfo ci) {
        this.dataEnergistics$host = host;
        this.registerClientAction(DATA_ENERGISTICS_ACTION_SET_REDSTONE_TUNING_MODE, Integer.class,
                this::dataEnergistics$applyRedstoneTuningMode);
        if (host instanceof PatternProviderHostAccessor accessor) {
            this.addSlot(new RestrictedInputSlot(
                    PlacableItemType.UPGRADES,
                    accessor.dataEnergistics$getRedstoneTuningUpgrades(),
                    0), SlotSemantics.UPGRADE);
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void dataEnergistics$syncEnhancements(CallbackInfo ci) {
        if (!this.isServerSide()) {
            return;
        }
        if (this.dataEnergistics$host instanceof PatternProviderHostAccessor accessor) {
            this.dataEnergistics$hasRedstoneTuningCard = accessor.dataEnergistics$hasRedstoneTuningCard();
            this.dataEnergistics$redstoneTuningMode = accessor.dataEnergistics$getRedstoneTuningMode().ordinal();
        }
    }

    @Override
    public boolean dataEnergistics$hasRedstoneTuningCard() {
        return this.dataEnergistics$hasRedstoneTuningCard;
    }

    @Override
    public int dataEnergistics$getRedstoneTuningMode() {
        return this.dataEnergistics$redstoneTuningMode;
    }

    @Override
    public void dataEnergistics$setRedstoneTuningMode(int ordinal) {
        if (this.isClientSide()) {
            this.sendClientAction(DATA_ENERGISTICS_ACTION_SET_REDSTONE_TUNING_MODE, ordinal);
        } else {
            this.dataEnergistics$applyRedstoneTuningMode(ordinal);
        }
    }

    @Unique
    private void dataEnergistics$applyRedstoneTuningMode(Integer ordinal) {
        if (!(this.dataEnergistics$host instanceof PatternProviderHostAccessor accessor)) {
            return;
        }
        var values = RedstoneTuningMode.values();
        var mode = values[Math.max(0, Math.min(values.length - 1, ordinal == null ? 0 : ordinal))];
        if (accessor.dataEnergistics$setRedstoneTuningMode(mode)) {
            this.dataEnergistics$redstoneTuningMode = mode.ordinal();
        }
    }
}
