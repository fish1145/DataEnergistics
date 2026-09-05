package com.fish_dan_.data_energistics.api.registry.terminal;

import appeng.api.util.IConfigManager;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Common-setup declaration surface for universal-terminal registrations.
 */
public interface UniversalTerminalRegistry {

    /**
     * Registers one complete terminal declaration in the current plugin staging transaction.
     *
     * @param registration registration to stage
     */
    void register(UniversalTerminalRegistration registration);

    /**
     * Captures a custom adapter as one immutable registration token.
     *
     * @param adapter stateless adapter to register
     */
    default void register(UniversalTerminalBehavior adapter) {
        this.register(new UniversalTerminalRegistration(adapter));
    }

    /**
     * Registers a terminal definition with the standard configuration profile.
     *
     * @param name             stable terminal name
     * @param matcher          predicate accepting compatible item stacks
     * @param iconSupplier     supplier for the terminal icon
     * @param menuTypeSupplier supplier for the terminal menu type
     */
    default void registerTerminal(
                                  String name,
                                  Predicate<ItemStack> matcher,
                                  Supplier<ItemStack> iconSupplier,
                                  Supplier<MenuType<?>> menuTypeSupplier) {
        this.register(UniversalTerminalRegistration.builder(name, matcher, iconSupplier, menuTypeSupplier).build());
    }

    /**
     * Registers a terminal definition with explicit configuration and menu-locator behavior.
     *
     * @param name                      stable terminal name
     * @param matcher                   predicate accepting compatible item stacks
     * @param iconSupplier              supplier for the terminal icon
     * @param menuTypeSupplier          supplier for the terminal menu type
     * @param configProfile             terminal configuration profile
     * @param requiresCustomMenuLocator whether the terminal needs a custom menu locator
     * @param configManagerFactory      optional configuration-manager factory
     */
    default void registerTerminal(
                                  String name,
                                  Predicate<ItemStack> matcher,
                                  Supplier<ItemStack> iconSupplier,
                                  Supplier<MenuType<?>> menuTypeSupplier,
                                  UniversalTerminalConfigurationProfile configProfile,
                                  boolean requiresCustomMenuLocator,
                                  @Nullable Function<Runnable, @Nullable IConfigManager> configManagerFactory) {
        UniversalTerminalRegistration.Builder builder = UniversalTerminalRegistration
                .builder(name, matcher, iconSupplier, menuTypeSupplier)
                .configurationProfile(configProfile)
                .requiresCustomMenuLocator(requiresCustomMenuLocator);
        if (configManagerFactory != null) {
            builder.configManagerFactory(configManagerFactory);
        }
        this.register(builder.build());
    }
}
