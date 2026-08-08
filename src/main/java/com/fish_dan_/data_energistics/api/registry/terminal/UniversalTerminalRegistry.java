package com.fish_dan_.data_energistics.api.registry.terminal;

import com.fish_dan_.data_energistics.util.UniversalTerminalAdapter;
import com.fish_dan_.data_energistics.util.UniversalTerminalConfigProfile;
import com.fish_dan_.data_energistics.util.UniversalTerminalDefinition;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Declaration and read-only query surface for universal-terminal adapters.
 */
public interface UniversalTerminalRegistry {

    /**
     * Registers one terminal adapter in the current plugin staging transaction.
     *
     * @param adapter adapter to register
     */
    void register(UniversalTerminalAdapter adapter);

    /**
     * Registers a terminal definition with the standard configuration profile.
     *
     * @param name             stable terminal name
     * @param matcher          predicate accepting compatible item stacks
     * @param iconSupplier     supplier for the terminal icon
     * @param menuTypeSupplier supplier for the terminal menu type
     */
    default void registerTerminal(String name,
                                  Predicate<ItemStack> matcher,
                                  Supplier<ItemStack> iconSupplier,
                                  Supplier<MenuType<?>> menuTypeSupplier) {
        this.register(new UniversalTerminalDefinition(
                name,
                matcher,
                iconSupplier,
                menuTypeSupplier));
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
    default void registerTerminal(String name,
                                  Predicate<ItemStack> matcher,
                                  Supplier<ItemStack> iconSupplier,
                                  Supplier<MenuType<?>> menuTypeSupplier,
                                  UniversalTerminalConfigProfile configProfile,
                                  boolean requiresCustomMenuLocator,
                                  @Nullable Function<Runnable, IConfigManager> configManagerFactory) {
        this.register(new UniversalTerminalDefinition(
                name,
                matcher,
                iconSupplier,
                menuTypeSupplier,
                configProfile,
                requiresCustomMenuLocator,
                configManagerFactory));
    }

    /**
     * Checks whether a stack is supported by the frozen terminal registry.
     *
     * <p>
     * This query is intentionally available on the typed facet so callers do not need the removed static API.
     * Implementations must evaluate the current immutable runtime snapshot.
     * </p>
     *
     * @param stack stack to inspect
     * @return whether at least one registered adapter accepts the stack
     */
    boolean isSupportedTerminal(ItemStack stack);
}
