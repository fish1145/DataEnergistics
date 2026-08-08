package com.fish_dan_.data_energistics.api.registry.terminal;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.NotNull;
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
    @NotNull
    String name();

    /**
     * Reports whether this behavior recognizes a candidate terminal stack.
     */
    boolean matches(@NotNull ItemStack stack);

    /**
     * Performs final installation validation after {@link #matches(ItemStack)} succeeds.
     */
    default boolean canInstall(@NotNull ItemStack stack) {
        return this.matches(stack);
    }

    /**
     * Captures one independently owned terminal item for combined-terminal storage.
     */
    default @NotNull ItemStack createStoredTerminal(@NotNull ItemStack stack) {
        return stack.copyWithCount(1);
    }

    /**
     * @return independently owned icon stack
     */
    @NotNull
    ItemStack createIcon();

    /**
     * @return registered menu type opened for this terminal
     */
    @NotNull
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
    default @Nullable IConfigManager createConfigManager(@NotNull Runnable saveAction) {
        return null;
    }

    /**
     * Resolves the menu host without exposing the concrete universal-terminal part.
     */
    default <T> @Nullable T resolveMenuHost(@NotNull UniversalTerminalContext context,
                                            @NotNull Class<T> hostInterface) {
        return context.resolveDefaultMenuHost(hostInterface);
    }

    /**
     * @return built-in settings layout selected by this behavior
     */
    default @NotNull UniversalTerminalConfigurationProfile configurationProfile() {
        return UniversalTerminalConfigurationProfile.STANDARD;
    }
}
