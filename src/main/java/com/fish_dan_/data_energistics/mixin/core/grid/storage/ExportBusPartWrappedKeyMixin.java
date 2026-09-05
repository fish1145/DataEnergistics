package com.fish_dan_.data_energistics.mixin.core.grid.storage;

import com.fish_dan_.data_energistics.ae2.DEAE2Keys;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.parts.automation.ExportBusPart;
import appeng.util.ConfigInventory;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExportBusPart.class)
public abstract class ExportBusPartWrappedKeyMixin {

    @Redirect(
              method = "doBusWork",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/util/ConfigInventory;getKey(I)Lappeng/api/stacks/AEKey;"))
    private AEKey dataEnergistics$unwrapWrappedCustomKey(ConfigInventory inventory, int slot) {
        AEKey key = inventory.getKey(slot);
        if (!(key instanceof AEItemKey itemKey)) {
            return key;
        }

        ItemStack stack = itemKey.toStack();
        GenericStack wrapped = GenericStack.unwrapItemStack(stack);
        if (wrapped == null || wrapped.what() == null) {
            return key;
        }

        AEKey wrappedKey = wrapped.what();
        if (DEAE2Keys.isCustomKey(wrappedKey)) {
            return wrappedKey;
        }

        return key;
    }
}
