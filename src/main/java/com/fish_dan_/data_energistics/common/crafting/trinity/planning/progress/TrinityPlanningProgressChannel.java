package com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Closeable latest-value channel shared by a planning worker and its owning server menu revision.
 *
 * <p>
 * It deliberately retains only immutable snapshots. The menu polls it from its server-thread broadcast lifecycle and
 * is solely responsible for networking, throttling, and player/container validation.
 * </p>
 */
public final class TrinityPlanningProgressChannel implements TrinityPlanningProgressReporter {

    private final AtomicReference<@Nullable TrinityPlanningProgressSnapshot> latest = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public void publish(TrinityPlanningProgressSnapshot snapshot) {
        if (!this.closed.get()) {
            this.latest.set(snapshot);
        }
    }

    public @Nullable TrinityPlanningProgressSnapshot latest() {
        return this.latest.get();
    }

    public void close() {
        this.closed.set(true);
        this.latest.set(null);
    }

    public boolean closed() {
        return this.closed.get();
    }
}
