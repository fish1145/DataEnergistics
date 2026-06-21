package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Ae2LtRuntimeBridge {

    private static final String MACHINE_ADAPTER_REGISTRY_CLASS = "com.moakiee.ae2lt.logic.MachineAdapterRegistry";
    private static final String EJECT_MODE_REGISTRY_CLASS = "com.moakiee.ae2lt.logic.EjectModeRegistry";
    private static final String GHOST_OUTPUT_BLOCK_ENTITY_CLASS = "com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity";
    private static final String POWER_COST_UTIL_CLASS = "com.moakiee.ae2lt.logic.energy.PowerCostUtil";
    private static final String SMART_DOUBLING_COMPAT_CLASS = "com.moakiee.ae2lt.logic.SmartDoublingCompat";
    private static final String ADVANCED_BLOCKING_COMPAT_CLASS = "com.moakiee.ae2lt.logic.AdvancedBlockingCompat";

    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();
    private static final ConcurrentHashMap<Class<?>, Optional<MethodHandle>> OVERLOAD_PATTERN_DETAILS_VIEW_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<MethodHandle>> OVERLOAD_DETAILS_OUTPUTS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<MethodHandle>> OVERLOAD_OUTPUT_MATCH_MODE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<MethodHandle>> OVERLOAD_OUTPUT_TEMPLATE_CACHE = new ConcurrentHashMap<>();

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

    public static @Nullable List<GenericStack> pushConnection(ServerLevel targetLevel,
                                                              BlockPos pos,
                                                              Direction face,
                                                              IPatternDetails patternDetails,
                                                              KeyCounter[] inputHolder,
                                                              boolean blocking,
                                                              Set<AEKey> patternInputs,
                                                              IActionSource actionSource,
                                                              @Nullable PatternProviderTarget fallbackTarget) {
        if (!isAvailable()) {
            return null;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return null;
            }

            Object adapter = methods.machineAdapterFind().invoke(targetLevel, pos);
            if (adapter == null) {
                return null;
            }

            Object result = methods.adapterPushCopies().invoke(
                    adapter,
                    targetLevel,
                    pos,
                    face,
                    patternDetails,
                    inputHolder,
                    1,
                    blocking,
                    patternInputs,
                    actionSource,
                    fallbackTarget);
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

    public static boolean canAccept(ServerLevel targetLevel,
                                    BlockPos pos,
                                    Direction face,
                                    IPatternDetails patternDetails) {
        if (!isAvailable() || patternDetails == null) {
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return false;
            }

            Object adapter = methods.machineAdapterFind().invoke(targetLevel, pos);
            if (adapter == null) {
                return false;
            }

            Object result = methods.adapterCanAccept().invoke(adapter, targetLevel, pos, face, patternDetails);
            return result instanceof Boolean accepted && accepted;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldBypassAdvancedBlocking(PatternProviderLogic logic,
                                                       PatternProviderTarget target,
                                                       IPatternDetails patternDetails) {
        if (!isAvailable()) {
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return false;
            }

            Object result = methods.advancedBlockingShouldBypass().invoke(logic, target, patternDetails);
            return result instanceof Boolean bypass && bypass;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean flushOverflow(ServerLevel targetLevel,
                                        BlockPos pos,
                                        Direction face,
                                        List<GenericStack> overflow,
                                        IActionSource actionSource,
                                        @Nullable PatternProviderTarget fallbackTarget) {
        if (!isAvailable()) {
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return false;
            }

            Object adapter = methods.machineAdapterFind().invoke(targetLevel, pos);
            if (adapter == null) {
                return false;
            }
            Object result = methods.adapterFlushOverflow().invoke(
                    adapter,
                    targetLevel,
                    pos,
                    face,
                    overflow,
                    actionSource,
                    fallbackTarget);
            return Boolean.TRUE.equals(result);
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

            Class<?> outputSinkClass = methods.adapterOutputSinkClass();
            if (outputSinkClass != null) {
                List<GenericStack> converted = new ArrayList<>();
                Object sink = createOutputSink(outputSinkClass, converted);
                Object extracted = methods.adapterExtractOutputs().invoke(adapter, level, pos, face, allowedOutputFilter, actionSource, sink);
                return Boolean.TRUE.equals(extracted) || !converted.isEmpty() ? converted : List.of();
            } else {
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
            }
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

    public static double totalCost(KeyCounter[] inputHolder) {
        if (!isAvailable()) {
            return 0.0D;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return 0.0D;
            }

            Object result = methods.powerCostTotalCost().invoke((Object) inputHolder);
            return result instanceof Number number ? number.doubleValue() : 0.0D;
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    public static boolean canAffordRaw(IGrid grid, double totalCost) {
        if (!isAvailable()) {
            return true;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return true;
            }

            Object result = methods.powerCostCanAfford().invoke(grid, totalCost);
            return !(result instanceof Boolean canAfford) || canAfford;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void consumeRaw(IGrid grid, double totalCost) {
        if (!isAvailable()) {
            return;
        }

        try {
            Access methods = access;
            if (methods != null) {
                methods.powerCostConsumeRaw().invoke(grid, totalCost);
            }
        } catch (Throwable ignored) {}
    }

    public static @Nullable Object overloadPatternDetailsView(IPatternDetails pattern) {
        if (!isAvailable() || pattern == null) {
            return null;
        }

        Optional<MethodHandle> method = OVERLOAD_PATTERN_DETAILS_VIEW_CACHE.computeIfAbsent(
                pattern.getClass(),
                type -> findDuckMethod(type, "overloadPatternDetailsView"));
        if (method.isEmpty()) {
            return null;
        }

        try {
            return method.get().invoke(pattern);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean containsOrUnwrapped(List<IPatternDetails> patterns, IPatternDetails pattern) {
        if (patterns.contains(pattern)) {
            return true;
        }
        if (!isAvailable() || pattern == null) {
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return false;
            }

            Object result = methods.smartDoublingContainsOrUnwrapped().invoke(patterns, pattern);
            return result instanceof Boolean matched && matched;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static @Nullable IPatternDetails unwrapSmartDoublingPattern(IPatternDetails pattern) {
        if (!isAvailable() || pattern == null) {
            return null;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return null;
            }

            Object result = methods.smartDoublingUnwrap().invoke(pattern);
            return result instanceof IPatternDetails unwrapped ? unwrapped : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void applySmartDoubling(PatternProviderLogic logic, List<IPatternDetails> patterns) {
        if (!isAvailable()) {
            return;
        }

        try {
            Access methods = access;
            if (methods != null) {
                methods.smartDoublingApplyTo().invoke(logic, patterns);
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    public static @Nullable List<Object> overloadOutputs(Object overloadDetails) {
        if (!isAvailable() || overloadDetails == null) {
            return null;
        }

        Optional<MethodHandle> method = OVERLOAD_DETAILS_OUTPUTS_CACHE.computeIfAbsent(
                overloadDetails.getClass(),
                type -> findDuckMethod(type, "outputs"));
        if (method.isEmpty()) {
            return null;
        }

        try {
            Object result = method.get().invoke(overloadDetails);
            return result instanceof List<?> list ? (List<Object>) list : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @Nullable String overloadOutputMatchMode(Object outputSlot) {
        if (!isAvailable() || outputSlot == null) {
            return null;
        }

        Optional<MethodHandle> method = OVERLOAD_OUTPUT_MATCH_MODE_CACHE.computeIfAbsent(
                outputSlot.getClass(),
                type -> findDuckMethod(type, "matchMode"));
        if (method.isEmpty()) {
            return null;
        }

        try {
            Object result = method.get().invoke(outputSlot);
            return result == null ? null : String.valueOf(result);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @Nullable ItemStack overloadOutputTemplate(Object outputSlot) {
        if (!isAvailable() || outputSlot == null) {
            return null;
        }

        Optional<MethodHandle> method = OVERLOAD_OUTPUT_TEMPLATE_CACHE.computeIfAbsent(
                outputSlot.getClass(),
                type -> findDuckMethod(type, "template"));
        if (method.isEmpty()) {
            return null;
        }

        try {
            Object result = method.get().invoke(outputSlot);
            return result instanceof ItemStack stack && !stack.isEmpty() ? stack.copy() : null;
        } catch (Throwable ignored) {
            return null;
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
                if (!(dimension instanceof ResourceKey<?> key) || !(pos instanceof BlockPos blockPos)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                ServerLevel targetLevel = server.getLevel((ResourceKey<Level>) key);
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
                    ServerLevel.class,
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
                    IActionSource.class,
                    PatternProviderTarget.class);
            MethodHandle adapterFlushOverflow = findVirtual(
                    machineAdapterClass,
                    "flushOverflow",
                    ServerLevel.class,
                    BlockPos.class,
                    Direction.class,
                    List.class,
                    IActionSource.class,
                    PatternProviderTarget.class);
            Class<?> allowedOutputFilterClass = Class.forName("com.moakiee.ae2lt.logic.AllowedOutputFilter");
            Class<?> outputSinkClass = null;
            MethodHandle adapterExtractOutputs;
            try {
                adapterExtractOutputs = findVirtual(
                        machineAdapterClass,
                        "extractOutputs",
                        ServerLevel.class,
                        BlockPos.class,
                        Direction.class,
                        allowedOutputFilterClass,
                        IActionSource.class);
            } catch (NoSuchMethodException oldSignatureMissing) {
                outputSinkClass = Class.forName("com.moakiee.ae2lt.logic.MachineAdapter$OutputSink");
                adapterExtractOutputs = findVirtual(
                        machineAdapterClass,
                        "extractOutputs",
                        ServerLevel.class,
                        BlockPos.class,
                        Direction.class,
                        allowedOutputFilterClass,
                        IActionSource.class,
                        outputSinkClass);
            }

            MethodHandle pushResultAcceptedCopies = findVirtual(pushResultClass, "acceptedCopies");
            MethodHandle pushResultOverflow = findVirtual(pushResultClass, "overflow");

            Class<?> powerCostClass = Class.forName(POWER_COST_UTIL_CLASS);
            MethodHandle powerCostMaxAffordable = findStatic(powerCostClass, "maxAffordable", IGrid.class, AEKey.class, long.class);
            MethodHandle powerCostConsume = findStatic(powerCostClass, "consume", IGrid.class, AEKey.class, long.class);
            MethodHandle powerCostTotalCost = findStatic(powerCostClass, "totalCost", KeyCounter[].class);
            MethodHandle powerCostCanAfford = findStatic(powerCostClass, "canAfford", IGrid.class, double.class);
            MethodHandle powerCostConsumeRaw = findStatic(powerCostClass, "consumeRaw", IGrid.class, double.class);

            Class<?> smartDoublingClass = Class.forName(SMART_DOUBLING_COMPAT_CLASS);
            MethodHandle smartDoublingContainsOrUnwrapped = findStatic(smartDoublingClass, "containsOrUnwrapped", List.class, IPatternDetails.class);
            MethodHandle smartDoublingUnwrap = findStatic(smartDoublingClass, "unwrap", IPatternDetails.class);
            MethodHandle smartDoublingApplyTo = findStatic(smartDoublingClass, "applyTo", PatternProviderLogic.class, List.class);

            Class<?> advancedBlockingClass = Class.forName(ADVANCED_BLOCKING_COMPAT_CLASS);
            MethodHandle advancedBlockingShouldBypass = findStatic(advancedBlockingClass, "shouldBypassBlocking", PatternProviderLogic.class, PatternProviderTarget.class, IPatternDetails.class);

            Class<?> ejectRegistryClass = Class.forName(EJECT_MODE_REGISTRY_CLASS);
            MethodHandle ejectUnregisterAll = findStatic(ejectRegistryClass, "unregisterAll", BlockEntity.class, boolean.class);
            Class<?> ejectEntryClass = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry$EjectEntry");
            Class<?> ghostClass = Class.forName(GHOST_OUTPUT_BLOCK_ENTITY_CLASS);
            MethodHandle ejectEntryConstructor = findConstructor(
                    ejectEntryClass,
                    WeakReference.class,
                    ghostClass,
                    ResourceKey.class,
                    BlockPos.class);
            MethodHandle ejectRegister = findStatic(
                    ejectRegistryClass,
                    "register",
                    ResourceKey.class,
                    long.class,
                    Direction.class,
                    ejectEntryClass);

            Class<?> dimPosClass = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry$DimPos");
            MethodHandle dimPosDimension = findVirtual(dimPosClass, "dimension");
            MethodHandle dimPosPos = findVirtual(dimPosClass, "pos");

            MethodHandle ghostOutputConstructor = findConstructor(ghostClass, BlockPos.class);
            MethodHandle ghostOutputSetLevel = findVirtual(ghostClass, "setLevel", Level.class);

            access = new Access(
                    machineAdapterFind,
                    adapterCanAccept,
                    adapterPushCopies,
                    adapterFlushOverflow,
                    adapterExtractOutputs,
                    outputSinkClass,
                    pushResultAcceptedCopies,
                    pushResultOverflow,
                    powerCostMaxAffordable,
                    powerCostConsume,
                    powerCostTotalCost,
                    powerCostCanAfford,
                    powerCostConsumeRaw,
                    smartDoublingContainsOrUnwrapped,
                    smartDoublingUnwrap,
                    smartDoublingApplyTo,
                    advancedBlockingShouldBypass,
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

    private static Object createOutputSink(Class<?> outputSinkClass, List<GenericStack> outputs) {
        return Proxy.newProxyInstance(
                outputSinkClass.getClassLoader(),
                new Class<?>[] { outputSinkClass },
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    switch (methodName) {
                        case "maxAccept" -> {
                            return Long.MAX_VALUE;
                        }
                        case "accept" -> {
                            return addOutputSinkStack(outputs, args);
                        }
                        case "acceptOverflow" -> {
                            addOutputSinkStack(outputs, args);
                            return null;
                        }
                        case "toString" -> {
                            return "DataEnergisticsAe2LtOutputSink";
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return args != null && args.length == 1 && proxy == args[0];
                        }
                    }
                    return defaultProxyReturnValue(method.getReturnType());
                });
    }

    private static long addOutputSinkStack(List<GenericStack> outputs, @Nullable Object[] args) {
        if (args == null || args.length < 2 || !(args[0] instanceof AEKey key) || !(args[1] instanceof Number amountNumber)) {
            return 0L;
        }

        long amount = amountNumber.longValue();
        if (amount > 0) {
            outputs.add(new GenericStack(key, amount));
        }
        return Math.max(0L, amount);
    }

    private static @Nullable Object defaultProxyReturnValue(Class<?> returnType) {
        if (returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static MethodHandle findStatic(Class<?> owner,
                                           String methodName,
                                           Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        for (var method : owner.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(methodName) && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                return PUBLIC_LOOKUP.unreflect(method);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + methodName);
    }

    private static MethodHandle findVirtual(Class<?> owner,
                                            String methodName,
                                            Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        for (var method : owner.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && method.getName().equals(methodName) && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                return PUBLIC_LOOKUP.unreflect(method);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + methodName);
    }

    private static MethodHandle findConstructor(Class<?> owner,
                                                Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        return PUBLIC_LOOKUP.findConstructor(owner, MethodType.methodType(void.class, parameterTypes));
    }

    private static Optional<MethodHandle> findDuckMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            for (var method : type.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getName().equals(methodName) && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                    return Optional.of(PUBLIC_LOOKUP.unreflect(method));
                }
            }
        } catch (IllegalAccessException | SecurityException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private record Access(MethodHandle machineAdapterFind,
                          MethodHandle adapterCanAccept,
                          MethodHandle adapterPushCopies,
                          MethodHandle adapterFlushOverflow,
                          MethodHandle adapterExtractOutputs,
                          @Nullable Class<?> adapterOutputSinkClass,
                          MethodHandle pushResultAcceptedCopies,
                          MethodHandle pushResultOverflow,
                          MethodHandle powerCostMaxAffordable,
                          MethodHandle powerCostConsume,
                          MethodHandle powerCostTotalCost,
                          MethodHandle powerCostCanAfford,
                          MethodHandle powerCostConsumeRaw,
                          MethodHandle smartDoublingContainsOrUnwrapped,
                          MethodHandle smartDoublingUnwrap,
                          MethodHandle smartDoublingApplyTo,
                          MethodHandle advancedBlockingShouldBypass,
                          MethodHandle ejectUnregisterAll,
                          MethodHandle ejectRegister,
                          MethodHandle dimPosDimension,
                          MethodHandle dimPosPos,
                          MethodHandle ghostOutputConstructor,
                          MethodHandle ghostOutputSetLevel,
                          MethodHandle ejectEntryConstructor) {}
}
