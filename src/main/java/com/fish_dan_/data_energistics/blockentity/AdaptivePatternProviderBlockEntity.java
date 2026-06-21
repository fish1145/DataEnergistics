package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost;
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
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
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
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;

public class AdaptivePatternProviderBlockEntity extends PatternProviderBlockEntity implements InternalInventoryHost, IUpgradeableObject, AdaptivePatternProviderHost, RedstoneTuningAwareHost {

    protected static final String ADAPTIVE_PATTERN_PROVIDER_KEY = "adaptive_pattern_provider";
    private static final ResourceLocation APPFLUX_INDUCTION_CARD_ID = ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
    private static final String TERMINAL_GROUP_LOCKED_SUFFIX_SUFFIX = ".terminal_hidden_slots";
    private static final String REDSTONE_TUNING_TAG = "data_energistics_redstone_tuning_mode";
    private static final int REDSTONE_PULSE_TICKS = 1;

    @Nullable
    private AdaptivePatternProviderState adaptiveState;
    private final IUpgradeInventory upgrades;
    private final IItemHandler externalReturnItemHandler = new AdaptivePatternProviderReturnItemHandler(this::getAdaptiveLogic);
    private final IFluidHandler externalReturnFluidHandler = new AdaptivePatternProviderReturnFluidHandler(this::getAdaptiveLogic);
    private final Object externalReturnChemicalHandler = AdaptivePatternProviderExternalHandlers.createChemicalHandler(this::getAdaptiveLogic);
    private int syncedPatternSlotCount = 0;
    private RedstoneTuningMode redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
    private int redstonePulseTicks;
    private long lastPulseTickTime = Long.MIN_VALUE;
    private boolean pendingRedstoneInputCheck;
    private boolean lastRedstoneInputPowered;
    private boolean redstoneInputPulsePending;

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

