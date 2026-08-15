package com.fish_dan_.data_energistics.common.recipe;

import java.util.concurrent.atomic.AtomicLong;

/** Process-local epoch used to invalidate immutable recipe-derived caches after server data reloads. */
public final class RecipeReloadEpoch {

    private static final AtomicLong EPOCH = new AtomicLong();

    private RecipeReloadEpoch() {}

    /**
     * Advances the epoch after tags and recipes have been rebound.
     *
     * @return new epoch
     */
    public static long advance() {
        return EPOCH.incrementAndGet();
    }

    /**
     * @return current server recipe reload epoch
     */
    public static long current() {
        return EPOCH.get();
    }
}
