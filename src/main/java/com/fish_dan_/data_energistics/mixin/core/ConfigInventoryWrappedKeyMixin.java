package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.DEAE2Keys;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfigInventory.class)
public abstract class ConfigInventoryWrappedKeyMixin {

    @Inject(method = "getKey", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$unwrapLegacyWrappedCustomKey(int slot, CallbackInfoReturnable<AEKey> cir) {
        AEKey key = cir.getReturnValue();
        if (!(key instanceof AEItemKey itemKey)) {
            return;
        }

        ItemStack stack = itemKey.toStack();
        GenericStack wrapped = GenericStack.unwrapItemStack(stack);
        if (wrapped == null || wrapped.what() == null) {
            return;
        }

        AEKey wrappedKey = wrapped.what();
        if (!DEAE2Keys.isCustomKey(wrappedKey)) {
            return;
        }

        cir.setReturnValue(wrappedKey);
    }
}
