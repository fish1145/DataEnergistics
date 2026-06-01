package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Ae2LtWirelessBridge {

    private static final String CONNECTOR_ITEM_CLASS = "com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem";
    private static final String TARGET_HELPER_CLASS = "com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper";
    private static final String PROVIDER_CLASS = "com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity";

    private static boolean coreInitialized;
    private static @Nullable Class<?> connectorItemClass;
    private static @Nullable Class<?> providerClass;
    private static Optional<MethodHandle> getConnectionsMethod = Optional.empty();
    private static Optional<MethodHandle> removeConnectionMethod = Optional.empty();
    private static Optional<MethodHandle> addOrUpdateConnectionMethod = Optional.empty();
    private static @Nullable String hostProviderType;

    private Ae2LtWirelessBridge() {}

    public static boolean isAvailable() {
        if (!ModFlags.isAe2LtLoaded()) {
            return false;
        }
        if (!coreInitialized) {
            initializeCore();
        }
        return connectorItemClass != null;
    }

    public static boolean isConnectorItem(ItemStack stack) {
        return isAvailable() && connectorItemClass != null && connectorItemClass.isInstance(stack.getItem());
    }

    public static @Nullable String hostProviderType() {
        return isAvailable() ? hostProviderType : null;
    }

    public static boolean hasSelection(ItemStack stack) {
        return isAvailable() && Boolean.TRUE.equals(ReflectionAccess.invokeStatic(
                CONNECTOR_ITEM_CLASS,
                "hasSelection",
                new Class<?>[] { ItemStack.class },
                stack));
    }

    public static @Nullable String getSelectedHostType(ItemStack stack) {
        Object result = isAvailable() ? ReflectionAccess.invokeStatic(
                CONNECTOR_ITEM_CLASS,
                "getSelectedHostType",
                new Class<?>[] { ItemStack.class },
                stack) : null;
        return result instanceof String string ? string : null;
    }

    public static void selectHost(ItemStack stack, Level level, BlockPos pos, String hostType) {
        if (isAvailable()) {
            ReflectionAccess.invokeStatic(
                    CONNECTOR_ITEM_CLASS,
                    "selectHost",
                    new Class<?>[] { ItemStack.class, Level.class, BlockPos.class, String.class },
                    stack,
                    level,
                    pos,
                    hostType);
        }
    }

    public static boolean isSelectionInCurrentDimension(Level level, ItemStack stack) {
        return isAvailable() && Boolean.TRUE.equals(ReflectionAccess.invokeStatic(
                CONNECTOR_ITEM_CLASS,
                "isSelectionInCurrentDimension",
                new Class<?>[] { Level.class, ItemStack.class },
                level,
                stack));
    }

    public static @Nullable BlockEntity getSelectedProvider(Level level, ItemStack stack) {
        Object result = isAvailable() ? ReflectionAccess.invokeStatic(
                CONNECTOR_ITEM_CLASS,
                "getSelectedProvider",
                new Class<?>[] { Level.class, ItemStack.class },
                level,
                stack) : null;
        return result instanceof BlockEntity blockEntity ? blockEntity : null;
    }

    public static List<BlockPos> collectTargets(Level level, BlockPos pos, boolean contiguous) {
        Object result = isAvailable() ? ReflectionAccess.invokeStatic(
                TARGET_HELPER_CLASS,
                "collectTargets",
                new Class<?>[] { Level.class, BlockPos.class, boolean.class },
                level,
                pos,
                contiguous) : null;
        if (!(result instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<BlockPos> converted = new ArrayList<>();
        for (Object entry : iterable) {
            if (entry instanceof BlockPos blockPos) {
                converted.add(blockPos);
            }
        }
        return converted;
    }

    public static boolean isVanillaOverloadedProvider(@Nullable BlockEntity blockEntity) {
        return isAvailable() && providerClass != null && providerClass.isInstance(blockEntity);
    }

    public static List<AdaptiveWirelessConnection> getConnectionsFromVanilla(@Nullable BlockEntity blockEntity) {
        if (!isVanillaOverloadedProvider(blockEntity)) {
            return List.of();
        }
        Object result = invokeProvider(getConnectionsMethod, blockEntity);
        if (!(result instanceof List<?> list)) {
            return List.of();
        }
        return convertConnections(list);
    }

    public static boolean removeConnection(@Nullable BlockEntity blockEntity, ResourceKey<Level> dimension, BlockPos pos) {
        if (!isVanillaOverloadedProvider(blockEntity)) {
            return false;
        }
        Object result = invokeProvider(removeConnectionMethod, blockEntity, dimension, pos);
        return !(result instanceof Boolean removed) || removed;
    }

    public static void addOrUpdateConnection(@Nullable BlockEntity blockEntity,
                                             ResourceKey<Level> dimension,
                                             BlockPos pos,
                                             Direction face) {
        if (!isVanillaOverloadedProvider(blockEntity)) {
            return;
        }
        invokeProvider(addOrUpdateConnectionMethod, blockEntity, dimension, pos, face);
    }

    private static List<AdaptiveWirelessConnection> convertConnections(List<?> list) {
        List<AdaptiveWirelessConnection> converted = new ArrayList<>(list.size());
        for (Object connection : list) {
            Object dimension = ReflectionAccess.invokeNoArg(connection, "dimension");
            Object pos = ReflectionAccess.invokeNoArg(connection, "pos");
            Object face = ReflectionAccess.invokeNoArg(connection, "boundFace");
            if (dimension instanceof ResourceKey<?> key && pos instanceof BlockPos blockPos && face instanceof Direction direction) {
                @SuppressWarnings("unchecked")
                ResourceKey<Level> levelKey = (ResourceKey<Level>) key;
                converted.add(new AdaptiveWirelessConnection(levelKey, blockPos, direction));
            }
        }
        return converted;
    }

    private static void initializeCore() {
        coreInitialized = true;
        try {
            connectorItemClass = Class.forName(CONNECTOR_ITEM_CLASS);
            providerClass = Class.forName(PROVIDER_CLASS);

            getConnectionsMethod = findProviderMethod("getConnections", List.class);
            removeConnectionMethod = findProviderMethod("removeConnection", boolean.class, ResourceKey.class, BlockPos.class);
            addOrUpdateConnectionMethod = findProviderMethod("addOrUpdateConnection", void.class, ResourceKey.class, BlockPos.class, Direction.class);
            Object hostProvider = ReflectionAccess.getField(
                    ReflectionAccess.findStaticField(connectorItemClass, "HOST_PROVIDER"),
                    null);
            hostProviderType = hostProvider instanceof String string ? string : null;
        } catch (Exception ignored) {
            connectorItemClass = null;
            providerClass = null;
            getConnectionsMethod = Optional.empty();
            removeConnectionMethod = Optional.empty();
            addOrUpdateConnectionMethod = Optional.empty();
        }
    }

    private static Optional<MethodHandle> findProviderMethod(String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        if (providerClass == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MethodHandles.publicLookup().findVirtual(
                    providerClass,
                    methodName,
                    MethodType.methodType(returnType, parameterTypes)));
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static @Nullable Object invokeProvider(Optional<MethodHandle> method, BlockEntity blockEntity, Object... args) {
        if (method.isEmpty()) {
            return null;
        }
        Object[] arguments = new Object[args.length + 1];
        arguments[0] = blockEntity;
        System.arraycopy(args, 0, arguments, 1, args.length);
        try {
            return method.get().invokeWithArguments(arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
