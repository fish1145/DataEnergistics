package com.fish_dan_.data_energistics.api.registry.provider.callback;

/**
 * Optional typed handler for opening a menu for an entire provider group.
 */
@FunctionalInterface
public interface PatternProviderMenuOpenAdapter {

    /**
     * Attempts to handle the requested provider group.
     *
     * @param context immutable server-side group context
     * @return whether to continue, report success, or stop with an explicit denial
     */
    PatternProviderMenuOpenResult open(PatternProviderMenuOpenContext context);
}
