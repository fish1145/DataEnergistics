package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;

import java.util.function.IntSupplier;

public final class AdaptivePatternProviderState {

    private static final String PROVIDER_SLOT_TAG = "provider_slot";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String ADVANCED_AE_FILTERED_IMPORT_TAG = "advanced_ae_filtered_import";
    private static final String RESONATING_PULL_ENABLED_TAG = "resonating_pull_enabled";

    public static final int PROVIDER_SLOT_LIMIT = 4;
    public static final int EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD = 4;
    public static final int BASE_UPGRADE_SLOTS = 6;
    private static final int MAX_NETWORK_SAFE_MENU_SLOTS = Short.MAX_VALUE + 1;
    private static final int FIXED_MENU_SLOT_OVERHEAD = 36 + 18 + 2 + 36 + (BASE_UPGRADE_SLOTS * 2) + 3;
    private static final int MENU_SLOT_SAFETY_MARGIN = 64;
    public static final int MAX_PATTERN_SLOTS = MAX_NETWORK_SAFE_MENU_SLOTS - FIXED_MENU_SLOT_OVERHEAD - MENU_SLOT_SAFETY_MARGIN;

    private final AppEngInternalInventory providerInventory;
    private final IntSupplier providerSlotLimit;
    private boolean advancedAeFilteredImport;
    private boolean resonatingPullEnabled;

    public AdaptivePatternProviderState(InternalInventoryHost inventoryHost, IntSupplier providerSlotLimit) {
        this.providerSlotLimit = providerSlotLimit;
        this.providerInventory = new AppEngInternalInventory(inventoryHost, 1);
        refreshProviderSlotLimit();
        this.providerInventory.setFilter(new ProviderFilter());
    }

    public AppEngInternalInventory getProviderInventory() {
        return this.providerInventory;
    }

    public ItemStack getProviderStack() {
        return this.providerInventory.getStackInSlot(0);
    }

    public void refreshProviderSlotLimit() {
        this.providerInventory.setMaxStackSize(0, this.providerSlotLimit.getAsInt());
    }

    public ItemStack extractProviderOverflow() {
        refreshProviderSlotLimit();
        ItemStack providerStack = getProviderStack();
        if (providerStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int providerLimit = this.providerSlotLimit.getAsInt();
        if (providerStack.getCount() <= providerLimit) {
            return ItemStack.EMPTY;
        }

        int overflowCount = providerStack.getCount() - providerLimit;
        ItemStack keptStack = providerStack.copyWithCount(providerLimit);
        ItemStack overflowStack = providerStack.copyWithCount(overflowCount);
        this.providerInventory.setItemDirect(0, keptStack);
        return overflowStack;
    }

    public boolean isAdvancedAeFilteredImportEnabled() {
        return this.advancedAeFilteredImport;
    }

    public boolean setAdvancedAeFilteredImportEnabled(boolean enabled) {
        if (this.advancedAeFilteredImport == enabled) {
            return false;
        }

        this.advancedAeFilteredImport = enabled;
        return true;
    }

    public boolean isResonatingPullEnabled() {
        return this.resonatingPullEnabled;
    }

    public boolean setResonatingPullEnabled(boolean enabled) {
        if (this.resonatingPullEnabled == enabled) {
            return false;
        }

        this.resonatingPullEnabled = enabled;
        return true;
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries, IUpgradeInventory upgrades) {
        this.providerInventory.writeToNBT(data, PROVIDER_SLOT_TAG, registries);
        upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG, this.advancedAeFilteredImport);
        data.putBoolean(RESONATING_PULL_ENABLED_TAG, this.resonatingPullEnabled);
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries, IUpgradeInventory upgrades) {
        this.providerInventory.readFromNBT(data, PROVIDER_SLOT_TAG, registries);
        upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        refreshProviderSlotLimit();
        this.advancedAeFilteredImport = data.getBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG);
        this.resonatingPullEnabled = data.getBoolean(RESONATING_PULL_ENABLED_TAG);
    }

    public CompoundTag writeMemoryCardSettings() {
        CompoundTag data = new CompoundTag();
        data.putBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG, this.advancedAeFilteredImport);
        data.putBoolean(RESONATING_PULL_ENABLED_TAG, this.resonatingPullEnabled);
        return data;
    }

    public boolean readMemoryCardSettings(CompoundTag data) {
        boolean changed = false;

        if (data.contains(ADVANCED_AE_FILTERED_IMPORT_TAG)) {
            boolean advancedAeFilteredImport = data.getBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG);
            if (this.advancedAeFilteredImport != advancedAeFilteredImport) {
                this.advancedAeFilteredImport = advancedAeFilteredImport;
                changed = true;
            }
        }
        if (data.contains(RESONATING_PULL_ENABLED_TAG)) {
            boolean resonatingPullEnabled = data.getBoolean(RESONATING_PULL_ENABLED_TAG);
            if (this.resonatingPullEnabled != resonatingPullEnabled) {
                this.resonatingPullEnabled = resonatingPullEnabled;
                changed = true;
            }
        }

        return changed;
    }

    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeNbt(getProviderStack().saveOptional(data.registryAccess()));
        data.writeBoolean(this.advancedAeFilteredImport);
        data.writeBoolean(this.resonatingPullEnabled);
    }

    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        CompoundTag providerStackTag = data.readNbt();
        ItemStack providerStack = providerStackTag == null
                ? ItemStack.EMPTY
                : ItemStack.parseOptional(data.registryAccess(), providerStackTag);
        boolean advancedAeFilteredImport = data.readBoolean();
        boolean resonatingPullEnabled = data.readBoolean();

        boolean changed = false;
        if (!ItemStack.matches(getProviderStack(), providerStack)) {
            this.providerInventory.setItemDirect(0, providerStack);
            changed = true;
        }

        if (this.advancedAeFilteredImport != advancedAeFilteredImport) {
            this.advancedAeFilteredImport = advancedAeFilteredImport;
            changed = true;
        }

        if (this.resonatingPullEnabled != resonatingPullEnabled) {
            this.resonatingPullEnabled = resonatingPullEnabled;
            changed = true;
        }

        refreshProviderSlotLimit();
        return changed;
    }

    public void clearContent() {
        this.providerInventory.clear();
    }

    private static final class ProviderFilter implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return AdaptivePatternProviderResolver.isSupportedProviderStack(stack);
        }
    }
}
