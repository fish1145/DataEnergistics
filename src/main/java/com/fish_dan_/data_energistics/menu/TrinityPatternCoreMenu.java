package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityPatternCoreUi;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import it.unimi.dsi.fastutil.shorts.ShortSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Eight-by-eight local pattern menu for a single Trinity P core.
 *
 * <p>
 * Only the current 64-slot page exists in the menu, so a 512-slot core does not synchronize hundreds of hidden
 * container slots. Each page slot delegates directly to its stable backing core index.
 */
public final class TrinityPatternCoreMenu extends AEBaseMenu {

    /** Number of local pattern slots synchronized per page. */
    public static final int SLOTS_PER_PAGE = 64;
    /** Eight independent row semantics used to lay out a true 8 by 8 pattern page. */
    public static final List<SlotSemantic> PAGE_PATTERN_ROWS = List.of(
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_1", false),
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_2", false),
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_3", false),
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_4", false),
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_5", false),
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_6", false),
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_7", false),
            SlotSemantics.register("TRINITY_PATTERN_CORE_PATTERN_ROW_8", false));

    private static final String ACTION_SET_PAGE = "set_page";
    private static final String ACTION_CONFIRM_PAGE = "confirm_page";
    private static final String ACTION_REFUND_ALL = "refund_all";
    private static final int PAGE_INDEX_SYNC_ID = 820;

    private final TrinityPatternCoreBlockEntity host;
    private final List<PagedPatternSlot> pagePatternSlots = new ArrayList<>(SLOTS_PER_PAGE);
    private boolean pageSelectionConfirmed = true;

    /** Current zero-based page selected by this menu instance. */
    @GuiSync(PAGE_INDEX_SYNC_ID)
    public int pageIndex;
    /** Total number of pages supplied by this physical core. */
    @GuiSync(821)
    public int totalPages = 1;
    /** Whether the core currently contains anything that an atomic refund would return. */
    @GuiSync(822)
    public boolean hasRefundableState;

