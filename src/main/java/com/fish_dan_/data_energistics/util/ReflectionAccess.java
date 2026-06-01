package com.fish_dan_.data_energistics.util;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
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
    private static final Map<FieldLookupKey, Optional<VarHandle>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<FieldLookupKey, Optional<VarHandle>> STATIC_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<ConstructorLookupKey, Optional<MethodHandle>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

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

    public static Optional<VarHandle> findField(Class<?> owner, String fieldName) {
        return FIELD_CACHE.computeIfAbsent(new FieldLookupKey(owner, fieldName), ReflectionAccess::findInstanceField);
    }

    public static Optional<VarHandle> findFieldAssignable(Class<?> owner, String fieldName, Class<?> fieldType) {
        Optional<VarHandle> handle = findField(owner, fieldName);
        return handle.filter(varHandle -> fieldType.isAssignableFrom(varHandle.varType()));
    }

    public static Optional<VarHandle> findStaticField(Class<?> owner, String fieldName) {
        return STATIC_FIELD_CACHE.computeIfAbsent(new FieldLookupKey(owner, fieldName), ReflectionAccess::findStaticField);
    }

    public static Optional<VarHandle> findStaticField(String ownerClassName, String fieldName) {
        try {
            return findStaticField(Class.forName(ownerClassName), fieldName);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    @Nullable
    public static Object getField(Optional<VarHandle> handle, @Nullable Object target) {
        if (handle.isEmpty()) {
            return null;
        }

        try {
            return target == null ? handle.get().get() : handle.get().get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean setField(Optional<VarHandle> handle, Object target, @Nullable Object value) {
        if (handle.isEmpty()) {
            return false;
        }

        try {
            handle.get().set(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static Object newInstance(String ownerClassName, Class<?>[] parameterTypes, Object... args) {
        Optional<MethodHandle> constructor = CONSTRUCTOR_CACHE.computeIfAbsent(
                new ConstructorLookupKey(ownerClassName, List.copyOf(Arrays.asList(parameterTypes))),
                ReflectionAccess::findConstructor);
        if (constructor.isEmpty()) {
            return null;
        }

        try {
            return constructor.get().invokeWithArguments(args);
        } catch (Throwable ignored) {
            return null;
        }
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

    private static Optional<VarHandle> findInstanceField(FieldLookupKey key) {
        Class<?> type = key.owner();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(key.fieldName());
                field.setAccessible(true);
                return Optional.of(MethodHandles.privateLookupIn(type, LOOKUP).unreflectVarHandle(field));
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<VarHandle> findStaticField(FieldLookupKey key) {
        try {
            Field field = key.owner().getDeclaredField(key.fieldName());
            field.setAccessible(true);
            return Optional.of(MethodHandles.privateLookupIn(key.owner(), LOOKUP).unreflectVarHandle(field));
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<MethodHandle> findConstructor(ConstructorLookupKey key) {
        try {
            Class<?> owner = Class.forName(key.ownerClassName());
            var constructor = owner.getDeclaredConstructor(key.parameterTypes().toArray(Class<?>[]::new));
            constructor.setAccessible(true);
            return Optional.of(MethodHandles.privateLookupIn(owner, LOOKUP).unreflectConstructor(constructor));
        } catch (ReflectiveOperationException | SecurityException ignored) {
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

    private record FieldLookupKey(Class<?> owner, String fieldName) {}

    private record ConstructorLookupKey(String ownerClassName, List<Class<?>> parameterTypes) {}
}
