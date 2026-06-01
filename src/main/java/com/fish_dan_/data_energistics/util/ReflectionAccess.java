package com.fish_dan_.data_energistics.util;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionAccess {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<MethodLookupKey, Optional<MethodHandle>> VIRTUAL_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<StaticMethodLookupKey, Optional<MethodHandle>> STATIC_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Method, Optional<MethodHandle>> METHOD_CACHE = new ConcurrentHashMap<>();

    private ReflectionAccess() {}

    public static boolean hasNoArgMethod(Class<?> type, String methodName) {
        return getNoArgVirtualMethod(type, methodName).isPresent();
    }

    @Nullable
    public static Object invokeNoArg(@Nullable Object target, String methodName) {
        if (target == null) {
            return null;
        }

        Optional<MethodHandle> method = getNoArgVirtualMethod(target.getClass(), methodName);
        if (method.isEmpty()) {
            return null;
        }

        try {
            return method.get().invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void invokeNoArgBestEffort(@Nullable Object target, String methodName) {
        invokeNoArg(target, methodName);
    }

    @Nullable
    public static Object invoke(Method method, @Nullable Object target, Object... args) {
        Optional<MethodHandle> handle = METHOD_CACHE.computeIfAbsent(method, ReflectionAccess::unreflectMethod);
        if (handle.isEmpty()) {
            return null;
        }

        Object[] arguments = target == null ? args : prependTarget(target, args);
        try {
            return handle.get().invokeWithArguments(arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static Object invokeStatic(String ownerClassName, String methodName, Class<?>[] parameterTypes, Object... args) {
        Optional<MethodHandle> method = STATIC_METHOD_CACHE.computeIfAbsent(
                new StaticMethodLookupKey(ownerClassName, methodName, List.copyOf(Arrays.asList(parameterTypes))),
                ReflectionAccess::findStaticMethod);
        if (method.isEmpty()) {
            return null;
        }

        try {
            return method.get().invokeWithArguments(args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Optional<MethodHandle> getNoArgVirtualMethod(Class<?> type, String methodName) {
        return VIRTUAL_METHOD_CACHE.computeIfAbsent(
                new MethodLookupKey(type, methodName),
                ReflectionAccess::findNoArgVirtualMethod);
    }

    private static Optional<MethodHandle> findNoArgVirtualMethod(MethodLookupKey key) {
        Class<?> type = key.type();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(key.methodName());
                method.setAccessible(true);
                return Optional.of(MethodHandles.privateLookupIn(type, LOOKUP).unreflect(method));
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<MethodHandle> findStaticMethod(StaticMethodLookupKey key) {
        try {
            Class<?> owner = Class.forName(key.ownerClassName());
            Method method = owner.getDeclaredMethod(key.methodName(), key.parameterTypes().toArray(Class<?>[]::new));
            method.setAccessible(true);
            return Optional.of(MethodHandles.privateLookupIn(owner, LOOKUP).unreflect(method));
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<MethodHandle> unreflectMethod(Method method) {
        try {
            method.setAccessible(true);
            return Optional.of(MethodHandles.privateLookupIn(method.getDeclaringClass(), LOOKUP).unreflect(method));
        } catch (IllegalAccessException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Object[] prependTarget(Object target, Object[] args) {
        Object[] arguments = new Object[args.length + 1];
        arguments[0] = target;
        System.arraycopy(args, 0, arguments, 1, args.length);
        return arguments;
    }

    private record MethodLookupKey(Class<?> type, String methodName) {}

    private record StaticMethodLookupKey(String ownerClassName, String methodName, List<Class<?>> parameterTypes) {}
}
