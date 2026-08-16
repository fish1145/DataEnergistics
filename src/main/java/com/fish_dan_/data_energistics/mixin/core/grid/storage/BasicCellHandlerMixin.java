package com.fish_dan_.data_energistics.mixin.core.grid.storage;

import com.fish_dan_.data_energistics.ae2.dataflow.DigitalStorageCellHandler;

import net.minecraft.world.item.ItemStack;

import appeng.api.storage.cells.ISaveProvider;
import appeng.me.cells.BasicCellHandler;
import appeng.me.cells.BasicCellInventory;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defers digital storage cells to their dedicated multi-resource cell handler before AE2's single-key handler claims
 * them.
 */
@Mixin(BasicCellHandler.class)
public abstract class BasicCellHandlerMixin {

    @Inject(method = "isCell", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$deferDigitalStorageCells(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (DigitalStorageCellHandler.isDigitalStorageCell(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getCellInventory", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$deferDigitalStorageCellInventories(
                                                                    ItemStack stack,
                                                                    @Nullable ISaveProvider container,
                                                                    CallbackInfoReturnable<BasicCellInventory> cir) {
        if (DigitalStorageCellHandler.isDigitalStorageCell(stack)) {
            cir.setReturnValue(null);
        }
    }
}
