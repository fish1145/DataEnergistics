package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class UniversalTerminalMenuHostResolver {

    private UniversalTerminalMenuHostResolver() {}

    static <T> @Nullable T resolve(UniversalTerminalPart part, Class<T> hostInterface) {
        if (hostInterface.isInstance(part)) {
            return hostInterface.cast(part);
        }

        Set<Class<?>> proxyInterfaces = getProxyInterfaces(part, hostInterface);
        if (!hostInterface.isInterface() || !canProxy(part.getClass(), proxyInterfaces)) {
            return null;
        }

        InvocationHandler handler = new MenuHostInvocationHandler(part);
        Object proxy = Proxy.newProxyInstance(
                hostInterface.getClassLoader(),
                proxyInterfaces.toArray(Class[]::new),
                handler);
        return hostInterface.cast(proxy);
    }

    private static Set<Class<?>> getProxyInterfaces(UniversalTerminalPart part, Class<?> hostInterface) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        interfaces.add(hostInterface);
        interfaces.add(IPart.class);
        interfaces.add(UniversalTerminalHostAccessor.class);
        if (part instanceof IActionHost) {
            interfaces.add(IActionHost.class);
        }
        return interfaces;
    }

    private static boolean canProxy(Class<?> partClass, Set<Class<?>> interfaces) {
        for (Class<?> proxyInterface : interfaces) {
            for (Method method : proxyInterface.getMethods()) {
                if (method.getDeclaringClass() == Object.class || method.isDefault()) {
                    continue;
                }

                if (method.getDeclaringClass() == UniversalTerminalHostAccessor.class) {
                    continue;
                }

                if (findPartMethod(partClass, method).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Optional<MethodHandle> findPartMethod(Class<?> partClass, Method method) {
        try {
            return Optional.of(MethodHandles.publicLookup().findVirtual(
                    partClass,
                    method.getName(),
                    MethodType.methodType(method.getReturnType(), method.getParameterTypes())));
        } catch (NoSuchMethodException | IllegalAccessException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private record MenuHostInvocationHandler(UniversalTerminalPart part) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "UniversalTerminalProxy[" + this.part + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                    default -> ReflectionAccess.invoke(method, this, args == null ? new Object[0] : args);
                };
            }

            if (method.isDefault()) {
                return MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
                        .unreflectSpecial(method, method.getDeclaringClass())
                        .bindTo(proxy)
                        .invokeWithArguments(args == null ? new Object[0] : args);
            }

            if (method.getDeclaringClass() == UniversalTerminalHostAccessor.class && method.getName().equals("getUniversalTerminalPart") && method.getParameterCount() == 0) {
                return this.part;
            }

            Optional<MethodHandle> partMethod = findPartMethod(this.part.getClass(), method);
            if (partMethod.isEmpty()) {
                throw new NoSuchMethodException(this.part.getClass().getName() + "." + method.getName());
            }
            return partMethod.get()
                    .bindTo(this.part)
                    .invokeWithArguments(args == null ? new Object[0] : args);
        }
    }
}
