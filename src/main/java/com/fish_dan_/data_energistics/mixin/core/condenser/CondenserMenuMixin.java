package com.fish_dan_.data_energistics.mixin.core.condenser;

import com.fish_dan_.data_energistics.accessor.condenser.CondenserBlockEntityAccessor;
import com.fish_dan_.data_energistics.accessor.condenser.CondenserMenuAccessor;
import com.fish_dan_.data_energistics.ae2.settings.CondenserOutputMode;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipeCatalog;

import appeng.api.config.CondenserOutput;
import appeng.api.config.Settings;
import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.CondenserMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CondenserMenu.class)
public abstract class CondenserMenuMixin extends AEBaseMenu implements CondenserMenuAccessor {

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_CONDENSER_OUTPUT_MODE = "dataEnergistics$setCondenserOutputMode";

    @Shadow
    @Final
    private CondenserBlockEntity condenser;

    @GuiSync(920)
    @Unique
    public int dataEnergistics$condenserOutputMode = CondenserOutputMode.TRASH;

    protected CondenserMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/blockentity/misc/CondenserBlockEntity;)V",
            at = @At("RETURN"))
    private void dataEnergistics$registerCondenserActions(int id, Inventory ip,
                                                          CondenserBlockEntity condenser, CallbackInfo ci) {
        this.registerClientAction(
                DATA_ENERGISTICS_ACTION_SET_CONDENSER_OUTPUT_MODE,
                Integer.class,
                this::dataEnergistics$applyCondenserOutputMode);
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void dataEnergistics$syncCondenserMode(CallbackInfo ci) {
        if (!this.isServerSide()) {
            return;
        }

        CondenserBlockEntityAccessor accessor = (CondenserBlockEntityAccessor) this.condenser;
        Level level = this.condenser.getLevel();
        ResourceLocation selectedRecipeId = accessor.dataEnergistics$getSelectedCondenserRecipeId();
        if (selectedRecipeId != null &&
                (level == null || CondenserOutputRecipeCatalog.find(level, selectedRecipeId) == null)) {
            accessor.dataEnergistics$setSelectedCondenserRecipeId(null);
            this.condenser.getConfigManager().putSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH);
        }
        var output = this.condenser.getConfigManager().getSetting(Settings.CONDENSER_OUTPUT);
        this.dataEnergistics$condenserOutputMode = CondenserOutputMode.fromState(
                output,
                accessor.dataEnergistics$getSelectedCondenserRecipeId(),
                level);
    }

    @Override
    public int dataEnergistics$getCondenserOutputMode() {
        return this.dataEnergistics$condenserOutputMode;
    }

    @Override
    public void dataEnergistics$setCondenserOutputMode(int ordinal) {
        if (this.isClientSide()) {
            this.sendClientAction(DATA_ENERGISTICS_ACTION_SET_CONDENSER_OUTPUT_MODE, ordinal);
            return;
        }

        this.dataEnergistics$applyCondenserOutputMode(ordinal);
    }

    @Unique
    private void dataEnergistics$applyCondenserOutputMode(Integer ordinal) {
        Level level = this.condenser.getLevel();
        if (level == null) {
            return;
        }

        var selection = CondenserOutputMode.resolve(level, ordinal);
        var configManager = this.condenser.getConfigManager();
        var previousOutput = configManager.getSetting(Settings.CONDENSER_OUTPUT);
        ((CondenserBlockEntityAccessor) this.condenser)
                .dataEnergistics$setSelectedCondenserRecipeId(selection.customRecipeId());
        configManager.putSetting(Settings.CONDENSER_OUTPUT, selection.vanillaOutput());
        if (previousOutput == selection.vanillaOutput()) {
            this.condenser.addPower(0.0D);
        }
        this.dataEnergistics$condenserOutputMode = CondenserOutputMode.fromState(
                selection.vanillaOutput(),
                selection.customRecipeId(),
                level);
    }
}
