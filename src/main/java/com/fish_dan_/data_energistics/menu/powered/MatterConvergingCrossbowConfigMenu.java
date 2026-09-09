package com.fish_dan_.data_energistics.menu.powered;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.CannonCellMenuHost;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.InaccessibleSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Native AE menu: real disk slots and server-authored ammo previews, bound to an AE item host. */
public final class MatterConvergingCrossbowConfigMenu extends AEBaseMenu {

    private static final String ACTION_SET_MODE = "set_mode";
    private static final String ACTION_CYCLE_AMMO = "cycle_ammo";
    private static final SlotSemantic[] CELLS = {
            SlotSemantics.register("CANNON_CELL_GRENADE", false),
            SlotSemantics.register("CANNON_CELL_RAIL", false),
            SlotSemantics.register("CANNON_CELL_CROSSBOW", false)
    };
    private static final SlotSemantic[] AMMUNITION = {
            SlotSemantics.register("CANNON_AMMO_GRENADE", false),
            SlotSemantics.register("CANNON_AMMO_RAIL", false),
            SlotSemantics.register("CANNON_AMMO_CROSSBOW", false)
    };

    @Getter
    private final CannonCellMenuHost host;
    private final List<Slot> cellSlots = new ArrayList<>();
    private final List<Slot> ammoSlots = new ArrayList<>();

    @GuiSync(810)
    public int activeMode;

    public MatterConvergingCrossbowConfigMenu(int id, Inventory inventory, CannonCellMenuHost host) {
        super(DEMenus.MATTER_CONVERGING_CROSSBOW_CONFIG.get(), id, inventory, host);
        this.host = host;
        registerClientAction(ACTION_SET_MODE, Integer.class, this::setModeFromClient);
        registerClientAction(ACTION_CYCLE_AMMO, Integer.class, this::cycleAmmoFromClient);
        AppEngInternalInventory displays = new AppEngInternalInventory(null, MountedAmmoCells.SLOT_COUNT, 1);
        for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
            int index = mode.id();
            Slot cell = new AmmoCellSlot(host.cells(), index);
            cellSlots.add(cell);
            addSlot(cell, CELLS[index]);
            InaccessibleSlot ammo = new InaccessibleSlot(displays, index);
            ammoSlots.add(ammo);
            addSlot(ammo, AMMUNITION[index]);
        }
        var upgrades = host.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, i)
                    .setNotDraggable(), SlotSemantics.UPGRADE);
        }
        createPlayerInventorySlots(inventory);
        if (isServerSide()) refreshDisplay();
    }

    public Slot cellSlot(MatterConvergingCrossbowMode mode) {
        return cellSlots.get(mode.id());
    }

    public Slot ammoSlot(MatterConvergingCrossbowMode mode) {
        return ammoSlots.get(mode.id());
    }

    public @Nullable MatterConvergingCrossbowMode rowOf(Slot slot) {
        int row = cellSlots.indexOf(slot);
        if (row < 0) row = ammoSlots.indexOf(slot);
        return row < 0 ? null : MatterConvergingCrossbowMode.fromId(row);
    }

    public void sendSetMode(MatterConvergingCrossbowMode mode) {
        sendClientAction(ACTION_SET_MODE, mode.id());
    }

    public void sendCycleAmmo(MatterConvergingCrossbowMode mode, boolean reverse) {
        sendClientAction(ACTION_CYCLE_AMMO, mode.id() + (reverse ? MountedAmmoCells.SLOT_COUNT : 0));
    }

    private void setModeFromClient(Integer mode) {
        if (!isServerSide() || !host.isValid() || mode < 0 || mode >= MountedAmmoCells.SLOT_COUNT) return;
        ItemStack weapon = host.getItemStack();
        weapon.remove(DEDataComponents.CANNON_CHARGE.get());
        weapon.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), mode);
        broadcastChanges();
    }

    private void cycleAmmoFromClient(Integer action) {
        if (!isServerSide() || !host.isValid() || action < 0 || action >= MountedAmmoCells.SLOT_COUNT * 2) return;
        MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.fromId(action % MountedAmmoCells.SLOT_COUNT);
        if (mode.id() != activeMode) return;
        MountedAmmoCells.cycle(host.getItemStack(), mode, action >= MountedAmmoCells.SLOT_COUNT);
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && host.isValid()) refreshDisplay();
        super.broadcastChanges();
    }

    private void refreshDisplay() {
        ItemStack weapon = host.getItemStack();
        activeMode = MatterConvergingCrossbowItem.mode(weapon).id();
        for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
            ItemStack ammo = MountedAmmoCells.peek(weapon, mode);
            Slot slot = ammoSlots.get(mode.id());
            if (!ItemStack.matches(slot.getItem(), ammo)) slot.set(ammo);
        }
    }

    private static final class AmmoCellSlot extends RestrictedInputSlot {

        private AmmoCellSlot(InternalInventory inventory, int slot) {
            super(PlacableItemType.STORAGE_CELLS, inventory, slot);
            setStackLimit(1);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return MountedAmmoCells.accepts(stack) && super.mayPlace(stack);
        }
    }
}
