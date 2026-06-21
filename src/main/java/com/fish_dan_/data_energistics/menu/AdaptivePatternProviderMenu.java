package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.accessor.PatternProviderMenuAccessor;
import com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderModes;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.IConfigurableObject;
import appeng.core.localization.Tooltips;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot.PlacableItemType;
import appeng.util.ConfigMenuInventory;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.shorts.ShortSet;

public class AdaptivePatternProviderMenu extends AEBaseMenu implements PatternProviderMenuAccessor {

    private static final String ACTION_SET_PAGE = "set_page";
    private static final String ACTION_SET_FILTERED_IMPORT = "set_filtered_import";
    private static final String ACTION_SET_RESONATING_PULL = "set_resonating_pull";
    private static final String ACTION_SET_REDSTONE_TUNING_MODE = "set_redstone_tuning_mode";
    private static final String ACTION_TOGGLE_AE2LT_MODE = "toggle_ae2lt_mode";
    private static final String ACTION_TOGGLE_AE2LT_RETURN_MODE = "toggle_ae2lt_return_mode";
    private static final String ACTION_TOGGLE_AE2LT_WIRELESS_DISPATCH = "toggle_ae2lt_wireless_dispatch";
    private static final String ACTION_TOGGLE_AE2LT_WIRELESS_SPEED = "toggle_ae2lt_wireless_speed";
    private static final int SLOTS_PER_PAGE = 36;
    private static final int DEFAULT_RETURN_SLOTS = 9;
    private static final int EXPANDED_RETURN_SLOTS = 18;

    public static final SlotSemantic PROVIDER_INPUT = SlotSemantics.register("ADAPTIVE_PATTERN_PROVIDER_PROVIDER", false);
    public static final SlotSemantic AE2LTPP_ADAPTER = SlotSemantics.register("ADAPTIVE_PATTERN_PROVIDER_AE2LTPP_ADAPTER", false);
    public static final SlotSemantic PAGE_PATTERN = SlotSemantics.register("ADAPTIVE_PATTERN_PROVIDER_PAGE_PATTERN", false);
    public static final SlotSemantic STORAGE_ROW_2 = SlotSemantics.register("ADAPTIVE_PATTERN_PROVIDER_STORAGE_ROW_2", false);

    private final AdaptivePatternProviderHost host;
    private final PatternProviderLogic logic;
    private final ToolboxMenu toolbox;

    @GuiSync(3)
    public YesNo blockingMode = YesNo.NO;
    @GuiSync(4)
    public YesNo showInAccessTerminal = YesNo.YES;
    @GuiSync(5)
    public LockCraftingMode lockCraftingMode = LockCraftingMode.NONE;
    @GuiSync(6)
    public LockCraftingMode craftingLockedReason = LockCraftingMode.NONE;
    @GuiSync(7)
    public GenericStack unlockStack;
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
    @GuiSync(788)
    public boolean resonatingProviderSelected;
    @GuiSync(789)
    public boolean resonatingPullEnabled;
    @GuiSync(790)
    public boolean ae2LtPackagedProviderSelected;
    @GuiSync(791)
    public boolean ae2LtPackagedWirelessProviderSelected;
    @GuiSync(792)
    public boolean hasRedstoneTuningCard;
    @GuiSync(793)
    public int redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH.ordinal();

    public AdaptivePatternProviderMenu(int id, Inventory playerInventory, AdaptivePatternProviderHost host) {
        super(ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), id, playerInventory, host);
        this.host = host;
        this.logic = host != null ? host.getLogic() : null;
        this.toolbox = new ToolboxMenu(this);

        registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);
        registerClientAction(ACTION_SET_FILTERED_IMPORT, Boolean.class, this::setAdvancedAeFilteredImport);
        registerClientAction(ACTION_SET_RESONATING_PULL, Boolean.class, this::setResonatingPullEnabled);
        registerClientAction(ACTION_SET_REDSTONE_TUNING_MODE, Integer.class, this::applyRedstoneTuningMode);
        registerClientAction(ACTION_TOGGLE_AE2LT_MODE, this::toggleAe2LtMode);
        registerClientAction(ACTION_TOGGLE_AE2LT_RETURN_MODE, this::toggleAe2LtReturnMode);
        registerClientAction(ACTION_TOGGLE_AE2LT_WIRELESS_DISPATCH, this::toggleAe2LtWirelessDispatchMode);
        registerClientAction(ACTION_TOGGLE_AE2LT_WIRELESS_SPEED, this::toggleAe2LtWirelessSpeedMode);

        addUpgradeSlots();
        addPatternPageSlots();
        addReturnSlots();
        addProviderSlot();
        addAe2LtPackagedAdapterSlot();
        this.createPlayerInventorySlots(playerInventory);

        refreshPatternPagination();
        loadSettingsFromHost();
        updatePatternSlotVisibility();
    }

    @Override
    public void onSlotChange(Slot slot) {
        super.onSlotChange(slot);
        if (slot == null) {
            return;
        }

        var semantic = this.getSlotSemantic(slot);
        if (semantic == PROVIDER_INPUT) {
            returnOverflowPatternsToPlayer();
            returnHiddenAe2LtPackagedAdapterToPlayer();
            refreshPatternPagination();
            updatePatternSlotVisibility();
        } else if (semantic == SlotSemantics.UPGRADE) {
            returnOverflowProvidersToPlayer();
            returnOverflowPatternsToPlayer();
            refreshPatternPagination();
            updatePatternSlotVisibility();
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            loadSettingsFromHost();
            refreshPatternPagination();
        }

        this.toolbox.tick();
        super.broadcastChanges();
        updatePatternSlotVisibility();
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        updatePatternSlotVisibility();
    }

    @Override
    protected boolean canSlotsBeHidden(SlotSemantic semantic) {
        return semantic == SlotSemantics.ENCODED_PATTERN;
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

        if (semantic == SlotSemantics.UPGRADE && !this.isPlayerSideSlot(slot) && Upgrades.isUpgradeCardItem(slot.getItem())) {
            moveUpgradeCardIntoToolbox(slot);
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }

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

    private void moveUpgradeCardIntoToolbox(Slot slot) {
        ItemStack original = slot.getItem();
        if (original.isEmpty()) {
            return;
        }

        ItemStack working = original.copy();
        moveIntoSemanticSlots(SlotSemantics.TOOLBOX, working);
        int moved = original.getCount() - working.getCount();
        if (moved <= 0) {
            return;
        }

        original.shrink(moved);
        slot.setChanged();
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        }
    }

    public void sendSetPage(int pageIndex) {
        sendClientAction(ACTION_SET_PAGE, pageIndex);
    }

    public void sendSetAdvancedAeFilteredImport(boolean enabled) {
        this.advancedAeFilteredImport = enabled;
        sendClientAction(ACTION_SET_FILTERED_IMPORT, enabled);
    }

    public void sendSetResonatingPullEnabled(boolean enabled) {
        this.resonatingPullEnabled = enabled;
        sendClientAction(ACTION_SET_RESONATING_PULL, enabled);
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

    public Component getProviderDisplayName() {
        if (this.host != null) {
            return this.host.getGuiDisplayName();
        }

        ItemStack providerStack = getProviderStack();
        return !providerStack.isEmpty() ? providerStack.getHoverName() : Component.translatable("block.data_energistics.adaptive_pattern_provider");
    }

    public ToolboxMenu getToolbox() {
        return this.toolbox;
    }

    public PatternProviderLogic getLogic() {
        return this.logic;
    }

    public IUpgradeInventory getUpgrades() {
        return this.host != null ? this.host.getUpgrades() : UpgradeInventories.empty();
    }

    public YesNo getBlockingMode() {
        return this.blockingMode;
    }

    public YesNo getShowInAccessTerminal() {
        return this.showInAccessTerminal;
    }

    public LockCraftingMode getLockCraftingMode() {
        return this.lockCraftingMode;
    }

    public LockCraftingMode getCraftingLockedReason() {
        return this.craftingLockedReason;
    }

    public GenericStack getUnlockStack() {
        return this.unlockStack;
    }

    public boolean isAdvancedAeProviderSelected() {
        return this.host != null && this.host.supportsFilteredImportToggle();
    }

    public boolean isAdvancedAeFilteredImportEnabled() {
        return this.advancedAeFilteredImport;
    }

    public boolean isAe2LtOverloadedProviderSelected() {
        return this.host != null && this.host.isAe2LightningTechOverloadedProviderSelected();
    }

    public boolean isAe2LtPackagedProviderSelected() {
        return this.ae2LtPackagedProviderSelected;
    }

    public boolean isAe2LtPackagedWirelessProviderSelected() {
        return this.ae2LtPackagedWirelessProviderSelected;
    }

    public boolean isAe2LtProviderFamilySelected() {
        return isAe2LtOverloadedProviderSelected() || isAe2LtPackagedProviderSelected();
    }

    public boolean isAe2LtModeSwitchVisible() {
        return isAe2LtOverloadedProviderSelected();
    }

    public boolean isAe2LtWirelessControlsVisible() {
        return isAe2LtOverloadedProviderSelected() && isAe2LtWirelessMode() || isAe2LtPackagedWirelessProviderSelected();
    }

    public boolean isResonatingProviderSelected() {
        return this.resonatingProviderSelected;
    }

    public boolean isResonatingPullEnabled() {
        return this.resonatingPullEnabled;
    }

    public boolean isAe2LtWirelessMode() {
        return this.ae2ltProviderMode == AdaptivePatternProviderModes.Ae2LtProviderMode.WIRELESS.ordinal();
    }

    public boolean isAe2LtEvenDistributionMode() {
        return this.ae2ltWirelessDispatchMode == AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION.ordinal();
    }

    public boolean isAe2LtFastSpeedMode() {
        return this.ae2ltWirelessSpeedMode == AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.FAST.ordinal();
    }

    public int getAe2LtReturnModeOrdinal() {
        return this.ae2ltReturnMode;
    }

    @Override
    public boolean dataEnergistics$hasRedstoneTuningCard() {
        return this.hasRedstoneTuningCard;
    }

    @Override
    public int dataEnergistics$getRedstoneTuningMode() {
        return this.redstoneTuningMode;
    }

    @Override
    public void dataEnergistics$setRedstoneTuningMode(int ordinal) {
        RedstoneTuningMode mode = redstoneTuningModeFromOrdinal(ordinal);
        this.redstoneTuningMode = mode.ordinal();
        if (this.isClientSide()) {
            sendClientAction(ACTION_SET_REDSTONE_TUNING_MODE, mode.ordinal());
            return;
        }

        applyRedstoneTuningMode(mode.ordinal());
    }

    private void loadSettingsFromHost() {
        if (this.host instanceof IConfigurableObject configurableObject) {
            var configManager = configurableObject.getConfigManager();
            this.blockingMode = configManager.getSetting(Settings.BLOCKING_MODE);
            this.showInAccessTerminal = configManager.getSetting(Settings.PATTERN_ACCESS_TERMINAL);
            this.lockCraftingMode = configManager.getSetting(Settings.LOCK_CRAFTING_MODE);
        }

        if (this.logic != null) {
            this.craftingLockedReason = this.logic.getCraftingLockedReason();
            this.unlockStack = this.logic.getUnlockStack();
        }
        syncRedstoneTuningFromHost();
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

    private void setResonatingPullEnabled(Boolean enabled) {
        if (enabled == null || this.host == null || !this.host.isResonatingProviderSelected()) {
            return;
        }

        this.host.setResonatingPullEnabled(enabled);
        syncStateFromHost();
        broadcastChanges();
    }

    private void applyRedstoneTuningMode(Integer ordinal) {
        RedstoneTuningAwareHost tuningHost = getRedstoneTuningHost();
        if (tuningHost == null) {
            return;
        }

        RedstoneTuningMode mode = redstoneTuningModeFromOrdinal(ordinal);
        if (tuningHost.dataEnergistics$setRedstoneTuningMode(mode)) {
            this.redstoneTuningMode = mode.ordinal();
            this.hasRedstoneTuningCard = tuningHost.dataEnergistics$hasRedstoneTuningCard();
        }
        broadcastChanges();
    }

    private void toggleAe2LtMode() {
        if (this.host == null || !this.host.isAe2LightningTechOverloadedProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtProviderMode();
        syncStateFromHost();
        broadcastChanges();
    }

    private void toggleAe2LtReturnMode() {
        if (this.host == null || !this.host.isAe2LightningTechOverloadedProviderSelected() && !this.host.isAe2LtPackagedProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtReturnMode();
        syncStateFromHost();
        broadcastChanges();
    }

    private void toggleAe2LtWirelessDispatchMode() {
        if (this.host == null || !this.host.isAe2LtWirelessConnectableProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtWirelessDispatchMode();
        syncStateFromHost();
        broadcastChanges();
    }

    private void toggleAe2LtWirelessSpeedMode() {
        if (this.host == null || !this.host.isAe2LtWirelessConnectableProviderSelected()) {
            return;
        }

        this.host.cycleAe2LtWirelessSpeedMode();
        syncStateFromHost();
        broadcastChanges();
    }

    private void refreshPatternPagination() {
        int slotCount = this.host != null ? this.host.getPatternSlotCountForMenu() : 0;
        this.visiblePatternSlots = slotCount;
        this.advancedAeFilteredImport = this.host != null && this.host.isAdvancedAeFilteredImportEnabled();
        syncStateFromHost();
        this.totalPages = Math.max(1, (slotCount + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        if (slotCount <= 0) {
            this.pageIndex = 0;
        } else if (this.pageIndex >= this.totalPages) {
            this.pageIndex = this.totalPages - 1;
        }
    }

    private void syncStateFromHost() {
        if (this.host == null) {
            return;
        }

        this.resonatingProviderSelected = this.host.isResonatingProviderSelected();
        this.resonatingPullEnabled = this.host.isResonatingPullEnabled();
        this.ae2LtPackagedProviderSelected = this.host.isAe2LtPackagedProviderSelected();
        this.ae2LtPackagedWirelessProviderSelected = this.host.isAe2LtPackagedWirelessProviderSelected();
        this.ae2ltProviderMode = this.host.getAe2LtProviderMode().ordinal();
        this.ae2ltReturnMode = this.host.getAe2LtReturnMode().ordinal();
        this.ae2ltWirelessDispatchMode = this.host.getAe2LtWirelessDispatchMode().ordinal();
        this.ae2ltWirelessSpeedMode = this.host.getAe2LtWirelessSpeedMode().ordinal();
        syncRedstoneTuningFromHost();
    }

    private void syncRedstoneTuningFromHost() {
        RedstoneTuningAwareHost tuningHost = getRedstoneTuningHost();
        if (tuningHost == null) {
            this.hasRedstoneTuningCard = false;
            this.redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH.ordinal();
            return;
        }

        this.hasRedstoneTuningCard = tuningHost.dataEnergistics$hasRedstoneTuningCard();
        this.redstoneTuningMode = tuningHost.dataEnergistics$getRedstoneTuningMode().ordinal();
    }

    private RedstoneTuningAwareHost getRedstoneTuningHost() {
        return this.host instanceof RedstoneTuningAwareHost tuningHost ? tuningHost : null;
    }

    private static RedstoneTuningMode redstoneTuningModeFromOrdinal(Integer ordinal) {
        if (ordinal == null) {
            throw new IllegalArgumentException("Redstone tuning mode ordinal is required");
        }
        RedstoneTuningMode[] values = RedstoneTuningMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid redstone tuning mode ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private void addUpgradeSlots() {
        if (this.host == null) {
            return;
        }

        var upgrades = this.host.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            this.addSlot(new RestrictedInputSlot(PlacableItemType.UPGRADES, upgrades, i), SlotSemantics.UPGRADE);
        }
    }

    private void addPatternPageSlots() {
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            this.addSlot(new PagedPatternSlot(i), PAGE_PATTERN);
        }
    }

    private void addReturnSlots() {
        if (this.logic == null) {
            return;
        }

        ConfigMenuInventory returnInv = this.logic.getReturnInv().createMenuWrapper();
        for (int i = 0; i < Math.min(DEFAULT_RETURN_SLOTS, returnInv.size()); i++) {
            this.addSlot(new AppEngSlot(returnInv, i), SlotSemantics.STORAGE);
        }

        for (int i = DEFAULT_RETURN_SLOTS; i < Math.min(EXPANDED_RETURN_SLOTS, returnInv.size()); i++) {
            this.addSlot(new AppEngSlot(returnInv, i), STORAGE_ROW_2);
        }
    }

    private void addProviderSlot() {
        var providerSlot = new ProviderSuffixSlot(
                this.host != null ? this.host.getProviderInventory() : new AppEngInternalInventory(1),
                0,
                this.host);
        providerSlot.setEmptyTooltip(() -> Tooltips.slotTooltip(
                Component.translatable("tooltip.data_energistics.adaptive_pattern_provider.provider_slot")));
        this.addSlot(providerSlot, PROVIDER_INPUT);
    }

    private void addAe2LtPackagedAdapterSlot() {
        var adapterSlot = new AppEngSlot(
                this.host != null ? this.host.getAe2LtPackagedAdapterInventory() : new AppEngInternalInventory(1),
                0);
        adapterSlot.setIcon(null);
        adapterSlot.setNotDraggable();
        adapterSlot.setEmptyTooltip(() -> Tooltips.slotTooltip(
                Component.translatable("ae2ltpp.gui.adapter_slot")));
        this.addSlot(adapterSlot, AE2LTPP_ADAPTER);
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

    private void returnHiddenAe2LtPackagedAdapterToPlayer() {
        if (this.host == null || this.host.isAe2LtPackagedProviderSelected()) {
            return;
        }

        AppEngInternalInventory adapterInventory = this.host.getAe2LtPackagedAdapterInventory();
        ItemStack adapterStack = adapterInventory.getStackInSlot(0);
        if (adapterStack.isEmpty()) {
            return;
        }

        adapterInventory.setItemDirect(0, ItemStack.EMPTY);
        this.getPlayerInventory().placeItemBackInInventory(adapterStack);
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

        for (var slot : this.getSlots(STORAGE_ROW_2)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(true);
                appEngSlot.setSlotEnabled(true);
            }
        }

        boolean showAdapterSlot = isAe2LtPackagedProviderSelected();
        for (var slot : this.getSlots(AE2LTPP_ADAPTER)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(showAdapterSlot);
                appEngSlot.setSlotEnabled(showAdapterSlot);
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

        if (Upgrades.isUpgradeCardItem(stack)) {
            moveIntoSemanticSlots(SlotSemantics.UPGRADE, stack);
        }

        if (!stack.isEmpty() && AdaptivePatternProviderResolver.isSupportedProviderStack(stack)) {
            moveIntoSemanticSlots(PROVIDER_INPUT, stack);
        }

        if (!stack.isEmpty() && isAe2LtPackagedProviderSelected()) {
            moveIntoSemanticSlots(AE2LTPP_ADAPTER, stack);
        }

        if (!stack.isEmpty() && isPatternLike(stack)) {
            moveIntoSemanticSlots(PAGE_PATTERN, stack);
        }

        return stack.getCount() < initialCount;
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

    private boolean isPatternLike(ItemStack stack) {
        return PatternDetailsHelper.isEncodedPattern(stack) || AdaptivePatternProviderResolver.isAe2LightningTechOverloadPatternStack(stack);
    }

    private boolean shouldAllowLightningTechOverloadPattern(ItemStack stack) {
        return AdaptivePatternProviderResolver.isAe2LightningTechOverloadPatternStack(stack) && this.host != null && this.host.isAe2LightningTechOverloadedProviderSelected();
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
            return backingIndex < this.backing.size() && (this.backing.isItemValid(backingIndex, stack) || AdaptivePatternProviderMenu.this.shouldAllowLightningTechOverloadPattern(stack));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int backingIndex = getBackingIndex();
            return backingIndex < this.backing.size() ? this.backing.extractItem(backingIndex, amount, simulate) : ItemStack.EMPTY;
        }
    }

    private final class PagedPatternSlot extends RestrictedInputSlot {

        private final int slotOnPage;

        private PagedPatternSlot(int slotOnPage) {
            super(
                    PlacableItemType.PROVIDER_PATTERN,
                    new PagedPatternInventory(
                            AdaptivePatternProviderMenu.this.logic != null ? AdaptivePatternProviderMenu.this.logic.getPatternInv() : new AppEngInternalInventory(SLOTS_PER_PAGE),
                            slotOnPage),
                    0);
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
        public boolean mayPickup(Player player) {
            return getBackingIndex() < AdaptivePatternProviderMenu.this.visiblePatternSlots && super.mayPickup(player);
        }
    }

    private static final class ProviderSuffixSlot extends AppEngSlot {

        private final AdaptivePatternProviderHost host;

        private ProviderSuffixSlot(InternalInventory inv, int slot, AdaptivePatternProviderHost host) {
            super(inv, slot);
            this.host = host;
            this.setIcon(null);
        }

        @Override
        public int getMaxStackSize() {
            return this.host != null ? this.host.getProviderSlotLimit() : super.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.host != null ? this.host.getProviderSlotLimit() : super.getMaxStackSize(stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return AdaptivePatternProviderResolver.isSupportedProviderStack(stack) && super.mayPlace(stack);
        }
    }
}
