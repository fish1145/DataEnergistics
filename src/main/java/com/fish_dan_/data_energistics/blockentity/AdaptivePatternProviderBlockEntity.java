package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderDisplayHelper;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderExternalHandlers;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderModes;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnFluidHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnItemHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderState;
import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

public class AdaptivePatternProviderBlockEntity extends PatternProviderBlockEntity implements InternalInventoryHost, IUpgradeableObject, AdaptivePatternProviderHost {

    protected static final String ADAPTIVE_PATTERN_PROVIDER_KEY = "adaptive_pattern_provider";
    private static final ResourceLocation APPFLUX_INDUCTION_CARD_ID = ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
    private static final String TERMINAL_GROUP_LOCKED_SUFFIX_SUFFIX = ".terminal_hidden_slots";

    @Nullable
    private AdaptivePatternProviderState adaptiveState;
    private final IUpgradeInventory upgrades;
    private final IItemHandler externalReturnItemHandler = new AdaptivePatternProviderReturnItemHandler(this::getAdaptiveLogic);
    private final IFluidHandler externalReturnFluidHandler = new AdaptivePatternProviderReturnFluidHandler(this::getAdaptiveLogic);
    private final Object externalReturnChemicalHandler = AdaptivePatternProviderExternalHandlers.createChemicalHandler(this::getAdaptiveLogic);
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
        return !this.upgrades.isEmpty();
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

        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null ? profile.displayName() : this.getMainMenuIcon().getHoverName();
    }

    @Override
    public Component getGuiDisplayName() {
        var adjacentGroup = getSingleAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return AdaptivePatternProviderDisplayHelper.decorateAttachedMachineName(adjacentGroup.name(), getResolvedProviderNameForGui());
        }

        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(getAdaptiveProviderVariantTranslationKey(), profile.displayName()) : this.getMainMenuIcon().getHoverName();
    }

    @Override
    public Component getTerminalDisplayName() {
        var adjacentGroup = getSingleAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return AdaptivePatternProviderDisplayHelper.decorateAttachedMachineName(adjacentGroup.name(), getResolvedInternalProviderName());
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
            var specialGroup = AdaptivePatternProviderResolver.resolveSpecialAdjacentMachineGroup(hostLevel, hostPos.relative(side));
            if (specialGroup != null) {
                return specialGroup;
            }
        }

        var groups = getAdjacentMachineGroups();
        return groups.size() == 1 ? groups.iterator().next() : null;
    }

    @Override
    public boolean isMeteoriteProviderSelected() {
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null && profile.kind() == AdaptivePatternProviderResolver.ProviderKind.METEORITE;
    }

    @Override
    public boolean isAdvancedAeProviderSelected() {
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == AdaptivePatternProviderResolver.ProviderKind.ADVANCED_SMALL || profile.kind() == AdaptivePatternProviderResolver.ProviderKind.ADVANCED_EXTENDED);
    }

    @Override
    public boolean isAe2LightningTechOverloadedProviderSelected() {
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null && profile.kind() == AdaptivePatternProviderResolver.ProviderKind.AE2LT_OVERLOADED;
    }

    @Override
    public boolean isAppliedCreateMechanicalProviderSelected() {
        if (!AdaptivePatternProviderExternalHandlers.supportsMechanicalProviders()) {
            return false;
        }
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == AdaptivePatternProviderResolver.ProviderKind.APPLIED_CREATE_ANDESITE || profile.kind() == AdaptivePatternProviderResolver.ProviderKind.APPLIED_CREATE_BRASS);
    }

    @Override
    public boolean isResonatingProviderSelected() {
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        if (profile == null) {
            return false;
        }

        return profile.kind() == AdaptivePatternProviderResolver.ProviderKind.RESONATING || profile.kind() == AdaptivePatternProviderResolver.ProviderKind.EXTENDED_RESONATING;
    }

    @Override
    public boolean supportsFilteredImportToggle() {
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == AdaptivePatternProviderResolver.ProviderKind.ADVANCED_SMALL || profile.kind() == AdaptivePatternProviderResolver.ProviderKind.ADVANCED_EXTENDED || profile.kind() == AdaptivePatternProviderResolver.ProviderKind.AE2LT_OVERLOADED);
    }

    @Override
    public AdaptivePatternProviderModes.Ae2LtProviderMode getAe2LtProviderMode() {
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
    public AdaptivePatternProviderModes.Ae2LtReturnMode getAe2LtReturnMode() {
        return getAdaptiveState().getAe2LtReturnMode();
    }

    @Override
    public void cycleAe2LtReturnMode() {
        getAdaptiveState().cycleAe2LtReturnMode();
        this.onAe2LtStateChanged();
    }

    @Override
    public AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode getAe2LtWirelessDispatchMode() {
        return getAdaptiveState().getAe2LtWirelessDispatchMode();
    }

    @Override
    public void cycleAe2LtWirelessDispatchMode() {
        getAdaptiveState().cycleAe2LtWirelessDispatchMode();
        this.onAe2LtStateChanged();
    }

    @Override
    public AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode getAe2LtWirelessSpeedMode() {
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

        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
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
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        ItemStack providerIcon = profile != null ? profile.mainMenuIcon() : null;
        return AdaptivePatternProviderDisplayHelper.resolveMainMenuIcon(
                adjacentGroup,
                providerIcon,
                getProviderBlock().get().asItem().getDefaultInstance());
    }

    @Override
    public ItemStack getProviderMainMenuIcon() {
        return getMainMenuIcon();
    }

    @Override
    public appeng.api.implementations.blockentities.PatternContainerGroup getTerminalGroup() {
        var baseGroup = buildAdaptiveTerminalGroup();
        int unlockedSlots = getConfiguredPatternSlotCount();
        int totalSlots = getCurrentProviderMaxPatternCapacity();
        var tooltip = AdaptivePatternProviderDisplayHelper.appendLockedSlotsTooltip(
                baseGroup.tooltip(),
                getTerminalGroupLockedSlotsKey(),
                unlockedSlots,
                totalSlots);

        Component displayName = this instanceof Nameable nameable && nameable.hasCustomName() ? baseGroup.name() : getTerminalDisplayName();
        return new appeng.api.implementations.blockentities.PatternContainerGroup(
                baseGroup.icon(),
                displayName,
                tooltip);
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
        return AdaptivePatternProviderDisplayHelper.getConfiguredPatternSlotCount(
                getAdaptiveState().getProviderStack(),
                getProviderSlotLimit());
    }

    private int getCurrentProviderMaxPatternCapacity() {
        return AdaptivePatternProviderDisplayHelper.getMaxPatternCapacity(
                getAdaptiveState().getProviderStack(),
                getProviderSlotLimit(),
                AdaptivePatternProviderState.MAX_PATTERN_SLOTS);
    }

    private int getExtraProviderSlotsFromCapacityCards() {
        if (this.upgrades == null) {
            return 0;
        }
        return Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD)) * AdaptivePatternProviderState.EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD;
    }

    @Nullable
    private AdaptivePatternProviderResolver.ProviderProfile getProviderProfile() {
        return AdaptivePatternProviderResolver.resolveProviderProfile(getAdaptiveState().getProviderStack());
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
        var icon = this.getTerminalIcon();
        return AdaptivePatternProviderDisplayHelper.createTerminalFallbackGroup(
                icon,
                List.copyOf(groups));
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
            var group = AdaptivePatternProviderDisplayHelper.resolveAdjacentMachineGroup(hostLevel, sidePos, side.getOpposite());
            if (group != null) {
                groups.add(group);
            }
        }
        return groups;
    }

    private Component getResolvedProviderNameForGui() {
        return AdaptivePatternProviderDisplayHelper.getGuiProviderName(
                getAdaptiveState().getProviderStack(),
                getProviderTranslationKey(),
                getAdaptiveProviderVariantTranslationKey());
    }

    private Component getResolvedProviderNameForTerminal() {
        return AdaptivePatternProviderDisplayHelper.getTerminalProviderName(
                getAdaptiveState().getProviderStack(),
                getProviderTranslationKey());
    }

    private Component getResolvedInternalProviderName() {
        return AdaptivePatternProviderDisplayHelper.getInternalProviderName(
                getAdaptiveState().getProviderStack(),
                getProviderTranslationKey());
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
}
