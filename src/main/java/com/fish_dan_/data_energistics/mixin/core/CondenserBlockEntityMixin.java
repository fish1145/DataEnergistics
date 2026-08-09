package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.accessor.CondenserBlockEntityAccessor;
import com.fish_dan_.data_energistics.item.carrier.DataCaptureBallItem;
import com.fish_dan_.data_energistics.item.cell.DataStorageComponentItem;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CondenserBlockEntity.class)
public abstract class CondenserBlockEntityMixin implements CondenserBlockEntityAccessor {

    @Unique
    private static final String DATA_ENERGISTICS_DATA_CAPTURE_BALL_MODE = "dataEnergisticsDataCaptureBallMode";
    @Unique
    private static final double DATA_ENERGISTICS_DATA_CAPTURE_BALL_REQUIRED_POWER = 131_072.0D;

    @Shadow
    @Final
    private AppEngInternalInventory storageSlot;

    @Unique
    private boolean dataEnergistics$dataCaptureBallMode;

    @Inject(method = "getOutput", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$replaceDataCaptureBallOutput(CallbackInfoReturnable<ItemStack> cir) {
        if (!this.dataEnergistics$dataCaptureBallMode) {
            return;
        }

        if (!this.dataEnergistics$isValidDataCaptureBallStorageComponent(this.storageSlot.getStackInSlot(0))) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        cir.setReturnValue(DataCaptureBallItem.createChargedStack());
    }

    @Inject(method = "getRequiredPower", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$replaceRequiredPower(CallbackInfoReturnable<Double> cir) {
        if (this.dataEnergistics$dataCaptureBallMode) {
            cir.setReturnValue(DATA_ENERGISTICS_DATA_CAPTURE_BALL_REQUIRED_POWER);
        }
    }

    @Inject(method = "getStorage", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$restrictStorageComponent(CallbackInfoReturnable<Double> cir) {
        if (!this.dataEnergistics$dataCaptureBallMode) {
            return;
        }

        cir.setReturnValue(this.dataEnergistics$getDataCaptureBallStorage(this.storageSlot.getStackInSlot(0)));
    }

    @Unique
    private boolean dataEnergistics$isValidDataCaptureBallStorageComponent(ItemStack stack) {
        return this.dataEnergistics$getDataCaptureBallStorage(stack) > 0.0D;
    }

    @Unique
    private double dataEnergistics$getDataCaptureBallStorage(ItemStack stack) {
        if (!(stack.getItem() instanceof DataStorageComponentItem component) || !component.isStorageComponent(stack)) {
            return 0.0D;
        }
        double storage = (double) component.getBytes(stack) * CondenserBlockEntity.BYTE_MULTIPLIER;
        return storage >= DATA_ENERGISTICS_DATA_CAPTURE_BALL_REQUIRED_POWER ? storage : 0.0D;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void dataEnergistics$saveDataCaptureBallMode(CompoundTag data, Provider registries,
                                                         CallbackInfo ci) {
        data.putBoolean(DATA_ENERGISTICS_DATA_CAPTURE_BALL_MODE, this.dataEnergistics$dataCaptureBallMode);
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void dataEnergistics$loadDataCaptureBallMode(CompoundTag data, Provider registries,
                                                         CallbackInfo ci) {
        this.dataEnergistics$dataCaptureBallMode = data.getBoolean(DATA_ENERGISTICS_DATA_CAPTURE_BALL_MODE);
    }

    @Override
    public boolean dataEnergistics$isDataCaptureBallMode() {
        return this.dataEnergistics$dataCaptureBallMode;
    }

    @Override
    public void dataEnergistics$setDataCaptureBallMode(boolean enabled) {
        if (this.dataEnergistics$dataCaptureBallMode == enabled) {
            return;
        }

        this.dataEnergistics$dataCaptureBallMode = enabled;
        var condenser = (CondenserBlockEntity) (Object) this;
        condenser.setChanged();
    }
}
