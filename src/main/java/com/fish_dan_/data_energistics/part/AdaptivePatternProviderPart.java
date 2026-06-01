package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderExternalHandlers;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnFluidHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderReturnItemHandler;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderState;
import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;
import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.integration.AppMekCompat;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
import appeng.items.parts.PartModels;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.crafting.PatternProviderPart;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AdaptivePatternProviderPart extends PatternProviderPart implements InternalInventoryHost, IUpgradeableObject, AdaptivePatternProviderHost {

    private static final ResourceLocation MODEL_BASE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/adaptive_pattern_provider_base");

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
    private final IItemHandler externalReturnItemHandler = new AdaptivePatternProviderReturnItemHandler(this::getLogic);
    private final IFluidHandler externalReturnFluidHandler = new AdaptivePatternProviderReturnFluidHandler(this::getLogic);
    private final Object externalReturnChemicalHandler = AdaptivePatternProviderExternalHandlers.createChemicalHandler(this::getLogic);

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

    public IItemHandler getExternalReturnItemHandler() {
        return this.externalReturnItemHandler;
    }

    public IFluidHandler getExternalReturnFluidHandler() {
        return this.externalReturnFluidHandler;
    }

    public Object getExternalReturnChemicalHandler() {
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
            return AdaptivePatternProviderBlockEntity.decorateAttachedMachineName(
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
            return AdaptivePatternProviderBlockEntity.decorateAttachedMachineName(
                    adjacentGroup.name(),
                    getResolvedInternalProviderName());
        }
        return getResolvedProviderNameForTerminal();
    }

    @Override
    public @Nullable PatternContainerGroup getPrimaryAttachedMachineGroup() {
        var blockEntity = this.getBlockEntity();
        var level = blockEntity != null ? blockEntity.getLevel() : null;
        var side = this.getSide();
        if (blockEntity == null || level == null || side == null) {
            return null;
        }

        BlockPos adjacentPos = blockEntity.getBlockPos().relative(side);
        PatternContainerGroup specialGroup = AdaptivePatternProviderResolver.resolveSpecialAdjacentMachineGroup(level, adjacentPos);
        if (specialGroup != null) {
            return specialGroup;
        }

        return getAdjacentMachineGroup();
    }

    @Override
    public boolean isMeteoriteProviderSelected() {
        return AdaptivePatternProviderBlockEntity.getResolvedProviderKind(getProviderStack()) == AdaptivePatternProviderBlockEntity.ProviderKind.METEORITE;
    }

    @Override
    public boolean isAdvancedAeProviderSelected() {
        var kind = AdaptivePatternProviderBlockEntity.getResolvedProviderKind(getProviderStack());
        return kind == AdaptivePatternProviderBlockEntity.ProviderKind.ADVANCED_SMALL || kind == AdaptivePatternProviderBlockEntity.ProviderKind.ADVANCED_EXTENDED;
    }

    @Override
    public boolean isAe2LightningTechOverloadedProviderSelected() {
        return AdaptivePatternProviderBlockEntity.getResolvedProviderKind(getProviderStack()) == AdaptivePatternProviderBlockEntity.ProviderKind.AE2LT_OVERLOADED;
    }

    @Override
    public boolean isAppliedCreateMechanicalProviderSelected() {
        if (!AdaptivePatternProviderExternalHandlers.supportsMechanicalProviders()) {
            return false;
        }
        var kind = AdaptivePatternProviderBlockEntity.getResolvedProviderKind(getProviderStack());
        return kind == AdaptivePatternProviderBlockEntity.ProviderKind.APPLIED_CREATE_ANDESITE || kind == AdaptivePatternProviderBlockEntity.ProviderKind.APPLIED_CREATE_BRASS;
    }

    @Override
    public boolean isResonatingProviderSelected() {
        var kind = AdaptivePatternProviderBlockEntity.getResolvedProviderKind(getProviderStack());
        return kind == AdaptivePatternProviderBlockEntity.ProviderKind.RESONATING || kind == AdaptivePatternProviderBlockEntity.ProviderKind.EXTENDED_RESONATING;
    }

    @Override
    public boolean supportsFilteredImportToggle() {
        var kind = AdaptivePatternProviderBlockEntity.getResolvedProviderKind(getProviderStack());
        return kind == AdaptivePatternProviderBlockEntity.ProviderKind.ADVANCED_SMALL || kind == AdaptivePatternProviderBlockEntity.ProviderKind.ADVANCED_EXTENDED || kind == AdaptivePatternProviderBlockEntity.ProviderKind.AE2LT_OVERLOADED;
    }

    @Override
    public AdaptivePatternProviderBlockEntity.Ae2LtProviderMode getAe2LtProviderMode() {
        return getAdaptiveState().getAe2LtProviderMode();
    }

    @Override
    public void cycleAe2LtProviderMode() {
        getAdaptiveState().cycleAe2LtProviderMode();
        onAdaptiveStateChanged();
    }

    @Override
    public boolean isAe2LtWirelessMode() {
        return getAdaptiveState().isAe2LtWirelessMode();
    }

    @Override
    public AdaptivePatternProviderBlockEntity.Ae2LtReturnMode getAe2LtReturnMode() {
        return getAdaptiveState().getAe2LtReturnMode();
    }

    @Override
    public void cycleAe2LtReturnMode() {
        getAdaptiveState().cycleAe2LtReturnMode();
        onAdaptiveStateChanged();
    }

    @Override
    public AdaptivePatternProviderBlockEntity.Ae2LtWirelessDispatchMode getAe2LtWirelessDispatchMode() {
        return getAdaptiveState().getAe2LtWirelessDispatchMode();
    }

    @Override
    public void cycleAe2LtWirelessDispatchMode() {
        getAdaptiveState().cycleAe2LtWirelessDispatchMode();
        onAdaptiveStateChanged();
    }

    @Override
    public AdaptivePatternProviderBlockEntity.Ae2LtWirelessSpeedMode getAe2LtWirelessSpeedMode() {
        return getAdaptiveState().getAe2LtWirelessSpeedMode();
    }

    @Override
    public void cycleAe2LtWirelessSpeedMode() {
        getAdaptiveState().cycleAe2LtWirelessSpeedMode();
        onAdaptiveStateChanged();
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
    public void addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {
        getAdaptiveState().addOrUpdateConnection(dimension, pos, boundFace);
        onAdaptiveStateChanged();
    }

    @Override
    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        if (getAdaptiveState().removeConnection(dimension, pos)) {
            onAdaptiveStateChanged();
            return true;
        }
        return false;
    }

    @Override
    public List<AdaptiveWirelessConnection> getConnections() {
        return getAdaptiveState().getConnections();
    }

    @Override
    public void markForClientUpdate() {
        if (this.getHost() != null) {
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), player, MenuLocators.forPart(this));
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), player, subMenu.getLocator());
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        getAdaptiveState().readFromNBT(data, registries, this.upgrades);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        getAdaptiveState().writeToNBT(data, registries, this.upgrades);
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
        if (adjacentGroup != null && adjacentGroup.icon() != null) {
            ItemStack adjacentIcon = adjacentGroup.icon().toStack();
            if (!adjacentIcon.isEmpty()) {
                return adjacentIcon;
            }
        }

        ItemStack icon = AdaptivePatternProviderResolver.getResolvedProviderMainMenuIcon(getProviderStack());
        return icon != null && !icon.isEmpty() ? icon.copy() : new ItemStack(this.getPartItem().asItem());
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (this instanceof Nameable nameable && nameable.hasCustomName()) {
            return new PatternContainerGroup(this.getTerminalIcon(), nameable.getCustomName(), List.of());
        }

        var adjacentGroup = getAdjacentMachineGroup();
        List<Component> tooltip = new ArrayList<>();
        int unlockedSlots = getConfiguredPatternSlotCount();
        int totalSlots = getCurrentProviderMaxPatternCapacity();
        if (unlockedSlots < totalSlots) {
            tooltip.add(Component.translatable(
                    "tooltip.data_energistics.adaptive_pattern_provider.terminal_hidden_slots",
                    unlockedSlots,
                    totalSlots));
        }

        return new PatternContainerGroup(
                adjacentGroup != null ? adjacentGroup.icon() : this.getTerminalIcon(),
                getTerminalDisplayName(),
                List.copyOf(tooltip));
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        if (inv == getAdaptiveState().getProviderInventory()) {
            this.getLogic().updatePatterns();
        }
        if (inv == this.upgrades) {
            getAdaptiveState().refreshProviderSlotLimit();
        }
        onAdaptiveStateChanged();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {}

    private int getConfiguredPatternSlotCount() {
        ItemStack providerStack = getProviderStack();
        int slotsPerProvider = AdaptivePatternProviderResolver.getResolvedSlotsPerProvider(providerStack);
        if (slotsPerProvider <= 0) {
            return 0;
        }

        int providerCount = Math.min(providerStack.getCount(), getProviderSlotLimit());
        return slotsPerProvider * providerCount;
    }

    private int getCurrentProviderMaxPatternCapacity() {
        int slotsPerProvider = AdaptivePatternProviderResolver.getResolvedSlotsPerProvider(getProviderStack());
        if (slotsPerProvider <= 0) {
            return 0;
        }

        return Math.min(AdaptivePatternProviderState.MAX_PATTERN_SLOTS, slotsPerProvider * getProviderSlotLimit());
    }

    private int getExtraProviderSlotsFromCapacityCards() {
        if (this.upgrades == null) {
            return 0;
        }
        return Math.max(0, this.upgrades.getInstalledUpgrades(appeng.core.definitions.AEItems.CAPACITY_CARD)) * AdaptivePatternProviderState.EXTRA_PROVIDER_SLOTS_PER_CAPACITY_CARD;
    }

    private void onAdaptiveStateChanged() {
        this.saveChanges();
        markForClientUpdate();
        this.getLogic().updatePatterns();
        this.getLogic().onHostStateChanged();
        try {
            appeng.api.networking.crafting.ICraftingProvider.requestUpdate(this.getMainNode());
        } catch (Throwable ignored) {}
    }

    private IUpgradeInventory createUpgradeInventory() {
        return UpgradeInventories.forMachine(
                this.getPartItem().asItem(),
                AdaptivePatternProviderState.BASE_UPGRADE_SLOTS,
                this::onAdaptiveStateChanged);
    }

    @Nullable
    private PatternContainerGroup getAdjacentMachineGroup() {
        var blockEntity = this.getBlockEntity();
        if (blockEntity == null) {
            return null;
        }

        var level = blockEntity.getLevel();
        var side = this.getSide();
        if (blockEntity == null || level == null || side == null) {
            return null;
        }

        BlockPos adjacentPos = blockEntity.getBlockPos().relative(side);
        if (AdaptivePatternProviderResolver.isPatternProviderAttachment(level, adjacentPos, side.getOpposite())) {
            return null;
        }
        PatternContainerGroup group = AdaptivePatternProviderResolver.resolveSpecialAdjacentMachineGroup(level, adjacentPos);
        if (group == null) {
            group = PatternContainerGroup.fromMachine(
                    level,
                    adjacentPos,
                    side.getOpposite());
        }
        return group;
    }

    private ItemStack getProviderStack() {
        return getAdaptiveState().getProviderStack();
    }

    private Component getResolvedProviderNameForGui() {
        ItemStack providerStack = getProviderStack();
        if (providerStack.isEmpty()) {
            return Component.translatable(getProviderTranslationKey());
        }

        Component displayName = AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack);
        return displayName != null ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(
                getAdaptiveProviderVariantTranslationKey(),
                displayName) : Component.translatable(getProviderTranslationKey());
    }

    private Component getResolvedProviderNameForTerminal() {
        ItemStack providerStack = getProviderStack();
        if (providerStack.isEmpty()) {
            return Component.translatable(getProviderTranslationKey());
        }

        Component displayName = AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack);
        return displayName != null ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(displayName) : Component.translatable(getProviderTranslationKey());
    }

    private Component getResolvedInternalProviderName() {
        ItemStack providerStack = getProviderStack();
        if (providerStack.isEmpty()) {
            return Component.translatable(getProviderTranslationKey());
        }

        Component displayName = AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack);
        return displayName != null ? displayName : Component.translatable(getProviderTranslationKey());
    }

    private String getProviderTranslationKey() {
        return "item.data_energistics.adaptive_pattern_provider_part";
    }

    private String getAdaptiveProviderVariantTranslationKey() {
        return "screen.data_energistics.adaptive_pattern_provider_part.provider_variant";
    }

    private AdaptivePatternProviderState getAdaptiveState() {
        if (this.adaptiveState == null) {
            this.adaptiveState = new AdaptivePatternProviderState(this, this::getProviderSlotLimit);
        }
        return this.adaptiveState;
    }
}
