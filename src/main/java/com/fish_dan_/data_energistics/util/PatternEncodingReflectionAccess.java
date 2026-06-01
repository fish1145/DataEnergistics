package com.fish_dan_.data_energistics.util;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Optional;

final class PatternEncodingReflectionAccess {

    private static final java.util.Map<SlotViewsLookupKey, Optional<SlotViewsInvoker>> SLOT_VIEWS_METHOD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private PatternEncodingReflectionAccess() {}

    @Nullable
    static ResourceLocation tryReadResourceLocation(@Nullable Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof ResourceLocation id ? id : null;
    }

    @Nullable
    static Object invokeSlotViewsByRole(@Nullable Object target, String roleName) {
        if (target == null) {
            return null;
        }

        Optional<SlotViewsInvoker> invoker = SLOT_VIEWS_METHOD_CACHE.computeIfAbsent(
                new SlotViewsLookupKey(target.getClass(), roleName),
                PatternEncodingReflectionAccess::findSlotViewsInvoker);
        if (invoker.isEmpty()) {
            return null;
        }

        try {
            return invoker.get().invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    static Object invokeNoArg(@Nullable Object target, String methodName) {
        return ReflectionAccess.invokeNoArg(target, methodName);
    }

    private static Optional<SlotViewsInvoker> findSlotViewsInvoker(SlotViewsLookupKey key) {
        Class<?> type = key.type();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("getSlotViews") || method.getParameterCount() != 1) {
                    continue;
                }

                Class<?> parameterType = method.getParameterTypes()[0];
                if (!parameterType.isEnum()) {
                    continue;
                }

                try {
                    @SuppressWarnings({ "rawtypes", "unchecked" })
                    Object enumValue = Enum.valueOf((Class<? extends Enum>) parameterType.asSubclass(Enum.class), key.roleName());
                    method.setAccessible(true);
                    MethodHandle handle = MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflect(method);
                    return Optional.of(new SlotViewsInvoker(handle, enumValue));
                } catch (IllegalArgumentException ignored) {
                    continue;
                } catch (IllegalAccessException | SecurityException ignored) {
                    return Optional.empty();
                }
            }
            type = type.getSuperclass();
        }
        return Optional.empty();
    }

    private record SlotViewsLookupKey(Class<?> type, String roleName) {}

    private record SlotViewsInvoker(MethodHandle method, Object role) {

        Object invoke(Object target) throws Throwable {
            return this.method.invoke(target, this.role);
        }
    }
}
