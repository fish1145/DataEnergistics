package com.fish_dan_.data_energistics.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();
    private static volatile @Nullable Access access;
    private static volatile boolean initialized;

    private Ae2LtPackagedRuntimeBridge() {
    }

    public static boolean isAvailable() {
        if (!ModFlags.isAe2LtPackagedProviderLoaded()) {
            return false;
        }

        if (!initialized) {
            initialize();
        }

        return access != null;
    }

    public static boolean dispatch(ServerLevel level,
                                   BlockPos pos,
                                   IPatternDetails patternDetails,
                                   KeyCounter[] inputHolder,
                                   @Nullable Object allowedOutputFilter,
                                   IActionSource actionSource,
                                   PatternProviderReturnInventory returnInventory) {
        if (!isAvailable() || patternDetails == null) {
            return false;
        }

        try {
            Access methods = access;
            if (methods == null) {
                return false;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return false;
            }

            Object adapter = methods.multiblockAdapterFind().invoke(level, pos, blockEntity);
            if (adapter == null) {
                return false;
            }

            Object requiredAdapterId = methods.adapterRequiredAdapterId().invoke(adapter, level, pos);
            if (requiredAdapterId instanceof ResourceLocation) {
                return false;
            }

            Object binding = methods.adapterBind().invoke(adapter, level, pos, patternDetails);
            if (binding == null) {
                return false;
            }

            Object handle = methods.bindingHandle().invoke(binding);
            Object mode = methods.bindingMode().invoke(binding);
            if (handle == null || mode != methods.realBindingMode()) {
                return false;
            }

            if (allowedOutputFilter != null) {
                Object outputs = methods.adapterExtractOutputs().invoke(adapter, level, pos, allowedOutputFilter, actionSource);
                insertOutputsToReturnInventory(outputs, returnInventory, actionSource);
            }

            Object canDispatch = methods.adapterCanDispatch().invoke(adapter, level, pos, handle);
            if (!Boolean.TRUE.equals(canDispatch)) {
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
                return false;
            }

            Object result = methods.dispatchExecute().invoke(plan, actionSource, returnInventory);
            Object success = methods.dispatchResultSuccess().invoke(result);
            return Boolean.TRUE.equals(success);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void insertOutputsToReturnInventory(@Nullable Object outputs,
                                                       PatternProviderReturnInventory returnInventory,
                                                       IActionSource actionSource) {
        if (!(outputs instanceof List<?> list) || list.isEmpty()) {
            return;
        }

        for (Object entry : list) {
            if (entry instanceof GenericStack stack && stack.what() != null && stack.amount() > 0) {
                returnInventory.insert(stack.what(), stack.amount(), Actionable.MODULATE, actionSource);
            }
        }
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
            MethodHandle bindingHandle = findVirtual(bindingResultClass, "handle", Object.class);
            MethodHandle bindingMode = findVirtual(bindingResultClass, "mode", bindingModeClass);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object realBindingMode = Enum.valueOf((Class<? extends Enum>) bindingModeClass.asSubclass(Enum.class), "REAL");
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
                    bindingHandle,
                    bindingMode,
                    realBindingMode,
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
                          MethodHandle bindingHandle,
                          MethodHandle bindingMode,
                          Object realBindingMode,
                          MethodHandle dispatchExecute,
                          MethodHandle dispatchResultSuccess) {
    }
}
