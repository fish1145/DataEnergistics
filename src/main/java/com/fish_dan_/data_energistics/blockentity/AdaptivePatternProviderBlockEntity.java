package com.fish_dan_.data_energistics.blockentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.AEBlocks;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternContainer;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class AdaptivePatternProviderBlockEntity extends PatternProviderBlockEntity implements InternalInventoryHost, IUpgradeableObject {
    private static final String PROVIDER_SLOT_TAG = "provider_slot";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String ADVANCED_AE_FILTERED_IMPORT_TAG = "advanced_ae_filtered_import";
    private static final String AE2LT_PROVIDER_MODE_TAG = "ae2lt_provider_mode";
    private static final String AE2LT_RETURN_MODE_TAG = "ae2lt_return_mode";
    private static final String AE2LT_WIRELESS_DISPATCH_MODE_TAG = "ae2lt_wireless_dispatch_mode";
    private static final String AE2LT_WIRELESS_SPEED_MODE_TAG = "ae2lt_wireless_speed_mode";
    private static final String AE2LT_CONNECTIONS_TAG = "ae2lt_wireless_connections";
    private static final int PROVIDER_SLOT_LIMIT = 4;
    private static final int EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD = 4;
    private static final int BASE_PATTERN_SLOTS = 9;
    private static final int SIMPLE_PATTERN_SLOTS = 5;
    private static final int EXTENDED_PATTERN_SLOTS = 36;
    private static final int METEORITE_PATTERN_SLOTS = 63;
    private static final int MAX_CAPACITY_CARD_UPGRADES = 3;
    private static final int MAX_PROVIDER_SLOT_LIMIT =
            PROVIDER_SLOT_LIMIT + MAX_CAPACITY_CARD_UPGRADES * EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD;
    private static final int APPFLUX_UPGRADE_SLOTS = 6;
    // Container slot ids are synced over the network as signed shorts. Keep the menu's total
    // slot count below 32768, accounting for player inventory, return slots, provider slot,
    // page proxy slots, and AppFlux upgrade slots.
    private static final int MAX_NETWORK_SAFE_MENU_SLOTS = Short.MAX_VALUE + 1;
    private static final int FIXED_MENU_SLOT_OVERHEAD =
            36  // player inventory + hotbar
                    + 18 // two return rows
                    + 1  // provider sample slot
                    + 36 // visible page proxy slots
                    + APPFLUX_UPGRADE_SLOTS;
    private static final int MAX_PATTERN_SLOTS = MAX_NETWORK_SAFE_MENU_SLOTS - FIXED_MENU_SLOT_OVERHEAD;
    private static final String AE2LT_NAMESPACE = "ae2lt";
    private static final String AE2LT_OVERLOADED_PATTERN_PROVIDER = "overloaded_pattern_provider";
    private static final String AE2LT_OVERLOAD_PATTERN = "overload_pattern";
    private static final ResourceLocation APPFLUX_INDUCTION_CARD_ID =
            ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
    private static final String TERMINAL_GROUP_LOCKED_SUFFIX_KEY = "tooltip.data_energistics.adaptive_pattern_provider.terminal_hidden_slots";
    private static final String TERMINAL_GROUP_NAME_KEY = "screen.data_energistics.adaptive_pattern_provider.terminal_group";

    private final AppEngInternalInventory providerInventory = new AppEngInternalInventory(this, 1);
    private final IUpgradeInventory upgrades = createUpgradeInventory();
    private final List<OverloadedPatternProviderBlockEntity.WirelessConnection> ae2LtConnections = new ArrayList<>();
    private int syncedPatternSlotCount = 0;
    private boolean advancedAeFilteredImport;
    private Ae2LtProviderMode ae2LtProviderMode = Ae2LtProviderMode.NORMAL;
    private Ae2LtReturnMode ae2LtReturnMode = Ae2LtReturnMode.OFF;
    private Ae2LtWirelessDispatchMode ae2LtWirelessDispatchMode = Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION;
    private Ae2LtWirelessSpeedMode ae2LtWirelessSpeedMode = Ae2LtWirelessSpeedMode.NORMAL;

    public AdaptivePatternProviderBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.providerInventory.setMaxStackSize(0, getProviderSlotLimit());
        this.providerInventory.setFilter(new ProviderSuffixFilter());
        this.getMainNode().setVisualRepresentation(ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get());
    }

    @Override
    protected AdaptivePatternProviderLogic createLogic() {
        return new AdaptivePatternProviderLogic(this.getMainNode(), this, MAX_PATTERN_SLOTS);
    }

    @Nullable
    private AdaptivePatternProviderLogic getAdaptiveLogic() {
        var logic = this.getLogic();
        return logic instanceof AdaptivePatternProviderLogic adaptive ? adaptive : null;
    }

    public AppEngInternalInventory getProviderInventory() {
        return this.providerInventory;
    }

    public int getProviderSlotLimit() {
        return PROVIDER_SLOT_LIMIT + getExtraProviderSlotsFromCapacityCards();
    }

    public ItemStack extractProviderOverflow() {
        this.providerInventory.setMaxStackSize(0, getProviderSlotLimit());
        ItemStack providerStack = this.providerInventory.getStackInSlot(0);
        if (providerStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int providerLimit = getProviderSlotLimit();
        if (providerStack.getCount() <= providerLimit) {
            return ItemStack.EMPTY;
        }

        int overflowCount = providerStack.getCount() - providerLimit;
        ItemStack keptStack = providerStack.copyWithCount(providerLimit);
        ItemStack overflowStack = providerStack.copyWithCount(overflowCount);
        this.providerInventory.setItemDirect(0, keptStack);
        return overflowStack;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    public boolean supportsAppliedFluxUpgradeSlot() {
        return this.upgrades.size() > 0;
    }

    public int getPatternSlotCountForMenu() {
        return getConfiguredPatternSlotCount();
    }

    public Component getProviderDisplayName() {
        ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.displayName() : this.getMainMenuIcon().getHoverName();
    }

    public boolean isMeteoriteProviderSelected() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && profile.kind() == ProviderKind.METEORITE;
    }

    public boolean isAdvancedAeProviderSelected() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == ProviderKind.ADVANCED_SMALL || profile.kind() == ProviderKind.ADVANCED_EXTENDED);
    }

    public boolean isAe2LightningTechOverloadedProviderSelected() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && profile.kind() == ProviderKind.AE2LT_OVERLOADED;
    }

    public boolean supportsFilteredImportToggle() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == ProviderKind.ADVANCED_SMALL
                || profile.kind() == ProviderKind.ADVANCED_EXTENDED
                || profile.kind() == ProviderKind.AE2LT_OVERLOADED);
    }

    public Ae2LtProviderMode getAe2LtProviderMode() {
        return this.ae2LtProviderMode;
    }

    public void cycleAe2LtProviderMode() {
        this.ae2LtProviderMode = this.ae2LtProviderMode.next();
        this.onAe2LtStateChanged();
    }

    public boolean isAe2LtWirelessMode() {
        return this.ae2LtProviderMode == Ae2LtProviderMode.WIRELESS;
    }

    public Ae2LtReturnMode getAe2LtReturnMode() {
        return this.ae2LtReturnMode;
    }

    public void cycleAe2LtReturnMode() {
        this.ae2LtReturnMode = this.ae2LtReturnMode.next();
        this.onAe2LtStateChanged();
    }

    public Ae2LtWirelessDispatchMode getAe2LtWirelessDispatchMode() {
        return this.ae2LtWirelessDispatchMode;
    }

    public void cycleAe2LtWirelessDispatchMode() {
        this.ae2LtWirelessDispatchMode = this.ae2LtWirelessDispatchMode.next();
        this.onAe2LtStateChanged();
    }

    public Ae2LtWirelessSpeedMode getAe2LtWirelessSpeedMode() {
        return this.ae2LtWirelessSpeedMode;
    }

    public void cycleAe2LtWirelessSpeedMode() {
        this.ae2LtWirelessSpeedMode = this.ae2LtWirelessSpeedMode.next();
        this.onAe2LtStateChanged();
    }

    public boolean isAdvancedAeFilteredImportEnabled() {
        return this.advancedAeFilteredImport;
    }

    public void setAdvancedAeFilteredImportEnabled(boolean enabled) {
        if (this.advancedAeFilteredImport == enabled) {
            return;
        }

        this.advancedAeFilteredImport = enabled;
        this.saveChanges();
        this.markForClientUpdate();
        AdaptivePatternProviderLogic logic = getAdaptiveLogic();
        if (logic != null) {
            logic.onHostStateChanged();
        }
    }

    public void addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {
        for (int i = 0; i < this.ae2LtConnections.size(); i++) {
            OverloadedPatternProviderBlockEntity.WirelessConnection connection = this.ae2LtConnections.get(i);
            if (connection.sameTarget(dimension, pos)) {
                this.ae2LtConnections.set(i, new OverloadedPatternProviderBlockEntity.WirelessConnection(dimension, pos, boundFace));
                this.onAe2LtStateChanged();
                return;
            }
        }

        this.ae2LtConnections.add(new OverloadedPatternProviderBlockEntity.WirelessConnection(dimension, pos, boundFace));
        this.onAe2LtStateChanged();
    }

    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        Iterator<OverloadedPatternProviderBlockEntity.WirelessConnection> iterator = this.ae2LtConnections.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().sameTarget(dimension, pos)) {
                iterator.remove();
                this.onAe2LtStateChanged();
                return true;
            }
        }

        return false;
    }

    public List<OverloadedPatternProviderBlockEntity.WirelessConnection> getConnections() {
        return Collections.unmodifiableList(this.ae2LtConnections);
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), player, subMenu.getLocator());
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.providerInventory.writeToNBT(data, PROVIDER_SLOT_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG, this.advancedAeFilteredImport);
        data.putString(AE2LT_PROVIDER_MODE_TAG, this.ae2LtProviderMode.name());
        data.putString(AE2LT_RETURN_MODE_TAG, this.ae2LtReturnMode.name());
        data.putString(AE2LT_WIRELESS_DISPATCH_MODE_TAG, this.ae2LtWirelessDispatchMode.name());
        data.putString(AE2LT_WIRELESS_SPEED_MODE_TAG, this.ae2LtWirelessSpeedMode.name());
        ListTag connectionList = new ListTag();
        for (OverloadedPatternProviderBlockEntity.WirelessConnection connection : this.ae2LtConnections) {
            connectionList.add(connection.toTag());
        }
        data.put(AE2LT_CONNECTIONS_TAG, connectionList);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.providerInventory.readFromNBT(data, PROVIDER_SLOT_TAG, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.providerInventory.setMaxStackSize(0, getProviderSlotLimit());
        this.advancedAeFilteredImport = data.getBoolean(ADVANCED_AE_FILTERED_IMPORT_TAG);
        this.ae2LtProviderMode = readEnum(data, AE2LT_PROVIDER_MODE_TAG, Ae2LtProviderMode.NORMAL, Ae2LtProviderMode.class);
        this.ae2LtReturnMode = readEnum(data, AE2LT_RETURN_MODE_TAG, Ae2LtReturnMode.OFF, Ae2LtReturnMode.class);
        this.ae2LtWirelessDispatchMode = readEnum(data, AE2LT_WIRELESS_DISPATCH_MODE_TAG, Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION, Ae2LtWirelessDispatchMode.class);
        this.ae2LtWirelessSpeedMode = readEnum(data, AE2LT_WIRELESS_SPEED_MODE_TAG, Ae2LtWirelessSpeedMode.NORMAL, Ae2LtWirelessSpeedMode.class);
        this.ae2LtConnections.clear();
        ListTag connectionList = data.getList(AE2LT_CONNECTIONS_TAG, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < connectionList.size(); i++) {
            this.ae2LtConnections.add(OverloadedPatternProviderBlockEntity.WirelessConnection.fromTag(connectionList.getCompound(i)));
        }
        this.syncedPatternSlotCount = getConfiguredPatternSlotCount();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeVarInt(getConfiguredPatternSlotCount());
        data.writeNbt(this.providerInventory.getStackInSlot(0).saveOptional(data.registryAccess()));
        data.writeBoolean(this.advancedAeFilteredImport);
        data.writeVarInt(this.ae2LtProviderMode.ordinal());
        data.writeVarInt(this.ae2LtReturnMode.ordinal());
        data.writeVarInt(this.ae2LtWirelessDispatchMode.ordinal());
        data.writeVarInt(this.ae2LtWirelessSpeedMode.ordinal());
        data.writeVarInt(this.ae2LtConnections.size());
        for (OverloadedPatternProviderBlockEntity.WirelessConnection connection : this.ae2LtConnections) {
            data.writeResourceLocation(connection.dimension().location());
            data.writeBlockPos(connection.pos());
            data.writeEnum(connection.boundFace());
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int syncedPatternSlotCount = data.readVarInt();
        CompoundTag providerStackTag = data.readNbt();
        ItemStack providerStack = providerStackTag == null
                ? ItemStack.EMPTY
                : ItemStack.parseOptional(data.registryAccess(), providerStackTag);
        boolean advancedAeFilteredImport = data.readBoolean();
        Ae2LtProviderMode ae2LtProviderMode = Ae2LtProviderMode.values()[data.readVarInt()];
        Ae2LtReturnMode ae2LtReturnMode = Ae2LtReturnMode.values()[data.readVarInt()];
        Ae2LtWirelessDispatchMode ae2LtWirelessDispatchMode = Ae2LtWirelessDispatchMode.values()[data.readVarInt()];
        Ae2LtWirelessSpeedMode ae2LtWirelessSpeedMode = Ae2LtWirelessSpeedMode.values()[data.readVarInt()];
        int connectionCount = data.readVarInt();
        List<OverloadedPatternProviderBlockEntity.WirelessConnection> incomingConnections = new ArrayList<>(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            var dimension = ResourceKey.create(Registries.DIMENSION, data.readResourceLocation());
            var pos = data.readBlockPos();
            var face = data.readEnum(Direction.class);
            incomingConnections.add(new OverloadedPatternProviderBlockEntity.WirelessConnection(dimension, pos, face));
        }
        if (this.syncedPatternSlotCount != syncedPatternSlotCount) {
            this.syncedPatternSlotCount = syncedPatternSlotCount;
            changed = true;
        }
        if (!ItemStack.matches(this.providerInventory.getStackInSlot(0), providerStack)) {
            this.providerInventory.setItemDirect(0, providerStack);
            changed = true;
        }
        if (this.advancedAeFilteredImport != advancedAeFilteredImport) {
            this.advancedAeFilteredImport = advancedAeFilteredImport;
            changed = true;
        }
        if (this.ae2LtProviderMode != ae2LtProviderMode) {
            this.ae2LtProviderMode = ae2LtProviderMode;
            changed = true;
        }
        if (this.ae2LtReturnMode != ae2LtReturnMode) {
            this.ae2LtReturnMode = ae2LtReturnMode;
            changed = true;
        }
        if (this.ae2LtWirelessDispatchMode != ae2LtWirelessDispatchMode) {
            this.ae2LtWirelessDispatchMode = ae2LtWirelessDispatchMode;
            changed = true;
        }
        if (this.ae2LtWirelessSpeedMode != ae2LtWirelessSpeedMode) {
            this.ae2LtWirelessSpeedMode = ae2LtWirelessSpeedMode;
            changed = true;
        }
        if (!this.ae2LtConnections.equals(incomingConnections)) {
            this.ae2LtConnections.clear();
            this.ae2LtConnections.addAll(incomingConnections);
            changed = true;
        }
        this.providerInventory.setMaxStackSize(0, getProviderSlotLimit());
        return changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack stack = this.providerInventory.getStackInSlot(0);
        if (!stack.isEmpty()) {
            drops.add(stack.copy());
        }
        for (ItemStack upgrade : this.upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.providerInventory.clear();
        this.upgrades.clear();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.terminalIcon() : AEItemKey.of(ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem().getDefaultInstance());
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        var logic = this.getLogic();
        if (logic == null) {
            return InternalInventory.empty();
        }

        int visibleSlots = Math.max(0, Math.min(getConfiguredPatternSlotCount(), logic.getPatternInv().size()));
        return logic.getPatternInv().getSubInventory(0, visibleSlots);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.mainMenuIcon().copy() : ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem().getDefaultInstance();
    }

    @Override
    public appeng.api.implementations.blockentities.PatternContainerGroup getTerminalGroup() {
        var baseGroup = buildAdaptiveTerminalGroup();
        var tooltip = new ArrayList<Component>(baseGroup.tooltip());
        baseGroup = new appeng.api.implementations.blockentities.PatternContainerGroup(
                AEItemKey.of(ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem().getDefaultInstance()),
                Component.translatable(TERMINAL_GROUP_NAME_KEY, getProviderDisplayName()),
                List.copyOf(tooltip)
        );
        int unlockedSlots = getConfiguredPatternSlotCount();
        int totalSlots = getCurrentProviderMaxPatternCapacity();
        if (unlockedSlots >= totalSlots) {
            return baseGroup;
        }

        tooltip = new ArrayList<Component>(baseGroup.tooltip());
        tooltip.add(Component.translatable(
                TERMINAL_GROUP_LOCKED_SUFFIX_KEY,
                unlockedSlots,
                totalSlots
        ));
        return new appeng.api.implementations.blockentities.PatternContainerGroup(
                baseGroup.icon(),
                baseGroup.name(),
                List.copyOf(tooltip)
        );
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        if (inv == this.upgrades) {
            this.providerInventory.setMaxStackSize(0, getProviderSlotLimit());
        }
        int oldSlotCount = this.syncedPatternSlotCount;
        int newSlotCount = getConfiguredPatternSlotCount();
        this.syncedPatternSlotCount = newSlotCount;
        this.saveChanges();
        this.markForClientUpdate();
        if (oldSlotCount != newSlotCount) {
            requestPatternAccessTerminalRefresh();
        }
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
    }

    private int getConfiguredPatternSlotCount() {
        ProviderProfile profile = getProviderProfile();
        if (profile == null) {
            return 0;
        }

        int providerCount = Math.min(this.providerInventory.getStackInSlot(0).getCount(), getProviderSlotLimit());
        return profile.slotsPerProvider() * providerCount;
    }

    private int getCurrentProviderMaxPatternCapacity() {
        ProviderProfile profile = getProviderProfile();
        if (profile == null) {
            return 0;
        }

        return Math.min(MAX_PATTERN_SLOTS, profile.slotsPerProvider() * getProviderSlotLimit());
    }

    private int getExtraProviderSlotsFromCapacityCards() {
        return Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD)) * EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD;
    }

    @Nullable
    private ProviderProfile getProviderProfile() {
        if (this.providerInventory == null) {
            return null;
        }
        return resolveProviderProfile(this.providerInventory.getStackInSlot(0));
    }

    public static boolean isSupportedProviderStack(ItemStack stack) {
        return resolveProviderProfile(stack) != null;
    }

    public static boolean isAdvancedAeProviderStack(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        if (profile == null) {
            return false;
        }

        return profile.kind() == ProviderKind.ADVANCED_SMALL || profile.kind() == ProviderKind.ADVANCED_EXTENDED;
    }

    public static boolean isAe2LightningTechOverloadedProviderStack(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null && profile.kind() == ProviderKind.AE2LT_OVERLOADED;
    }

    public static boolean isAe2LightningTechOverloadPatternStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null
                && AE2LT_NAMESPACE.equals(itemId.getNamespace())
                && AE2LT_OVERLOAD_PATTERN.equals(itemId.getPath());
    }

    @Nullable
    private static ProviderProfile resolveProviderProfile(ItemStack stack) {
        if (stack.isEmpty() || stack.is(ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem())) {
            return null;
        }

        ProviderProfile profile = resolveAe2CrystalScienceProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolveAdvancedAeProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolveAe2LightningTechProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolvePartProviderProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolveBlockProviderProfile(stack);
        if (profile != null) {
            return profile;
        }

        return resolveLegacyProviderProfile(stack);
    }

    @Nullable
    private static ProviderProfile resolveAe2CrystalScienceProfile(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"ae2cs".equals(itemId.getNamespace())) {
            return null;
        }

        String path = itemId.getPath();
        int slotCount = switch (path) {
            case "resonating_pattern_provider",
                 "resonating_pattern_provider_part" -> BASE_PATTERN_SLOTS;
            case "simple_pattern_provider",
                 "simple_pattern_provider_part" -> SIMPLE_PATTERN_SLOTS;
            case "ex_resonating_pattern_provider",
                 "ex_resonating_pattern_provider_part" -> EXTENDED_PATTERN_SLOTS;
            case "meteorite_pattern_provider",
                 "meteorite_pattern_provider_part" -> METEORITE_PATTERN_SLOTS;
            default -> -1;
        };

        if (slotCount <= 0) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        ProviderKind kind = switch (path) {
            case "resonating_pattern_provider", "resonating_pattern_provider_part" -> ProviderKind.RESONATING;
            case "simple_pattern_provider", "simple_pattern_provider_part" -> ProviderKind.SIMPLE;
            case "ex_resonating_pattern_provider", "ex_resonating_pattern_provider_part" -> ProviderKind.EXTENDED_RESONATING;
            case "meteorite_pattern_provider", "meteorite_pattern_provider_part" -> ProviderKind.METEORITE;
            default -> ProviderKind.UNKNOWN;
        };
        return new ProviderProfile(kind, slotCount, icon, AEItemKey.of(icon), icon.getHoverName());
    }

    @Nullable
    private static ProviderProfile resolveAdvancedAeProfile(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"advanced_ae".equals(itemId.getNamespace())) {
            return null;
        }

        String path = itemId.getPath();
        int slotCount = switch (path) {
            case "small_adv_pattern_provider", "small_adv_pattern_provider_part" -> BASE_PATTERN_SLOTS;
            case "adv_pattern_provider", "adv_pattern_provider_part" -> EXTENDED_PATTERN_SLOTS;
            default -> -1;
        };

        if (slotCount <= 0) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        ProviderKind kind = switch (path) {
            case "small_adv_pattern_provider", "small_adv_pattern_provider_part" -> ProviderKind.ADVANCED_SMALL;
            case "adv_pattern_provider", "adv_pattern_provider_part" -> ProviderKind.ADVANCED_EXTENDED;
            default -> ProviderKind.UNKNOWN;
        };
        return new ProviderProfile(kind, slotCount, icon, AEItemKey.of(icon), icon.getHoverName());
    }

    @Nullable
    private static ProviderProfile resolveAe2LightningTechProfile(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !AE2LT_NAMESPACE.equals(itemId.getNamespace())) {
            return null;
        }

        if (!AE2LT_OVERLOADED_PATTERN_PROVIDER.equals(itemId.getPath())) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        return new ProviderProfile(ProviderKind.AE2LT_OVERLOADED, EXTENDED_PATTERN_SLOTS, icon, AEItemKey.of(icon), icon.getHoverName());
    }

    @Nullable
    private static ProviderProfile resolvePartProviderProfile(ItemStack stack) {
        if (!(stack.getItem() instanceof IPartItem<?> partItem)) {
            return null;
        }

        try {
            IPart part = partItem.createPart();
            if (!(part instanceof PatternProviderLogicHost host)) {
                return null;
            }

            int slotCount = host.getLogic().getPatternInv().size();
            if (slotCount <= 0) {
                return null;
            }

            ItemStack menuIcon = resolveMainMenuIcon(part, new ItemStack(stack.getItem()));
            return new ProviderProfile(resolveKindFromSlotCount(slotCount), slotCount, menuIcon, host.getTerminalIcon(), menuIcon.getHoverName());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static ProviderProfile resolveBlockProviderProfile(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }

        try {
            BlockState state = blockItem.getBlock().defaultBlockState();
            BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
            if (!(blockEntity instanceof PatternProviderLogicHost host)) {
                return null;
            }

            int slotCount = host.getLogic().getPatternInv().size();
            if (slotCount <= 0) {
                return null;
            }

            ItemStack menuIcon = resolveMainMenuIcon(blockEntity, new ItemStack(stack.getItem()));
            return new ProviderProfile(resolveKindFromSlotCount(slotCount), slotCount, menuIcon, host.getTerminalIcon(), menuIcon.getHoverName());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static ProviderProfile resolveLegacyProviderProfile(ItemStack stack) {
        if (AEBlocks.PATTERN_PROVIDER.is(stack) || AEParts.PATTERN_PROVIDER.is(stack)) {
            ItemStack icon = new ItemStack(stack.getItem());
            return new ProviderProfile(ProviderKind.STANDARD, BASE_PATTERN_SLOTS, icon, AEItemKey.of(icon), icon.getHoverName());
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null && "extendedae".equals(itemId.getNamespace())
                && ("ex_pattern_provider".equals(itemId.getPath())
                || "ex_pattern_provider_part".equals(itemId.getPath())
                || "wireless_ex_pat".equals(itemId.getPath()))) {
            ItemStack icon = new ItemStack(stack.getItem());
            return new ProviderProfile(ProviderKind.EXTENDED, EXTENDED_PATTERN_SLOTS, icon, AEItemKey.of(icon), icon.getHoverName());
        }

        return null;
    }

    private static ProviderKind resolveKindFromSlotCount(int slotCount) {
        if (slotCount == SIMPLE_PATTERN_SLOTS) {
            return ProviderKind.SIMPLE;
        }
        if (slotCount == BASE_PATTERN_SLOTS) {
            return ProviderKind.STANDARD;
        }
        if (slotCount == EXTENDED_PATTERN_SLOTS) {
            return ProviderKind.EXTENDED;
        }
        if (slotCount == METEORITE_PATTERN_SLOTS) {
            return ProviderKind.METEORITE;
        }
        return ProviderKind.UNKNOWN;
    }

    private static ItemStack resolveMainMenuIcon(Object source, ItemStack fallback) {
        try {
            Method method = source.getClass().getMethod("getMainMenuIcon");
            Object result = method.invoke(source);
            if (result instanceof ItemStack stack && !stack.isEmpty()) {
                return stack.copy();
            }
        } catch (Exception ignored) {
        }
        return fallback.copy();
    }

    private void onAe2LtStateChanged() {
        this.saveChanges();
        this.markForClientUpdate();
        AdaptivePatternProviderLogic logic = getAdaptiveLogic();
        if (logic != null) {
            logic.onHostStateChanged();
        }
    }

    private IUpgradeInventory createUpgradeInventory() {
        if (getAppliedFluxInductionCard() == null) {
            return UpgradeInventories.empty();
        }

        return UpgradeInventories.forMachine(
                ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(),
                APPFLUX_UPGRADE_SLOTS,
                this::onUpgradesChanged
        );
    }

    private void onUpgradesChanged() {
        this.providerInventory.setMaxStackSize(0, getProviderSlotLimit());
        this.saveChanges();
        this.markForClientUpdate();
        AdaptivePatternProviderLogic logic = getAdaptiveLogic();
        if (logic != null) {
            logic.onHostStateChanged();
        }
    }

    @Nullable
    public static Item getAppliedFluxInductionCard() {
        Item item = BuiltInRegistries.ITEM.get(APPFLUX_INDUCTION_CARD_ID);
        return item == null || item == Items.AIR ? null : item;
    }

    private appeng.api.implementations.blockentities.PatternContainerGroup buildAdaptiveTerminalGroup() {
        if (this instanceof Nameable nameable && nameable.hasCustomName()) {
            return new appeng.api.implementations.blockentities.PatternContainerGroup(
                    this.getTerminalIcon(),
                    nameable.getCustomName(),
                    List.of()
            );
        }

        var logic = this.getLogic();
        if (logic == null) {
            var icon = this.getTerminalIcon();
            return new appeng.api.implementations.blockentities.PatternContainerGroup(
                    icon,
                    icon.getDisplayName(),
                    List.of()
            );
        }

        var hostLevel = this.getLevel();
        var hostPos = this.getBlockPos();
        var sides = this.getTargets();
        var groups = new java.util.LinkedHashSet<appeng.api.implementations.blockentities.PatternContainerGroup>(sides.size());
        for (var side : sides) {
            var sidePos = hostPos.relative(side);
            var group = appeng.api.implementations.blockentities.PatternContainerGroup.fromMachine(
                    hostLevel,
                    sidePos,
                    side.getOpposite()
            );
            if (group != null) {
                groups.add(group);
            }
        }

        if (groups.size() == 1) {
            return groups.iterator().next();
        }

        List<Component> tooltip = List.of();
        if (groups.size() > 1) {
            var builtTooltip = new ArrayList<Component>();
            builtTooltip.add(GuiText.AdjacentToDifferentMachines.text());
            for (var group : groups) {
                builtTooltip.add(group.name());
                for (var line : group.tooltip()) {
                    builtTooltip.add(Component.literal("  ").append(line));
                }
            }
            tooltip = List.copyOf(builtTooltip);
        }

        var icon = this.getTerminalIcon();
        return new appeng.api.implementations.blockentities.PatternContainerGroup(
                icon,
                icon.getDisplayName(),
                tooltip
        );
    }

    private void requestPatternAccessTerminalRefresh() {
        var grid = getGridNode() != null ? getGridNode().getGrid() : null;
        if (grid == null) {
            return;
        }

        PatternProviderLogicHost host = this;
        try {
            Class<?> updateHelper = Class.forName("appeng.api.networking.crafting.ICraftingProvider");
            Method requestUpdate = updateHelper.getMethod("requestUpdate", appeng.api.networking.IManagedGridNode.class);
            requestUpdate.invoke(null, this.getMainNode());
        } catch (Exception ignored) {
        }

        try {
            for (Class<?> machineClass : grid.getMachineClasses()) {
                if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                    continue;
                }

                for (Object machine : grid.getActiveMachines((Class<? extends PatternContainer>) machineClass)) {
                    if (machine == host) {
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
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

    private static final class ProviderSuffixFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
            return isSupportedProviderStack(stack);
        }
    }

    public enum ProviderKind {
        UNKNOWN,
        STANDARD,
        SIMPLE,
        EXTENDED,
        ADVANCED_SMALL,
        ADVANCED_EXTENDED,
        AE2LT_OVERLOADED,
        RESONATING,
        EXTENDED_RESONATING,
        METEORITE
    }

    public enum Ae2LtProviderMode {
        NORMAL,
        WIRELESS;

        public Ae2LtProviderMode next() {
            return this == NORMAL ? WIRELESS : NORMAL;
        }
    }

    public enum Ae2LtReturnMode {
        OFF,
        AUTO,
        EJECT;

        public Ae2LtReturnMode next() {
            return switch (this) {
                case OFF -> AUTO;
                case AUTO -> EJECT;
                case EJECT -> OFF;
            };
        }
    }

    public enum Ae2LtWirelessDispatchMode {
        EVEN_DISTRIBUTION,
        SINGLE_TARGET;

        public Ae2LtWirelessDispatchMode next() {
            return this == EVEN_DISTRIBUTION ? SINGLE_TARGET : EVEN_DISTRIBUTION;
        }
    }

    public enum Ae2LtWirelessSpeedMode {
        NORMAL,
        FAST;

        public Ae2LtWirelessSpeedMode next() {
            return this == NORMAL ? FAST : NORMAL;
        }
    }

    private record ProviderProfile(ProviderKind kind, int slotsPerProvider, ItemStack mainMenuIcon, AEItemKey terminalIcon, Component displayName) {
    }
}
