package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.integration.Ae2LtPackagedRuntimeBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntSupplier;

public final class AdaptivePatternProviderState {

    private static final String PROVIDER_SLOT_TAG = "provider_slot";
    private static final String AE2LTPP_ADAPTER_SLOT_TAG = "ae2ltpp_adapter_slot";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String ADVANCED_AE_FILTERED_IMPORT_TAG = "advanced_ae_filtered_import";
    private static final String RESONATING_PULL_ENABLED_TAG = "resonating_pull_enabled";
    private static final String AE2LT_PROVIDER_MODE_TAG = "ae2lt_provider_mode";
    private static final String AE2LT_RETURN_MODE_TAG = "ae2lt_return_mode";
    private static final String AE2LT_WIRELESS_DISPATCH_MODE_TAG = "ae2lt_wireless_dispatch_mode";
    private static final String AE2LT_WIRELESS_SPEED_MODE_TAG = "ae2lt_wireless_speed_mode";
    private static final String AE2LT_CONNECTIONS_TAG = "ae2lt_wireless_connections";

    public static final int PROVIDER_SLOT_LIMIT = 4;
    public static final int EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD = 4;
    public static final int BASE_UPGRADE_SLOTS = 6;
    private static final int MAX_NETWORK_SAFE_MENU_SLOTS = Short.MAX_VALUE + 1;
    // Keep the large backing inventory headroom so future provider variants can scale up without another migration.
    // The adaptive menu now pages against proxy slots instead of registering one GUI slot for every backing slot.
    private static final int FIXED_MENU_SLOT_OVERHEAD = 36 + 18 + 2 + 36 + (BASE_UPGRADE_SLOTS * 2) + 3;
    private static final int MENU_SLOT_SAFETY_MARGIN = 64;
    public static final int MAX_PATTERN_SLOTS = MAX_NETWORK_SAFE_MENU_SLOTS - FIXED_MENU_SLOT_OVERHEAD - MENU_SLOT_SAFETY_MARGIN;

    private final AppEngInternalInventory providerInventory;
    private final AppEngInternalInventory ae2LtPackagedAdapterInventory;
    private final IntSupplier providerSlotLimit;
    private final List<AdaptiveWirelessConnection> ae2LtConnections = new ArrayList<>();
    private boolean advancedAeFilteredImport;
    private boolean resonatingPullEnabled;
    private AdaptivePatternProviderModes.Ae2LtProviderMode ae2LtProviderMode = AdaptivePatternProviderModes.Ae2LtProviderMode.NORMAL;
    private AdaptivePatternProviderModes.Ae2LtReturnMode ae2LtReturnMode = AdaptivePatternProviderModes.Ae2LtReturnMode.OFF;
    private AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode ae2LtWirelessDispatchMode = AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION;
    private AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode ae2LtWirelessSpeedMode = AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.NORMAL;

    public AdaptivePatternProviderState(InternalInventoryHost inventoryHost, IntSupplier providerSlotLimit) {
        this.providerSlotLimit = providerSlotLimit;
        this.providerInventory = new AppEngInternalInventory(inventoryHost, 1);
        this.ae2LtPackagedAdapterInventory = new AppEngInternalInventory(inventoryHost, 1);
        refreshProviderSlotLimit();
        this.providerInventory.setFilter(new ProviderFilter());
        this.ae2LtPackagedAdapterInventory.setFilter(new Ae2LtPackagedAdapterFilter(
                inventoryHost instanceof AdaptivePatternProviderHost adaptiveHost ? adaptiveHost : null));
        this.ae2LtPackagedAdapterInventory.setMaxStackSize(0, 1);
    }

    public AppEngInternalInventory getProviderInventory() {
        return this.providerInventory;
    }

    public AppEngInternalInventory getAe2LtPackagedAdapterInventory() {
        return this.ae2LtPackagedAdapterInventory;
    }

