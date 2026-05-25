package com.fish_dan_.data_energistics.integration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Ae2LtRuntimeBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static @Nullable Method machineAdapterFindMethod;
    private static @Nullable Method adapterCanAcceptMethod;
    private static @Nullable Method adapterPushCopiesMethod;
    private static @Nullable Method adapterFlushOverflowMethod;
    private static @Nullable Method adapterExtractOutputsMethod;
    private static @Nullable Method pushResultAcceptedCopiesMethod;
    private static @Nullable Method pushResultOverflowMethod;
    private static @Nullable Method powerCostMaxAffordableMethod;
    private static @Nullable Method powerCostConsumeMethod;
    private static @Nullable Method ejectUnregisterAllMethod;
    private static @Nullable Method ejectRegisterMethod;
    private static @Nullable Method dimPosDimensionMethod;
    private static @Nullable Method dimPosPosMethod;
    private static @Nullable Method ghostOutputSetLevelMethod;
    private static @Nullable Constructor<?> ghostOutputConstructor;
    private static @Nullable Constructor<?> ejectEntryConstructor;
    private static boolean initialized;
    private static boolean available;
    private static boolean initFailureLogged;

    private Ae2LtRuntimeBridge() {
    }

    public static boolean isAvailable() {
        if (!Ae2LtCompat.isLoaded()) {
            return false;
        }

        return ensureInitialized();
    }

    public static @Nullable List<GenericStack> pushWirelessConnection(ServerLevel targetLevel,
                                                                      AdaptiveWirelessConnection connection,
                                                                      IPatternDetails patternDetails,
                                                                      KeyCounter[] inputHolder,
                                                                      boolean blocking,
                                                                      Set<AEKey> patternInputs,
                                                                      IActionSource actionSource) {
        if (!isAvailable()) {
            return null;
        }

        try {
            Object adapter = machineAdapterFindMethod.invoke(null, targetLevel, connection.pos());
            if (adapter == null) {
                return null;
            }

            Object canAccept = adapterCanAcceptMethod.invoke(
                    adapter,
                    targetLevel,
                    connection.pos(),
                    connection.boundFace(),
                    patternDetails
            );
            if (!Boolean.TRUE.equals(canAccept)) {
                return null;
            }

            Object result = adapterPushCopiesMethod.invoke(
                    adapter,
                    targetLevel,
                    connection.pos(),
                    connection.boundFace(),
                    patternDetails,
                    inputHolder,
                    1,
                    blocking,
                    patternInputs,
                    actionSource
            );
            if (result == null) {
                return null;
            }

            Object acceptedCopies = pushResultAcceptedCopiesMethod.invoke(result);
            if (!(acceptedCopies instanceof Number number) || number.intValue() == 0) {
                return null;
            }

            Object overflow = pushResultOverflowMethod.invoke(result);
            if (!(overflow instanceof List<?> list)) {
                return List.of();
            }

            List<GenericStack> converted = new ArrayList<>(list.size());
            for (Object entry : list) {
                if (entry instanceof GenericStack stack) {
                    converted.add(stack);
                }
            }
            return converted;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean flushWirelessOverflow(ServerLevel targetLevel,
                                                AdaptiveWirelessConnection connection,
                                                List<GenericStack> overflow,
                                                IActionSource actionSource) {
        if (!isAvailable()) {
            return false;
        }

        try {
            Object adapter = machineAdapterFindMethod.invoke(null, targetLevel, connection.pos());
            return adapter != null && Boolean.TRUE.equals(adapterFlushOverflowMethod.invoke(
                    adapter,
                    targetLevel,
                    connection.pos(),
                    connection.boundFace(),
                    overflow,
                    actionSource
            ));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static List<GenericStack> extractOutputs(ServerLevel level,
                                                    BlockPos pos,
                                                    Direction face,
                                                    @Nullable Object allowedOutputFilter,
                                                    IActionSource actionSource) {
        if (!isAvailable() || allowedOutputFilter == null) {
            return List.of();
        }

        try {
            Object adapter = machineAdapterFindMethod.invoke(null, level, pos);
            if (adapter == null) {
                return List.of();
            }

            Object outputs = adapterExtractOutputsMethod.invoke(adapter, level, pos, face, allowedOutputFilter, actionSource);
            if (!(outputs instanceof List<?> list)) {
                return List.of();
            }

            List<GenericStack> converted = new ArrayList<>(list.size());
            for (Object entry : list) {
                if (entry instanceof GenericStack stack) {
                    converted.add(stack);
                }
            }
            return converted;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static long maxAffordable(IGrid grid, AEKey key, long amount) {
        if (!isAvailable()) {
            return 0L;
        }

        try {
            Object result = powerCostMaxAffordableMethod.invoke(null, grid, key, amount);
            return result instanceof Number number ? number.longValue() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static void consume(IGrid grid, AEKey key, long amount) {
        if (!isAvailable()) {
            return;
        }

        try {
            powerCostConsumeMethod.invoke(null, grid, key, amount);
        } catch (Exception ignored) {
        }
    }

    public static void refreshEjectRegistrations(BlockEntity host,
                                                 List<AdaptiveWirelessConnection> connections,
                                                 boolean ejectModeEnabled,
                                                 boolean wirelessModeEnabled) {
        if (!isAvailable() || !(host.getLevel() instanceof ServerLevel level)) {
            return;
        }

        try {
            Object removed = ejectUnregisterAllMethod.invoke(null, host, true);
            invalidateCapabilities(removed, level);

            if (!ejectModeEnabled || !wirelessModeEnabled) {
                return;
            }

            for (var connection : connections) {
                if (!connection.dimension().equals(level.dimension())) {
                    continue;
                }

                ServerLevel targetLevel = level.getServer().getLevel(connection.dimension());
                if (targetLevel == null) {
                    continue;
                }

                BlockPos adjacentPos = connection.pos().relative(connection.boundFace());
                Direction queryFace = connection.boundFace().getOpposite();
                Object ghostBlockEntity = ghostOutputConstructor.newInstance(adjacentPos);
                ghostOutputSetLevelMethod.invoke(ghostBlockEntity, targetLevel);

                Object entry = ejectEntryConstructor.newInstance(
                        new WeakReference<>(host),
                        ghostBlockEntity,
                        level.dimension(),
                        host.getBlockPos()
                );

                ejectRegisterMethod.invoke(null, targetLevel.dimension(), adjacentPos.asLong(), queryFace, entry);
                targetLevel.invalidateCapabilities(adjacentPos);
            }
        } catch (Exception ignored) {
        }
    }

    private static void invalidateCapabilities(@Nullable Object positions, ServerLevel sourceLevel) {
        if (!(positions instanceof Iterable<?> iterable)) {
            return;
        }

        var server = sourceLevel.getServer();
        for (Object dimPos : iterable) {
            try {
                Object dimension = dimPosDimensionMethod.invoke(dimPos);
                Object pos = dimPosPosMethod.invoke(dimPos);
                if (!(dimension instanceof net.minecraft.resources.ResourceKey<?> key)
                        || !(pos instanceof BlockPos blockPos)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                ServerLevel targetLevel = server.getLevel((net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) key);
                if (targetLevel != null) {
                    targetLevel.invalidateCapabilities(blockPos);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean ensureInitialized() {
        if (initialized) {
            return available;
        }
        synchronized (Ae2LtRuntimeBridge.class) {
            if (initialized) {
                return available;
            }
            try {
                Class<?> machineAdapterRegistryClass = Class.forName(Ae2LtInternalNames.MACHINE_ADAPTER_REGISTRY);
                machineAdapterFindMethod = machineAdapterRegistryClass.getMethod("find", net.minecraft.world.level.Level.class, BlockPos.class);

                Class<?> machineAdapterClass = Class.forName(Ae2LtInternalNames.MACHINE_ADAPTER);
                adapterCanAcceptMethod = machineAdapterClass.getMethod(
                        "canAccept",
                        ServerLevel.class,
                        BlockPos.class,
                        Direction.class,
                        IPatternDetails.class
                );
                adapterPushCopiesMethod = machineAdapterClass.getMethod(
                        "pushCopies",
                        ServerLevel.class,
                        BlockPos.class,
                        Direction.class,
                        IPatternDetails.class,
                        KeyCounter[].class,
                        int.class,
                        boolean.class,
                        Set.class,
                        IActionSource.class
                );
                adapterFlushOverflowMethod = machineAdapterClass.getMethod(
                        "flushOverflow",
                        ServerLevel.class,
                        BlockPos.class,
                        Direction.class,
                        List.class,
                        IActionSource.class
                );
                Class<?> allowedOutputFilterClass = Class.forName(Ae2LtInternalNames.ALLOWED_OUTPUT_FILTER);
                adapterExtractOutputsMethod = machineAdapterClass.getMethod(
                        "extractOutputs",
                        ServerLevel.class,
                        BlockPos.class,
                        Direction.class,
                        allowedOutputFilterClass,
                        IActionSource.class
                );

                Class<?> pushResultClass = Class.forName(Ae2LtInternalNames.PUSH_RESULT);
                pushResultAcceptedCopiesMethod = pushResultClass.getMethod("acceptedCopies");
                pushResultOverflowMethod = pushResultClass.getMethod("overflow");

                Class<?> powerCostClass = Class.forName(Ae2LtInternalNames.POWER_COST_UTIL);
                powerCostMaxAffordableMethod = powerCostClass.getMethod("maxAffordable", IGrid.class, AEKey.class, long.class);
                powerCostConsumeMethod = powerCostClass.getMethod("consume", IGrid.class, AEKey.class, long.class);

                Class<?> ejectRegistryClass = Class.forName(Ae2LtInternalNames.EJECT_MODE_REGISTRY);
                ejectUnregisterAllMethod = ejectRegistryClass.getMethod("unregisterAll", BlockEntity.class, boolean.class);
                Class<?> ejectEntryClass = Class.forName(Ae2LtInternalNames.EJECT_ENTRY);
                ejectEntryConstructor = ejectEntryClass.getConstructor(
                        WeakReference.class,
                        BlockEntity.class,
                        net.minecraft.resources.ResourceKey.class,
                        BlockPos.class
                );
                ejectRegisterMethod = ejectRegistryClass.getMethod(
                        "register",
                        net.minecraft.resources.ResourceKey.class,
                        long.class,
                        Direction.class,
                        ejectEntryClass
                );

                Class<?> dimPosClass = Class.forName(Ae2LtInternalNames.EJECT_DIM_POS);
                dimPosDimensionMethod = dimPosClass.getMethod("dimension");
                dimPosPosMethod = dimPosClass.getMethod("pos");

                Class<?> ghostClass = Class.forName(Ae2LtInternalNames.GHOST_OUTPUT_BLOCK_ENTITY);
                ghostOutputConstructor = ghostClass.getConstructor(BlockPos.class);
                ghostOutputSetLevelMethod = ghostClass.getMethod("setLevel", net.minecraft.world.level.Level.class);
                available = true;
            } catch (ReflectiveOperationException | LinkageError ex) {
                clearResolvedMembers();
                available = false;
                logBridgeUnavailableOnce(ex);
            } finally {
                initialized = true;
            }
            return available;
        }
    }

    private static void clearResolvedMembers() {
        machineAdapterFindMethod = null;
        adapterCanAcceptMethod = null;
        adapterPushCopiesMethod = null;
        adapterFlushOverflowMethod = null;
        adapterExtractOutputsMethod = null;
        pushResultAcceptedCopiesMethod = null;
        pushResultOverflowMethod = null;
        powerCostMaxAffordableMethod = null;
        powerCostConsumeMethod = null;
        ejectUnregisterAllMethod = null;
        ejectRegisterMethod = null;
        dimPosDimensionMethod = null;
        dimPosPosMethod = null;
        ghostOutputSetLevelMethod = null;
        ghostOutputConstructor = null;
        ejectEntryConstructor = null;
    }

    private static void logBridgeUnavailableOnce(Throwable ex) {
        if (initFailureLogged) {
            return;
        }
        initFailureLogged = true;
        LOGGER.warn("AE2LT runtime bridge disabled; Data Energistics will keep running without AE2LT internal features", ex);
    }
}
