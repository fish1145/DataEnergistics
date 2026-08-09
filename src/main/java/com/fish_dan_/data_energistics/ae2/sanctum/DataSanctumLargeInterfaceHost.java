package com.fish_dan_.data_energistics.ae2.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.orientation.RelativeSide;
import appeng.helpers.InterfaceLogicHost;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface DataSanctumLargeInterfaceHost extends InterfaceLogicHost {

    DataSanctumReturnInventory getReturnInventory();

    int getInstalledCapacityCardCount();

    Set<Direction> getActivePullSides();

    void setActivePullSideEnabled(Direction side, boolean enabled);

    default boolean hasActivePullSideSelection() {
        return true;
    }

    @Nullable
    default Direction getSingleActivePullSide() {
        return null;
    }

    default Direction getDefaultActivePullSide() {
        Direction side = getSingleActivePullSide();
        return side != null ? side : mapRelativeSide(RelativeSide.FRONT);
    }

    @Nullable
    Level getInterfaceLevel();

    BlockPos getInterfaceBlockPos();

    Direction mapRelativeSide(RelativeSide relativeSide);

    ItemStack getMainMenuIcon();

    default int getUnlockedPageCount() {
        return Math.min(
                DataSanctumInterfaceConstants.PAGE_COUNT,
                DataSanctumInterfaceConstants.BASE_PAGE_COUNT + getInstalledCapacityCardCount() * DataSanctumInterfaceConstants.PAGES_PER_CAPACITY_CARD);
    }
}
