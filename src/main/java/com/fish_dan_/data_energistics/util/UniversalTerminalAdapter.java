package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalBehavior;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalConfigurationProfile;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalContext;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated implement the public API's terminal behavior and use {@link UniversalTerminalContext} instead. This
 *             compatibility contract will be removed in 3.1.0.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public interface UniversalTerminalAdapter extends UniversalTerminalBehavior {

    String name();

    boolean matches(ItemStack stack);

    default boolean canInstall(ItemStack stack) {
        return this.matches(stack);
    }

    default ItemStack createStoredTerminal(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    ItemStack createIcon();

    MenuType<?> getMenuType();

    /**
     * Bridges the historical bean-style menu type accessor to the public runtime contract.
     */
    @Override
    default MenuType<?> menuType() {
        return this.getMenuType();
    }

    default boolean requiresCustomMenuLocator() {
        return false;
    }

    default @Nullable IConfigManager createConfigManager(Runnable saveAction) {
        return null;
    }

    default <T> @Nullable T resolveMenuHost(UniversalTerminalPart part,
                                            Player player,
                                            Class<T> hostInterface) {
        return hostInterface.isInstance(part) ? hostInterface.cast(part) : null;
    }

    /**
     * Routes the public context through the concrete-part compatibility bridge.
     */
    @Override
    default <T> @Nullable T resolveMenuHost(UniversalTerminalContext context,
                                            Class<T> hostInterface) {
        if (!(context instanceof UniversalTerminalContextBridge bridge)) {
            throw new IllegalArgumentException("Legacy universal terminal adapter requires the internal context bridge");
        }
        return this.resolveMenuHost(bridge.part(), context.player(), hostInterface);
    }

    default UniversalTerminalConfigProfile configProfile() {
        return UniversalTerminalConfigProfile.STANDARD;
    }

    /**
     * Maps the deprecated configuration enum onto the public API profile.
     */
    @Override
    default UniversalTerminalConfigurationProfile configurationProfile() {
        return switch (this.configProfile()) {
            case STANDARD -> UniversalTerminalConfigurationProfile.STANDARD;
            case PATTERN_ACCESS -> UniversalTerminalConfigurationProfile.PATTERN_ACCESS;
        };
    }
}
