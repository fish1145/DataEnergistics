package com.fish_dan_.data_energistics.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

public class MeVacuumMenuHost extends ItemMenuHost<MeVacuumItem> implements InternalInventoryHost {

    public static final int STORAGE_SLOT_COUNT = 5;
    private static final String TAG_STORAGE = "StorageComponents";

    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, STORAGE_SLOT_COUNT);

    public MeVacuumMenuHost(MeVacuumItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);

        for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
            this.storage.setMaxStackSize(i, 1);
        }

        loadStorage();
    }

    public AppEngInternalInventory getStorage() {
        return this.storage;
    }

    public static NonNullList<ItemStack> readStoredCells(ItemStack stack, HolderLookup.Provider registries) {
        var inventory = new AppEngInternalInventory(null, STORAGE_SLOT_COUNT, 1);
        inventory.readFromNBT(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(),
                TAG_STORAGE, registries);

        var cells = NonNullList.withSize(STORAGE_SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
            cells.set(i, inventory.getStackInSlot(i).copy());
        }
        return cells;
    }

    public static long insertIntoStoredCells(ItemStack stack, HolderLookup.Provider registries, AEKey key, long amount,
                                             IActionSource actionSource) {
        return insertIntoStoredCells(stack, registries, key, amount, actionSource, Actionable.MODULATE);
    }

    public static long simulateInsertIntoStoredCells(ItemStack stack, HolderLookup.Provider registries, AEKey key,
                                                     long amount, IActionSource actionSource) {
        return insertIntoStoredCells(stack, registries, key, amount, actionSource, Actionable.SIMULATE);
    }

    private static long insertIntoStoredCells(ItemStack stack, HolderLookup.Provider registries, AEKey key, long amount,
                                              IActionSource actionSource, Actionable mode) {
        if (stack.isEmpty() || key == null || amount <= 0L) {
            return 0L;
        }

        var cells = readStoredCells(stack, registries);
        long remaining = amount;
        boolean changed = false;

        for (int i = 0; i < cells.size() && remaining > 0L; i++) {
            ItemStack cellStack = cells.get(i);
            if (cellStack.isEmpty()) {
                continue;
            }

            boolean[] cellChanged = { false };
            var cellInventory = StorageCells.getCellInventory(cellStack, () -> cellChanged[0] = true);
            if (cellInventory == null) {
                continue;
            }

            long inserted = cellInventory.insert(key, remaining, mode, actionSource);
            if (inserted <= 0L) {
                continue;
            }

            if (mode == Actionable.MODULATE) {
                cellInventory.persist();
            }
            remaining -= inserted;
            changed = true;
        }

        long inserted = amount - remaining;
        if (mode == Actionable.MODULATE && changed && inserted > 0L) {
            writeStoredCells(stack, registries, cells);
        }
        return inserted;
    }

    private static void writeStoredCells(ItemStack stack, HolderLookup.Provider registries, NonNullList<ItemStack> cells) {
        var inventory = new AppEngInternalInventory(null, STORAGE_SLOT_COUNT, 1);
        for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
            inventory.setItemDirect(i, i < cells.size() ? cells.get(i).copy() : ItemStack.EMPTY);
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        inventory.writeToNBT(tag, TAG_STORAGE, registries);

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        ItemStack stack = getItemStack();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        inv.writeToNBT(tag, TAG_STORAGE, getPlayer().registryAccess());

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public boolean isClientSide() {
        return getPlayer().level().isClientSide();
    }

    private void loadStorage() {
        CompoundTag tag = getItemStack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        this.storage.readFromNBT(tag, TAG_STORAGE, getPlayer().registryAccess());
    }
}
