package com.fish_dan_.data_energistics.util;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @deprecated build a public {@code UniversalTerminalRegistration} or implement its terminal behavior instead. This
 * compatibility definition will be removed in 3.1.0.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public record UniversalTerminalDefinition(
        @NotNull String name,
        @NotNull Predicate<@NotNull ItemStack> matcher,
        @NotNull Supplier<@NotNull ItemStack> iconSupplier,
        @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier,
        @NotNull UniversalTerminalConfigProfile configProfile,
        boolean requiresCustomMenuLocator,
        @Nullable Function<@NotNull Runnable, @Nullable IConfigManager> configManagerFactory)
        implements UniversalTerminalAdapter {

    public UniversalTerminalDefinition(
            @NotNull String name,
            @NotNull Predicate<@NotNull ItemStack> matcher,
            @NotNull Supplier<@NotNull ItemStack> iconSupplier,
            @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier) {
        this(name, matcher, iconSupplier, menuTypeSupplier, UniversalTerminalConfigProfile.STANDARD, false, null);
    }

    public UniversalTerminalDefinition(
            @NotNull String name,
            @NotNull Predicate<@NotNull ItemStack> matcher,
            @NotNull Supplier<@NotNull ItemStack> iconSupplier,
            @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier,
            @NotNull UniversalTerminalConfigProfile configProfile) {
        this(name, matcher, iconSupplier, menuTypeSupplier, configProfile, false, null);
    }

    public UniversalTerminalDefinition(
            @NotNull String name,
            @NotNull Predicate<@NotNull ItemStack> matcher,
            @NotNull Supplier<@NotNull ItemStack> iconSupplier,
            @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier,
            @NotNull UniversalTerminalConfigProfile configProfile,
            boolean requiresCustomMenuLocator) {
        this(name, matcher, iconSupplier, menuTypeSupplier, configProfile, requiresCustomMenuLocator, null);
    }

    @Override
    public boolean matches(@NotNull ItemStack stack) {
        return this.matcher.test(stack);
    }

    @Override
    public @NotNull MenuType<?> getMenuType() {
        return this.menuTypeSupplier.get();
    }

    @Override
    public @NotNull ItemStack createIcon() {
        return this.iconSupplier.get().copy();
    }

    @Override
    public @Nullable IConfigManager createConfigManager(@NotNull Runnable saveAction) {
        return this.configManagerFactory != null ? this.configManagerFactory.apply(saveAction) : null;
    }
}
