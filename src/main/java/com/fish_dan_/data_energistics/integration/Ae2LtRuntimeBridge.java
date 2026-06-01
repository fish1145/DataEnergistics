package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class Ae2LtRuntimeBridge {

    private static final String MACHINE_ADAPTER_REGISTRY_CLASS = "com.moakiee.ae2lt.logic.MachineAdapterRegistry";
    private static final String EJECT_MODE_REGISTRY_CLASS = "com.moakiee.ae2lt.logic.EjectModeRegistry";
    private static final String GHOST_OUTPUT_BLOCK_ENTITY_CLASS = "com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity";
    private static final String POWER_COST_UTIL_CLASS = "com.moakiee.ae2lt.logic.energy.PowerCostUtil";

    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

    private static volatile @Nullable Access access;
    private static volatile boolean initialized;

    private Ae2LtRuntimeBridge() {}

    public static boolean isAvailable() {
        if (!ModFlags.isAe2LtLoaded()) {
            return false;
        }

        if (!initialized) {
            initialize();
        }

        return access != null;
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
            Access methods = access;
            if (methods == null) {
                return null;
            }

            Object adapter = methods.machineAdapterFind().invoke(targetLevel, connection.pos());
            if (adapter == null) {
                return null;
            }

            Object canAccept = methods.adapterCanAccept().invoke(
                    adapter,
                    targetLevel,
                    connection.pos(),
                    connection.boundFace(),
                    patternDetails);
            if (!Boolean.TRUE.equals(canAccept)) {
                return null;
            }

            Object result = methods.adapterPushCopies().invoke(
                    adapter,
                    targetLevel,
                    connection.pos(),
                    connection.boundFace(),
                    patternDetails,
                    inputHolder,
                    1,
                    blocking,
                    patternInputs,
                    actionSource);
            if (result == null) {
                return null;
            }

            Object acceptedCopies = methods.pushResultAcceptedCopies().invoke(result);
            if (!(acceptedCopies instanceof Number number) || number.intValue() == 0) {
                return null;
            }

            Object overflow = methods.pushResultOverflow().invoke(result);
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
        } catch (Throwable ignored) {
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
            Access methods = access;
            if (methods == null) {
                return false;
            }

            Object adapter = methods.machineAdapterFind().invoke(targetLevel, connection.pos());
            return adapter != null && Boolean.TRUE.equals(methods.adapterFlushOverflow().invoke(
                    adapter,
                    targetLevel,
                    connection.pos(),
                    connection.boundFace(),
                    overflow,
                    actionSource));
        } catch (Throwable ignored) {
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
            Access methods = access;
            if (methods == null) {
                return List.of();
            }

            Object adapter = methods.machineAdapterFind().invoke(level, pos);
            if (adapter == null) {
                return List.of();
            }

            Object outputs = methods.adapterExtractOutputs().invoke(adapter, level, pos, face, allowedOutputFilter, actionSource);
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
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static long maxAffordable(IGrid grid, AEKey key, long amount) {
        if (!isAvailable()) {
            return 0L;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return 0L;
            }

            Object result = methods.powerCostMaxAffordable().invoke(grid, key, amount);
            return result instanceof Number number ? number.longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static void consume(IGrid grid, AEKey key, long amount) {
        if (!isAvailable()) {
            return;
        }

        try {
            Access methods = access;
            if (methods != null) {
                methods.powerCostConsume().invoke(grid, key, amount);
            }
        } catch (Throwable ignored) {}
    }

    public static void refreshEjectRegistrations(BlockEntity host,
                                                 List<AdaptiveWirelessConnection> connections,
                                                 boolean ejectModeEnabled,
                                                 boolean wirelessModeEnabled) {
        if (!isAvailable() || !(host.getLevel() instanceof ServerLevel level)) {
            return;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return;
            }

            Object removed = methods.ejectUnregisterAll().invoke(host, true);
            invalidateCapabilities(methods, removed, level);

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
                Object ghostBlockEntity = methods.ghostOutputConstructor().invoke(adjacentPos);
                methods.ghostOutputSetLevel().invoke(ghostBlockEntity, targetLevel);

                Object entry = methods.ejectEntryConstructor().invoke(
                        new WeakReference<>(host),
                        ghostBlockEntity,
                        level.dimension(),
                        host.getBlockPos());

                methods.ejectRegister().invoke(targetLevel.dimension(), adjacentPos.asLong(), queryFace, entry);
                targetLevel.invalidateCapabilities(adjacentPos);
            }
        } catch (Throwable ignored) {}
    }

    private static void invalidateCapabilities(Access methods, @Nullable Object positions, ServerLevel sourceLevel) {
        if (!(positions instanceof Iterable<?> iterable)) {
            return;
        }

        var server = sourceLevel.getServer();
        for (Object dimPos : iterable) {
            try {
                Object dimension = methods.dimPosDimension().invoke(dimPos);
                Object pos = methods.dimPosPos().invoke(dimPos);
                if (!(dimension instanceof net.minecraft.resources.ResourceKey<?> key) || !(pos instanceof BlockPos blockPos)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                ServerLevel targetLevel = server.getLevel((net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) key);
                if (targetLevel != null) {
                    targetLevel.invalidateCapabilities(blockPos);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void initialize() {
        initialized = true;
        try {
            Class<?> machineAdapterRegistryClass = Class.forName(MACHINE_ADAPTER_REGISTRY_CLASS);
            MethodHandle machineAdapterFind = findStatic(
                    machineAdapterRegistryClass,
                    "find",
                    net.minecraft.world.level.Level.class,
                    BlockPos.class);

            Class<?> machineAdapterClass = Class.forName("com.moakiee.ae2lt.logic.MachineAdapter");
            MethodHandle adapterCanAccept = findVirtual(
                    machineAdapterClass,
                    "canAccept",
                    ServerLevel.class,
                    BlockPos.class,
                    Direction.class,
                    IPatternDetails.class);
            Class<?> pushResultClass = Class.forName("com.moakiee.ae2lt.logic.PushResult");
            MethodHandle adapterPushCopies = findVirtual(
                    machineAdapterClass,
                    "pushCopies",
                    ServerLevel.class,
                    BlockPos.class,
                    Direction.class,
                    IPatternDetails.class,
                    KeyCounter[].class,
                    int.class,
                    boolean.class,
                    Set.class,
                    IActionSource.class);
            MethodHandle adapterFlushOverflow = findVirtual(
                    machineAdapterClass,
                    "flushOverflow",
                    ServerLevel.class,
                    BlockPos.class,
                    Direction.class,
                    List.class,
                    IActionSource.class);
            Class<?> allowedOutputFilterClass = Class.forName("com.moakiee.ae2lt.logic.AllowedOutputFilter");
            MethodHandle adapterExtractOutputs = findVirtual(
                    machineAdapterClass,
                    "extractOutputs",
                    ServerLevel.class,
                    BlockPos.class,
                    Direction.class,
                    allowedOutputFilterClass,
                    IActionSource.class);

            MethodHandle pushResultAcceptedCopies = findVirtual(pushResultClass, "acceptedCopies");
            MethodHandle pushResultOverflow = findVirtual(pushResultClass, "overflow");

            Class<?> powerCostClass = Class.forName(POWER_COST_UTIL_CLASS);
            MethodHandle powerCostMaxAffordable = findStatic(powerCostClass, "maxAffordable", IGrid.class, AEKey.class, long.class);
            MethodHandle powerCostConsume = findStatic(powerCostClass, "consume", IGrid.class, AEKey.class, long.class);

            Class<?> ejectRegistryClass = Class.forName(EJECT_MODE_REGISTRY_CLASS);
            MethodHandle ejectUnregisterAll = findStatic(ejectRegistryClass, "unregisterAll", BlockEntity.class, boolean.class);
            Class<?> ejectEntryClass = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry$EjectEntry");
            MethodHandle ejectEntryConstructor = findConstructor(
                    ejectEntryClass,
                    WeakReference.class,
                    BlockEntity.class,
                    net.minecraft.resources.ResourceKey.class,
                    BlockPos.class);
            MethodHandle ejectRegister = findStatic(
                    ejectRegistryClass,
                    "register",
                    net.minecraft.resources.ResourceKey.class,
                    long.class,
                    Direction.class,
                    ejectEntryClass);

            Class<?> dimPosClass = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry$DimPos");
            MethodHandle dimPosDimension = findVirtual(dimPosClass, "dimension");
            MethodHandle dimPosPos = findVirtual(dimPosClass, "pos");

            Class<?> ghostClass = Class.forName(GHOST_OUTPUT_BLOCK_ENTITY_CLASS);
            MethodHandle ghostOutputConstructor = findConstructor(ghostClass, BlockPos.class);
            MethodHandle ghostOutputSetLevel = findVirtual(ghostClass, "setLevel", net.minecraft.world.level.Level.class);

            access = new Access(
                    machineAdapterFind,
                    adapterCanAccept,
                    adapterPushCopies,
                    adapterFlushOverflow,
                    adapterExtractOutputs,
                    pushResultAcceptedCopies,
                    pushResultOverflow,
                    powerCostMaxAffordable,
                    powerCostConsume,
                    ejectUnregisterAll,
                    ejectRegister,
                    dimPosDimension,
                    dimPosPos,
                    ghostOutputConstructor,
                    ghostOutputSetLevel,
                    ejectEntryConstructor);
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            access = null;
        }
    }

    private static MethodHandle findStatic(Class<?> owner,
                                           String methodName,
                                           Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        for (var method : owner.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && method.getName().equals(methodName)
                    && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                return PUBLIC_LOOKUP.unreflect(method);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + methodName);
    }

    private static MethodHandle findVirtual(Class<?> owner,
                                            String methodName,
                                            Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        for (var method : owner.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && method.getName().equals(methodName)
                    && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                return PUBLIC_LOOKUP.unreflect(method);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + methodName);
    }

    private static MethodHandle findConstructor(Class<?> owner,
                                                Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        return PUBLIC_LOOKUP.findConstructor(owner, MethodType.methodType(void.class, parameterTypes));
    }

    private record Access(MethodHandle machineAdapterFind,
                          MethodHandle adapterCanAccept,
                          MethodHandle adapterPushCopies,
                          MethodHandle adapterFlushOverflow,
                          MethodHandle adapterExtractOutputs,
                          MethodHandle pushResultAcceptedCopies,
                          MethodHandle pushResultOverflow,
                          MethodHandle powerCostMaxAffordable,
                          MethodHandle powerCostConsume,
                          MethodHandle ejectUnregisterAll,
                          MethodHandle ejectRegister,
                          MethodHandle dimPosDimension,
                          MethodHandle dimPosPos,
                          MethodHandle ghostOutputConstructor,
                          MethodHandle ghostOutputSetLevel,
                          MethodHandle ejectEntryConstructor) {}
}
