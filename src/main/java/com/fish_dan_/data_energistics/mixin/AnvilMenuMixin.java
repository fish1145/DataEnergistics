package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.item.PoweredEnergyItem;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    public int repairItemCountCost;

    protected AnvilMenuMixin(int containerId, Inventory playerInventory) {
        super((MenuType<?>) null, 0, (Inventory) null, (ContainerLevelAccess) null);
        throw new AssertionError();
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void dataEnergistics$blockPoweredItemAnvilRepair(CallbackInfo ci) {
        ItemStack baseStack = this.inputSlots.getItem(0);
        ItemStack additionStack = this.inputSlots.getItem(1);
        if (!PoweredEnergyItem.isAnvilRepairBlocked(baseStack, additionStack)) {
            return;
        }

        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.cost.set(0);
        this.repairItemCountCost = 0;
    }
}
