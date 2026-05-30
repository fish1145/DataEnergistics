package com.fish_dan_.data_energistics.util;

import net.minecraft.network.chat.Component;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.parts.AEBasePart;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PatternProviderNameHelper {

    private static final Field AE_BASE_PART_CUSTOM_NAME_FIELD = resolveField(AEBasePart.class, "customName");
    private static final Field AE_BASE_BLOCK_ENTITY_CUSTOM_NAME_FIELD = resolveField(AEBaseBlockEntity.class, "customName");

    private PatternProviderNameHelper() {}

    public static boolean setCustomName(Object target, @Nullable Component customName) {
        if (target instanceof AEBasePart part) {
            return writeField(AE_BASE_PART_CUSTOM_NAME_FIELD, part, customName);
        }
        if (target instanceof AEBaseBlockEntity blockEntity) {
            return writeField(AE_BASE_BLOCK_ENTITY_CUSTOM_NAME_FIELD, blockEntity, customName);
        }
        return writeField(resolveCustomNameField(target.getClass()), target, customName);
    }

    @Nullable
    public static Component getCustomName(Object target) {
        if (target instanceof AEBasePart part) {
            return readField(AE_BASE_PART_CUSTOM_NAME_FIELD, part);
        }
        if (target instanceof AEBaseBlockEntity blockEntity) {
            return readField(AE_BASE_BLOCK_ENTITY_CUSTOM_NAME_FIELD, blockEntity);
        }
        return readField(resolveCustomNameField(target.getClass()), target);
    }

    public static boolean canRename(Object target) {
        if (target instanceof AEBasePart || target instanceof AEBaseBlockEntity) {
            return true;
        }
        return resolveCustomNameField(target.getClass()) != null;
    }

    public static void syncRename(Object target) {
        invokeNoArg(target, "saveChanges");
        invokeNoArg(target, "setChanged");
        invokeNoArg(target, "markForUpdate");
        invokeNoArg(target, "markForClientUpdate");
        if (target instanceof AEBasePart part && part.getHost() != null) {
            part.getHost().markForUpdate();
        }
    }

    @Nullable
    private static Field resolveField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Field resolveCustomNameField(Class<?> owner) {
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            Field field = resolveField(current, "customName");
            if (field != null && Component.class.isAssignableFrom(field.getType())) {
                return field;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean writeField(@Nullable Field field, Object target, @Nullable Component value) {
        if (field == null) {
            return false;
        }
        try {
            field.set(target, value);
            return true;
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    @Nullable
    private static Component readField(@Nullable Field field, Object target) {
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(target);
            return value instanceof Component component ? component : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static void invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException ignored) {}
    }
}
