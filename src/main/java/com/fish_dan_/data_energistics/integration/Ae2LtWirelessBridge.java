package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class Ae2LtWirelessBridge {

    private static final String CONNECTOR_ITEM_CLASS = "com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem";
    private static final String TARGET_HELPER_CLASS = "com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper";
    private static final String PROVIDER_CLASS = "com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity";
    private static final String RENDER_TYPES_CLASS = "com.moakiee.ae2lt.client.Ae2ltRenderTypes";

    private static boolean coreInitialized;
    private static boolean renderInitialized;
    private static @Nullable Class<?> connectorItemClass;
    private static @Nullable Class<?> providerClass;
    private static @Nullable Method hasSelectionMethod;
    private static @Nullable Method getSelectedHostTypeMethod;
    private static @Nullable Method selectHostMethod;
    private static @Nullable Method isSelectionInCurrentDimensionMethod;
    private static @Nullable Method getSelectedProviderMethod;
    private static @Nullable Method collectTargetsMethod;
    private static @Nullable Method getConnectionsMethod;
    private static @Nullable Method removeConnectionMethod;
    private static @Nullable Method addOrUpdateConnectionMethod;
    private static @Nullable Method renderFaceSeeThroughMethod;
    private static @Nullable String hostProviderType;

    private Ae2LtWirelessBridge() {}

    public static boolean isAvailable() {
        if (!Ae2LtCompat.isLoaded()) {
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
        try {
            return isAvailable() && Boolean.TRUE.equals(hasSelectionMethod.invoke(null, stack));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static @Nullable String getSelectedHostType(ItemStack stack) {
        try {
            Object result = isAvailable() ? getSelectedHostTypeMethod.invoke(null, stack) : null;
            return result instanceof String string ? string : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void selectHost(ItemStack stack, Level level, BlockPos pos, String hostType) {
        try {
            if (isAvailable()) {
                selectHostMethod.invoke(null, stack, level, pos, hostType);
            }
        } catch (Exception ignored) {}
    }

    public static boolean isSelectionInCurrentDimension(Level level, ItemStack stack) {
        try {
            return isAvailable() && Boolean.TRUE.equals(isSelectionInCurrentDimensionMethod.invoke(null, level, stack));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static @Nullable BlockEntity getSelectedProvider(Level level, ItemStack stack) {
        try {
            Object result = isAvailable() ? getSelectedProviderMethod.invoke(null, level, stack) : null;
            return result instanceof BlockEntity blockEntity ? blockEntity : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static List<BlockPos> collectTargets(Level level, BlockPos pos, boolean contiguous) {
        try {
            Object result = isAvailable() ? collectTargetsMethod.invoke(null, level, pos, contiguous) : null;
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
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static boolean isVanillaOverloadedProvider(@Nullable BlockEntity blockEntity) {
        return isAvailable() && providerClass != null && providerClass.isInstance(blockEntity);
    }

    public static List<AdaptiveWirelessConnection> getConnectionsFromVanilla(@Nullable BlockEntity blockEntity) {
        if (!isVanillaOverloadedProvider(blockEntity)) {
            return List.of();
        }
        try {
            Object result = getConnectionsMethod.invoke(blockEntity);
            if (!(result instanceof List<?> list)) {
                return List.of();
            }
            return convertConnections(list);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static boolean removeConnection(@Nullable BlockEntity blockEntity, ResourceKey<Level> dimension, BlockPos pos) {
        if (!isVanillaOverloadedProvider(blockEntity)) {
            return false;
        }
        try {
            Object result = removeConnectionMethod.invoke(blockEntity, dimension, pos);
            return !(result instanceof Boolean removed) || removed;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void addOrUpdateConnection(@Nullable BlockEntity blockEntity,
                                             ResourceKey<Level> dimension,
                                             BlockPos pos,
                                             Direction face) {
        if (!isVanillaOverloadedProvider(blockEntity)) {
            return;
        }
        try {
            addOrUpdateConnectionMethod.invoke(blockEntity, dimension, pos, face);
        } catch (Exception ignored) {}
    }

    public static RenderType getFaceSeeThroughRenderType() {
        if (!isAvailable()) {
            return null;
        }
        if (!renderInitialized) {
            initializeRender();
        }
        try {
            Object result = renderFaceSeeThroughMethod != null ? renderFaceSeeThroughMethod.invoke(null) : null;
            return result instanceof RenderType renderType ? renderType : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<AdaptiveWirelessConnection> convertConnections(List<?> list) {
        List<AdaptiveWirelessConnection> converted = new ArrayList<>(list.size());
        for (Object connection : list) {
            try {
                Method dimensionMethod = connection.getClass().getMethod("dimension");
                Method posMethod = connection.getClass().getMethod("pos");
                Method boundFaceMethod = connection.getClass().getMethod("boundFace");
                Object dimension = dimensionMethod.invoke(connection);
                Object pos = posMethod.invoke(connection);
                Object face = boundFaceMethod.invoke(connection);
                if (dimension instanceof ResourceKey<?> key && pos instanceof BlockPos blockPos && face instanceof Direction direction) {
                    @SuppressWarnings("unchecked")
                    ResourceKey<Level> levelKey = (ResourceKey<Level>) key;
                    converted.add(new AdaptiveWirelessConnection(levelKey, blockPos, direction));
                }
            } catch (Exception ignored) {}
        }
        return converted;
    }

    private static void initializeCore() {
        coreInitialized = true;
        try {
            connectorItemClass = Class.forName(CONNECTOR_ITEM_CLASS);
            providerClass = Class.forName(PROVIDER_CLASS);
            Class<?> targetHelperClass = Class.forName(TARGET_HELPER_CLASS);

            hasSelectionMethod = connectorItemClass.getMethod("hasSelection", ItemStack.class);
            getSelectedHostTypeMethod = connectorItemClass.getMethod("getSelectedHostType", ItemStack.class);
            selectHostMethod = connectorItemClass.getMethod("selectHost", ItemStack.class, Level.class, BlockPos.class, String.class);
            isSelectionInCurrentDimensionMethod = connectorItemClass.getMethod("isSelectionInCurrentDimension", Level.class, ItemStack.class);
            getSelectedProviderMethod = connectorItemClass.getMethod("getSelectedProvider", Level.class, ItemStack.class);
            collectTargetsMethod = targetHelperClass.getMethod("collectTargets", Level.class, BlockPos.class, boolean.class);
            getConnectionsMethod = providerClass.getMethod("getConnections");
            removeConnectionMethod = providerClass.getMethod("removeConnection", ResourceKey.class, BlockPos.class);
            addOrUpdateConnectionMethod = providerClass.getMethod("addOrUpdateConnection", ResourceKey.class, BlockPos.class, Direction.class);
            Object hostProvider = connectorItemClass.getField("HOST_PROVIDER").get(null);
            hostProviderType = hostProvider instanceof String string ? string : null;
        } catch (Exception ignored) {
            connectorItemClass = null;
            providerClass = null;
        }
    }

    private static void initializeRender() {
        renderInitialized = true;
        try {
            Class<?> renderTypesClass = Class.forName(RENDER_TYPES_CLASS);
            renderFaceSeeThroughMethod = renderTypesClass.getMethod("getFaceSeeThrough");
        } catch (Exception ignored) {
            renderFaceSeeThroughMethod = null;
        }
    }
}
