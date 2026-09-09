package com.fish_dan_.data_energistics.common.multiblock.autobuild.material;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;

/** Converts container capabilities only at the boundary; empty buckets and changed tank contents remain owned. */
final class AutoBuildFluidContainerSource implements AutoBuildMaterialSource {

    private final AutoBuildInventoryTraversal.Slot slot;
    private final Player player;

    AutoBuildFluidContainerSource(AutoBuildInventoryTraversal.Slot slot, Player player) {
        this.slot = slot;
        this.player = player;
    }

    @Override
    public long available(AEKey key, long amount) {
        if (!(key instanceof AEFluidKey fluid)) return 0;
        ItemStack current = slot.stack();
        if (current.getCount() != 1) return 0;
        GenericStack wrapped = GenericStack.unwrapItemStack(current);
        if (wrapped != null) return wrapped.what().equals(key) ? Math.min(amount, wrapped.amount()) : 0;
        IFluidHandlerItem handler = current.copy().getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return 0;
        FluidStack drained = handler.drain(fluid.toStack((int) Math.min(amount, Integer.MAX_VALUE)), FluidAction.SIMULATE);
        return fluid.matches(drained) ? drained.getAmount() : 0;
    }

    @Override
    public long extract(AEKey key, long amount) {
        if (!(key instanceof AEFluidKey fluid)) return 0;
        ItemStack current = slot.stack().copy();
        if (current.getCount() != 1) return 0;
        GenericStack wrapped = GenericStack.unwrapItemStack(current);
        if (wrapped != null) {
            if (!wrapped.what().equals(key)) return 0;
            long extracted = Math.min(amount, wrapped.amount());
            ItemStack replacement = extracted == wrapped.amount() ? ItemStack.EMPTY :
                    GenericStack.wrapInItemStack(key, wrapped.amount() - extracted);
            return replace(current, replacement) ? extracted : 0;
        }
        IFluidHandlerItem handler = current.copy().getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return 0;
        int requested = (int) Math.min(amount, Integer.MAX_VALUE);
        FluidStack drained = handler.drain(fluid.toStack(requested), FluidAction.EXECUTE);
        if (drained.isEmpty()) return 0;
        if (!fluid.matches(drained) || drained.getAmount() > requested) {
            throw new IllegalStateException("Container returned unexpected fluid during auto-build simulation");
        }
        return replace(current, handler.getContainer()) ? drained.getAmount() : 0;
    }

    @Override
    public long refund(AEKey key, long amount) {
        if (!(key instanceof AEFluidKey fluid)) return 0;
        ItemStack current = slot.stack().copy();
        if (current.getCount() != 1) return 0;
        IFluidHandlerItem handler = current.copy().getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return 0;
        int offered = (int) Math.min(amount, Integer.MAX_VALUE);
        int inserted = handler.fill(fluid.toStack(offered), FluidAction.EXECUTE);
        if (inserted < 0 || inserted > offered) throw new IllegalStateException("Invalid fluid refund amount");
        return inserted > 0 && replace(current, handler.getContainer()) ? inserted : 0;
    }

    private boolean replace(ItemStack expected, ItemStack replacement) {
        IItemHandler handler = slot.binding().get();
        if (handler == null || slot.index() >= handler.getSlots() || !ItemStack.matches(slot.stack(), expected)) return false;
        ItemStack simulated = handler.extractItem(slot.index(), 1, true);
        if (!ItemStack.matches(simulated, expected)) return false;
        ItemStack taken = handler.extractItem(slot.index(), 1, false);
        if (taken.isEmpty()) return false;
        if (!ItemStack.matches(taken, expected)) {
            AutoBuildStackDelivery.give(player, taken);
            throw new IllegalStateException("Fluid container changed during extraction");
        }
        ItemStack remainder = replacement.isEmpty() ? ItemStack.EMPTY : handler.insertItem(slot.index(), replacement.copy(), false);
        if (remainder.isEmpty()) return true;
        AutoBuildStackDelivery.give(player, handler.insertItem(slot.index(), taken, false));
        return false;
    }
}
