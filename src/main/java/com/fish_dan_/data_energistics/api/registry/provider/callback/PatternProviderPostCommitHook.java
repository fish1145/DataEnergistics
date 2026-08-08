package com.fish_dan_.data_energistics.api.registry.provider.callback;

import org.jetbrains.annotations.NotNull;

/**
 * Observes a provider commit after the server has confirmed the real inventory delta.
 */
@FunctionalInterface
public interface PatternProviderPostCommitHook {

    /**
     * Observes one confirmed provider commit.
     *
     * @param context immutable commit facts
     */
    void afterCommit(@NotNull PatternProviderPostCommitContext context);
}
