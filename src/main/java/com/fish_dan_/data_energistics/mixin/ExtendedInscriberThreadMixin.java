package com.fish_dan_.data_energistics.mixin;

import appeng.recipes.handlers.InscriberRecipe;
import appeng.util.inv.AppEngInternalInventory;
import appeng.api.networking.ticking.TickRateModulation;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.glodblock.github.extendedae.common.me.InscriberThread;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InscriberThread.class)
public abstract class ExtendedInscriberThreadMixin {
    @Shadow
    private AppEngInternalInventory sideItemHandler;

    @Shadow
    public abstract InscriberRecipe getTask();

    @Unique
    private ItemStack dataEnergistics$templateSnapshot = ItemStack.EMPTY;

    @Inject(method = "tick", at = @At("HEAD"))
    private void dataEnergistics$captureTemplate(CallbackInfoReturnable<TickRateModulation> cir) {
        this.dataEnergistics$templateSnapshot = ItemStack.EMPTY;

        InscriberRecipe task = this.getTask();
        if (task == null || !task.getResultItem().is(ModItems.DATA_CIRCUIT_BOARD.get())) {
            return;
        }

        ItemStack stack = this.sideItemHandler.getStackInSlot(0);
        if (stack.is(ModItems.DATA_INSCRIBER_TEMPLATE.get())) {
            this.dataEnergistics$templateSnapshot = stack.copy();
        }
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/util/inv/AppEngInternalInventory;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            )
    )
    private void dataEnergistics$restoreTemplate(CallbackInfoReturnable<TickRateModulation> cir) {
        if (this.dataEnergistics$templateSnapshot.isEmpty()) {
            return;
        }

        if (!ItemStack.isSameItemSameComponents(this.sideItemHandler.getStackInSlot(0), this.dataEnergistics$templateSnapshot)) {
            this.sideItemHandler.setItemDirect(0, this.dataEnergistics$templateSnapshot.copy());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void dataEnergistics$clearSnapshot(CallbackInfoReturnable<TickRateModulation> cir) {
        this.dataEnergistics$templateSnapshot = ItemStack.EMPTY;
    }
}
