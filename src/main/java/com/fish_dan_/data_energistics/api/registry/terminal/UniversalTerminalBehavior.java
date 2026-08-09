package com.fish_dan_.data_energistics.api.registry.terminal;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.Nullable;

/**
 * Complete runtime behavior retained by one universal-terminal registration.
 *
 * <p>
 * The same contract also permits the deprecated implementation bridge to participate without making the API
 * depend on its legacy package. New integrations register this behavior through {@link UniversalTerminalRegistry}.
 * </p>
 */
public interface UniversalTerminalBehavior {

    /**
     * @return non-blank stable persisted terminal name
     */
    String name();

    /**
     * Reports whether this behavior recognizes a candidate terminal stack.
     */
    boolean matches(ItemStack stack);

    /**
     * Performs final installation validation after {@link #matches(ItemStack)} succeeds.
     */
    default boolean canInstall(ItemStack stack) {
        return this.matches(stack);
    }

    /**
     * Captures one independently owned terminal item for combined-terminal storage.
     */
    default ItemStack createStoredTerminal(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    /**
     * @return independently owned icon stack
     */
    ItemStack createIcon();

    /**
     * @return registered menu type opened for this terminal
     */
    MenuType<?> menuType();

    /**
     * @return whether menu opening needs the terminal-name-aware locator
     */
    default boolean requiresCustomMenuLocator() {
        return false;
    }

    /**
     * Creates optional persistent menu configuration for one host.
     *
     * @param saveAction callback invoked when configuration changes
     * @return host-local configuration, or {@code null} when none is required
     */
    default @Nullable IConfigManager createConfigManager(Runnable saveAction) {
        return null;
    }

    /**
     * Resolves the menu host without exposing the concrete universal-terminal part.
     */
    default <T> @Nullable T resolveMenuHost(UniversalTerminalContext context,
                                            Class<T> hostInterface) {
        return context.resolveDefaultMenuHost(hostInterface);
    }

    /**
     * @return built-in settings layout selected by this behavior
     */
    default UniversalTerminalConfigurationProfile configurationProfile() {
        return UniversalTerminalConfigurationProfile.STANDARD;
    }
}
