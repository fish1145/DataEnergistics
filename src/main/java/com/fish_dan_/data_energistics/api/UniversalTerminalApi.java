package com.fish_dan_.data_energistics.api;

import com.fish_dan_.data_energistics.util.UniversalTerminalAdapter;
import com.fish_dan_.data_energistics.util.UniversalTerminalConfigProfile;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;
import com.fish_dan_.data_energistics.util.UniversalTerminalDefinition;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class UniversalTerminalApi {

    private UniversalTerminalApi() {}

    public static void registerAdapter(UniversalTerminalAdapter adapter) {
        UniversalTerminalData.registerAdapter(adapter);
    }

    public static void registerTerminal(String name,
                                        Predicate<ItemStack> matcher,
                                        Supplier<ItemStack> iconSupplier,
                                        Supplier<MenuType<?>> menuTypeSupplier) {
        registerAdapter(new UniversalTerminalDefinition(name, matcher, iconSupplier, menuTypeSupplier));
    }

    public static void registerTerminal(String name,
                                        Predicate<ItemStack> matcher,
                                        Supplier<ItemStack> iconSupplier,
                                        Supplier<MenuType<?>> menuTypeSupplier,
                                        UniversalTerminalConfigProfile configProfile,
                                        boolean requiresCustomMenuLocator,
                                        @Nullable Function<Runnable, IConfigManager> configManagerFactory) {
        registerAdapter(new UniversalTerminalDefinition(
                name,
                matcher,
                iconSupplier,
                menuTypeSupplier,
                configProfile,
                requiresCustomMenuLocator,
                configManagerFactory));
    }

    public static boolean isSupportedTerminal(ItemStack stack) {
        return UniversalTerminalData.isSupportedTerminal(stack);
    }
}
