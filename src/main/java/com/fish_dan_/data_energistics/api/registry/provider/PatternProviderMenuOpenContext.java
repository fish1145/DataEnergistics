package com.fish_dan_.data_energistics.api.registry.provider;

import net.minecraft.server.level.ServerPlayer;

import appeng.helpers.patternprovider.PatternContainer;

import java.util.List;

/**
 * Immutable group snapshot supplied to a menu-open adapter.
 *
 * @param player   server-side player requesting the provider menu
 * @param providers complete provider group selected by the terminal
 */
public record PatternProviderMenuOpenContext(ServerPlayer player,
                                             List<PatternContainer> providers) {

    /**
     * Copies the group so an adapter cannot mutate the dispatcher-owned list.
     */
    public PatternProviderMenuOpenContext {
        providers = List.copyOf(providers);
    }
}
