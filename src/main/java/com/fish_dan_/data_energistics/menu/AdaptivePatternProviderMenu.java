package com.fish_dan_.data_energistics.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.client.gui.Icon;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class AdaptivePatternProviderMenu extends PatternProviderMenu {
    private static final String ACTION_SET_PAGE = "set_page";
    private static final String ACTION_SET_FILTERED_IMPORT = "set_filtered_import";
    private static final String ACTION_TOGGLE_AE2LT_MODE = "toggle_ae2lt_mode";
    private static final String ACTION_TOGGLE_AE2LT_RETURN_MODE = "toggle_ae2lt_return_mode";
    private static final String ACTION_TOGGLE_AE2LT_WIRELESS_DISPATCH = "toggle_ae2lt_wireless_dispatch";
    private static final String ACTION_TOGGLE_AE2LT_WIRELESS_SPEED = "toggle_ae2lt_wireless_speed";
    private static final int SLOTS_PER_PAGE = 36;
    private static final int DEFAULT_RETURN_SLOTS = 9;
    private static final int EXPANDED_RETURN_SLOTS = 18;
    public static final SlotSemantic PROVIDER_INPUT = SlotSemantics.register("ADAPTIVE_PATTERN_PROVIDER_PROVIDER", false);
    public static final SlotSemantic PAGE_PATTERN = SlotSemantics.register("ADAPTIVE_PATTERN_PROVIDER_PAGE_PATTERN", false);
    public static final SlotSemantic STORAGE_ROW_2 = SlotSemantics.register("ADAPTIVE_PATTERN_PROVIDER_STORAGE_ROW_2", false);

    private final AdaptivePatternProviderBlockEntity host;

    @GuiSync(780)
    public int visiblePatternSlots;
    @GuiSync(781)
    public int pageIndex;
    @GuiSync(782)
    public int totalPages = 1;
    @GuiSync(783)
    public boolean advancedAeFilteredImport;
    @GuiSync(784)
    public int ae2ltProviderMode;
    @GuiSync(785)
    public int ae2ltReturnMode;
    @GuiSync(786)
    public int ae2ltWirelessDispatchMode;
    @GuiSync(787)
    public int ae2ltWirelessSpeedMode;

    public AdaptivePatternProviderMenu(int id, Inventory playerInventory, AdaptivePatternProviderBlockEntity host) {
        super(ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), id, playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);
        registerClientAction(ACTION_SET_FILTERED_IMPORT, Boolean.class, this::setAdvancedAeFilteredImport);
        registerClientAction(ACTION_TOGGLE_AE2LT_MODE, this::toggleAe2LtMode);
        registerClientAction(ACTION_TOGGLE_AE2LT_RETURN_MODE, this::toggleAe2LtReturnMode);
        registerClientAction(ACTION_TOGGLE_AE2LT_WIRELESS_DISPATCH, this::toggleAe2LtWirelessDispatchMode);
        registerClientAction(ACTION_TOGGLE_AE2LT_WIRELESS_SPEED, this::toggleAe2LtWirelessSpeedMode);

        // Disable the real underlying pattern slots. We expose paged proxy slots below.
        for (var slot : this.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(false);
                appEngSlot.setSlotEnabled(false);
            }
        }

        this.visiblePatternSlots = host != null ? host.getPatternSlotCountForMenu() : 0;
        this.advancedAeFilteredImport = host != null && host.isAdvancedAeFilteredImportEnabled();
        syncAe2LtStateFromHost();
        addPatternPageSlots();
        addExpandedReturnSlots();

        var providerSlot = new ProviderSuffixSlot(
                host != null ? host.getProviderInventory() : new AppEngInternalInventory(1),
                0,
                host
        );
        providerSlot.setEmptyTooltip(() -> Tooltips.slotTooltip(
                Component.translatable("tooltip.data_energistics.adaptive_pattern_provider.provider_slot")
        ));
        this.addSlot(providerSlot, PROVIDER_INPUT);
        updatePatternSlotVisibility();
    }

    @Override
    public void onSlotChange(net.minecraft.world.inventory.Slot slot) {
        super.onSlotChange(slot);
        if (slot != null && this.getSlotSemantic(slot) == PROVIDER_INPUT) {
            returnOverflowPatternsToPlayer();
            refreshPatternPagination();
            updatePatternSlotVisibility();
        } else if (slot != null && this.getSlotSemantic(slot) == SlotSemantics.UPGRADE) {
            returnOverflowProvidersToPlayer();
            returnOverflowPatternsToPlayer();
            refreshPatternPagination();
            updatePatternSlotVisibility();
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            refreshPatternPagination();
        }
        super.broadcastChanges();
        updatePatternSlotVisibility();
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        updatePatternSlotVisibility();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (idx < 0 || idx >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        var slot = this.slots.get(idx);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        var semantic = this.getSlotSemantic(slot);
        if (semantic == PAGE_PATTERN || semantic == PROVIDER_INPUT) {
            return super.quickMoveStack(player, idx);
        }

        ItemStack original = slot.getItem();
        ItemStack working = original.copy();
        if (tryMoveSneakPriority(working)) {
            int moved = original.getCount() - working.getCount();
            if (moved > 0) {
                original.shrink(moved);
                slot.setChanged();
                if (original.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                }
                return slot.getItem().copy();
            }
        }

        return super.quickMoveStack(player, idx);
    }

    public void sendSetPage(int pageIndex) {
        sendClientAction(ACTION_SET_PAGE, pageIndex);
    }

    public Component getProviderDisplayName() {
        ItemStack providerStack = getProviderStack();
        if (!providerStack.isEmpty()) {
            return providerStack.getHoverName();
        }

        return this.host != null
                ? this.host.getProviderDisplayName()
                : Component.translatable("block.data_energistics.adaptive_pattern_provider");
    }

    public boolean isAdvancedAeProviderSelected() {
        ItemStack providerStack = getProviderStack();
        if (!providerStack.isEmpty()) {
            return AdaptivePatternProviderBlockEntity.isAdvancedAeProviderStack(providerStack)
                    || AdaptivePatternProviderBlockEntity.isAe2LightningTechOverloadedProviderStack(providerStack);
        }

        return this.host != null && this.host.supportsFilteredImportToggle();
    }

    public boolean isAdvancedAeFilteredImportEnabled() {
        return this.advancedAeFilteredImport;
    }

    public void sendSetAdvancedAeFilteredImport(boolean enabled) {
        this.advancedAeFilteredImport = enabled;
        sendClientAction(ACTION_SET_FILTERED_IMPORT, enabled);
    }

    public boolean isAe2LtOverloadedProviderSelected() {
        ItemStack providerStack = getProviderStack();
        if (!providerStack.isEmpty()) {
            return AdaptivePatternProviderBlockEntity.isAe2LightningTechOverloadedProviderStack(providerStack);
        }

        return this.host != null && this.host.isAe2LightningTechOverloadedProviderSelected();
    }

    public boolean isAe2LtWirelessMode() {
        return this.ae2ltProviderMode == AdaptivePatternProviderBlockEntity.Ae2LtProviderMode.WIRELESS.ordinal();
    }

    public boolean isAe2LtEvenDistributionMode() {
        return this.ae2ltWirelessDispatchMode
                == AdaptivePatternProviderBlockEntity.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION.ordinal();
    }

    public boolean isAe2LtFastSpeedMode() {
        return this.ae2ltWirelessSpeedMode == AdaptivePatternProviderBlockEntity.Ae2LtWirelessSpeedMode.FAST.ordinal();
    }

    public int getAe2LtReturnModeOrdinal() {
        return this.ae2ltReturnMode;
    }

    public void sendToggleAe2LtMode() {
        sendClientAction(ACTION_TOGGLE_AE2LT_MODE);
    }

    public void sendToggleAe2LtReturnMode() {
        sendClientAction(ACTION_TOGGLE_AE2LT_RETURN_MODE);
    }

    public void sendToggleAe2LtWirelessDispatchMode() {
        sendClientAction(ACTION_TOGGLE_AE2LT_WIRELESS_DISPATCH);
    }

    public void sendToggleAe2LtWirelessSpeedMode() {
        sendClientAction(ACTION_TOGGLE_AE2LT_WIRELESS_SPEED);
    }

    private void setPage(Integer pageIndex) {
        if (pageIndex == null) {
            return;
        }
        this.pageIndex = Math.max(0, Math.min(pageIndex, Math.max(0, this.totalPages - 1)));
        updatePatternSlotVisibility();
        broadcastChanges();
    }

    private void setAdvancedAeFilteredImport(Boolean enabled) {
        if (enabled == null || this.host == null || !this.host.supportsFilteredImportToggle()) {
            return;
        }

        this.host.setAdvancedAeFilteredImportEnabled(enabled);
        this.advancedAeFilteredImport = this.host.isAdvancedAeFilteredImportEnabled();
        broadcastChanges();
    }

    private void toggleAe2LtMode() {
        if (this.host == null || !this.host.isAe2LightningTechOverloadedProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtProviderMode();
        syncAe2LtStateFromHost();
        broadcastChanges();
    }

    private void toggleAe2LtReturnMode() {
        if (this.host == null || !this.host.isAe2LightningTechOverloadedProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtReturnMode();
        syncAe2LtStateFromHost();
        broadcastChanges();
    }

    private void toggleAe2LtWirelessDispatchMode() {
        if (this.host == null || !this.host.isAe2LightningTechOverloadedProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtWirelessDispatchMode();
        syncAe2LtStateFromHost();
        broadcastChanges();
    }

    private void toggleAe2LtWirelessSpeedMode() {
        if (this.host == null || !this.host.isAe2LightningTechOverloadedProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtWirelessSpeedMode();
        syncAe2LtStateFromHost();
        broadcastChanges();
    }

    private void refreshPatternPagination() {
        int slotCount = this.host != null ? this.host.getPatternSlotCountForMenu() : 0;
        this.visiblePatternSlots = slotCount;
        this.advancedAeFilteredImport = this.host != null && this.host.isAdvancedAeFilteredImportEnabled();
        syncAe2LtStateFromHost();
        this.totalPages = Math.max(1, (slotCount + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        if (slotCount <= 0) {
            this.pageIndex = 0;
        } else if (this.pageIndex >= this.totalPages) {
            this.pageIndex = this.totalPages - 1;
        }
    }

    private void returnOverflowPatternsToPlayer() {
        if (this.logic == null) {
            return;
        }

        int slotCount = this.host != null ? this.host.getPatternSlotCountForMenu() : 0;
        InternalInventory patternInventory = this.logic.getPatternInv();
        Inventory playerInventory = this.getPlayerInventory();
        boolean changed = false;

        for (int i = slotCount; i < patternInventory.size(); i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            patternInventory.setItemDirect(i, ItemStack.EMPTY);
            playerInventory.placeItemBackInInventory(stack);
            changed = true;
        }

        if (changed && this.host != null) {
            this.host.saveChanges();
            this.host.markForClientUpdate();
        }
    }

    private void returnOverflowProvidersToPlayer() {
        if (this.host == null) {
            return;
        }

        ItemStack overflow = this.host.extractProviderOverflow();
        if (overflow.isEmpty()) {
            return;
        }

        this.getPlayerInventory().placeItemBackInInventory(overflow);
        this.host.saveChanges();
        this.host.markForClientUpdate();
    }

    private void updatePatternSlotVisibility() {
        for (var slot : this.getSlots(PAGE_PATTERN)) {
            if (slot instanceof AppEngSlot appEngSlot && slot instanceof PagedPatternSlot pagedPatternSlot) {
                boolean visible = pagedPatternSlot.getBackingIndex() < this.visiblePatternSlots;
                appEngSlot.setActive(visible);
                appEngSlot.setSlotEnabled(visible);
            }
        }

        boolean showStorageRow2 = true;
        for (var slot : this.getSlots(STORAGE_ROW_2)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(showStorageRow2);
                appEngSlot.setSlotEnabled(showStorageRow2);
            }
        }
    }

    private ItemStack getProviderStack() {
        var slots = this.getSlots(PROVIDER_INPUT);
        return slots.isEmpty() ? ItemStack.EMPTY : slots.get(0).getItem();
    }

    private boolean tryMoveSneakPriority(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        int initialCount = stack.getCount();

        if (AdaptivePatternProviderBlockEntity.isSupportedProviderStack(stack)) {
            moveIntoSemanticSlots(PROVIDER_INPUT, stack);
        }

        if (!stack.isEmpty() && isPatternLike(stack)) {
            moveIntoSemanticSlots(PAGE_PATTERN, stack);
        }

        return stack.getCount() < initialCount;
    }

    private boolean isPatternLike(ItemStack stack) {
        return PatternDetailsHelper.isEncodedPattern(stack)
                || AdaptivePatternProviderBlockEntity.isAe2LightningTechOverloadPatternStack(stack);
    }

    private void moveIntoSemanticSlots(SlotSemantic semantic, ItemStack stack) {
        for (var slot : this.getSlots(semantic)) {
            if (stack.isEmpty()) {
                return;
            }
            if (!slot.mayPlace(stack)) {
                continue;
            }

            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }

            int maxStackSize = Math.min(slot.getMaxStackSize(stack), stack.getMaxStackSize());
            int existingCount = existing.getCount();
            if (existingCount >= maxStackSize) {
                continue;
            }

            int toMove = Math.min(stack.getCount(), maxStackSize - existingCount);
            if (toMove <= 0) {
                continue;
            }

            ItemStack movedStack = stack.copyWithCount(existingCount + toMove);
            slot.set(movedStack);
            stack.shrink(toMove);
            slot.setChanged();
        }
    }

    private void syncAe2LtStateFromHost() {
        if (this.host == null) {
            return;
        }

        this.ae2ltProviderMode = this.host.getAe2LtProviderMode().ordinal();
        this.ae2ltReturnMode = this.host.getAe2LtReturnMode().ordinal();
        this.ae2ltWirelessDispatchMode = this.host.getAe2LtWirelessDispatchMode().ordinal();
        this.ae2ltWirelessSpeedMode = this.host.getAe2LtWirelessSpeedMode().ordinal();
    }

    private void addPatternPageSlots() {
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            this.addSlot(new PagedPatternSlot(i), PAGE_PATTERN);
        }
    }

    private void addExpandedReturnSlots() {
        if (this.logic == null) {
            return;
        }

        var returnInv = this.logic.getReturnInv().createMenuWrapper();
        for (int i = DEFAULT_RETURN_SLOTS; i < Math.min(EXPANDED_RETURN_SLOTS, returnInv.size()); i++) {
            this.addSlot(new AppEngSlot(returnInv, i), STORAGE_ROW_2);
        }
    }

    private final class PagedPatternInventory implements InternalInventory {
        private final InternalInventory backing;
        private final int slotOnPage;

        private PagedPatternInventory(InternalInventory backing, int slotOnPage) {
            this.backing = backing;
            this.slotOnPage = slotOnPage;
        }

        private int getBackingIndex() {
            return AdaptivePatternProviderMenu.this.pageIndex * SLOTS_PER_PAGE + this.slotOnPage;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public int getSlotLimit(int slot) {
            int backingIndex = getBackingIndex();
            return backingIndex < this.backing.size() ? this.backing.getSlotLimit(backingIndex) : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int backingIndex = getBackingIndex();
            return backingIndex < this.backing.size() ? this.backing.getStackInSlot(backingIndex) : ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            int backingIndex = getBackingIndex();
            if (backingIndex < this.backing.size()) {
                this.backing.setItemDirect(backingIndex, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int backingIndex = getBackingIndex();
            return backingIndex < this.backing.size()
                    && (this.backing.isItemValid(backingIndex, stack)
                    || AdaptivePatternProviderMenu.this.shouldAllowLightningTechOverloadPattern(stack));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int backingIndex = getBackingIndex();
            return backingIndex < this.backing.size()
                    ? this.backing.extractItem(backingIndex, amount, simulate)
                    : ItemStack.EMPTY;
        }
    }

    private final class PagedPatternSlot extends RestrictedInputSlot {
        private final int slotOnPage;

        private PagedPatternSlot(int slotOnPage) {
            super(PlacableItemType.PROVIDER_PATTERN,
                    new PagedPatternInventory(
                            AdaptivePatternProviderMenu.this.logic != null
                                    ? AdaptivePatternProviderMenu.this.logic.getPatternInv()
                                    : new AppEngInternalInventory(SLOTS_PER_PAGE),
                            slotOnPage
                    ),
                    0
            );
            this.slotOnPage = slotOnPage;
            this.setIcon(null);
        }

        private int getBackingIndex() {
            return AdaptivePatternProviderMenu.this.pageIndex * SLOTS_PER_PAGE + this.slotOnPage;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (getBackingIndex() >= AdaptivePatternProviderMenu.this.visiblePatternSlots) {
                return false;
            }

            return AdaptivePatternProviderMenu.this.shouldAllowLightningTechOverloadPattern(stack) || super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
            return getBackingIndex() < AdaptivePatternProviderMenu.this.visiblePatternSlots && super.mayPickup(player);
        }
    }

    private boolean shouldAllowLightningTechOverloadPattern(ItemStack stack) {
        return AdaptivePatternProviderBlockEntity.isAe2LightningTechOverloadPatternStack(stack)
                && this.host != null
                && this.host.isAe2LightningTechOverloadedProviderSelected();
    }

    private static final class ProviderSuffixSlot extends RestrictedInputSlot {
        private final AdaptivePatternProviderBlockEntity host;

        private ProviderSuffixSlot(appeng.api.inventories.InternalInventory inv, int slot, AdaptivePatternProviderBlockEntity host) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, slot);
            this.host = host;
            this.setStackLimit(host != null ? host.getProviderSlotLimit() : 4);
            this.setIcon(Icon.PLACEMENT_BLOCK);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.host != null ? this.host.getProviderSlotLimit() : super.getMaxStackSize(stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return AdaptivePatternProviderBlockEntity.isSupportedProviderStack(stack) && super.mayPlace(stack);
        }
    }
}
