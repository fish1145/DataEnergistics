package com.fish_dan_.data_energistics.api.registry.terminal;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Immutable registration token that captures one adapter's stable terminal name.
 *
 * <p>
 * The wrapper separates the common-setup declaration from the adapter instance invoked at runtime and prevents a
 * mutable adapter name from changing registry identity after validation.
 * </p>
 */
public final class UniversalTerminalRegistration {

    /**
     * Stable persisted terminal name captured at registration construction.
     */
    private final @NotNull String name;
    /**
     * Runtime behavior retained by the frozen snapshot.
     */
    private final @NotNull UniversalTerminalBehavior adapter;

    /**
     * Captures and validates a custom adapter.
     *
     * @param adapter stateless terminal behavior
     */
    public UniversalTerminalRegistration(@NotNull UniversalTerminalBehavior adapter) {
        this.adapter = adapter;
        this.name = requireAdapterResult(adapter.name(), "name");
        if (this.name.isBlank()) {
            throw new IllegalArgumentException("Universal terminal name must not be blank");
        }
    }

    /**
     * Starts a standard registration using declarative match, icon and menu suppliers.
     *
     * @param name             stable persisted terminal name
     * @param matcher          predicate accepting compatible item stacks
     * @param iconSupplier     supplier creating menu icons
     * @param menuTypeSupplier supplier resolving the registered menu type
     * @return configurable registration builder
     */
    public static @NotNull Builder builder(
            @NotNull String name,
            @NotNull Predicate<@NotNull ItemStack> matcher,
            @NotNull Supplier<@NotNull ItemStack> iconSupplier,
            @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier) {
        return new Builder(name, matcher, iconSupplier, menuTypeSupplier);
    }

    /**
     * @return stable persisted terminal name
     */
    public @NotNull String name() {
        return this.name;
    }

    /**
     * Delegates candidate matching to the frozen adapter.
     */
    public boolean matches(@NotNull ItemStack stack) {
        return this.adapter.matches(stack);
    }

    /**
     * Delegates final installation validation to the frozen adapter.
     */
    public boolean canInstall(@NotNull ItemStack stack) {
        return this.adapter.canInstall(stack);
    }

    /**
     * Delegates stored-stack capture to the frozen adapter.
     */
    public @NotNull ItemStack createStoredTerminal(@NotNull ItemStack stack) {
        ItemStack storedTerminal = requireAdapterResult(
                this.adapter.createStoredTerminal(stack), "stored terminal stack");
        if (storedTerminal.isEmpty()) {
            throw new IllegalStateException("Universal terminal adapter returned an empty stored terminal stack");
        }
        return storedTerminal.copyWithCount(1);
    }

    /**
     * Creates an independently owned icon for presentation.
     */
    public @NotNull ItemStack createIcon() {
        ItemStack icon = requireAdapterResult(this.adapter.createIcon(), "icon stack");
        if (icon.isEmpty()) {
            throw new IllegalStateException("Universal terminal adapter returned an empty icon stack");
        }
        return icon.copy();
    }

    /**
     * Resolves the registered menu type.
     */
    public @NotNull MenuType<?> menuType() {
        return requireAdapterResult(this.adapter.menuType(), "menu type");
    }

    /**
     * @return whether this registration needs the terminal-name-aware locator
     */
    public boolean requiresCustomMenuLocator() {
        return this.adapter.requiresCustomMenuLocator();
    }

    /**
     * Creates optional host-local configuration through the adapter.
     */
    public @Nullable IConfigManager createConfigManager(@NotNull Runnable saveAction) {
        return this.adapter.createConfigManager(saveAction);
    }

    /**
     * Resolves the menu host through the implementation-independent context.
     */
    public <T> @Nullable T resolveMenuHost(@NotNull UniversalTerminalContext context,
                                           @NotNull Class<T> hostInterface) {
        return this.adapter.resolveMenuHost(context, hostInterface);
    }

    /**
     * @return built-in settings layout selected by the adapter
     */
    public @NotNull UniversalTerminalConfigurationProfile configurationProfile() {
        return requireAdapterResult(this.adapter.configurationProfile(), "configuration profile");
    }

