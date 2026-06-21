package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

public final class Ae2LtPackagedRuntimeBridge {

    private static final String MULTIBLOCK_ADAPTER_REGISTRY_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry";
    private static final String MULTIBLOCK_ADAPTER_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter";
    private static final String DISPATCH_EXECUTOR_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor";
    private static final String DISPATCH_PLAN_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan";
    private static final String BINDING_RESULT_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult";
    private static final String BINDING_MODE_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode";
    private static final String DISPATCH_RESULT_CLASS = "com.moakiee.ae2lt.packaged.logic.DispatchResult";
    private static final String ALLOWED_OUTPUT_FILTER_CLASS = "com.moakiee.ae2lt.logic.AllowedOutputFilter";
    private static final String MULTIBLOCK_ADAPTER_ITEM_CLASS = "com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem";
    private static final String VIRTUAL_CRAFTING_ADAPTER_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter";
    private static final String VIRTUAL_CRAFTING_RESULT_CLASS = "com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult";

    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();
    private static volatile @Nullable Access access;
    private static volatile boolean initialized;

    private Ae2LtPackagedRuntimeBridge() {}

    public static boolean isAvailable() {
        if (!ModFlags.isAe2LtPackagedProviderLoaded()) {
            return false;
        }

        if (!initialized) {
            initialize();
        }

        return access != null;
    }

    public static boolean isAdapterItem(ItemStack stack) {
        if (stack.isEmpty() || !isAvailable()) {
            return false;
        }

        Access methods = access;
        return methods != null && methods.adapterItemClass().isInstance(stack.getItem());
    }

