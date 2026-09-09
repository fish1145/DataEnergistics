package com.fish_dan_.data_energistics.common.multiblock.autobuild.material;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Visits direct slots before nested containers, keeping containers out of the consumable material set. */
final class AutoBuildInventoryTraversal {

    private static final int MAX_CONTAINER_DEPTH = 4;
    private final Set<IItemHandler> visited = new ReferenceOpenHashSet<>();
    private final List<Slot> slots = new ObjectArrayList<>();

    List<Slot> collect(IItemHandler root) {
        collect(() -> root, 0);
        return List.copyOf(slots);
    }

    private void collect(Supplier<@Nullable IItemHandler> binding, int depth) {
        IItemHandler handler = binding.get();
        if (handler == null || !visited.add(handler)) return;
        List<Supplier<@Nullable IItemHandler>> children = new ObjectArrayList<>();
        for (int index = 0; index < handler.getSlots(); index++) {
            Slot slot = new Slot(binding, index);
            ItemStack container = handler.getStackInSlot(index);
            if (container.isEmpty()) continue;
            IItemHandler child = container.getCapability(Capabilities.ItemHandler.ITEM);
            if (child == null) {
                slots.add(slot);
            } else if (depth < MAX_CONTAINER_DEPTH) {
                // Keep the actual container identity: a removed or replaced container must not remain accessible
                // through a capability captured before a callback changed the player's inventory.
                children.add(() -> slot.stack() == container ? container.getCapability(Capabilities.ItemHandler.ITEM) : null);
            }
        }
        for (Supplier<@Nullable IItemHandler> child : children) collect(child, depth + 1);
    }

    record Slot(Supplier<@Nullable IItemHandler> binding, int index) implements AutoBuildMaterialSource {

        ItemStack stack() {
            IItemHandler handler = binding.get();
            return handler == null || index >= handler.getSlots() ? ItemStack.EMPTY : handler.getStackInSlot(index);
        }

        @Override
        public long available(AEKey key, long amount) {
            if (!(key instanceof AEItemKey item)) return 0;
            IItemHandler handler = binding.get();
            if (handler == null || index >= handler.getSlots() || !item.matches(handler.getStackInSlot(index))) return 0;
            ItemStack stack = handler.getStackInSlot(index);
            if (stack.getCapability(Capabilities.FluidHandler.ITEM) != null || GenericStack.unwrapItemStack(stack) != null) return 0;
            ItemStack extracted = handler.extractItem(index, (int) Math.min(amount, Integer.MAX_VALUE), true);
            return item.matches(extracted) ? extracted.getCount() : 0;
        }

        @Override
        public long extract(AEKey key, long amount) {
            if (!(key instanceof AEItemKey item)) return 0;
            IItemHandler handler = binding.get();
            if (handler == null || index >= handler.getSlots() || !item.matches(handler.getStackInSlot(index))) {
                return 0;
            }
            int requested = (int) Math.min(amount, Integer.MAX_VALUE);
            ItemStack extracted = handler.extractItem(index, requested, false);
            if (!extracted.isEmpty() && (!item.matches(extracted) || extracted.getCount() > requested)) {
                throw new IllegalStateException("Container returned unexpected auto-build material: " + extracted);
            }
            return extracted.getCount();
        }

        @Override
        public long refund(AEKey key, long amount) {
            if (!(key instanceof AEItemKey item)) return 0;
            IItemHandler handler = binding.get();
            if (handler == null || index >= handler.getSlots()) return 0;
            int offered = (int) Math.min(amount, Integer.MAX_VALUE);
            ItemStack remainder = handler.insertItem(index, item.toStack(offered), false);
            if (remainder.getCount() > offered || !remainder.isEmpty() && !item.matches(remainder)) {
                throw new IllegalStateException("Container returned an invalid auto-build refund remainder");
            }
            return offered - remainder.getCount();
        }
    }
}
