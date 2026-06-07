package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.ae2.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.SetStockAmountMenu;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot.PlacableItemType;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

public class DataSanctumLargeInterfaceMenu extends UpgradeableMenu<DataSanctumLargeInterfaceHost> {

    public static final String ACTION_OPEN_SET_AMOUNT = "setAmount";
    public static final String ACTION_SET_PAGE = "set_page";
    public static final String ACTION_SET_ACTIVE_PULL_SIDE = "set_active_pull_side";
    public static final int CONFIG_SLOT_COUNT = DataSanctumInterfaceConstants.CONFIG_SLOTS_PER_PAGE;
    public static final int STOCK_SLOT_COUNT = DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE;
    public static final int RETURN_SLOT_COUNT = DataSanctumInterfaceConstants.RETURN_SLOTS_PER_PAGE;
    public static final SlotSemantic RETURN_ROW_1 = SlotSemantics.register("DATA_SANCTUM_LARGE_INTERFACE_RETURN_ROW_1", false);
    public static final SlotSemantic RETURN_ROW_2 = SlotSemantics.register("DATA_SANCTUM_LARGE_INTERFACE_RETURN_ROW_2", false);

    @GuiSync(860)
    public int pageIndex;
    @GuiSync(861)
    public int totalPages = DataSanctumInterfaceConstants.BASE_PAGE_COUNT;
    @GuiSync(862)
    public int activePullSidesMask;

    private List<Slot> configSlots;

