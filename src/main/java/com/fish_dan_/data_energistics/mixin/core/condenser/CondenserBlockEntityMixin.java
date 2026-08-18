package com.fish_dan_.data_energistics.mixin.core.condenser;

import com.fish_dan_.data_energistics.accessor.condenser.CondenserBlockEntityAccessor;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipeCatalog;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
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

@Mixin(CondenserBlockEntity.class)
public abstract class CondenserBlockEntityMixin implements CondenserBlockEntityAccessor {

    @Unique
    private static final String SELECTED_CONDENSER_RECIPE_TAG = "dataEnergisticsSelectedCondenserRecipe";

    @Shadow
    @Final
    private AppEngInternalInventory storageSlot;

    @Unique
    @Nullable
    private ResourceLocation dataEnergistics$selectedCondenserRecipeId;

    @Inject(method = "getOutput", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$replaceCondenserOutput(CallbackInfoReturnable<ItemStack> cir) {
        CondenserOutputRecipe recipe = this.dataEnergistics$getSelectedCondenserRecipe();
        if (recipe == null) {
            return;
        }

        if (!recipe.acceptsStorage(this.storageSlot.getStackInSlot(0))) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        cir.setReturnValue(recipe.getResult());
    }

    @Inject(method = "getRequiredPower", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$replaceRequiredPower(CallbackInfoReturnable<Double> cir) {
        CondenserOutputRecipe recipe = this.dataEnergistics$getSelectedCondenserRecipe();
        if (this.dataEnergistics$selectedCondenserRecipeId != null) {
            cir.setReturnValue(recipe == null ? 0.0D : recipe.getRequiredPower());
        }
    }

    @Inject(method = "getStorage", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$restrictStorageComponent(CallbackInfoReturnable<Double> cir) {
        CondenserOutputRecipe recipe = this.dataEnergistics$getSelectedCondenserRecipe();
        if (this.dataEnergistics$selectedCondenserRecipeId == null) {
            return;
        }

        ItemStack storage = this.storageSlot.getStackInSlot(0);
        cir.setReturnValue(recipe != null && recipe.acceptsStorage(storage) ? CondenserOutputRecipe.getStorageCapacity(storage) : 0.0D);
    }

    @Unique
    @Nullable
    private CondenserOutputRecipe dataEnergistics$getSelectedCondenserRecipe() {
        if (this.dataEnergistics$selectedCondenserRecipeId == null) {
            return null;
        }
        CondenserBlockEntity condenser = (CondenserBlockEntity) (Object) this;
        if (condenser.getLevel() == null) {
            return null;
        }
        var holder = CondenserOutputRecipeCatalog.find(
                condenser.getLevel(),
                this.dataEnergistics$selectedCondenserRecipeId);
        return holder == null ? null : holder.value();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void dataEnergistics$saveSelectedCondenserRecipe(CompoundTag data, Provider registries, CallbackInfo ci) {
        if (this.dataEnergistics$selectedCondenserRecipeId == null) {
            data.remove(SELECTED_CONDENSER_RECIPE_TAG);
        } else {
            data.putString(SELECTED_CONDENSER_RECIPE_TAG, this.dataEnergistics$selectedCondenserRecipeId.toString());
        }
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void dataEnergistics$loadSelectedCondenserRecipe(CompoundTag data, Provider registries, CallbackInfo ci) {
        this.dataEnergistics$selectedCondenserRecipeId = data.contains(SELECTED_CONDENSER_RECIPE_TAG) ? ResourceLocation.tryParse(data.getString(SELECTED_CONDENSER_RECIPE_TAG)) : null;
    }

    @Override
    public @Nullable ResourceLocation dataEnergistics$getSelectedCondenserRecipeId() {
        return this.dataEnergistics$selectedCondenserRecipeId;
    }

    @Override
    public void dataEnergistics$setSelectedCondenserRecipeId(@Nullable ResourceLocation recipeId) {
        if (Objects.equals(this.dataEnergistics$selectedCondenserRecipeId, recipeId)) {
            return;
        }

        this.dataEnergistics$selectedCondenserRecipeId = recipeId;
        var condenser = (CondenserBlockEntity) (Object) this;
        condenser.setChanged();
    }
}
