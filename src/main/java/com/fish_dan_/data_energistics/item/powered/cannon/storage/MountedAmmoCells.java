package com.fish_dan_.data_energistics.item.powered.cannon.storage;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Owns the three removable ammunition cells. Item contents are copied before mutation and saved back explicitly. */
public final class MountedAmmoCells {

    public static final int SLOT_COUNT = 3;

    private MountedAmmoCells() {}

    /** Returns a detached cell copy. Read-only callers must not save it back. */
    public static ItemStack cell(ItemStack weapon, MatterConvergingCrossbowMode mode) {
        return cells(weapon).get(mode.id());
    }

    public static NonNullList<ItemStack> cells(ItemStack weapon) {
        NonNullList<ItemStack> result = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        weapon.getOrDefault(DEDataComponents.CANNON_CELLS.get(), ItemContainerContents.EMPTY).copyInto(result);
        return result;
    }

    /** Only item-channel cells are useful as magazines; the weapon itself no longer implements the cell API. */
    public static boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof IBasicCellItem cellItem && cellItem.getKeyType() == AEKeyType.items() && StorageCells.isCellHandled(stack);
    }

    /** Called on the server after an AE slot edit; validates all cells before replacing the stored contents. */
    public static void setCells(ItemStack weapon, ItemContainerContents contents) {
        if (contents.getSlots() > SLOT_COUNT) throw new IllegalArgumentException("Too many ammunition cell slots");
        NonNullList<ItemStack> slots = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        contents.copyInto(slots);
        for (ItemStack cell : slots) {
            if (!cell.isEmpty() && (cell.getCount() != 1 || !accepts(cell))) {
                throw new IllegalArgumentException("Invalid ammunition cell: " + cell);
            }
        }
        weapon.set(DEDataComponents.CANNON_CELLS.get(), ItemContainerContents.fromItems(slots));
        weapon.remove(DEDataComponents.CANNON_CHARGE.get());
    }

    /** Compatible types retain their full component identity, including charged sabers and configured mod items. */
    public static List<AEItemKey> ammunition(ItemStack weapon, MatterConvergingCrossbowMode mode) {
        StorageCell inventory = open(cell(weapon, mode));
        return inventory == null ? List.of() : ammunition(inventory, mode);
    }

    private static List<AEItemKey> ammunition(StorageCell inventory, MatterConvergingCrossbowMode mode) {
        List<AEItemKey> result = new ArrayList<>();
        for (var entry : inventory.getAvailableStacks()) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key && MatterConvergingCrossbowItem.supportsAmmo(mode, key.toStack(1))) {
                result.add(key);
            }
        }
        result.sort(Comparator.comparing(AEItemKey::toString));
        return result;
    }

    public static ItemStack peek(ItemStack weapon, MatterConvergingCrossbowMode mode) {
        List<AEItemKey> choices = ammunition(weapon, mode);
        return selected(weapon, mode, choices);
    }

    private static ItemStack selected(ItemStack weapon, MatterConvergingCrossbowMode mode, List<AEItemKey> choices) {
        if (choices.isEmpty()) return ItemStack.EMPTY;
        NonNullList<ItemStack> selected = selections(weapon);
        AEItemKey current = AEItemKey.of(selected.get(mode.id()));
        if (current != null && choices.contains(current)) return current.toStack(1);
        ResourceLocation legacy = weapon.get(DEDataComponents.MATTER_CONVERGING_CROSSBOW_SELECTED_AMMO.get());
        if (legacy != null) {
            for (AEItemKey choice : choices) {
                if (BuiltInRegistries.ITEM.getKey(choice.getItem()).equals(legacy)) return choice.toStack(1);
            }
        }
        return choices.getFirst().toStack(1);
    }

    /** Server-side selection used by AE's wheel and menu protocols; incompatible and absent types are skipped. */
    public static void cycle(ItemStack weapon, MatterConvergingCrossbowMode mode, boolean reverse) {
        if (MatterConvergingCrossbowItem.isCharged(weapon)) return;
        List<AEItemKey> choices = ammunition(weapon, mode);
        if (choices.isEmpty()) return;
        int current = choices.indexOf(AEItemKey.of(selected(weapon, mode, choices)));
        int next = Math.floorMod(current + (reverse ? -1 : 1), choices.size());
        select(weapon, mode, choices.get(next));
    }

    private static void select(ItemStack weapon, MatterConvergingCrossbowMode mode, AEItemKey key) {
        NonNullList<ItemStack> selected = selections(weapon);
        selected.set(mode.id(), key.toStack(1));
        weapon.set(DEDataComponents.CANNON_AMMO_SELECTIONS.get(), ItemContainerContents.fromItems(selected));
        weapon.remove(DEDataComponents.MATTER_CONVERGING_CROSSBOW_SELECTED_AMMO.get());
        weapon.remove(DEDataComponents.CANNON_CHARGE.get());
    }

    private static NonNullList<ItemStack> selections(ItemStack weapon) {
        NonNullList<ItemStack> result = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        weapon.getOrDefault(DEDataComponents.CANNON_AMMO_SELECTIONS.get(), ItemContainerContents.EMPTY).copyInto(result);
        return result;
    }

    /**
     * Extracts exactly the same key shown by peek, persists the cell, then publishes its new contents on the weapon.
     */
    public static ItemStack extractOne(ItemStack weapon, MatterConvergingCrossbowMode mode, IActionSource source) {
        ItemStack cell = cell(weapon, mode);
        StorageCell inventory = open(cell);
        if (inventory == null) return ItemStack.EMPTY;
        ItemStack ammo = selected(weapon, mode, ammunition(inventory, mode));
        AEItemKey key = AEItemKey.of(ammo);
        if (key == null || inventory.extract(key, 1, Actionable.MODULATE, source) != 1) return ItemStack.EMPTY;
        inventory.persist();
        saveCell(weapon, mode, cell);
        return ammo;
    }

    /** Loads matching inventory ammunition into the installed disk, respecting its capacity and partition rules. */
    public static long insert(ItemStack weapon, MatterConvergingCrossbowMode mode, AEItemKey key, long amount,
                              IActionSource source, Actionable action) {
        if (amount <= 0 || !MatterConvergingCrossbowItem.supportsAmmo(mode, key.toStack(1))) return 0;
        ItemStack cell = cell(weapon, mode);
        StorageCell inventory = open(cell);
        if (inventory == null) return 0;
        long inserted = inventory.insert(key, amount, action, source);
        if (action == Actionable.MODULATE && inserted > 0) {
            inventory.persist();
            saveCell(weapon, mode, cell);
        }
        return inserted;
    }

    private static @Nullable StorageCell open(ItemStack cell) {
        return cell.isEmpty() || !accepts(cell) ? null : StorageCells.getCellInventory(cell, null);
    }

    private static void saveCell(ItemStack weapon, MatterConvergingCrossbowMode mode, ItemStack cell) {
        NonNullList<ItemStack> slots = cells(weapon);
        slots.set(mode.id(), cell);
        weapon.set(DEDataComponents.CANNON_CELLS.get(), ItemContainerContents.fromItems(slots));
    }

    /**
     * Moves the legacy on-weapon cell contents once into a real 1k item cell (larger than the old 512-byte capacity).
     * Existing installed cells are never overwritten. If all slots are occupied, legacy data stays intact until one is
     * freed.
     * Call only on the server, before opening the menu or using ammunition.
     */
    public static void migrateLegacy(ItemStack weapon) {
        if (!weapon.has(AEComponents.STORAGE_CELL_INV)) return;
        List<GenericStack> contents = weapon.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
        if (contents.isEmpty()) {
            weapon.remove(AEComponents.STORAGE_CELL_INV);
            weapon.remove(AEComponents.STORAGE_CELL_CONFIG_INV);
            return;
        }
        NonNullList<ItemStack> slots = cells(weapon);
        int preferred = MatterConvergingCrossbowItem.mode(weapon).id();
        int slot = slots.get(preferred).isEmpty() ? preferred : -1;
        for (int i = 0; slot < 0 && i < SLOT_COUNT; i++) {
            if (slots.get(i).isEmpty()) slot = i;
        }
        if (slot < 0) return;
        ItemStack migratedCell = AEItems.ITEM_CELL_1K.stack();
        migratedCell.set(AEComponents.STORAGE_CELL_INV, List.copyOf(contents));
        if (weapon.has(AEComponents.STORAGE_CELL_CONFIG_INV)) {
            migratedCell.set(AEComponents.STORAGE_CELL_CONFIG_INV, weapon.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of()));
        }
        slots.set(slot, migratedCell);
        weapon.set(DEDataComponents.CANNON_CELLS.get(), ItemContainerContents.fromItems(slots));
        weapon.remove(AEComponents.STORAGE_CELL_INV);
        weapon.remove(AEComponents.STORAGE_CELL_CONFIG_INV);
    }
}
