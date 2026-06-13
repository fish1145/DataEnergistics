package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AdaptivePatternProviderHost extends PatternProviderLogicHost, IUpgradeableObject {

    AppEngInternalInventory getProviderInventory();

    int getProviderSlotLimit();

    ItemStack extractProviderOverflow();

    int getPatternSlotCountForMenu();

    Component getProviderDisplayName();

    Component getGuiDisplayName();

    Component getTerminalDisplayName();

    boolean isMeteoriteProviderSelected();

    boolean isAdvancedAeProviderSelected();

    boolean isAe2LightningTechOverloadedProviderSelected();

    boolean isAe2LtPackagedProviderSelected();

    boolean isAe2LtPackagedWirelessProviderSelected();

    default boolean isAe2LtWirelessConnectableProviderSelected() {
        return isAe2LtPackagedWirelessProviderSelected() || isAe2LightningTechOverloadedProviderSelected() && isAe2LtWirelessMode();
    }

    boolean isResonatingProviderSelected();

    boolean isAppliedCreateMechanicalProviderSelected();

    boolean supportsFilteredImportToggle();

    AdaptivePatternProviderModes.Ae2LtProviderMode getAe2LtProviderMode();

    void cycleAe2LtProviderMode();

    boolean isAe2LtWirelessMode();

    AdaptivePatternProviderModes.Ae2LtReturnMode getAe2LtReturnMode();

    void cycleAe2LtReturnMode();

    AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode getAe2LtWirelessDispatchMode();

    void cycleAe2LtWirelessDispatchMode();

    AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode getAe2LtWirelessSpeedMode();

    void cycleAe2LtWirelessSpeedMode();

    boolean isAdvancedAeFilteredImportEnabled();

    void setAdvancedAeFilteredImportEnabled(boolean enabled);

    boolean isResonatingPullEnabled();

    void setResonatingPullEnabled(boolean enabled);

    void addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace);

    boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos);

    List<AdaptiveWirelessConnection> getConnections();

    void markForClientUpdate();

    ItemStack getProviderMainMenuIcon();

    @Nullable
    PatternContainerGroup getPrimaryAttachedMachineGroup();
}
