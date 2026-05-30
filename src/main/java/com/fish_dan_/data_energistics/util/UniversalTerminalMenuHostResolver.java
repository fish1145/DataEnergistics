package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
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

                try {
                    partClass.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException ignored) {
                    return false;
                }
            }
        }
        return true;
    }

    private record MenuHostInvocationHandler(UniversalTerminalPart part) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "UniversalTerminalProxy[" + this.part + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                    default -> method.invoke(this, args);
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

            Method partMethod = this.part.getClass().getMethod(method.getName(), method.getParameterTypes());
            return partMethod.invoke(this.part, args);
        }
    }
}
