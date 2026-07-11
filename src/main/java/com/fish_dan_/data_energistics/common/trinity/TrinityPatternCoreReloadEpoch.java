package com.fish_dan_.data_energistics.common.trinity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local epoch used by loaded pattern cores to invalidate recipe caches after a server data reload.
 */
public final class TrinityPatternCoreReloadEpoch {

    private static final AtomicLong EPOCH = new AtomicLong();

    private TrinityPatternCoreReloadEpoch() {}

    /**
     * Advances the epoch after tags and recipes have been rebound.
     *
     * @return new epoch
     */
    public static long advance() {
        return EPOCH.incrementAndGet();
    }

    /**
     * @return current reload epoch observed by pattern core block entities
     */
    public static long current() {
        return EPOCH.get();
    }
}