    public DataSanctumLargeInterfaceMenu(int id, Inventory playerInventory, DataSanctumLargeInterfaceHost host) {
        super(ModMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), id, playerInventory, host);
        registerClientAction(ACTION_OPEN_SET_AMOUNT, Integer.class, this::openSetAmountMenu);
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);
        registerClientAction(ACTION_SET_ACTIVE_PULL_SIDE, String.class, this::setActivePullSide);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getInterfaceLogic().getStorage();
        var returnInventory = this.getHost().getReturnInventory();
        for (int i = 0; i < STOCK_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.addSlot(new AppEngSlot(new PagedMenuInventory(storage, () -> DataSanctumInterfaceConstants.stockSlotIndex(this.pageIndex, slotOnPage)), 0), SlotSemantics.STORAGE);
        }
        for (int i = 0; i < CONFIG_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.addSlot(new AppEngSlot(new PagedMenuInventory(returnInventory, () -> DataSanctumInterfaceConstants.returnSlotIndex(this.pageIndex, slotOnPage)), 0), RETURN_ROW_1);
        }
        for (int i = CONFIG_SLOT_COUNT; i < RETURN_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.addSlot(new AppEngSlot(new PagedMenuInventory(returnInventory, () -> DataSanctumInterfaceConstants.returnSlotIndex(this.pageIndex, slotOnPage)), 0), RETURN_ROW_2);
        }
    }

    @Override
    protected void setupConfig() {
        this.configSlots = new ArrayList<>(CONFIG_SLOT_COUNT);
        var config = this.getHost().getInterfaceLogic().getConfig();
        for (int i = 0; i < CONFIG_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.configSlots.add(this.addSlot(new PagedFakeSlot(new PagedMenuInventory(config, () -> DataSanctumInterfaceConstants.stockSlotIndex(this.pageIndex, slotOnPage))), SlotSemantics.CONFIG));
        }
    }

    @Override
    protected void setupUpgrades() {
        var upgrades = this.getHost().getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            this.addSlot(new EnergyUpgradeSlot(upgrades, i), SlotSemantics.UPGRADE);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.totalPages = this.getHost().getUnlockedPageCount();
            this.pageIndex = clampPage(this.pageIndex);
            this.activePullSidesMask = encodeSides(this.getHost().getActivePullSides());
        }

        super.broadcastChanges();
    }

    public List<Slot> getConfigSlots() {
        return this.configSlots != null ? this.configSlots : List.of();
    }

    public void sendSetPage(int page) {
        this.pageIndex = clampPage(page);
        sendClientAction(ACTION_SET_PAGE, this.pageIndex);
    }

    public List<Direction> getActivePullSides() {
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if ((this.activePullSidesMask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    public void sendSetActivePullSide(Direction side, boolean enabled) {
        sendClientAction(ACTION_SET_ACTIVE_PULL_SIDE, side.getName() + ":" + enabled);
    }

    public void openSetAmountMenu(int slotOnPage) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_SET_AMOUNT, slotOnPage);
            return;
        }

        int configSlot = DataSanctumInterfaceConstants.stockSlotIndex(this.pageIndex, slotOnPage);
        var stack = getHost().getConfig().getStack(configSlot);
        if (stack != null) {
            SetStockAmountMenu.open((ServerPlayer) getPlayer(), getLocator(), configSlot, stack.what(), (int) stack.amount());
        }
    }

    private void setPage(Integer page) {
        if (page == null) {
            return;
        }
        this.pageIndex = clampPage(page);
        broadcastChanges();
    }

    private void setActivePullSide(String payload) {
        if (payload == null || this.getHost() == null) {
            return;
        }

        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return;
        }

        Direction side = Direction.byName(payload.substring(0, separator));
        if (side == null) {
            return;
        }

        boolean enabled = Boolean.parseBoolean(payload.substring(separator + 1));
        this.getHost().setActivePullSideEnabled(side, enabled);
        this.activePullSidesMask = encodeSides(this.getHost().getActivePullSides());
        broadcastChanges();
    }

    private int clampPage(int page) {
        int pages = Math.max(1, this.totalPages);
        if (isServerSide() && this.getHost() != null) {
            pages = Math.max(1, this.getHost().getUnlockedPageCount());
        }
        return Math.max(0, Math.min(page, pages - 1));
    }

    private static int encodeSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private static final class EnergyUpgradeSlot extends RestrictedInputSlot {

        private EnergyUpgradeSlot(appeng.api.inventories.InternalInventory inv, int invSlot) {
            super(PlacableItemType.UPGRADES, inv, invSlot);
        }

        @Override
        public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
            return (stack.is(AEItems.ENERGY_CARD.asItem()) || stack.is(AEItems.CAPACITY_CARD.asItem())) && super.mayPlace(stack);
        }
    }

    private static final class PagedMenuInventory extends ConfigMenuInventory {

        private final IntSupplier backingSlotSupplier;

        private PagedMenuInventory(appeng.helpers.externalstorage.GenericStackInv inv, IntSupplier backingSlotSupplier) {
            super(inv);
            this.backingSlotSupplier = backingSlotSupplier;
        }

        private int backingSlot() {
            return this.backingSlotSupplier.getAsInt();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return super.isItemValid(backingSlot(), stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return super.getSlotLimit(backingSlot());
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return super.getStackInSlot(backingSlot());
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            super.setItemDirect(backingSlot(), stack);
        }
    }

    private static final class PagedFakeSlot extends FakeSlot {

        private final PagedMenuInventory inventory;

        private PagedFakeSlot(PagedMenuInventory inv) {
            super(inv, 0);
            this.inventory = inv;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {}

        @Override
        public void increase(ItemStack stack) {
            var realInv = this.inventory.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                GenericStack newFilter = this.inventory.convertToSuitableStack(stack);
                int backingSlot = this.inventory.backingSlot();
                if (newFilter != null && newFilter.what().equals(realInv.getKey(backingSlot))) {
                    realInv.insert(backingSlot, newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }
            set(stack);
        }

        @Override
        public void decrease(ItemStack stack) {
            var realInv = this.inventory.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                GenericStack newFilter = this.inventory.convertToSuitableStack(stack);
                if (newFilter != null) {
                    realInv.extract(this.inventory.backingSlot(), newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }

            ItemStack current = getItem();
            if (stack.isEmpty()) {
                current = current.copy();
                current.shrink(1);
                set(current);
            } else if (ItemStack.isSameItemSameComponents(current, stack)) {
                current = current.copy();
                current.grow(1);
                set(current);
            } else {
                stack = stack.copy();
                stack.setCount(1);
                set(stack);
            }
        }
    }
}