    public static boolean isSupportedTarget(ServerLevel level, BlockPos pos) {
        if (!isAvailable()) {
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return false;
            }

            return findMultiblockAdapter(methods, level, pos) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAdapterStackCompatible(ServerLevel level, BlockPos pos, ItemStack adapterStack) {
        if (!isAdapterItem(adapterStack)) {
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return false;
            }

            Object adapter = findMultiblockAdapter(methods, level, pos);
            return adapter != null && adapterStackMatchesTarget(methods, adapter, level, pos, adapterStack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean dispatch(ServerLevel level,
                                   BlockPos pos,
                                   IPatternDetails patternDetails,
                                   KeyCounter[] inputHolder,
                                   ItemStack adapterStack,
                                   @Nullable Object allowedOutputFilter,
                                   IActionSource actionSource,
                                   PatternProviderReturnInventory returnInventory) {
        if (!isAvailable() || patternDetails == null) {
            logDispatch("dispatch aborted: bridge unavailable={} patternNull={} pos={}", !isAvailable(), patternDetails == null, pos);
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                logDispatch("dispatch aborted: access missing pos={}", pos);
                return false;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                logDispatch("dispatch aborted: block entity missing pos={}", pos);
                return false;
            }

            Object adapter = methods.multiblockAdapterFind().invoke(level, pos, blockEntity);
            if (adapter == null) {
                logDispatch("dispatch aborted: no multiblock adapter pos={} blockEntity={}", pos, blockEntity.getClass().getName());
                return false;
            }

            Object requiredAdapterId = methods.adapterRequiredAdapterId().invoke(adapter, level, pos);
            if (requiredAdapterId instanceof ResourceLocation) {
                Object covered = methods.adapterItemStackCovers().invoke(adapterStack, requiredAdapterId);
                if (!Boolean.TRUE.equals(covered)) {
                    logDispatch("dispatch aborted: adapter mismatch pos={} required={} adapter={}", pos, requiredAdapterId, adapterStack);
                    return false;
                }
            }

            Object binding = methods.adapterBind().invoke(adapter, level, pos, patternDetails);
            if (binding == null) {
                logDispatch("dispatch aborted: binding null pos={} pattern={}", pos, patternDetails.getClass().getName());
                return false;
            }

            Object handle = methods.bindingHandle().invoke(binding);
            Object mode = methods.bindingMode().invoke(binding);
            if (mode == methods.virtualBindingMode()) {
                return executeVirtualDispatch(methods, adapter, level, pos, patternDetails, inputHolder, handle, actionSource, returnInventory);
            }
            if (mode != methods.realBindingMode()) {
                logDispatch("dispatch aborted: binding invalid pos={} mode={} expectedReal={} expectedVirtual={}", pos, mode, methods.realBindingMode(), methods.virtualBindingMode());
                return false;
            }
            if (handle == null) {
                logDispatch("dispatch aborted: real binding handle missing pos={} mode={}", pos, mode);
                return false;
            }

            if (allowedOutputFilter != null) {
                Object outputs = methods.adapterExtractOutputs().invoke(adapter, level, pos, allowedOutputFilter, actionSource);
                insertOutputsToReturnInventory(outputs, returnInventory, actionSource);
            }

            Object canDispatch = methods.adapterCanDispatch().invoke(adapter, level, pos, handle);
            if (!Boolean.TRUE.equals(canDispatch)) {
                logDispatch("dispatch aborted: adapter cannot dispatch pos={}", pos);
                return false;
            }

            Object plan = methods.adapterPlanWithBinding().invoke(
                    adapter,
                    level,
                    pos,
                    patternDetails,
                    inputHolder,
                    handle,
                    actionSource);
            if (plan == null) {
                logDispatch("dispatch aborted: dispatch plan null pos={}", pos);
                return false;
            }

            Object result = methods.dispatchExecute().invoke(plan, actionSource, returnInventory);
            Object success = methods.dispatchResultSuccess().invoke(result);
            boolean succeeded = Boolean.TRUE.equals(success);
            logDispatch("dispatch completed: pos={} success={}", pos, succeeded);
            return succeeded;
        } catch (Throwable ignored) {
            logDispatch("dispatch threw exception pos={} type={} message={}", pos, ignored.getClass().getName(), ignored.getMessage());
            return false;
        }
    }

    public static List<GenericStack> extractOutputs(ServerLevel level,
                                                    BlockPos pos,
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

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return List.of();
            }

            Object adapter = methods.multiblockAdapterFind().invoke(level, pos, blockEntity);
            if (adapter == null || methods.virtualCraftingAdapterClass().isInstance(adapter)) {
                return List.of();
            }

            Object outputs = methods.adapterExtractOutputs().invoke(adapter, level, pos, allowedOutputFilter, actionSource);
            if (!(outputs instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }

            return collectGenericStacks(list);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static boolean executeVirtualDispatch(Access methods,
                                                  Object adapter,
                                                  ServerLevel level,
                                                  BlockPos pos,
                                                  IPatternDetails patternDetails,
                                                  KeyCounter[] inputHolder,
                                                  Object handle,
                                                  IActionSource actionSource,
                                                  PatternProviderReturnInventory returnInventory) throws Throwable {
        if (!methods.virtualCraftingAdapterClass().isInstance(adapter)) {
            logDispatch("virtual dispatch aborted: adapter does not implement virtual crafting pos={} adapter={}", pos, adapter.getClass().getName());
            return false;
        }

        Object result = methods.virtualPlanWithBinding().invoke(adapter, level, pos, patternDetails, inputHolder, handle, actionSource);
        if (result == null) {
            logDispatch("virtual dispatch aborted: virtual plan null pos={}", pos);
            return false;
        }

        Object outputs = methods.virtualResultOutputs().invoke(result);
        if (!(outputs instanceof List<?> list) || list.isEmpty()) {
            logDispatch("virtual dispatch aborted: no outputs pos={}", pos);
            return false;
        }

        insertOutputsToReturnInventory(list, returnInventory, actionSource);
        methods.virtualOnBatchFlush().invoke(adapter, level, pos, handle, actionSource);
        logDispatch("virtual dispatch completed: pos={} outputs={}", pos, list.size());
        return true;
    }

    private static void logDispatch(String message, Object... args) {
        if (!Data_Energistics.isDev()) {
            return;
        }
        Data_Energistics.LOGGER.info("[DE][AE2LTPP][Dispatch] " + message, args);
    }

    private static @Nullable Object findMultiblockAdapter(Access methods, ServerLevel level, BlockPos pos) throws Throwable {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }

        return methods.multiblockAdapterFind().invoke(level, pos, blockEntity);
    }

    private static boolean adapterStackMatchesTarget(Access methods,
                                                     Object adapter,
                                                     ServerLevel level,
                                                     BlockPos pos,
                                                     ItemStack adapterStack) throws Throwable {
        Object requiredAdapterId = methods.adapterRequiredAdapterId().invoke(adapter, level, pos);
        if (requiredAdapterId instanceof ResourceLocation resourceLocation) {
            Object covered = methods.adapterItemStackCovers().invoke(adapterStack, resourceLocation);
            return Boolean.TRUE.equals(covered);
        }
        return true;
    }

    private static void insertOutputsToReturnInventory(@Nullable Object outputs,
                                                       PatternProviderReturnInventory returnInventory,
                                                       IActionSource actionSource) {
        if (!(outputs instanceof List<?> list) || list.isEmpty()) {
            return;
        }

        for (GenericStack stack : collectGenericStacks(list)) {
            returnInventory.insert(stack.what(), stack.amount(), Actionable.MODULATE, actionSource);
        }
    }

    private static List<GenericStack> collectGenericStacks(List<?> list) {
        if (list.isEmpty()) {
            return List.of();
        }

        ArrayList<GenericStack> converted = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof GenericStack stack && stack.what() != null && stack.amount() > 0) {
                converted.add(stack);
            }
        }
        return converted.isEmpty() ? List.of() : List.copyOf(converted);
    }