    public ItemStack getAe2LtPackagedAdapterStack() {
        return this.ae2LtPackagedAdapterInventory.getStackInSlot(0);
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

    public AdaptivePatternProviderModes.Ae2LtProviderMode getAe2LtProviderMode() {
        return this.ae2LtProviderMode;
    }

    public void cycleAe2LtProviderMode() {
        this.ae2LtProviderMode = this.ae2LtProviderMode.next();
    }

    public boolean isAe2LtWirelessMode() {
        return this.ae2LtProviderMode == AdaptivePatternProviderModes.Ae2LtProviderMode.WIRELESS;
    }

    public AdaptivePatternProviderModes.Ae2LtReturnMode getAe2LtReturnMode() {
        return this.ae2LtReturnMode;
    }

    public void cycleAe2LtReturnMode() {
        this.ae2LtReturnMode = this.ae2LtReturnMode.next();
    }

    public AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode getAe2LtWirelessDispatchMode() {
        return this.ae2LtWirelessDispatchMode;
    }

    public void cycleAe2LtWirelessDispatchMode() {
        this.ae2LtWirelessDispatchMode = this.ae2LtWirelessDispatchMode.next();
    }

    public AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode getAe2LtWirelessSpeedMode() {
        return this.ae2LtWirelessSpeedMode;
    }

    public void cycleAe2LtWirelessSpeedMode() {
        this.ae2LtWirelessSpeedMode = this.ae2LtWirelessSpeedMode.next();
    }

    public void addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {
        for (int i = 0; i < this.ae2LtConnections.size(); i++) {
            var connection = this.ae2LtConnections.get(i);
            if (connection.sameTarget(dimension, pos)) {
                this.ae2LtConnections.set(i, new AdaptiveWirelessConnection(dimension, pos, boundFace));
                return;
            }
        }

        this.ae2LtConnections.add(new AdaptiveWirelessConnection(dimension, pos, boundFace));
    }

    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        Iterator<AdaptiveWirelessConnection> iterator = this.ae2LtConnections.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().sameTarget(dimension, pos)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public List<AdaptiveWirelessConnection> getConnections() {
        return Collections.unmodifiableList(this.ae2LtConnections);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries, IUpgradeInventory upgrades) {
        this.providerInventory.writeToNBT(data, PROVIDER_SLOT_TAG, registries);
        this.ae2LtPackagedAdapterInventory.writeToNBT(data, AE2LTPP_ADAPTER_SLOT_TAG, registries);
        upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG, this.advancedAeFilteredImport);
        data.putBoolean(RESONATING_PULL_ENABLED_TAG, this.resonatingPullEnabled);
        data.putString(AE2LT_PROVIDER_MODE_TAG, this.ae2LtProviderMode.name());
        data.putString(AE2LT_RETURN_MODE_TAG, this.ae2LtReturnMode.name());
        data.putString(AE2LT_WIRELESS_DISPATCH_MODE_TAG, this.ae2LtWirelessDispatchMode.name());
        data.putString(AE2LT_WIRELESS_SPEED_MODE_TAG, this.ae2LtWirelessSpeedMode.name());
        ListTag connectionList = new ListTag();
        for (var connection : this.ae2LtConnections) {
            connectionList.add(connection.toTag());
        }
        data.put(AE2LT_CONNECTIONS_TAG, connectionList);
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries, IUpgradeInventory upgrades) {
        this.providerInventory.readFromNBT(data, PROVIDER_SLOT_TAG, registries);
        this.ae2LtPackagedAdapterInventory.readFromNBT(data, AE2LTPP_ADAPTER_SLOT_TAG, registries);
        upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        refreshProviderSlotLimit();
        this.advancedAeFilteredImport = data.getBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG);
        this.resonatingPullEnabled = data.getBoolean(RESONATING_PULL_ENABLED_TAG);
        this.ae2LtProviderMode = readEnum(data, AE2LT_PROVIDER_MODE_TAG,
                AdaptivePatternProviderModes.Ae2LtProviderMode.NORMAL,
                AdaptivePatternProviderModes.Ae2LtProviderMode.class);
        this.ae2LtReturnMode = readEnum(data, AE2LT_RETURN_MODE_TAG,
                AdaptivePatternProviderModes.Ae2LtReturnMode.OFF,
                AdaptivePatternProviderModes.Ae2LtReturnMode.class);
        this.ae2LtWirelessDispatchMode = readEnum(data, AE2LT_WIRELESS_DISPATCH_MODE_TAG,
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION,
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.class);
        this.ae2LtWirelessSpeedMode = readEnum(data, AE2LT_WIRELESS_SPEED_MODE_TAG,
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.NORMAL,
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.class);
        this.ae2LtConnections.clear();
        ListTag connectionList = data.getList(AE2LT_CONNECTIONS_TAG, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < connectionList.size(); i++) {
            this.ae2LtConnections.add(AdaptiveWirelessConnection.fromTag(connectionList.getCompound(i)));
        }
    }

    public CompoundTag writeMemoryCardSettings() {
        CompoundTag data = new CompoundTag();
        data.putBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG, this.advancedAeFilteredImport);
        data.putBoolean(RESONATING_PULL_ENABLED_TAG, this.resonatingPullEnabled);
        data.putString(AE2LT_PROVIDER_MODE_TAG, this.ae2LtProviderMode.name());
        data.putString(AE2LT_RETURN_MODE_TAG, this.ae2LtReturnMode.name());
        data.putString(AE2LT_WIRELESS_DISPATCH_MODE_TAG, this.ae2LtWirelessDispatchMode.name());
        data.putString(AE2LT_WIRELESS_SPEED_MODE_TAG, this.ae2LtWirelessSpeedMode.name());
        ListTag connectionList = new ListTag();
        for (var connection : this.ae2LtConnections) {
            connectionList.add(connection.toTag());
        }
        data.put(AE2LT_CONNECTIONS_TAG, connectionList);
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

        AdaptivePatternProviderModes.Ae2LtProviderMode ae2LtProviderMode = readEnum(data, AE2LT_PROVIDER_MODE_TAG,
                this.ae2LtProviderMode,
                AdaptivePatternProviderModes.Ae2LtProviderMode.class);
        if (this.ae2LtProviderMode != ae2LtProviderMode) {
            this.ae2LtProviderMode = ae2LtProviderMode;
            changed = true;
        }

        AdaptivePatternProviderModes.Ae2LtReturnMode ae2LtReturnMode = readEnum(data, AE2LT_RETURN_MODE_TAG,
                this.ae2LtReturnMode,
                AdaptivePatternProviderModes.Ae2LtReturnMode.class);
        if (this.ae2LtReturnMode != ae2LtReturnMode) {
            this.ae2LtReturnMode = ae2LtReturnMode;
            changed = true;
        }

        AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode ae2LtWirelessDispatchMode = readEnum(data, AE2LT_WIRELESS_DISPATCH_MODE_TAG,
                this.ae2LtWirelessDispatchMode,
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.class);
        if (this.ae2LtWirelessDispatchMode != ae2LtWirelessDispatchMode) {
            this.ae2LtWirelessDispatchMode = ae2LtWirelessDispatchMode;
            changed = true;
        }

        AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode ae2LtWirelessSpeedMode = readEnum(data, AE2LT_WIRELESS_SPEED_MODE_TAG,
                this.ae2LtWirelessSpeedMode,
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.class);
        if (this.ae2LtWirelessSpeedMode != ae2LtWirelessSpeedMode) {
            this.ae2LtWirelessSpeedMode = ae2LtWirelessSpeedMode;
            changed = true;
        }

        if (data.contains(AE2LT_CONNECTIONS_TAG)) {
            ListTag connectionList = data.getList(AE2LT_CONNECTIONS_TAG, CompoundTag.TAG_COMPOUND);
            List<AdaptiveWirelessConnection> incomingConnections = new ArrayList<>(connectionList.size());
            for (int i = 0; i < connectionList.size(); i++) {
                incomingConnections.add(AdaptiveWirelessConnection.fromTag(connectionList.getCompound(i)));
            }
            if (!this.ae2LtConnections.equals(incomingConnections)) {
                this.ae2LtConnections.clear();
                this.ae2LtConnections.addAll(incomingConnections);
                changed = true;
            }
        }

        return changed;
    }

    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeNbt(getProviderStack().saveOptional(data.registryAccess()));
        data.writeNbt(getAe2LtPackagedAdapterStack().saveOptional(data.registryAccess()));
        data.writeBoolean(this.advancedAeFilteredImport);
        data.writeBoolean(this.resonatingPullEnabled);
        data.writeVarInt(this.ae2LtProviderMode.ordinal());
        data.writeVarInt(this.ae2LtReturnMode.ordinal());
        data.writeVarInt(this.ae2LtWirelessDispatchMode.ordinal());
        data.writeVarInt(this.ae2LtWirelessSpeedMode.ordinal());
        data.writeVarInt(this.ae2LtConnections.size());
        for (var connection : this.ae2LtConnections) {
            data.writeResourceLocation(connection.dimension().location());
            data.writeBlockPos(connection.pos());
            data.writeEnum(connection.boundFace());
        }
    }

    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = false;

        CompoundTag providerStackTag = data.readNbt();
        ItemStack providerStack = providerStackTag == null ? ItemStack.EMPTY : ItemStack.parseOptional(data.registryAccess(), providerStackTag);
        if (!ItemStack.matches(getProviderStack(), providerStack)) {
            this.providerInventory.setItemDirect(0, providerStack);
            changed = true;
        }

        CompoundTag adapterStackTag = data.readNbt();
        ItemStack adapterStack = adapterStackTag == null ? ItemStack.EMPTY : ItemStack.parseOptional(data.registryAccess(), adapterStackTag);
        if (!ItemStack.matches(getAe2LtPackagedAdapterStack(), adapterStack)) {
            this.ae2LtPackagedAdapterInventory.setItemDirect(0, adapterStack);
            changed = true;
        }

        boolean advancedAeFilteredImport = data.readBoolean();
        if (this.advancedAeFilteredImport != advancedAeFilteredImport) {
            this.advancedAeFilteredImport = advancedAeFilteredImport;
            changed = true;
        }

        boolean resonatingPullEnabled = data.readBoolean();
        if (this.resonatingPullEnabled != resonatingPullEnabled) {
            this.resonatingPullEnabled = resonatingPullEnabled;
            changed = true;
        }

        var ae2LtProviderMode = AdaptivePatternProviderModes.Ae2LtProviderMode.values()[data.readVarInt()];
        if (this.ae2LtProviderMode != ae2LtProviderMode) {
            this.ae2LtProviderMode = ae2LtProviderMode;
            changed = true;
        }

        var ae2LtReturnMode = AdaptivePatternProviderModes.Ae2LtReturnMode.values()[data.readVarInt()];
        if (this.ae2LtReturnMode != ae2LtReturnMode) {
            this.ae2LtReturnMode = ae2LtReturnMode;
            changed = true;
        }

        var ae2LtWirelessDispatchMode = AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.values()[data.readVarInt()];
        if (this.ae2LtWirelessDispatchMode != ae2LtWirelessDispatchMode) {
            this.ae2LtWirelessDispatchMode = ae2LtWirelessDispatchMode;
            changed = true;
        }

        var ae2LtWirelessSpeedMode = AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.values()[data.readVarInt()];
        if (this.ae2LtWirelessSpeedMode != ae2LtWirelessSpeedMode) {
            this.ae2LtWirelessSpeedMode = ae2LtWirelessSpeedMode;
            changed = true;
        }

        int connectionCount = data.readVarInt();
        List<AdaptiveWirelessConnection> incomingConnections = new ArrayList<>(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            var dimension = ResourceKey.create(Registries.DIMENSION, data.readResourceLocation());
            var pos = data.readBlockPos();
            var face = data.readEnum(Direction.class);
            incomingConnections.add(new AdaptiveWirelessConnection(dimension, pos, face));
        }
        if (!this.ae2LtConnections.equals(incomingConnections)) {
            this.ae2LtConnections.clear();
            this.ae2LtConnections.addAll(incomingConnections);
            changed = true;
        }

        refreshProviderSlotLimit();
        return changed;
    }

    public void clearContent() {
        this.providerInventory.clear();
        this.ae2LtPackagedAdapterInventory.clear();
        this.ae2LtConnections.clear();
    }

    private static <E extends Enum<E>> E readEnum(CompoundTag data, String key, E fallback, Class<E> enumClass) {
        if (!data.contains(key)) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumClass, data.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static final class ProviderFilter implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return AdaptivePatternProviderResolver.isSupportedProviderStack(stack);
        }
    }

    private static final class Ae2LtPackagedAdapterFilter implements IAEItemFilter {

        private final @Nullable AdaptivePatternProviderHost host;

        private Ae2LtPackagedAdapterFilter(@Nullable AdaptivePatternProviderHost host) {
            this.host = host;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (!Ae2LtPackagedRuntimeBridge.isAdapterItem(stack)) {
                return false;
            }
            return this.host == null || this.host.isAe2LtPackagedAdapterValid(stack);
        }
    }
}