    /**
     * Validates an untrusted adapter callback result without duplicating ordinary parameter checks.
     */
    private static <T> @NotNull T requireAdapterResult(
            @UnknownNullability T result,
            @NotNull String role) {
        if (result == null) {
            throw new IllegalStateException("Universal terminal adapter returned a null " + role);
        }
        return result;
    }

    /**
     * Builder for the standard adapter used by integrations that need no custom host behavior.
     */
    public static final class Builder {

        /**
         * Stable persisted terminal name.
         */
        private final @NotNull String name;
        /**
         * Candidate matcher.
         */
        private final @NotNull Predicate<@NotNull ItemStack> matcher;
        /**
         * Menu icon factory.
         */
        private final @NotNull Supplier<@NotNull ItemStack> iconSupplier;
        /**
         * Registered menu type resolver.
         */
        private final @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier;
        /**
         * Optional host-local configuration factory.
         */
        @Nullable
        private Function<@NotNull Runnable, @Nullable IConfigManager> configManagerFactory;
        /**
         * Built-in settings layout.
         */
        private @NotNull UniversalTerminalConfigurationProfile configurationProfile = UniversalTerminalConfigurationProfile.STANDARD;
        /**
         * Whether menu opening needs the terminal-name-aware locator.
         */
        private boolean requiresCustomMenuLocator;

        /**
         * Captures required declarative fields before optional behavior is selected.
         */
        private Builder(
                @NotNull String name,
                @NotNull Predicate<@NotNull ItemStack> matcher,
                @NotNull Supplier<@NotNull ItemStack> iconSupplier,
                @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier) {
            this.name = name;
            this.matcher = matcher;
            this.iconSupplier = iconSupplier;
            this.menuTypeSupplier = menuTypeSupplier;
        }

        /**
         * Selects the built-in settings layout.
         *
         * @param profile required configuration profile
         * @return this builder
         */
        public @NotNull Builder configurationProfile(@NotNull UniversalTerminalConfigurationProfile profile) {
            this.configurationProfile = profile;
            return this;
        }

        /**
         * Selects the terminal-name-aware menu locator.
         *
         * @param required whether the custom locator is required
         * @return this builder
         */
        public @NotNull Builder requiresCustomMenuLocator(boolean required) {
            this.requiresCustomMenuLocator = required;
            return this;
        }

        /**
         * Supplies optional host-local menu configuration.
         *
         * @param factory configuration factory
         * @return this builder
         */
        public @NotNull Builder configManagerFactory(
                @NotNull Function<@NotNull Runnable, @Nullable IConfigManager> factory) {
            this.configManagerFactory = factory;
            return this;
        }

        /**
         * Freezes the configured standard behavior into a registration.
         *
         * @return validated universal-terminal registration
         */
        public @NotNull UniversalTerminalRegistration build() {
            return new UniversalTerminalRegistration(new StandardAdapter(
                    this.name,
                    this.matcher,
                    this.iconSupplier,
                    this.menuTypeSupplier,
                    this.configurationProfile,
                    this.requiresCustomMenuLocator,
                    this.configManagerFactory));
        }
    }

    /**
     * Standard immutable adapter assembled by {@link Builder}.
     */
    private record StandardAdapter(@NotNull String name,
                                   @NotNull Predicate<@NotNull ItemStack> matcher,
                                   @NotNull Supplier<@NotNull ItemStack> iconSupplier,
                                   @NotNull Supplier<@NotNull MenuType<?>> menuTypeSupplier,
                                   @NotNull UniversalTerminalConfigurationProfile configurationProfile,
                                   boolean requiresCustomMenuLocator,
                                   @Nullable Function<@NotNull Runnable, @Nullable IConfigManager> configManagerFactory)
            implements UniversalTerminalBehavior {

        @Override
        public boolean matches(@NotNull ItemStack stack) {
            return this.matcher.test(stack);
        }

        @Override
        public @NotNull ItemStack createIcon() {
            return this.iconSupplier.get();
        }

        @Override
        public @NotNull MenuType<?> menuType() {
            return this.menuTypeSupplier.get();
        }

        @Override
        public @Nullable IConfigManager createConfigManager(@NotNull Runnable saveAction) {
            return this.configManagerFactory == null ? null : this.configManagerFactory.apply(saveAction);
        }
    }
}
