package com.fish_dan_.data_energistics.integration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;
import com.fish_dan_.data_energistics.compat.CompatIds;
import com.fish_dan_.data_energistics.compat.OptionalMods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class Ae2LtCompat {
    private static final boolean AE2LT_LOADED = OptionalMods.isLoaded(CompatIds.AE2LT);

    private Ae2LtCompat() {
    }

    public static boolean isLoaded() {
        return AE2LT_LOADED;
    }

    public static boolean isRuntimeBridgeAvailable() {
        return isLoaded() && Ae2LtRuntimeBridge.isAvailable();
    }

    public static @Nullable List<GenericStack> pushWirelessConnection(ServerLevel targetLevel,
                                                                      AdaptiveWirelessConnection connection,
                                                                      IPatternDetails patternDetails,
                                                                      KeyCounter[] inputHolder,
                                                                      boolean blocking,
                                                                      Set<AEKey> patternInputs,
                                                                      IActionSource actionSource) {
        return Ae2LtRuntimeBridge.pushWirelessConnection(
                targetLevel,
                connection,
                patternDetails,
                inputHolder,
                blocking,
                patternInputs,
                actionSource
        );
    }

    public static boolean flushWirelessOverflow(ServerLevel targetLevel,
                                                AdaptiveWirelessConnection connection,
                                                List<GenericStack> overflow,
                                                IActionSource actionSource) {
        return Ae2LtRuntimeBridge.flushWirelessOverflow(targetLevel, connection, overflow, actionSource);
    }

    public static List<GenericStack> extractOutputs(ServerLevel level,
                                                    BlockPos pos,
                                                    Direction face,
                                                    @Nullable Object allowedOutputFilter,
                                                    IActionSource actionSource) {
        return Ae2LtRuntimeBridge.extractOutputs(level, pos, face, allowedOutputFilter, actionSource);
    }

    public static long maxAffordable(IGrid grid, AEKey key, long amount) {
        return Ae2LtRuntimeBridge.maxAffordable(grid, key, amount);
    }

    public static void consume(IGrid grid, AEKey key, long amount) {
        Ae2LtRuntimeBridge.consume(grid, key, amount);
    }

    public static void refreshEjectRegistrations(BlockEntity host,
                                                 List<AdaptiveWirelessConnection> connections,
                                                 boolean ejectModeEnabled,
                                                 boolean wirelessModeEnabled) {
        Ae2LtRuntimeBridge.refreshEjectRegistrations(host, connections, ejectModeEnabled, wirelessModeEnabled);
    }
}
