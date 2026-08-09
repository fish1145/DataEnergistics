package com.fish_dan_.data_energistics.mixin.core.condenser;

import com.fish_dan_.data_energistics.accessor.CondenserBlockEntityAccessor;
import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
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
    private static final String RADIX_CONTAINMENT_SPHERE_MODE_TAG = "dataEnergisticsRadixContainmentSphereMode";
    @Unique
    private static final String LEGACY_DATA_CAPTURE_BALL_MODE_TAG = "dataEnergisticsDataCaptureBallMode";
    @Unique
    private static final double DATA_ENERGISTICS_RADIX_CONTAINMENT_SPHERE_REQUIRED_POWER = 131_072.0D;

    @Shadow
    @Final
    private AppEngInternalInventory storageSlot;

    @Unique
    private boolean dataEnergistics$radixContainmentSphereMode;

    @Inject(method = "getOutput", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$replaceRadixContainmentSphereOutput(CallbackInfoReturnable<ItemStack> cir) {
        if (!this.dataEnergistics$radixContainmentSphereMode) {
            return;
        }

        if (!this.dataEnergistics$isValidRadixContainmentSphereStorageComponent(this.storageSlot.getStackInSlot(0))) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        cir.setReturnValue(RadixContainmentSphereItem.createChargedStack());
    }

    @Inject(method = "getRequiredPower", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$replaceRequiredPower(CallbackInfoReturnable<Double> cir) {
        if (this.dataEnergistics$radixContainmentSphereMode) {
            cir.setReturnValue(DATA_ENERGISTICS_RADIX_CONTAINMENT_SPHERE_REQUIRED_POWER);
        }
    }

    @Inject(method = "getStorage", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$restrictStorageComponent(CallbackInfoReturnable<Double> cir) {
        if (!this.dataEnergistics$radixContainmentSphereMode) {
            return;
        }

        cir.setReturnValue(this.dataEnergistics$getRadixContainmentSphereStorage(this.storageSlot.getStackInSlot(0)));
    }

    @Unique
    private boolean dataEnergistics$isValidRadixContainmentSphereStorageComponent(ItemStack stack) {
        return this.dataEnergistics$getRadixContainmentSphereStorage(stack) > 0.0D;
    }

    @Unique
    private double dataEnergistics$getRadixContainmentSphereStorage(ItemStack stack) {
        if (!(stack.getItem() instanceof DataStorageComponentItem component) || !component.isStorageComponent(stack)) {
            return 0.0D;
        }
        double storage = (double) component.getBytes(stack) * CondenserBlockEntity.BYTE_MULTIPLIER;
        return storage >= DATA_ENERGISTICS_RADIX_CONTAINMENT_SPHERE_REQUIRED_POWER ? storage : 0.0D;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void dataEnergistics$saveRadixContainmentSphereMode(CompoundTag data, Provider registries,
                                                                CallbackInfo ci) {
        data.remove(LEGACY_DATA_CAPTURE_BALL_MODE_TAG);
        data.putBoolean(RADIX_CONTAINMENT_SPHERE_MODE_TAG, this.dataEnergistics$radixContainmentSphereMode);
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void dataEnergistics$loadRadixContainmentSphereMode(CompoundTag data, Provider registries,
                                                                CallbackInfo ci) {
        String modeTag = data.contains(RADIX_CONTAINMENT_SPHERE_MODE_TAG) ? RADIX_CONTAINMENT_SPHERE_MODE_TAG : LEGACY_DATA_CAPTURE_BALL_MODE_TAG;
        this.dataEnergistics$radixContainmentSphereMode = data.getBoolean(modeTag);
    }

    @Override
    public boolean dataEnergistics$isRadixContainmentSphereMode() {
        return this.dataEnergistics$radixContainmentSphereMode;
    }

    @Override
    public void dataEnergistics$setRadixContainmentSphereMode(boolean enabled) {
        if (this.dataEnergistics$radixContainmentSphereMode == enabled) {
            return;
        }

        this.dataEnergistics$radixContainmentSphereMode = enabled;
        var condenser = (CondenserBlockEntity) (Object) this;
        condenser.setChanged();
    }
}