    /**
     * Creates a local core menu with exactly 64 backing proxies plus the player inventory.
     *
     * @param id              container id
     * @param playerInventory opening player's inventory
     * @param host            local P-core block entity
     */
    public TrinityPatternCoreMenu(int id, Inventory playerInventory, TrinityPatternCoreBlockEntity host) {
        super(ModMenus.TRINITY_PATTERN_CORE.get(), id, playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);
        registerClientAction(ACTION_CONFIRM_PAGE, Integer.class, this::confirmPage);
        registerClientAction(ACTION_REFUND_ALL, this::refundAll);

        for (int slotOnPage = 0; slotOnPage < SLOTS_PER_PAGE; slotOnPage++) {
            PagedPatternSlot slot = new PagedPatternSlot(slotOnPage);
            this.pagePatternSlots.add(slot);
            addSlot(slot, PAGE_PATTERN_ROWS.get(slotOnPage / 8));
        }
        createPlayerInventorySlots(playerInventory);
        refreshState();
        updatePatternSlotVisibility();
        TrinityPatternCoreUi.mount(this, host.getBlockState().getBlock().getName());
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            refreshState();
        }
        super.broadcastChanges();
        updatePatternSlotVisibility();
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        boolean pageUpdated = updatedFields.contains((short) PAGE_INDEX_SYNC_ID);
        if (pageUpdated) {
            this.pageSelectionConfirmed = true;
        }
        updatePatternSlotVisibility();
        if (pageUpdated) {
            sendClientAction(ACTION_CONFIRM_PAGE, this.pageIndex);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.host.getLevel() == player.level() &&
                this.host.getLevel().getBlockEntity(this.host.getBlockPos()) == this.host &&
                player.distanceToSqr(
                        this.host.getBlockPos().getX() + 0.5D,
                        this.host.getBlockPos().getY() + 0.5D,
                        this.host.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    /**
     * Sends a validated page change through AE2's menu action channel.
     *
     * @param requestedPage requested zero-based page
     */
    public void sendSetPage(int requestedPage) {
        int targetPage = clampPage(requestedPage);
        if (targetPage == this.pageIndex) {
            return;
        }
        this.pageSelectionConfirmed = false;
        this.pageIndex = targetPage;
        updatePatternSlotVisibility();
        sendClientAction(ACTION_SET_PAGE, targetPage);
    }

    /** Sends an atomic refund request to the authoritative server menu. */
    public void sendRefundAll() {
        sendClientAction(ACTION_REFUND_ALL);
    }

    /** Returns whether both menu sides currently agree on the selected backing page. */
    public boolean isPageSelectionConfirmed() {
        return this.pageSelectionConfirmed;
    }

    /** Returns the immutable set of the 64 pattern proxies owned by this page. */
    public List<Slot> pagePatternSlots() {
        return List.copyOf(this.pagePatternSlots);
    }

    void setPage(int requestedPage) {
        int targetPage = clampPage(requestedPage);
        if (targetPage == this.pageIndex) {
            return;
        }
        this.pageSelectionConfirmed = false;
        this.pageIndex = targetPage;
        updatePatternSlotVisibility();
        broadcastChanges();
    }

    void confirmPage(int confirmedPage) {
        if (confirmedPage != this.pageIndex) {
            return;
        }
        this.pageSelectionConfirmed = true;
        updatePatternSlotVisibility();
        broadcastChanges();
    }

    void refundAll() {
        boolean refunded = this.host.tryRefundAll(getPlayer());
        getPlayer().displayClientMessage(Component.translatable(
                refunded ? "message.data_energistics.trinity_pattern_core.refund.success" : "message.data_energistics.trinity_pattern_core.refund.failure"), true);
        refreshState();
        broadcastChanges();
    }

    private void refreshState() {
        this.totalPages = Math.max(1, (this.host.patternCapacity() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        this.pageIndex = clampPage(this.pageIndex);
        this.hasRefundableState = this.host.hasWork();
    }

    private void updatePatternSlotVisibility() {
        for (PagedPatternSlot slot : this.pagePatternSlots) {
            boolean visible = slot.backingIndex() < this.host.patternCapacity();
            boolean enabled = visible && this.pageSelectionConfirmed;
            slot.setActive(enabled);
            slot.setSlotEnabled(enabled);
        }
    }

    private int clampPage(int requestedPage) {
        return Math.max(0, Math.min(requestedPage, this.totalPages - 1));
    }

    private final class PagedPatternSlot extends RestrictedInputSlot {

        private final int slotOnPage;

        private PagedPatternSlot(int slotOnPage) {
            super(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN, new PagedPatternInventory(slotOnPage), 0);
            this.slotOnPage = slotOnPage;
            // TrinityPatternCoreScreen draws the dedicated empty-pattern sprite.
            setIcon(null);
        }

        private int backingIndex() {
            return TrinityPatternCoreMenu.this.pageIndex * SLOTS_PER_PAGE + this.slotOnPage;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return TrinityPatternCoreMenu.this.pageSelectionConfirmed &&
                    backingIndex() < TrinityPatternCoreMenu.this.host.patternCapacity() &&
                    super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return TrinityPatternCoreMenu.this.pageSelectionConfirmed &&
                    backingIndex() < TrinityPatternCoreMenu.this.host.patternCapacity() &&
                    super.mayPickup(player);
        }
    }

    private final class PagedPatternInventory implements InternalInventory {

        private final int slotOnPage;

        private PagedPatternInventory(int slotOnPage) {
            this.slotOnPage = slotOnPage;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public int getSlotLimit(int slot) {
            return TrinityPatternCoreMenu.this.pageSelectionConfirmed &&
                    backingIndex() < TrinityPatternCoreMenu.this.host.patternCapacity() ? 1 : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            int backingIndex = backingIndex();
            return backingIndex < TrinityPatternCoreMenu.this.host.patternCapacity() ? TrinityPatternCoreMenu.this.host.pattern(backingIndex) : ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            int backingIndex = backingIndex();
            if (backingIndex < TrinityPatternCoreMenu.this.host.patternCapacity()) {
                TrinityPatternCoreMenu.this.host.trySetPattern(backingIndex, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int backingIndex = backingIndex();
            InternalInventory inventory = TrinityPatternCoreMenu.this.host.patternInventory();
            return TrinityPatternCoreMenu.this.pageSelectionConfirmed &&
                    backingIndex < inventory.size() &&
                    inventory.isItemValid(backingIndex, stack);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int backingIndex = backingIndex();
            InternalInventory inventory = TrinityPatternCoreMenu.this.host.patternInventory();
            return TrinityPatternCoreMenu.this.pageSelectionConfirmed && backingIndex < inventory.size() ? inventory.extractItem(backingIndex, amount, simulate) : ItemStack.EMPTY;
        }

        private int backingIndex() {
            return TrinityPatternCoreMenu.this.pageIndex * SLOTS_PER_PAGE + this.slotOnPage;
        }
    }
}
