package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.accessor.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderDisplayHelper;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderExternalHandlers;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnFluidHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnItemHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderState;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.crafting.PatternProviderPart;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AdaptivePatternProviderPart extends PatternProviderPart implements InternalInventoryHost, IUpgradeableObject, AdaptivePatternProviderHost, RedstoneTuningAwareHost {

    private static final ResourceLocation MODEL_BASE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/adaptive_pattern_provider_base");
    private static final String REDSTONE_TUNING_TAG = "data_energistics_redstone_tuning_mode";
    private static final int REDSTONE_PULSE_TICKS = 1;

    @PartModels
    private static final PartModel MODELS_OFF;
    @PartModels
    private static final PartModel MODELS_ON;
    @PartModels
    private static final PartModel MODELS_HAS_CHANNEL;

    static {
        MODELS_OFF = new PartModel(MODEL_BASE,
                ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/adaptive_pattern_provider_off"));
        MODELS_ON = new PartModel(MODEL_BASE,
                ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/adaptive_pattern_provider_on"));
        MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
                ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/adaptive_pattern_provider_has_channel"));
    }

    @Nullable
    private AdaptivePatternProviderState adaptiveState;
    private final IUpgradeInventory upgrades;
    @Getter
    private final IItemHandler externalReturnItemHandler = new AdaptivePatternProviderReturnItemHandler(this::getLogic);
    @Getter
    private final IFluidHandler externalReturnFluidHandler = new AdaptivePatternProviderReturnFluidHandler(this::getLogic);
    @Getter
    private final Object externalReturnChemicalHandler = AdaptivePatternProviderExternalHandlers.createChemicalHandler(this::getLogic);
    private RedstoneTuningMode redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
    private int redstonePulseTicks;
    private long lastPulseTickTime = Long.MIN_VALUE;
    private boolean pendingRedstoneInputCheck;
    private boolean lastRedstoneInputPowered;
    private boolean redstoneInputPulsePending;

    public AdaptivePatternProviderPart(IPartItem<?> partItem) {
        super(partItem);
        this.upgrades = createUpgradeInventory();
    }

    @Override
    protected AdaptivePatternProviderLogic createLogic() {
        return new AdaptivePatternProviderLogic(this.getMainNode(), this, AdaptivePatternProviderState.MAX_PATTERN_SLOTS);
    }

    @Override
    public AdaptivePatternProviderLogic getLogic() {
        return (AdaptivePatternProviderLogic) super.getLogic();
    }

    @Override
    public AppEngInternalInventory getProviderInventory() {
        return getAdaptiveState().getProviderInventory();
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

    @Override
    public int getPatternSlotCountForMenu() {
        return getConfiguredPatternSlotCount();
    }

    @Override
    public Component getProviderDisplayName() {
        var adjacentGroup = getAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return adjacentGroup.name();
        }

        ItemStack providerStack = getProviderStack();
        Component displayName = AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack);
        return displayName != null ? displayName : this.getMainMenuIcon().getHoverName();
    }

    @Override
    public Component getGuiDisplayName() {
        var adjacentGroup = getAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return AdaptivePatternProviderDisplayHelper.decorateAttachedMachineName(
                    adjacentGroup.name(),
                    getResolvedProviderNameForGui());
        }

        ItemStack providerStack = getProviderStack();
        Component displayName = AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack);
        return displayName != null ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(
                getAdaptiveProviderVariantTranslationKey(),
                displayName) : this.getMainMenuIcon().getHoverName();
    }

    @Override
    public Component getTerminalDisplayName() {
        var adjacentGroup = getAdjacentMachineGroup();
        if (adjacentGroup != null) {
            return AdaptivePatternProviderDisplayHelper.decorateAttachedMachineName(
                    adjacentGroup.name(),
                    getResolvedInternalProviderName());
        }
        return getResolvedProviderNameForTerminal();
    }

    @Override
    public @Nullable PatternContainerGroup getPrimaryAttachedMachineGroup() {
        return getAdjacentMachineGroup();
    }

    @Override
    public boolean isMeteoriteProviderSelected() {
        return hasProviderCapability(AdaptivePatternProviderCapabilities.METEORITE);
    }

    @Override
    public boolean isAdvancedAeProviderSelected() {
        return hasProviderCapability(AdaptivePatternProviderCapabilities.ADVANCED_PATTERN);
    }

    @Override
    public boolean isAppliedCreateMechanicalProviderSelected() {
        if (!AdaptivePatternProviderExternalHandlers.supportsMechanicalProviders()) {
            return false;
        }
        return hasProviderCapability(AdaptivePatternProviderCapabilities.MECHANICAL_CRAFTING);
    }

    @Override
    public boolean isResonatingProviderSelected() {
        return hasProviderCapability(AdaptivePatternProviderCapabilities.RESONATING);
    }

    @Override
    public boolean supportsFilteredImportToggle() {
        return hasProviderCapability(AdaptivePatternProviderCapabilities.FILTERED_IMPORT);
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
        onAdaptiveStateChanged();
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
        onAdaptiveStateChanged();
    }

    @Override
    public void markForClientUpdate() {
        if (this.getHost() != null) {
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(DEMenus.ADAPTIVE_PATTERN_PROVIDER.get(), player, MenuLocators.forPart(this));
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(DEMenus.ADAPTIVE_PATTERN_PROVIDER.get(), player, subMenu.getLocator());
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        getAdaptiveState().readFromNBT(data, registries, this.upgrades);
        readRedstoneTuningMode(data);
        if (this.getLogic().reconcileConfiguredPatternSlots()) {
            this.saveChanges();
            this.markForClientUpdate();
        } else {
            this.getLogic().updatePatterns();
        }
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        getAdaptiveState().writeToNBT(data, registries, this.upgrades);
        data.putString(REDSTONE_TUNING_TAG, this.redstoneTuningMode.name());
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        builder.set(DEDataComponents.ADAPTIVE_PATTERN_PROVIDER_SETTINGS.get(), getAdaptiveState().writeMemoryCardSettings());
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        if (mode != SettingsFrom.MEMORY_CARD) {
            super.importSettings(mode, input, player);
            return;
        }

        boolean patternInventoryChanged = this.getLogic().runWithPatternInventoryCallbacksSuppressed(
                () -> super.importSettings(mode, input, player));
        CompoundTag settings = input.get(DEDataComponents.ADAPTIVE_PATTERN_PROVIDER_SETTINGS.get());
        boolean stateChanged = settings != null && getAdaptiveState().readMemoryCardSettings(settings);
        boolean patternSlotsReconciled = this.getLogic().reconcileConfiguredPatternSlotsAfterSettingsImport();
        boolean patternsUpdated = patternSlotsReconciled;
        if (patternInventoryChanged && !patternSlotsReconciled) {
            this.getLogic().updatePatterns();
            patternsUpdated = true;
        }
        if (stateChanged || patternsUpdated) {
            onAdaptiveStateChanged(patternsUpdated);
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        ItemStack stack = getProviderStack();
        if (!stack.isEmpty()) {
            drops.add(stack.copy());
        }
        if (this.upgrades != null) {
            for (ItemStack upgrade : this.upgrades) {
                if (!upgrade.isEmpty()) {
                    drops.add(upgrade.copy());
                }
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        getAdaptiveState().clearContent();
        this.upgrades.clear();
        clearRedstoneState();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        var adjacentGroup = getAdjacentMachineGroup();
        if (adjacentGroup != null && adjacentGroup.icon() != null) {
            return adjacentGroup.icon();
        }

        AEItemKey icon = AdaptivePatternProviderResolver.getResolvedProviderTerminalIcon(getProviderStack());
        return icon != null ? icon : AEItemKey.of(this.getPartItem());
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        int visibleSlots = Math.max(0, Math.min(getConfiguredPatternSlotCount(), this.getLogic().getPatternInv().size()));
        return this.getLogic().getPatternInv().getSubInventory(0, visibleSlots);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        var adjacentGroup = getAdjacentMachineGroup();
        ItemStack providerIcon = AdaptivePatternProviderResolver.getResolvedProviderMainMenuIcon(getProviderStack());
        return AdaptivePatternProviderDisplayHelper.resolveMainMenuIcon(
                adjacentGroup,
                providerIcon,
                new ItemStack(this.getPartItem().asItem()));
    }

    @Override
    public ItemStack getProviderMainMenuIcon() {
        return getMainMenuIcon();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (this.hasCustomName()) {
            return new PatternContainerGroup(this.getTerminalIcon(), this.getCustomName(), List.of());
        }

        var adjacentGroup = getAdjacentMachineGroup();
        int unlockedSlots = getConfiguredPatternSlotCount();
        int totalSlots = getCurrentProviderMaxPatternCapacity();
        var tooltip = AdaptivePatternProviderDisplayHelper.appendLockedSlotsTooltip(
                List.of(),
                "tooltip.data_energistics.adaptive_pattern_provider.terminal_hidden_slots",
                unlockedSlots,
                totalSlots);

        return new PatternContainerGroup(
                adjacentGroup != null ? adjacentGroup.icon() : this.getTerminalIcon(),
                getTerminalDisplayName(),
                tooltip);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        boolean providerInventoryChanged = inv == getAdaptiveState().getProviderInventory();
        if (inv == this.upgrades) {
            getAdaptiveState().refreshProviderSlotLimit();
        }
        boolean patternSlotsReconciled = (providerInventoryChanged || inv == this.upgrades) && this.getLogic().reconcileConfiguredPatternSlots();
        if (providerInventoryChanged && !patternSlotsReconciled) {
            this.getLogic().updatePatterns();
            patternSlotsReconciled = true;
        }
        onAdaptiveStateChanged(patternSlotsReconciled);
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {}

    @Override
    public boolean dataEnergistics$hasRedstoneTuningCard() {
        return this.getUpgrades().getInstalledUpgrades(DEItems.REDSTONE_TUNING_CARD.get()) > 0;
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
        var level = this.getLevel();
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (this.lastPulseTickTime == gameTime) {
            return;
        }
        this.lastPulseTickTime = gameTime;
        var blockEntity = this.getBlockEntity();
        if (this.pendingRedstoneInputCheck && blockEntity != null) {
            this.pendingRedstoneInputCheck = false;
            boolean powered = level.hasNeighborSignal(blockEntity.getBlockPos());
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
        var level = this.getLevel();
        var blockEntity = this.getBlockEntity();
        if (level == null || level.isClientSide() || blockEntity == null) {
            return;
        }
        if (this.redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        boolean powered = level.hasNeighborSignal(blockEntity.getBlockPos());
        if (powered && !this.lastRedstoneInputPowered) {
            this.redstoneInputPulsePending = true;
            tryForcePulseUnlock();
        }
        this.lastRedstoneInputPowered = powered;
        this.pendingRedstoneInputCheck = true;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock(), 1);
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
                getProviderStack(),
                getProviderSlotLimit());
    }

    private int getCurrentProviderMaxPatternCapacity() {
        return AdaptivePatternProviderDisplayHelper.getMaxPatternCapacity(
                getProviderStack(),
                getProviderSlotLimit(),
                AdaptivePatternProviderState.MAX_PATTERN_SLOTS);
    }

    private int getExtraProviderSlotsFromCapacityCards() {
        if (this.upgrades == null) {
            return 0;
        }
        return Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD)) * AdaptivePatternProviderState.EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD;
    }

    private void onAdaptiveStateChanged() {
        onAdaptiveStateChanged(false);
    }

    private void onAdaptiveStateChanged(boolean patternsAlreadyUpdated) {
        this.saveChanges();
        markForClientUpdate();
        if (!patternsAlreadyUpdated) {
            this.getLogic().updatePatterns();
        }
        this.getLogic().onHostStateChanged();
    }

    private IUpgradeInventory createUpgradeInventory() {
        return UpgradeInventories.forMachine(
                this.getPartItem().asItem(),
                AdaptivePatternProviderState.BASE_UPGRADE_SLOTS,
                this::onUpgradesChanged);
    }

    private void onUpgradesChanged() {
        getAdaptiveState().refreshProviderSlotLimit();
        boolean patternSlotsReconciled = this.getLogic().reconcileConfiguredPatternSlots();
        onAdaptiveStateChanged(patternSlotsReconciled);
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
                    "Invalid adaptive pattern provider part redstone tuning mode '{}', falling back to {}",
                    modeName,
                    RedstoneTuningMode.EMIT_ON_DISPATCH,
                    e);
            this.redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
        }
    }

    private void notifyPulseChanged() {
        if (this.getHost() != null) {
            this.getHost().notifyNeighbors();
        }
    }

    private void schedulePulseTick() {
        var blockEntity = this.getBlockEntity();
        if (this.getLevel() instanceof ServerLevel level && blockEntity != null) {
            level.scheduleTick(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock(), 1);
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

    private void clearRedstoneState() {
        this.redstonePulseTicks = 0;
        this.lastPulseTickTime = Long.MIN_VALUE;
        this.pendingRedstoneInputCheck = false;
        this.lastRedstoneInputPowered = false;
        this.redstoneInputPulsePending = false;
    }

    private void syncRedstoneInputBaseline() {
        var blockEntity = this.getBlockEntity();
        if (this.getLevel() != null && blockEntity != null) {
            this.lastRedstoneInputPowered = this.getLevel().hasNeighborSignal(blockEntity.getBlockPos());
        }
    }

    private void tryForcePulseUnlock() {
        if (!this.redstoneInputPulsePending || !this.dataEnergistics$hasRedstoneTuningCard() || this.redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        Object logic = this.getLogic();
        if (logic instanceof PatternProviderLogicAccessor accessor && accessor.dataEnergistics$forcePulseUnlock()) {
            this.redstoneInputPulsePending = false;
        }
    }

    @Nullable
    private PatternContainerGroup getAdjacentMachineGroup() {
        var blockEntity = this.getBlockEntity();
        if (blockEntity == null) {
            return null;
        }

        var level = blockEntity.getLevel();
        var side = this.getSide();
        if (level == null || side == null) {
            return null;
        }

        BlockPos adjacentPos = blockEntity.getBlockPos().relative(side);
        return AdaptivePatternProviderDisplayHelper.resolveAdjacentMachineGroup(level, adjacentPos, side.getOpposite());
    }

    private ItemStack getProviderStack() {
        return getAdaptiveState().getProviderStack();
    }

    private Component getResolvedProviderNameForGui() {
        return AdaptivePatternProviderDisplayHelper.getGuiProviderName(
                getProviderStack(),
                getProviderTranslationKey(),
                getAdaptiveProviderVariantTranslationKey());
    }

    private Component getResolvedProviderNameForTerminal() {
        return AdaptivePatternProviderDisplayHelper.getTerminalProviderName(
                getProviderStack(),
                getProviderTranslationKey());
    }

    private Component getResolvedInternalProviderName() {
        return AdaptivePatternProviderDisplayHelper.getInternalProviderName(
                getProviderStack(),
                getProviderTranslationKey());
    }

    private String getProviderTranslationKey() {
        return "item.data_energistics.adaptive_pattern_provider_part";
    }

    private String getAdaptiveProviderVariantTranslationKey() {
        return "screen.data_energistics.adaptive_pattern_provider_part.provider_variant";
    }

    /**
     * Checks one registered behavior on the currently installed provider.
     */
    private boolean hasProviderCapability(ResourceLocation capability) {
        return AdaptivePatternProviderResolver.hasResolvedCapability(getProviderStack(), capability);
    }

    private AdaptivePatternProviderState getAdaptiveState() {
        if (this.adaptiveState == null) {
            this.adaptiveState = new AdaptivePatternProviderState(this, this::getProviderSlotLimit);
        }
        return this.adaptiveState;
    }
}