    private static void initialize() {
        initialized = true;
        try {
            Class<?> registryClass = Class.forName(MULTIBLOCK_ADAPTER_REGISTRY_CLASS);
            Class<?> adapterClass = Class.forName(MULTIBLOCK_ADAPTER_CLASS);
            Class<?> bindingResultClass = Class.forName(BINDING_RESULT_CLASS);
            Class<?> bindingModeClass = Class.forName(BINDING_MODE_CLASS);
            Class<?> allowedOutputFilterClass = Class.forName(ALLOWED_OUTPUT_FILTER_CLASS);
            Class<?> dispatchPlanClass = Class.forName(DISPATCH_PLAN_CLASS);
            Class<?> dispatchExecutorClass = Class.forName(DISPATCH_EXECUTOR_CLASS);
            Class<?> dispatchResultClass = Class.forName(DISPATCH_RESULT_CLASS);
            Class<?> adapterItemClass = Class.forName(MULTIBLOCK_ADAPTER_ITEM_CLASS);
            Class<?> virtualCraftingAdapterClass = Class.forName(VIRTUAL_CRAFTING_ADAPTER_CLASS);
            Class<?> virtualCraftingResultClass = Class.forName(VIRTUAL_CRAFTING_RESULT_CLASS);

            MethodHandle multiblockAdapterFind = findStatic(
                    registryClass,
                    "find",
                    adapterClass,
                    ServerLevel.class,
                    BlockPos.class,
                    BlockEntity.class);
            MethodHandle adapterRequiredAdapterId = findVirtual(
                    adapterClass,
                    "requiredAdapterId",
                    ResourceLocation.class,
                    ServerLevel.class,
                    BlockPos.class);
            MethodHandle adapterBind = findVirtual(
                    adapterClass,
                    "bind",
                    bindingResultClass,
                    ServerLevel.class,
                    BlockPos.class,
                    IPatternDetails.class);
            MethodHandle adapterCanDispatch = findVirtual(
                    adapterClass,
                    "canDispatch",
                    boolean.class,
                    ServerLevel.class,
                    BlockPos.class,
                    Object.class);
            MethodHandle adapterPlanWithBinding = findVirtual(
                    adapterClass,
                    "planWithBinding",
                    dispatchPlanClass,
                    ServerLevel.class,
                    BlockPos.class,
                    IPatternDetails.class,
                    KeyCounter[].class,
                    Object.class,
                    IActionSource.class);
            MethodHandle adapterExtractOutputs = findVirtual(
                    adapterClass,
                    "extractOutputs",
                    List.class,
                    ServerLevel.class,
                    BlockPos.class,
                    allowedOutputFilterClass,
                    IActionSource.class);
            MethodHandle adapterItemStackCovers = findStatic(
                    adapterItemClass,
                    "stackCovers",
                    boolean.class,
                    ItemStack.class,
                    ResourceLocation.class);
            MethodHandle virtualPlanWithBinding = findVirtual(
                    virtualCraftingAdapterClass,
                    "planVirtualWithBinding",
                    virtualCraftingResultClass,
                    ServerLevel.class,
                    BlockPos.class,
                    IPatternDetails.class,
                    KeyCounter[].class,
                    Object.class,
                    IActionSource.class);
            MethodHandle virtualResultOutputs = findVirtual(virtualCraftingResultClass, "outputs", List.class);
            MethodHandle virtualOnBatchFlush = findVirtual(
                    virtualCraftingAdapterClass,
                    "onVirtualBatchFlush",
                    void.class,
                    ServerLevel.class,
                    BlockPos.class,
                    Object.class,
                    IActionSource.class);
            MethodHandle bindingHandle = findVirtual(bindingResultClass, "handle", Object.class);
            MethodHandle bindingMode = findVirtual(bindingResultClass, "mode", bindingModeClass);
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object realBindingMode = Enum.valueOf((Class<? extends Enum>) bindingModeClass.asSubclass(Enum.class), "REAL");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object virtualBindingMode = Enum.valueOf((Class<? extends Enum>) bindingModeClass.asSubclass(Enum.class), "VIRTUAL");
            MethodHandle dispatchExecute = findStatic(
                    dispatchExecutorClass,
                    "execute",
                    dispatchResultClass,
                    dispatchPlanClass,
                    IActionSource.class,
                    PatternProviderReturnInventory.class);
            MethodHandle dispatchResultSuccess = findVirtual(dispatchResultClass, "success", boolean.class);

            access = new Access(
                    multiblockAdapterFind,
                    adapterRequiredAdapterId,
                    adapterBind,
                    adapterCanDispatch,
                    adapterPlanWithBinding,
                    adapterExtractOutputs,
                    adapterItemClass,
                    adapterItemStackCovers,
                    virtualCraftingAdapterClass,
                    virtualPlanWithBinding,
                    virtualResultOutputs,
                    virtualOnBatchFlush,
                    bindingHandle,
                    bindingMode,
                    realBindingMode,
                    virtualBindingMode,
                    dispatchExecute,
                    dispatchResultSuccess);
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            access = null;
        }
    }

    private static MethodHandle findStatic(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        return PUBLIC_LOOKUP.findStatic(owner, name, MethodType.methodType(returnType, parameterTypes));
    }

    private static MethodHandle findVirtual(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        return PUBLIC_LOOKUP.findVirtual(owner, name, MethodType.methodType(returnType, parameterTypes));
    }

    private record Access(MethodHandle multiblockAdapterFind,
                          MethodHandle adapterRequiredAdapterId,
                          MethodHandle adapterBind,
                          MethodHandle adapterCanDispatch,
                          MethodHandle adapterPlanWithBinding,
                          MethodHandle adapterExtractOutputs,
                          Class<?> adapterItemClass,
                          MethodHandle adapterItemStackCovers,
                          Class<?> virtualCraftingAdapterClass,
                          MethodHandle virtualPlanWithBinding,
                          MethodHandle virtualResultOutputs,
                          MethodHandle virtualOnBatchFlush,
                          MethodHandle bindingHandle,
                          MethodHandle bindingMode,
                          Object realBindingMode,
                          Object virtualBindingMode,
                          MethodHandle dispatchExecute,
                          MethodHandle dispatchResultSuccess) {}
}
