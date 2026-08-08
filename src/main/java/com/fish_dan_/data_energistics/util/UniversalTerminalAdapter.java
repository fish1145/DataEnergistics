package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalBehavior;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalConfigurationProfile;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalContext;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated implement the public API's terminal behavior and use {@link UniversalTerminalContext} instead. This
 * compatibility contract will be removed in 3.1.0.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public interface UniversalTerminalAdapter extends UniversalTerminalBehavior {

    @NotNull
    String name();

    boolean matches(@NotNull ItemStack stack);

    default boolean canInstall(@NotNull ItemStack stack) {
        return this.matches(stack);
    }

    default @NotNull ItemStack createStoredTerminal(@NotNull ItemStack stack) {
        return stack.copyWithCount(1);
    }

    @NotNull
    ItemStack createIcon();

    @NotNull
    MenuType<?> getMenuType();

    /**
     * Bridges the historical bean-style menu type accessor to the public runtime contract.
     */
    @Override
    default @NotNull MenuType<?> menuType() {
        return this.getMenuType();
    }

    default boolean requiresCustomMenuLocator() {
        return false;
    }

    default @Nullable IConfigManager createConfigManager(@NotNull Runnable saveAction) {
        return null;
    }

    default <T> @Nullable T resolveMenuHost(@NotNull UniversalTerminalPart part,
                                            @NotNull Player player,
                                            @NotNull Class<T> hostInterface) {
        return hostInterface.isInstance(part) ? hostInterface.cast(part) : null;
    }

    /**
     * Routes the public context through the concrete-part compatibility bridge.
     */
    @Override
    default <T> @Nullable T resolveMenuHost(@NotNull UniversalTerminalContext context,
                                            @NotNull Class<T> hostInterface) {
        if (!(context instanceof UniversalTerminalContextBridge bridge)) {
            throw new IllegalArgumentException("Legacy universal terminal adapter requires the internal context bridge");
        }
        return this.resolveMenuHost(bridge.part(), context.player(), hostInterface);
    }

    default @NotNull UniversalTerminalConfigProfile configProfile() {
        return UniversalTerminalConfigProfile.STANDARD;
    }

    /**
     * Maps the deprecated configuration enum onto the public API profile.
     */
    @Override
    default @NotNull UniversalTerminalConfigurationProfile configurationProfile() {
        return switch (this.configProfile()) {
            case STANDARD -> UniversalTerminalConfigurationProfile.STANDARD;
            case PATTERN_ACCESS -> UniversalTerminalConfigurationProfile.PATTERN_ACCESS;
        };
    }
}
