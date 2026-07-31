package com.fish_dan_.data_energistics.integration.ae2lt;

import com.fish_dan_.data_energistics.Data_Energistics;
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

    private static final Set<String> LOGGED_INVOCATION_FAILURES = ConcurrentHashMap.newKeySet();

    private static volatile @Nullable RuntimeCapabilities runtimeCapabilities;

    private Ae2LtRuntimeBridge() {}

    public static boolean isReady() {
        RuntimeCapabilities capabilities = capabilities();
        return capabilities.machine() != null && capabilities.energy() != null;
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
        MachineAccess methods = capabilities().machine();
        if (methods == null) {
            return null;
        }

        try {
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
        } catch (Throwable exception) {
            handleInvocationFailure("machine", "pushConnection", exception);
            return null;
        }
    }

    public static boolean canAccept(ServerLevel targetLevel,
                                    BlockPos pos,
                                    Direction face,
                                    IPatternDetails patternDetails) {
        MachineAccess methods = capabilities().machine();
        if (methods == null || patternDetails == null) {
            return false;
        }

        try {
            Object adapter = methods.machineAdapterFind().invoke(targetLevel, pos);
            if (adapter == null) {
                return false;
            }

            Object result = methods.adapterCanAccept().invoke(adapter, targetLevel, pos, face, patternDetails);
            return result instanceof Boolean accepted && accepted;
        } catch (Throwable exception) {
            handleInvocationFailure("machine", "canAccept", exception);
            return false;
        }
    }

    public static boolean shouldBypassAdvancedBlocking(PatternProviderLogic logic,
                                                       PatternProviderTarget target,
                                                       IPatternDetails patternDetails) {
        AdvancedBlockingAccess methods = capabilities().advancedBlocking();
        if (methods == null) {
            return false;
        }

        try {
            Object result = methods.advancedBlockingShouldBypass().invoke(logic, target, patternDetails);
            return result instanceof Boolean bypass && bypass;
        } catch (Throwable exception) {
            handleInvocationFailure("advanced-blocking", "shouldBypassBlocking", exception);
            return false;
        }
    }

    public static boolean flushOverflow(ServerLevel targetLevel,
                                        BlockPos pos,
                                        Direction face,
                                        List<GenericStack> overflow,
                                        IActionSource actionSource,
                                        @Nullable PatternProviderTarget fallbackTarget) {
        MachineAccess methods = capabilities().machine();
        if (methods == null) {
            return false;
        }

        try {
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
        } catch (Throwable exception) {
            handleInvocationFailure("machine", "flushOverflow", exception);
            return false;
        }
    }

    public static List<GenericStack> extractOutputs(ServerLevel level,
                                                    BlockPos pos,
                                                    Direction face,
                                                    @Nullable Object allowedOutputFilter,
                                                    IActionSource actionSource) {
        MachineAccess methods = capabilities().machine();
        if (methods == null || allowedOutputFilter == null) {
            return List.of();
        }

        try {
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
        } catch (Throwable exception) {
            handleInvocationFailure("machine", "extractOutputs", exception);
            return List.of();
        }
    }

    public static long maxAffordable(IGrid grid, AEKey key, long amount) {
        EnergyAccess methods = capabilities().energy();
        if (methods == null) {
            return 0L;
        }

        try {
            Object result = methods.powerCostMaxAffordable().invoke(grid, key, amount);
            return result instanceof Number number ? number.longValue() : 0L;
        } catch (Throwable exception) {
            handleInvocationFailure("energy", "maxAffordable", exception);
            return 0L;
        }
    }

    public static void consume(IGrid grid, AEKey key, long amount) {
        EnergyAccess methods = capabilities().energy();
        if (methods == null) {
            return;
        }

        try {
            methods.powerCostConsume().invoke(grid, key, amount);
        } catch (Throwable exception) {
            handleInvocationFailure("energy", "consume", exception);
        }
    }

    public static double totalCost(KeyCounter[] inputHolder) {
        EnergyAccess methods = capabilities().energy();
        if (methods == null) {
            return Double.POSITIVE_INFINITY;
        }

        try {
            Object result = methods.powerCostTotalCost().invoke((Object) inputHolder);
            return result instanceof Number number ? number.doubleValue() : Double.POSITIVE_INFINITY;
        } catch (Throwable exception) {
            handleInvocationFailure("energy", "totalCost", exception);
            return Double.POSITIVE_INFINITY;
        }
    }

    public static boolean canAffordRaw(IGrid grid, double totalCost) {
        EnergyAccess methods = capabilities().energy();
        if (methods == null) {
            return false;
        }

        try {
            Object result = methods.powerCostCanAfford().invoke(grid, totalCost);
            return result instanceof Boolean canAfford && canAfford;
        } catch (Throwable exception) {
            handleInvocationFailure("energy", "canAfford", exception);
            return false;
        }
    }

    public static void consumeRaw(IGrid grid, double totalCost) {
        EnergyAccess methods = capabilities().energy();
        if (methods == null) {
            return;
        }

        try {
            methods.powerCostConsumeRaw().invoke(grid, totalCost);
        } catch (Throwable exception) {
            handleInvocationFailure("energy", "consumeRaw", exception);
        }
    }

    public static @Nullable Object overloadPatternDetailsView(IPatternDetails pattern) {
        if (pattern == null) {
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
        } catch (Throwable exception) {
            handleInvocationFailure("overload-pattern", "overloadPatternDetailsView", exception);
            return null;
        }
    }

    public static boolean containsOrUnwrapped(List<IPatternDetails> patterns, IPatternDetails pattern) {
        if (patterns.contains(pattern)) {
            return true;
        }
        SmartDoublingAccess methods = capabilities().smartDoubling();
        if (methods == null || pattern == null) {
            return false;
        }

        try {
            Object result = methods.smartDoublingContainsOrUnwrapped().invoke(patterns, pattern);
            return result instanceof Boolean matched && matched;
        } catch (Throwable exception) {
            handleInvocationFailure("smart-doubling", "containsOrUnwrapped", exception);
            return false;
        }
    }

    public static @Nullable IPatternDetails unwrapSmartDoublingPattern(IPatternDetails pattern) {
        SmartDoublingAccess methods = capabilities().smartDoubling();
        if (methods == null || pattern == null) {
            return null;
        }

        try {
            Object result = methods.smartDoublingUnwrap().invoke(pattern);
            return result instanceof IPatternDetails unwrapped ? unwrapped : null;
        } catch (Throwable exception) {
            handleInvocationFailure("smart-doubling", "unwrap", exception);
            return null;
        }
    }

    public static void applySmartDoubling(PatternProviderLogic logic, List<IPatternDetails> patterns) {
        SmartDoublingAccess methods = capabilities().smartDoubling();
        if (methods == null) {
            return;
        }

        try {
            methods.smartDoublingApplyTo().invoke(logic, patterns);
        } catch (Throwable exception) {
            handleInvocationFailure("smart-doubling", "applyTo", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static @Nullable List<Object> overloadOutputs(Object overloadDetails) {
        if (overloadDetails == null) {
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
        } catch (Throwable exception) {
            handleInvocationFailure("overload-pattern", "outputs", exception);
            return null;
        }
    }

    public static @Nullable String overloadOutputMatchMode(Object outputSlot) {
        if (outputSlot == null) {
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
        } catch (Throwable exception) {
            handleInvocationFailure("overload-pattern", "matchMode", exception);
            return null;
        }
    }

    public static @Nullable ItemStack overloadOutputTemplate(Object outputSlot) {
        if (outputSlot == null) {
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
        } catch (Throwable exception) {
            handleInvocationFailure("overload-pattern", "template", exception);
            return null;
        }
    }

    public static void refreshEjectRegistrations(BlockEntity host,
                                                 List<AdaptiveWirelessConnection> connections,
                                                 boolean ejectModeEnabled,
                                                 boolean wirelessModeEnabled) {
        EjectAccess methods = capabilities().eject();
        if (methods == null || !(host.getLevel() instanceof ServerLevel level)) {
            return;
        }

        try {
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
        } catch (Throwable exception) {
            handleInvocationFailure("eject", "refreshRegistrations", exception);
        }
    }

    private static void invalidateCapabilities(EjectAccess methods,
                                               @Nullable Object positions,
                                               ServerLevel sourceLevel) {
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
            } catch (Throwable exception) {
                handleInvocationFailure("eject", "invalidateCapabilities", exception);
            }
        }
    }

    /**
     * Initializes and safely publishes all capability groups once. Expected optional-mod lookup failures are isolated
     * by
     * {@link Ae2LtRuntimeBootstrap}; unknown runtime failures remain fail-fast and leave initialization retryable.
     */
    private static RuntimeCapabilities capabilities() {
        RuntimeCapabilities current = runtimeCapabilities;
        if (current != null) {
            return current;
        }

        synchronized (Ae2LtRuntimeBridge.class) {
            current = runtimeCapabilities;
            if (current != null) {
                return current;
            }

            var loaded = Ae2LtRuntimeBootstrap.initialize(
                    new Ae2LtRuntimeBootstrap.Loaders<>(
                            Ae2LtRuntimeBridge::loadMachineAccess,
                            Ae2LtRuntimeBridge::loadEnergyAccess,
                            Ae2LtRuntimeBridge::loadSmartDoublingAccess,
                            Ae2LtRuntimeBridge::loadAdvancedBlockingAccess,
                            Ae2LtRuntimeBridge::loadEjectAccess),
                    (capability, exception) -> Data_Energistics.LOGGER.warn(
                            "AE2LT runtime capability {} is unavailable; only that capability was disabled.",
                            capability,
                            exception));
            current = new RuntimeCapabilities(
                    loaded.machine(),
                    loaded.energy(),
                    loaded.smartDoubling(),
                    loaded.advancedBlocking(),
                    loaded.eject());
            runtimeCapabilities = current;
            return current;
        }
    }

    /**
     * Resolves machine dispatch and output extraction as one atomic capability.
     */
    private static MachineAccess loadMachineAccess() throws ReflectiveOperationException {
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

        return new MachineAccess(
                machineAdapterFind,
                adapterCanAccept,
                adapterPushCopies,
                adapterFlushOverflow,
                adapterExtractOutputs,
                outputSinkClass,
                findVirtual(pushResultClass, "acceptedCopies"),
                findVirtual(pushResultClass, "overflow"));
    }

    /**
     * Resolves all energy accounting entry points together so missing energy support fails closed.
     */
    private static EnergyAccess loadEnergyAccess() throws ReflectiveOperationException {
        Class<?> powerCostClass = Class.forName(POWER_COST_UTIL_CLASS);
        return new EnergyAccess(
                findStatic(powerCostClass, "maxAffordable", IGrid.class, AEKey.class, long.class),
                findStatic(powerCostClass, "consume", IGrid.class, AEKey.class, long.class),
                findStatic(powerCostClass, "totalCost", KeyCounter[].class),
                findStatic(powerCostClass, "canAfford", IGrid.class, double.class),
                findStatic(powerCostClass, "consumeRaw", IGrid.class, double.class));
    }

    /**
     * Resolves the optional Smart Doubling hook independently from every core capability.
     */
    private static SmartDoublingAccess loadSmartDoublingAccess() throws ReflectiveOperationException {
        Class<?> smartDoublingClass = Class.forName(SMART_DOUBLING_COMPAT_CLASS);
        return new SmartDoublingAccess(
                findStatic(smartDoublingClass, "containsOrUnwrapped", List.class, IPatternDetails.class),
                findStatic(smartDoublingClass, "unwrap", IPatternDetails.class),
                findStatic(smartDoublingClass, "applyTo", PatternProviderLogic.class, List.class));
    }

    /**
     * Resolves the optional Advanced Blocking hook independently from Smart Doubling.
     */
    private static AdvancedBlockingAccess loadAdvancedBlockingAccess() throws ReflectiveOperationException {
        Class<?> advancedBlockingClass = Class.forName(ADVANCED_BLOCKING_COMPAT_CLASS);
        return new AdvancedBlockingAccess(findStatic(
                advancedBlockingClass,
                "shouldBypassBlocking",
                PatternProviderLogic.class,
                PatternProviderTarget.class,
                IPatternDetails.class));
    }

    /**
     * Resolves wireless EJECT registration independently from machine, energy, and pattern hooks.
     */
    private static EjectAccess loadEjectAccess() throws ReflectiveOperationException {
        Class<?> ejectRegistryClass = Class.forName(EJECT_MODE_REGISTRY_CLASS);
        Class<?> ejectEntryClass = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry$EjectEntry");
        Class<?> ghostClass = Class.forName(GHOST_OUTPUT_BLOCK_ENTITY_CLASS);
        Class<?> dimPosClass = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry$DimPos");
        return new EjectAccess(
                findStatic(ejectRegistryClass, "unregisterAll", BlockEntity.class, boolean.class),
                findStatic(
                        ejectRegistryClass,
                        "register",
                        ResourceKey.class,
                        long.class,
                        Direction.class,
                        ejectEntryClass),
                findVirtual(dimPosClass, "dimension"),
                findVirtual(dimPosClass, "pos"),
                findConstructor(ghostClass, BlockPos.class),
                findVirtual(ghostClass, "setLevel", Level.class),
                findConstructor(
                        ejectEntryClass,
                        WeakReference.class,
                        ghostClass,
                        ResourceKey.class,
                        BlockPos.class));
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
        } catch (IllegalAccessException | SecurityException exception) {
            logInvocationFailureOnce("duck-lookup", type.getName() + "." + methodName, exception);
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Logs expected cross-version invocation failures once while preserving fail-fast behavior for unknown runtime
     * exceptions and non-linkage errors.
     */
    private static void handleInvocationFailure(String capability, String operation, Throwable exception) {
        if (exception instanceof LinkageError) {
            logInvocationFailureOnce(capability, operation, exception);
            return;
        }
        Data_Energistics.LOGGER.error(
                "AE2LT {} operation {} failed with an unexpected exception.",
                capability,
                operation,
                exception);
        if (exception instanceof RuntimeException runtimeException) throw runtimeException;
        if (exception instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked exception from AE2LT " + capability + ' ' + operation,
                exception);
    }

    /**
     * Emits one diagnostic for a stable capability/operation/failure combination.
     */
    private static void logInvocationFailureOnce(String capability, String operation, Throwable exception) {
        String key = capability + ':' + operation + ':' + exception.getClass().getName();
        if (LOGGED_INVOCATION_FAILURES.add(key)) {
            Data_Energistics.LOGGER.error(
                    "AE2LT {} operation {} is unavailable for this runtime combination.",
                    capability,
                    operation,
                    exception);
        }
    }

    /**
     * Safely published aggregate whose nullable members are independent capability states.
     */
    private record RuntimeCapabilities(@Nullable MachineAccess machine,
                                       @Nullable EnergyAccess energy,
                                       @Nullable SmartDoublingAccess smartDoubling,
                                       @Nullable AdvancedBlockingAccess advancedBlocking,
                                       @Nullable EjectAccess eject) {}

    /**
     * Method handles required for machine dispatch and output extraction.
     */
    private record MachineAccess(MethodHandle machineAdapterFind,
                                 MethodHandle adapterCanAccept,
                                 MethodHandle adapterPushCopies,
                                 MethodHandle adapterFlushOverflow,
                                 MethodHandle adapterExtractOutputs,
                                 @Nullable Class<?> adapterOutputSinkClass,
                                 MethodHandle pushResultAcceptedCopies,
                                 MethodHandle pushResultOverflow) {}

    /**
     * Method handles required for fail-closed AE2LT energy accounting.
     */
    private record EnergyAccess(MethodHandle powerCostMaxAffordable,
                                MethodHandle powerCostConsume,
                                MethodHandle powerCostTotalCost,
                                MethodHandle powerCostCanAfford,
                                MethodHandle powerCostConsumeRaw) {}

    /**
     * Optional Smart Doubling pattern hooks.
     */
    private record SmartDoublingAccess(MethodHandle smartDoublingContainsOrUnwrapped,
                                       MethodHandle smartDoublingUnwrap,
                                       MethodHandle smartDoublingApplyTo) {}

    /**
     * Optional Advanced Blocking hook.
     */
    private record AdvancedBlockingAccess(MethodHandle advancedBlockingShouldBypass) {}

    /**
     * Method handles required to register and invalidate EJECT endpoints.
     */
    private record EjectAccess(MethodHandle ejectUnregisterAll,
                               MethodHandle ejectRegister,
                               MethodHandle dimPosDimension,
                               MethodHandle dimPosPos,
                               MethodHandle ghostOutputConstructor,
                               MethodHandle ghostOutputSetLevel,
                               MethodHandle ejectEntryConstructor) {}
}
