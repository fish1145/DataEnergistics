package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.crafting.PatternProviderPart;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class PatternProviderMenuOpenHelper {

    private static final String APPLIED_PNEUMATICS_MENUS_CLASS = "com.wintercogs.appliedpneumatics.common.init.APMenus";
    private static final Map<String, String> APPLIED_PNEUMATICS_MENU_FIELDS = Map.of(
            "com.wintercogs.appliedpneumatics.common.blocks.entitis.MEAmadronProcessStationBlockEntity",
            "ME_AMADRON_PROCESS_STATION_MENU",
            "com.wintercogs.appliedpneumatics.common.blocks.entitis.MEPressureInterfaceBlockEntity",
            "ME_PRESSURE_INTERFACE_MENU",
            "com.wintercogs.appliedpneumatics.common.blocks.entitis.METemperatureInterfaceBlockEntity",
            "ME_TEMPERATURE_INTERFACE_MENU");
    private static final Set<String> REFLECTIVE_OPEN_METHOD_NAMES = Set.of(
            "openMenu",
            "openGui",
            "openGUI",
            "openUi",
            "openUI");

    private PatternProviderMenuOpenHelper() {}

    public static boolean openProviderGroup(@Nullable List<PatternContainer> providers, @Nullable Player player) {
        if (player == null || providers == null || providers.isEmpty() || player.level().isClientSide()) {
            return false;
        }

        if (openNeoEcoCraftingSubsystemMenu(providers, player)) {
            return true;
        }

        for (var provider : providers) {
            if (openSingleProvider(provider, player)) {
                return true;
            }
        }

        return false;
    }

    public static boolean openSingleProvider(@Nullable PatternContainer provider, @Nullable Player player) {
        if (provider == null || player == null || player.level().isClientSide()) {
            return false;
        }

        return openAssemblerMatrixMainMenu(provider, player) || openAppliedPneumaticsMenu(provider, player) || openViaPatternProviderLogicHost(provider, player) || openViaMenuProvider(provider, player) || openViaReflectiveMenuProvider(provider, player) || openViaBlockUi(provider, player) || openViaReflectiveMenuType(provider, player) || openViaReflectiveOpenMethod(provider, player);
    }

    private static boolean openAppliedPneumaticsMenu(PatternContainer provider, Player player) {
        if (!(provider instanceof BlockEntity blockEntity)) {
            return false;
        }

        String menuFieldName = APPLIED_PNEUMATICS_MENU_FIELDS.get(provider.getClass().getName());
        if (menuFieldName == null) {
            return false;
        }

        try {
            Class<?> menusClass = Class.forName(APPLIED_PNEUMATICS_MENUS_CLASS);
            Field field = menusClass.getField(menuFieldName);
            Object fieldValue = field.get(null);
            MenuType<?> menuType = null;
            if (fieldValue instanceof MenuType<?> directMenuType) {
                menuType = directMenuType;
            } else if (fieldValue instanceof Supplier<?> supplier && supplier.get() instanceof MenuType<?> suppliedMenuType) {
                menuType = suppliedMenuType;
            }
            return menuType != null && MenuOpener.open(menuType, player, MenuLocators.forBlockEntity(blockEntity));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean openViaPatternProviderLogicHost(PatternContainer provider, Player player) {
        if (!(provider instanceof PatternProviderLogicHost providerHost)) {
            return false;
        }

        try {
            if (providerHost instanceof AdaptivePatternProviderPart adaptivePart) {
                providerHost.openMenu(player, MenuLocators.forPart(adaptivePart));
                return true;
            } else if (providerHost instanceof PatternProviderPart providerPart) {
                providerHost.openMenu(player, MenuLocators.forPart(providerPart));
                return true;
            } else if (providerHost instanceof AdaptivePatternProviderBlockEntity adaptiveBlockEntity) {
                providerHost.openMenu(player, MenuLocators.forBlockEntity(adaptiveBlockEntity));
                return true;
            } else if (providerHost instanceof PatternProviderBlockEntity providerBlockEntity) {
                providerHost.openMenu(player, MenuLocators.forBlockEntity(providerBlockEntity));
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    private static boolean openViaMenuProvider(PatternContainer provider, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        if (provider instanceof MenuProvider menuProvider) {
            return serverPlayer.openMenu(menuProvider).isPresent();
        }

        if (provider instanceof BlockEntity blockEntity && blockEntity instanceof MenuProvider menuProvider) {
            return serverPlayer.openMenu(menuProvider).isPresent();
        }

        return false;
    }

    private static boolean openViaReflectiveMenuProvider(PatternContainer provider, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        Object menuProvider = invokeNoArg(provider, "getMenuProvider");
        if (menuProvider instanceof MenuProvider providerMenu) {
            return serverPlayer.openMenu(providerMenu).isPresent();
        }

        return false;
    }

    private static boolean openViaBlockUi(PatternContainer provider, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(provider instanceof BlockEntity blockEntity)) {
            return false;
        }

        try {
            Class<?> blockUiMenuType = Class.forName("com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType");
            Method openUi = blockUiMenuType.getMethod("openUI", ServerPlayer.class, BlockPos.class);
            Object result = openUi.invoke(null, serverPlayer, blockEntity.getBlockPos());
            return result instanceof Boolean opened && opened;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean openViaReflectiveMenuType(PatternContainer provider, Player player) {
        if (!(provider instanceof BlockEntity blockEntity)) {
            return false;
        }

        MenuType<?> menuType = findMenuType(provider);
        return menuType != null && MenuOpener.open(menuType, player, MenuLocators.forBlockEntity(blockEntity));
    }

    private static boolean openViaReflectiveOpenMethod(PatternContainer provider, Player player) {
        Object locator = provider instanceof BlockEntity blockEntity ? MenuLocators.forBlockEntity(blockEntity) : null;

        for (Method method : getAllDeclaredMethods(provider.getClass())) {
            if (!REFLECTIVE_OPEN_METHOD_NAMES.contains(method.getName())) {
                continue;
            }

            Object[] args = buildOpenArgs(method.getParameterTypes(), player, locator);
            if (args == null) {
                continue;
            }

            try {
                method.setAccessible(true);
                Object result = method.invoke(provider, args);
                if (result instanceof Boolean opened) {
                    if (opened) {
                        return true;
                    }
                } else {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        return false;
    }

    private static boolean openNeoEcoCraftingSubsystemMenu(List<PatternContainer> providers, Player player) {
        if (!(player instanceof ServerPlayer)) {
            return false;
        }

        return providers.stream()
                .filter(PatternProviderMenuOpenHelper::isNeoEcoSmartPatternBus)
                .filter(provider -> !isPatternInventoryFull(provider))
                .sorted(Comparator
                        .comparingInt(PatternProviderMenuOpenHelper::neoEcoControllerDistanceSquared)
                        .thenComparingInt(PatternProviderMenuOpenHelper::neoEcoVerticalPriority)
                        .thenComparingLong(provider -> provider instanceof BlockEntity be ? be.getBlockPos().asLong() : Long.MAX_VALUE))
                .anyMatch(provider -> openViaBlockUi(provider, player));
    }

    private static boolean isNeoEcoSmartPatternBus(PatternContainer provider) {
        if (!(provider instanceof BlockEntity blockEntity)) {
            return false;
        }

        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return key != null && "neoecoae".equals(key.getNamespace()) && "crafting_pattern_bus".equals(key.getPath());
    }

    private static boolean isPatternInventoryFull(PatternContainer provider) {
        var inventory = provider.getTerminalPatternInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return inventory.size() > 0;
    }

    private static int neoEcoControllerDistanceSquared(PatternContainer provider) {
        if (!(provider instanceof BlockEntity blockEntity)) {
            return Integer.MAX_VALUE;
        }

        BlockPos controllerPos = getNeoEcoControllerPos(provider);
        if (controllerPos == null) {
            return Integer.MAX_VALUE;
        }

        return (int) blockEntity.getBlockPos().distSqr(controllerPos);
    }

    private static int neoEcoVerticalPriority(PatternContainer provider) {
        if (!(provider instanceof BlockEntity blockEntity)) {
            return Integer.MAX_VALUE;
        }

        BlockPos controllerPos = getNeoEcoControllerPos(provider);
        if (controllerPos == null) {
            return Integer.MAX_VALUE;
        }

        int deltaY = blockEntity.getBlockPos().getY() - controllerPos.getY();
        return deltaY >= 0 ? deltaY * 2 : (-deltaY * 2) - 1;
    }

    @Nullable
    private static BlockPos getNeoEcoControllerPos(PatternContainer provider) {
        Object cluster = invokeNoArg(provider, "getCluster");
        if (cluster == null) {
            return null;
        }

        Object controller = invokeNoArg(cluster, "getController");
        if (controller instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockPos();
        }

        return null;
    }

    private static boolean openAssemblerMatrixMainMenu(PatternContainer provider, Player player) {
        Object cluster = invokeNoArg(provider, "getCluster");
        if (cluster == null) {
            return false;
        }

        Object core = invokeNoArg(cluster, "getCore");
        if (!(core instanceof BlockEntity coreBlockEntity)) {
            return false;
        }

        try {
            Class<?> containerClass = Class.forName("com.glodblock.github.extendedae.container.ContainerAssemblerMatrix");
            Object type = containerClass.getField("TYPE").get(null);
            if (type instanceof MenuType<?> menuType) {
                return MenuOpener.open(menuType, player, MenuLocators.forBlockEntity(coreBlockEntity));
            }
        } catch (ReflectiveOperationException ignored) {}

        return false;
    }

    @Nullable
    private static MenuType<?> findMenuType(Object target) {
        Object value = invokeNoArg(target, "getMenuType");
        if (value instanceof MenuType<?> menuType) {
            return menuType;
        }
        if (value instanceof Supplier<?> supplier && supplier.get() instanceof MenuType<?> menuType) {
            return menuType;
        }

        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!MenuType.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(null);
                    if (fieldValue instanceof MenuType<?> menuType) {
                        return menuType;
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
            type = type.getSuperclass();
        }

        return null;
    }

    @Nullable
    private static Object[] buildOpenArgs(Class<?>[] parameterTypes, Player player, @Nullable Object locator) {
        if (parameterTypes.length == 1) {
            if (parameterTypes[0].isInstance(player)) {
                return new Object[] { player };
            }
            return null;
        }

        if (parameterTypes.length == 2 && locator != null) {
            boolean firstPlayer = parameterTypes[0].isInstance(player);
            boolean secondPlayer = parameterTypes[1].isInstance(player);
            boolean firstLocator = parameterTypes[0].isInstance(locator);
            boolean secondLocator = parameterTypes[1].isInstance(locator);

            if (firstPlayer && secondLocator) {
                return new Object[] { player, locator };
            }
            if (firstLocator && secondPlayer) {
                return new Object[] { locator, player };
            }
        }

        return null;
    }

    private static List<Method> getAllDeclaredMethods(Class<?> type) {
        var methods = new java.util.ArrayList<Method>();
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                methods.add(method);
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }
}
