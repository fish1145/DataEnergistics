package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnFluidHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnItemHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderState;
import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.AppMekCompat;
import com.fish_dan_.data_energistics.integration.AppliedCreateCompat;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AdaptivePatternProviderBlockEntity extends PatternProviderBlockEntity implements InternalInventoryHost, IUpgradeableObject, AdaptivePatternProviderHost {

    protected static final String ADAPTIVE_PATTERN_PROVIDER_KEY = "adaptive_pattern_provider";
    private static final int BASE_PATTERN_SLOTS = 9;
    private static final int SIMPLE_PATTERN_SLOTS = 5;
    private static final int EXTENDED_PATTERN_SLOTS = 36;
    private static final int METEORITE_PATTERN_SLOTS = 63;
    private static final String AE2LT_NAMESPACE = "ae2lt";
    private static final String AE2LT_OVERLOADED_PATTERN_PROVIDER = "overloaded_pattern_provider";
    private static final String AE2LT_OVERLOAD_PATTERN = "overload_pattern";
    private static final String APPLIED_CREATE_NAMESPACE = "appliedcreate";
    private static final String EXTENDEDAE_NAMESPACE = "extendedae";
    private static final String EXTENDEDAE_PLUS_NAMESPACE = "extendedae_plus";
    private static final ResourceLocation EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID = ResourceLocation.fromNamespaceAndPath(EXTENDEDAE_NAMESPACE, "assembler_matrix_speed");
    private static final ResourceLocation EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_SPEED_ID = ResourceLocation.fromNamespaceAndPath(EXTENDEDAE_PLUS_NAMESPACE, "assembler_matrix_speed_plus");
    private static final String EXTENDEDAE_ASSEMBLER_MATRIX_NAME_KEY = "gui.extendedae.assembler_matrix";
    private static final Set<String> EXTENDEDAE_ASSEMBLER_MATRIX_COMPONENTS = Set.of(
            "assembler_matrix_wall",
            "assembler_matrix_frame",
            "assembler_matrix_glass",
            "assembler_matrix_crafter",
            "assembler_matrix_pattern",
            "assembler_matrix_speed");
    private static final Set<String> EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_COMPONENTS = Set.of(
            "assembler_matrix_crafter_plus",
            "assembler_matrix_pattern_plus",
            "assembler_matrix_speed_plus");
    private static final ResourceLocation APPFLUX_INDUCTION_CARD_ID = ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
    private static final String TERMINAL_GROUP_LOCKED_SUFFIX_SUFFIX = ".terminal_hidden_slots";

    @Nullable
    private AdaptivePatternProviderState adaptiveState;
    private final IUpgradeInventory upgrades;
    private final IItemHandler externalReturnItemHandler = new AdaptivePatternProviderReturnItemHandler(this::getAdaptiveLogic);
    private final IFluidHandler externalReturnFluidHandler = new AdaptivePatternProviderReturnFluidHandler(this::getAdaptiveLogic);
    private final Object externalReturnChemicalHandler = AppMekCompat.createReturnChemicalHandler(this::getAdaptiveLogic);
    private int syncedPatternSlotCount = 0;

    public AdaptivePatternProviderBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.upgrades = createUpgradeInventory();
        this.getMainNode().setVisualRepresentation(getProviderBlock().get());
    }

    @Override
    protected AdaptivePatternProviderLogic createLogic() {
        return new AdaptivePatternProviderLogic(this.getMainNode(), this, AdaptivePatternProviderState.MAX_PATTERN_SLOTS);
    }

    @Nullable
    private AdaptivePatternProviderLogic getAdaptiveLogic() {
        var logic = this.getLogic();
        return logic instanceof AdaptivePatternProviderLogic adaptive ? adaptive : null;
    }

    @Override
    public AppEngInternalInventory getProviderInventory() {
        return getAdaptiveState().getProviderInventory();
    }

    @Nullable
    public IItemHandler getExternalReturnItemHandler(@Nullable Direction side) {
        if (side != null && !this.getTargets().contains(side)) {
            return null;
        }
        return this.externalReturnItemHandler;
    }

    @Nullable
    public IFluidHandler getExternalReturnFluidHandler(@Nullable Direction side) {
        if (side != null && !this.getTargets().contains(side)) {
            return null;
        }
        return this.externalReturnFluidHandler;
    }

    @Nullable
    public Object getExternalReturnChemicalHandler(@Nullable Direction side) {
        if (side != null && !this.getTargets().contains(side)) {
            return null;
        }
        return this.externalReturnChemicalHandler;
    }

    @Override
    public int getProviderSlotLimit() {
        return AdaptivePatternProviderState.PROVIDER_SLOT_LIMIT + getExtraProviderSlotsFromCapacityCards();
    }

    @Override
    public ItemStack extractProviderOverflow() {
        return getAdaptiveState().extractProviderOverflow();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    public boolean supportsAppliedFluxUpgradeSlot() {
        return this.upgrades.size() > 0;
    }

    @Override
    public int getPatternSlotCountForMenu() {
        return getConfiguredPatternSlotCount();
    }

    @Override
    public Component getProviderDisplayName() {
        var adjacentGroup = getSingleAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return adjacentGroup.name();
        }

        ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.displayName() : this.getMainMenuIcon().getHoverName();
    }

    @Override
    public Component getGuiDisplayName() {
        var adjacentGroup = getSingleAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return decorateAttachedMachineName(adjacentGroup.name(), getResolvedProviderNameForGui());
        }

        ProviderProfile profile = getProviderProfile();
        return profile != null ? decorateAdaptiveProviderName(getAdaptiveProviderVariantTranslationKey(), profile.displayName()) : this.getMainMenuIcon().getHoverName();
    }

    @Override
    public Component getTerminalDisplayName() {
        var adjacentGroup = getSingleAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return decorateAttachedMachineName(adjacentGroup.name(), getResolvedInternalProviderName());
        }
        return getResolvedProviderNameForTerminal();
    }

    @Override
    public @Nullable appeng.api.implementations.blockentities.PatternContainerGroup getPrimaryAttachedMachineGroup() {
        var hostLevel = this.getLevel();
        if (hostLevel == null) {
            return null;
        }

        var hostPos = this.getBlockPos();
        for (var side : this.getTargets()) {
            var specialGroup = resolveSpecialAdjacentMachineGroup(hostLevel, hostPos.relative(side));
            if (specialGroup != null) {
                return specialGroup;
            }
        }

        var groups = getAdjacentMachineGroups();
        return groups.size() == 1 ? groups.iterator().next() : null;
    }

    @Override
    public boolean isMeteoriteProviderSelected() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && profile.kind() == ProviderKind.METEORITE;
    }

    @Override
    public boolean isAdvancedAeProviderSelected() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == ProviderKind.ADVANCED_SMALL || profile.kind() == ProviderKind.ADVANCED_EXTENDED);
    }

    @Override
    public boolean isAe2LightningTechOverloadedProviderSelected() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && profile.kind() == ProviderKind.AE2LT_OVERLOADED;
    }

    @Override
    public boolean isAppliedCreateMechanicalProviderSelected() {
        if (!AppliedCreateCompat.isMechanicalProviderSupportEnabled()) {
            return false;
        }
        ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == ProviderKind.APPLIED_CREATE_ANDESITE || profile.kind() == ProviderKind.APPLIED_CREATE_BRASS);
    }

    @Override
    public boolean isResonatingProviderSelected() {
        ProviderProfile profile = getProviderProfile();
        if (profile == null) {
            return false;
        }

        return profile.kind() == ProviderKind.RESONATING || profile.kind() == ProviderKind.EXTENDED_RESONATING;
    }

    @Override
    public boolean supportsFilteredImportToggle() {
        ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == ProviderKind.ADVANCED_SMALL || profile.kind() == ProviderKind.ADVANCED_EXTENDED || profile.kind() == ProviderKind.AE2LT_OVERLOADED);
    }

    @Override
    public Ae2LtProviderMode getAe2LtProviderMode() {
        return getAdaptiveState().getAe2LtProviderMode();
    }

    @Override
    public void cycleAe2LtProviderMode() {
        getAdaptiveState().cycleAe2LtProviderMode();
        this.onAe2LtStateChanged();
    }

    @Override
    public boolean isAe2LtWirelessMode() {
        return getAdaptiveState().isAe2LtWirelessMode();
    }

    @Override
    public Ae2LtReturnMode getAe2LtReturnMode() {
        return getAdaptiveState().getAe2LtReturnMode();
    }

    @Override
    public void cycleAe2LtReturnMode() {
        getAdaptiveState().cycleAe2LtReturnMode();
        this.onAe2LtStateChanged();
    }

    @Override
    public Ae2LtWirelessDispatchMode getAe2LtWirelessDispatchMode() {
        return getAdaptiveState().getAe2LtWirelessDispatchMode();
    }

    @Override
    public void cycleAe2LtWirelessDispatchMode() {
        getAdaptiveState().cycleAe2LtWirelessDispatchMode();
        this.onAe2LtStateChanged();
    }

    @Override
    public Ae2LtWirelessSpeedMode getAe2LtWirelessSpeedMode() {
        return getAdaptiveState().getAe2LtWirelessSpeedMode();
    }

    @Override
    public void cycleAe2LtWirelessSpeedMode() {
        getAdaptiveState().cycleAe2LtWirelessSpeedMode();
        this.onAe2LtStateChanged();
    }

    @Override
    public boolean isAdvancedAeFilteredImportEnabled() {
        return getAdaptiveState().isAdvancedAeFilteredImportEnabled();
    }

    @Override
    public void setAdvancedAeFilteredImportEnabled(boolean enabled) {
        if (!getAdaptiveState().setAdvancedAeFilteredImportEnabled(enabled)) {
            return;
        }
        this.saveChanges();
        this.markForClientUpdate();
        AdaptivePatternProviderLogic logic = getAdaptiveLogic();
        if (logic != null) {
            logic.onHostStateChanged();
        }
    }

    @Override
    public boolean isResonatingPullEnabled() {
        return getAdaptiveState().isResonatingPullEnabled();
    }

    @Override
    public void setResonatingPullEnabled(boolean enabled) {
        if (!getAdaptiveState().setResonatingPullEnabled(enabled)) {
            return;
        }
        this.saveChanges();
        this.markForClientUpdate();
        AdaptivePatternProviderLogic logic = getAdaptiveLogic();
        if (logic != null) {
            logic.onHostStateChanged();
        }
    }

    @Override
    public void addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {
        getAdaptiveState().addOrUpdateConnection(dimension, pos, boundFace);
        this.onAe2LtStateChanged();
    }

    @Override
    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        if (getAdaptiveState().removeConnection(dimension, pos)) {
            this.onAe2LtStateChanged();
            return true;
        }
        return false;
    }

    @Override
    public List<AdaptiveWirelessConnection> getConnections() {
        return getAdaptiveState().getConnections();
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(getProviderMenu().get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(getProviderMenu().get(), player, subMenu.getLocator());
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        getAdaptiveState().writeToNBT(data, registries, this.upgrades);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        getAdaptiveState().readFromNBT(data, registries, this.upgrades);
        this.syncedPatternSlotCount = getConfiguredPatternSlotCount();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeVarInt(getConfiguredPatternSlotCount());
        getAdaptiveState().writeToStream(data);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int syncedPatternSlotCount = data.readVarInt();
        if (this.syncedPatternSlotCount != syncedPatternSlotCount) {
            this.syncedPatternSlotCount = syncedPatternSlotCount;
            changed = true;
        }
        return getAdaptiveState().readFromStream(data) || changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack stack = getAdaptiveState().getProviderStack();
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
        getAdaptiveState().clearContent();
        this.upgrades.clear();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        var adjacentGroup = getSingleAdjacentMachineGroup();
        if (adjacentGroup != null && adjacentGroup.icon() != null) {
            return adjacentGroup.icon();
        }

        ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.terminalIcon() : AEItemKey.of(getProviderBlock().get().asItem().getDefaultInstance());
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
        var adjacentGroup = getSingleAdjacentMachineGroup();
        if (adjacentGroup != null && adjacentGroup.icon() != null) {
            ItemStack adjacentIcon = adjacentGroup.icon().toStack();
            if (!adjacentIcon.isEmpty()) {
                return adjacentIcon;
            }
        }

        ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.mainMenuIcon().copy() : getProviderBlock().get().asItem().getDefaultInstance();
    }

    @Override
    public appeng.api.implementations.blockentities.PatternContainerGroup getTerminalGroup() {
        var baseGroup = buildAdaptiveTerminalGroup();
        var tooltip = new ArrayList<Component>(baseGroup.tooltip());
        int unlockedSlots = getConfiguredPatternSlotCount();
        int totalSlots = getCurrentProviderMaxPatternCapacity();
        if (unlockedSlots < totalSlots) {
            tooltip.add(Component.translatable(
                    getTerminalGroupLockedSlotsKey(),
                    unlockedSlots,
                    totalSlots));
        }

        Component displayName = this instanceof Nameable nameable && nameable.hasCustomName() ? baseGroup.name() : getTerminalDisplayName();
        return new appeng.api.implementations.blockentities.PatternContainerGroup(
                baseGroup.icon(),
                displayName,
                List.copyOf(tooltip));
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        if (inv == this.upgrades) {
            getAdaptiveState().refreshProviderSlotLimit();
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
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {}

    private int getConfiguredPatternSlotCount() {
        ProviderProfile profile = getProviderProfile();
        if (profile == null) {
            return 0;
        }

        int providerCount = Math.min(getAdaptiveState().getProviderStack().getCount(), getProviderSlotLimit());
        return profile.slotsPerProvider() * providerCount;
    }

    private int getCurrentProviderMaxPatternCapacity() {
        ProviderProfile profile = getProviderProfile();
        if (profile == null) {
            return 0;
        }

        return Math.min(AdaptivePatternProviderState.MAX_PATTERN_SLOTS, profile.slotsPerProvider() * getProviderSlotLimit());
    }

    private int getExtraProviderSlotsFromCapacityCards() {
        if (this.upgrades == null) {
            return 0;
        }
        return Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD)) * AdaptivePatternProviderState.EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD;
    }

    @Nullable
    private ProviderProfile getProviderProfile() {
        return resolveProviderProfile(getAdaptiveState().getProviderStack());
    }

    public static boolean isSupportedProviderStack(ItemStack stack) {
        return resolveProviderProfile(stack) != null;
    }

    @Nullable
    public static ProviderKind getResolvedProviderKind(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.kind() : null;
    }

    public static int getResolvedSlotsPerProvider(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.slotsPerProvider() : 0;
    }

    @Nullable
    public static ItemStack getResolvedProviderMainMenuIcon(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.mainMenuIcon().copy() : null;
    }

    @Nullable
    public static AEItemKey getResolvedProviderTerminalIcon(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.terminalIcon() : null;
    }

    @Nullable
    public static Component getResolvedProviderDisplayName(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.displayName() : null;
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
        if (!Data_Energistics.Mods.isAe2LtLoaded() || stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && AE2LT_NAMESPACE.equals(itemId.getNamespace()) && AE2LT_OVERLOAD_PATTERN.equals(itemId.getPath());
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

        profile = resolveAppliedCreateProfile(stack);
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
            case "resonating_pattern_provider", "resonating_pattern_provider_part" -> BASE_PATTERN_SLOTS;
            case "simple_pattern_provider", "simple_pattern_provider_part" -> SIMPLE_PATTERN_SLOTS;
            case "extended_resonating_pattern_provider", "extended_resonating_pattern_provider_part", "ex_resonating_pattern_provider", "ex_resonating_pattern_provider_part" -> EXTENDED_PATTERN_SLOTS;
            case "meteorite_pattern_provider", "meteorite_pattern_provider_part" -> METEORITE_PATTERN_SLOTS;
            default -> -1;
        };

        if (slotCount <= 0) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        ProviderKind kind = switch (path) {
            case "resonating_pattern_provider", "resonating_pattern_provider_part" -> ProviderKind.RESONATING;
            case "simple_pattern_provider", "simple_pattern_provider_part" -> ProviderKind.SIMPLE;
            case "extended_resonating_pattern_provider", "extended_resonating_pattern_provider_part", "ex_resonating_pattern_provider", "ex_resonating_pattern_provider_part" -> ProviderKind.EXTENDED_RESONATING;
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
        if (!Data_Energistics.Mods.isAe2LtLoaded()) {
            return null;
        }

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
    private static ProviderProfile resolveAppliedCreateProfile(ItemStack stack) {
        if (!AppliedCreateCompat.isMechanicalProviderSupportEnabled()) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !APPLIED_CREATE_NAMESPACE.equals(itemId.getNamespace())) {
            return null;
        }

        String path = itemId.getPath();
        int slotCount = switch (path) {
            case "andesite_pattern_provider" -> BASE_PATTERN_SLOTS;
            case "brass_pattern_provider" -> EXTENDED_PATTERN_SLOTS;
            default -> -1;
        };

        if (slotCount <= 0) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        ProviderKind kind = switch (path) {
            case "andesite_pattern_provider" -> ProviderKind.APPLIED_CREATE_ANDESITE;
            case "brass_pattern_provider" -> ProviderKind.APPLIED_CREATE_BRASS;
            default -> ProviderKind.UNKNOWN;
        };
        return new ProviderProfile(kind, slotCount, icon, AEItemKey.of(icon), icon.getHoverName());
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
        if (itemId != null && "extendedae".equals(itemId.getNamespace()) && ("ex_pattern_provider".equals(itemId.getPath()) || "ex_pattern_provider_part".equals(itemId.getPath()) || "wireless_ex_pat".equals(itemId.getPath()))) {
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
        } catch (Exception ignored) {}
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
        return UpgradeInventories.forMachine(
                getProviderBlock().get(),
                AdaptivePatternProviderState.BASE_UPGRADE_SLOTS,
                this::onUpgradesChanged);
    }

    private void onUpgradesChanged() {
        getAdaptiveState().refreshProviderSlotLimit();
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
                    List.of());
        }

        var logic = this.getLogic();
        if (logic == null) {
            var icon = this.getTerminalIcon();
            return new appeng.api.implementations.blockentities.PatternContainerGroup(
                    icon,
                    icon.getDisplayName(),
                    List.of());
        }

        var groups = getAdjacentMachineGroups();
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
                tooltip);
    }

    @Nullable
    private appeng.api.implementations.blockentities.PatternContainerGroup getSingleAdjacentMachineGroup() {
        var groups = getAdjacentMachineGroups();
        return groups.size() == 1 ? groups.iterator().next() : null;
    }

    private java.util.LinkedHashSet<appeng.api.implementations.blockentities.PatternContainerGroup> getAdjacentMachineGroups() {
        var hostLevel = this.getLevel();
        if (hostLevel == null) {
            return new java.util.LinkedHashSet<>();
        }

        var hostPos = this.getBlockPos();
        var sides = this.getTargets();
        var groups = new java.util.LinkedHashSet<appeng.api.implementations.blockentities.PatternContainerGroup>(sides.size());
        for (var side : sides) {
            var sidePos = hostPos.relative(side);
            if (isPatternProviderAttachment(hostLevel, sidePos, side.getOpposite())) {
                continue;
            }
            var group = resolveSpecialAdjacentMachineGroup(hostLevel, sidePos);
            if (group == null) {
                group = appeng.api.implementations.blockentities.PatternContainerGroup.fromMachine(
                        hostLevel,
                        sidePos,
                        side.getOpposite());
            }
            if (group != null) {
                groups.add(group);
            }
        }
        return groups;
    }

    public static Component decorateAttachedMachineName(Component machineName, Component providerName) {
        return Component.translatable(
                "screen.data_energistics.adaptive_pattern_provider.attached_machine",
                machineName,
                providerName);
    }

    public static Component decorateAdaptiveProviderName(Component providerName) {
        return decorateAdaptiveProviderName(
                "screen.data_energistics.adaptive_pattern_provider.provider_variant",
                providerName);
    }

    public static Component decorateAdaptiveProviderName(String translationKey, Component providerName) {
        return Component.translatable(
                translationKey,
                providerName);
    }

    private Component getResolvedProviderNameForGui() {
        if (getAdaptiveState().getProviderStack().isEmpty()) {
            return Component.translatable(getProviderTranslationKey());
        }

        ProviderProfile profile = getProviderProfile();
        return profile != null ? decorateAdaptiveProviderName(getAdaptiveProviderVariantTranslationKey(), profile.displayName()) : Component.translatable(getProviderTranslationKey());
    }

    private Component getResolvedProviderNameForTerminal() {
        if (getAdaptiveState().getProviderStack().isEmpty()) {
            return Component.translatable(getProviderTranslationKey());
        }

        ProviderProfile profile = getProviderProfile();
        return profile != null ? decorateAdaptiveProviderName(profile.displayName()) : Component.translatable(getProviderTranslationKey());
    }

    private Component getResolvedInternalProviderName() {
        if (getAdaptiveState().getProviderStack().isEmpty()) {
            return Component.translatable(getProviderTranslationKey());
        }

        ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.displayName() : Component.translatable(getProviderTranslationKey());
    }

    private AdaptivePatternProviderState getAdaptiveState() {
        if (this.adaptiveState == null) {
            this.adaptiveState = new AdaptivePatternProviderState(this, this::getProviderSlotLimit);
        }
        return this.adaptiveState;
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
        } catch (Exception ignored) {}

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
        } catch (Exception ignored) {}
    }

    protected String getProviderTranslationKey() {
        return "block.data_energistics." + ADAPTIVE_PATTERN_PROVIDER_KEY;
    }

    protected String getAdaptiveProviderVariantTranslationKey() {
        return "screen.data_energistics.adaptive_pattern_provider.provider_variant";
    }

    protected String getTerminalGroupLockedSlotsKey() {
        return "tooltip.data_energistics." + ADAPTIVE_PATTERN_PROVIDER_KEY + TERMINAL_GROUP_LOCKED_SUFFIX_SUFFIX;
    }

    protected DeferredBlock<Block> getProviderBlock() {
        return ModBlocks.ADAPTIVE_PATTERN_PROVIDER;
    }

    protected DeferredHolder<MenuType<?>, ? extends MenuType<?>> getProviderMenu() {
        return ModMenus.ADAPTIVE_PATTERN_PROVIDER;
    }

    @Nullable
    public static appeng.api.implementations.blockentities.PatternContainerGroup resolveSpecialAdjacentMachineGroup(
                                                                                                                    Level level,
                                                                                                                    BlockPos pos) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (blockId == null || !isAssemblerMatrixComponent(blockId)) {
            return null;
        }

        var speedBlock = BuiltInRegistries.BLOCK.getOptional(EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID).orElse(null);
        if (speedBlock == null) {
            speedBlock = BuiltInRegistries.BLOCK.getOptional(EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_SPEED_ID).orElse(null);
        }
        if (speedBlock == null) {
            return null;
        }

        ItemStack iconStack = speedBlock.asItem().getDefaultInstance();
        if (iconStack.isEmpty()) {
            return null;
        }

        return new appeng.api.implementations.blockentities.PatternContainerGroup(
                AEItemKey.of(iconStack),
                Component.translatable(EXTENDEDAE_ASSEMBLER_MATRIX_NAME_KEY),
                List.of());
    }

    private static boolean isAssemblerMatrixComponent(ResourceLocation blockId) {
        return (EXTENDEDAE_NAMESPACE.equals(blockId.getNamespace()) && EXTENDEDAE_ASSEMBLER_MATRIX_COMPONENTS.contains(blockId.getPath())) || (EXTENDEDAE_PLUS_NAMESPACE.equals(blockId.getNamespace()) && EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_COMPONENTS.contains(blockId.getPath()));
    }

    public static boolean isPatternProviderAttachment(Level level, BlockPos pos, @Nullable Direction side) {
        if (level.getBlockEntity(pos) instanceof PatternProviderBlockEntity) {
            return true;
        }

        if (!(level.getBlockEntity(pos) instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return false;
        }

        var cableBus = cableBusBlockEntity.getCableBus();
        IPart centerPart = cableBus.getPart(null);
        if (centerPart instanceof appeng.parts.crafting.PatternProviderPart) {
            return true;
        }

        if (side == null) {
            for (Direction direction : Direction.values()) {
                if (cableBus.getPart(direction) instanceof appeng.parts.crafting.PatternProviderPart) {
                    return true;
                }
            }
            return false;
        }

        return cableBus.getPart(side) instanceof appeng.parts.crafting.PatternProviderPart;
    }

    public enum ProviderKind {
        UNKNOWN,
        STANDARD,
        SIMPLE,
        EXTENDED,
        ADVANCED_SMALL,
        ADVANCED_EXTENDED,
        AE2LT_OVERLOADED,
        APPLIED_CREATE_ANDESITE,
        APPLIED_CREATE_BRASS,
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

    private record ProviderProfile(ProviderKind kind, int slotsPerProvider, ItemStack mainMenuIcon, AEItemKey terminalIcon, Component displayName) {}
}