    @Override
    public AppEngInternalInventory getAe2LtPackagedAdapterInventory() {
        return getAdaptiveState().getAe2LtPackagedAdapterInventory();
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
    public @Nullable PatternContainerGroup getPrimaryAttachedMachineGroup() {
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
    public boolean isAe2LtPackagedProviderSelected() {
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null && (profile.kind() == AdaptivePatternProviderResolver.ProviderKind.AE2LT_PACKAGED || profile.kind() == AdaptivePatternProviderResolver.ProviderKind.AE2LT_WIRELESS_PACKAGED);
    }

    @Override
    public boolean isAe2LtPackagedWirelessProviderSelected() {
        AdaptivePatternProviderResolver.ProviderProfile profile = getProviderProfile();
        return profile != null && profile.kind() == AdaptivePatternProviderResolver.ProviderKind.AE2LT_WIRELESS_PACKAGED;
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
        data.putString(REDSTONE_TUNING_TAG, this.redstoneTuningMode.name());
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        getAdaptiveState().readFromNBT(data, registries, this.upgrades);
        readRedstoneTuningMode(data);
        this.syncedPatternSlotCount = getConfiguredPatternSlotCount();
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        builder.set(ModDataComponents.ADAPTIVE_PATTERN_PROVIDER_SETTINGS.get(), getAdaptiveState().writeMemoryCardSettings());
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = input.get(ModDataComponents.ADAPTIVE_PATTERN_PROVIDER_SETTINGS.get());
        if (settings != null && getAdaptiveState().readMemoryCardSettings(settings)) {
            onAe2LtStateChanged();
        }
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
        clearPulseState();
        clearInputState();
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
    public PatternContainerGroup getTerminalGroup() {
        var baseGroup = buildAdaptiveTerminalGroup();
        int unlockedSlots = getConfiguredPatternSlotCount();
        int totalSlots = getCurrentProviderMaxPatternCapacity();
        var tooltip = AdaptivePatternProviderDisplayHelper.appendLockedSlotsTooltip(
                baseGroup.tooltip(),
                getTerminalGroupLockedSlotsKey(),
                unlockedSlots,
                totalSlots);

        Component displayName = this instanceof Nameable nameable && nameable.hasCustomName() ? baseGroup.name() : getTerminalDisplayName();
        return new PatternContainerGroup(
                baseGroup.icon(),
                displayName,
                tooltip);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        boolean providerInventoryChanged = inv == getAdaptiveState().getProviderInventory();
        boolean adapterInventoryChanged = inv == getAdaptiveState().getAe2LtPackagedAdapterInventory();
        AdaptivePatternProviderLogic logic = getAdaptiveLogic();

        if (providerInventoryChanged && logic != null) {
            logic.updatePatterns();
        }
        if (inv == this.upgrades) {
            getAdaptiveState().refreshProviderSlotLimit();
        }
        int oldSlotCount = this.syncedPatternSlotCount;
        int newSlotCount = getConfiguredPatternSlotCount();
        this.syncedPatternSlotCount = newSlotCount;
        this.saveChanges();
        this.markForClientUpdate();
        if ((providerInventoryChanged || adapterInventoryChanged || inv == this.upgrades) && logic != null) {
            logic.onHostStateChanged();
        }
        if (oldSlotCount != newSlotCount) {
            requestPatternAccessTerminalRefresh();
        }
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
    }

    @Override
    public boolean dataEnergistics$hasRedstoneTuningCard() {
        return this.getUpgrades().getInstalledUpgrades(ModItems.REDSTONE_TUNING_CARD.get()) > 0;
    }

    @Override
    public RedstoneTuningMode dataEnergistics$getRedstoneTuningMode() {
        return this.redstoneTuningMode;
    }

    @Override
    public boolean dataEnergistics$setRedstoneTuningMode(RedstoneTuningMode mode) {
        if (mode == null || this.redstoneTuningMode == mode) {
            return false;
        }
        clearPulseState();
        clearInputState();
        this.redstoneTuningMode = mode;
        if (mode == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            syncRedstoneInputBaseline();
        }
        this.saveChanges();
        this.markForClientUpdate();
        return true;
    }

    @Override
    public void dataEnergistics$onRedstoneTuningDispatch() {
        if (!this.dataEnergistics$hasRedstoneTuningCard() || this.redstoneTuningMode != RedstoneTuningMode.EMIT_ON_DISPATCH) {
            return;
        }
        if (this.redstonePulseTicks > 0) {
            return;
        }

        this.redstonePulseTicks = REDSTONE_PULSE_TICKS;
        notifyPulseChanged();
        schedulePulseTick();
    }

    @Override
    public void dataEnergistics$serverTick() {
        if (this.getLevel() == null) {
            return;
        }
        long gameTime = this.getLevel().getGameTime();
        if (this.lastPulseTickTime == gameTime) {
            return;
        }
        this.lastPulseTickTime = gameTime;
        if (this.pendingRedstoneInputCheck) {
            this.pendingRedstoneInputCheck = false;
            boolean powered = this.getLevel().hasNeighborSignal(this.getBlockPos());
            if (powered && !this.lastRedstoneInputPowered) {
                this.redstoneInputPulsePending = true;
                tryForcePulseUnlock();
            }
            this.lastRedstoneInputPowered = powered;
        }
        if (this.redstonePulseTicks <= 0) {
            return;
        }
        this.redstonePulseTicks--;
        if (this.redstonePulseTicks > 0) {
            schedulePulseTick();
        } else {
            notifyPulseChanged();
        }
    }

    @Override
    public boolean dataEnergistics$isRedstoneTuningPulseActive() {
        return this.redstoneTuningMode == RedstoneTuningMode.EMIT_ON_DISPATCH && this.redstonePulseTicks > 0;
    }

    @Override
    public void dataEnergistics$scheduleRedstoneInputCheck() {
        if (this.getLevel() == null || this.getLevel().isClientSide()) {
            return;
        }
        if (this.redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        boolean powered = this.getLevel().hasNeighborSignal(this.getBlockPos());
        if (powered && !this.lastRedstoneInputPowered) {
            this.redstoneInputPulsePending = true;
            tryForcePulseUnlock();
        }
        this.lastRedstoneInputPowered = powered;
        this.pendingRedstoneInputCheck = true;
        if (this.getLevel() instanceof ServerLevel level) {
            level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 1);
        }
    }

    @Override
    public boolean dataEnergistics$consumeRedstoneInputPulse() {
        boolean pending = this.redstoneInputPulsePending;
        this.redstoneInputPulsePending = false;
        return pending;
    }

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

    private void readRedstoneTuningMode(CompoundTag data) {
        if (!data.contains(REDSTONE_TUNING_TAG)) {
            this.redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
            return;
        }

        String modeName = data.getString(REDSTONE_TUNING_TAG);
        try {
            this.redstoneTuningMode = RedstoneTuningMode.valueOf(modeName);
        } catch (IllegalArgumentException e) {
            Data_Energistics.LOGGER.warn(
                    "Invalid adaptive pattern provider redstone tuning mode '{}', falling back to {}",
                    modeName,
                    RedstoneTuningMode.EMIT_ON_DISPATCH,
                    e);
            this.redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
        }
    }

    private void notifyPulseChanged() {
        if (this.getLevel() != null) {
            this.getLevel().updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        }
    }

    private void schedulePulseTick() {
        if (this.getLevel() instanceof ServerLevel level) {
            level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 1);
        }
    }

    private void clearPulseState() {
        if (this.redstonePulseTicks <= 0) {
            this.lastPulseTickTime = Long.MIN_VALUE;
            return;
        }
        this.redstonePulseTicks = 0;
        this.lastPulseTickTime = Long.MIN_VALUE;
        notifyPulseChanged();
    }

    private void clearInputState() {
        this.pendingRedstoneInputCheck = false;
        this.redstoneInputPulsePending = false;
        this.lastRedstoneInputPowered = false;
    }

    private void syncRedstoneInputBaseline() {
        if (this.getLevel() != null) {
            this.lastRedstoneInputPowered = this.getLevel().hasNeighborSignal(this.getBlockPos());
        }
    }

    private void tryForcePulseUnlock() {
        if (!this.redstoneInputPulsePending || !this.dataEnergistics$hasRedstoneTuningCard() || this.redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        AdaptivePatternProviderLogic logic = getAdaptiveLogic();
        if (logic != null && logic.dataEnergistics$forcePulseUnlock()) {
            this.redstoneInputPulsePending = false;
        }
    }

    @Nullable
    public static Item getAppliedFluxInductionCard() {
        Item item = BuiltInRegistries.ITEM.get(APPFLUX_INDUCTION_CARD_ID);
        return item == null || item == Items.AIR ? null : item;
    }

    private PatternContainerGroup buildAdaptiveTerminalGroup() {
        if (this instanceof Nameable nameable && nameable.hasCustomName()) {
            return new PatternContainerGroup(
                    this.getTerminalIcon(),
                    nameable.getCustomName(),
                    List.of());
        }

        var logic = this.getLogic();
        if (logic == null) {
            var icon = this.getTerminalIcon();
            return new PatternContainerGroup(
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
    private PatternContainerGroup getSingleAdjacentMachineGroup() {
        var groups = getAdjacentMachineGroups();
        return groups.size() == 1 ? groups.iterator().next() : null;
    }

    private LinkedHashSet<PatternContainerGroup> getAdjacentMachineGroups() {
        var hostLevel = this.getLevel();
        if (hostLevel == null) {
            return new LinkedHashSet<>();
        }

        var hostPos = this.getBlockPos();
        var sides = this.getTargets();
        var groups = new LinkedHashSet<PatternContainerGroup>(sides.size());
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
        ICraftingProvider.requestUpdate(this.getMainNode());

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
