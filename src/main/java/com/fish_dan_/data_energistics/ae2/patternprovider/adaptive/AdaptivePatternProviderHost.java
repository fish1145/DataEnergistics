package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import org.jetbrains.annotations.Nullable;

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

    boolean isResonatingProviderSelected();

    boolean isAppliedCreateMechanicalProviderSelected();

    boolean supportsFilteredImportToggle();

    boolean isAdvancedAeFilteredImportEnabled();

    void setAdvancedAeFilteredImportEnabled(boolean enabled);

    boolean isResonatingPullEnabled();

    void setResonatingPullEnabled(boolean enabled);

    void markForClientUpdate();

    ItemStack getProviderMainMenuIcon();

    @Nullable
    PatternContainerGroup getPrimaryAttachedMachineGroup();
}
