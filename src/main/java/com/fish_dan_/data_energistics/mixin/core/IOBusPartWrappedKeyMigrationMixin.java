package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.DEAE2Keys;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.parts.automation.IOBusPart;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IOBusPart.class)
public abstract class IOBusPartWrappedKeyMigrationMixin {

    @Shadow
    public abstract ConfigInventory getConfig();

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void dataEnergistics$migrateWrappedCustomKeys(CompoundTag extra, HolderLookup.Provider registries, CallbackInfo ci) {
        ConfigInventory config = getConfig();
        for (int slot = 0; slot < config.size(); slot++) {
            GenericStack stack = config.getStack(slot);
            if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
                continue;
            }

            GenericStack wrapped = GenericStack.unwrapItemStack(itemKey.toStack());
            if (wrapped == null || wrapped.what() == null) {
                continue;
            }

            AEKey wrappedKey = wrapped.what();
            if (!DEAE2Keys.isCustomKey(wrappedKey)) {
                continue;
            }

            config.setStack(slot, new GenericStack(wrappedKey, stack.amount()));
        }
    }
}
