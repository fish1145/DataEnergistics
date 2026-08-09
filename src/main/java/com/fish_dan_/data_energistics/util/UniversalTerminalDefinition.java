package com.fish_dan_.data_energistics.util;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @deprecated build a public {@code UniversalTerminalRegistration} or implement its terminal behavior instead. This
 *             compatibility definition will be removed in 3.1.0.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public record UniversalTerminalDefinition(
                                          String name,
                                          Predicate<ItemStack> matcher,
                                          Supplier<ItemStack> iconSupplier,
                                          Supplier<MenuType<?>> menuTypeSupplier,
                                          UniversalTerminalConfigProfile configProfile,
                                          boolean requiresCustomMenuLocator,
                                          @Nullable Function<Runnable, @Nullable IConfigManager> configManagerFactory)
        implements UniversalTerminalAdapter {

    public UniversalTerminalDefinition(
                                       String name,
                                       Predicate<ItemStack> matcher,
                                       Supplier<ItemStack> iconSupplier,
                                       Supplier<MenuType<?>> menuTypeSupplier) {
        this(name, matcher, iconSupplier, menuTypeSupplier, UniversalTerminalConfigProfile.STANDARD, false, null);
    }

    public UniversalTerminalDefinition(
                                       String name,
                                       Predicate<ItemStack> matcher,
                                       Supplier<ItemStack> iconSupplier,
                                       Supplier<MenuType<?>> menuTypeSupplier,
                                       UniversalTerminalConfigProfile configProfile) {
        this(name, matcher, iconSupplier, menuTypeSupplier, configProfile, false, null);
    }

    public UniversalTerminalDefinition(
                                       String name,
                                       Predicate<ItemStack> matcher,
                                       Supplier<ItemStack> iconSupplier,
                                       Supplier<MenuType<?>> menuTypeSupplier,
                                       UniversalTerminalConfigProfile configProfile,
                                       boolean requiresCustomMenuLocator) {
        this(name, matcher, iconSupplier, menuTypeSupplier, configProfile, requiresCustomMenuLocator, null);
    }

    @Override
    public boolean matches(ItemStack stack) {
        return this.matcher.test(stack);
    }

    @Override
    public MenuType<?> getMenuType() {
        return this.menuTypeSupplier.get();
    }

    @Override
    public ItemStack createIcon() {
        return this.iconSupplier.get().copy();
    }

    @Override
    public @Nullable IConfigManager createConfigManager(Runnable saveAction) {
        return this.configManagerFactory != null ? this.configManagerFactory.apply(saveAction) : null;
    }
}
