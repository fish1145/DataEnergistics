package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import net.minecraft.core.HolderLookup;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Server-thread, time-sliced graph capture with atomic immutable publication.
 *
 * <p>
 * Runtime pattern references exist only inside the active server-thread build. Planner threads can read
 * {@link #publishedSnapshot()} and receive value objects only.
 * </p>
 */
public final class TrinityCraftingGraphRebuilder {

    /**
     * Outcome of one bounded server-thread advance.
     */
    public enum AdvanceStatus {

        /**
         * The already-published snapshot still represents the source revision.
         */
        CURRENT,
        /**
         * The current revision still has uncaptured work after spending this advance's budget.
         */
        IN_PROGRESS,
        /**
         * A stale build was discarded and capture state was reset to the newly observed revision.
         */
        RESTARTED,
        /**
         * A complete revision-consistent snapshot was atomically published.
         */
        PUBLISHED
    }

    /**
     * Observable progress returned without exposing the mutable build state.
     *
     * @param status               result category
     * @param targetRevision       revision now current or being rebuilt
     * @param capturedPatternCount distinct complete pattern semantics captured for the active/current revision
     */
    public record AdvanceResult(AdvanceStatus status, long targetRevision, int capturedPatternCount) {

        /**
         * Validates progress metadata before it is reported to diagnostics.
         */
        public AdvanceResult {
            if (targetRevision < 0L || capturedPatternCount < 0) {
                throw new IllegalArgumentException("A Trinity graph advance requires non-negative progress");
            }
        }
    }

    private final TrinityCraftingGraphCaptureSource source;
    private final LongSupplier nanoTime;
    private final AtomicReference<TrinityCraftingGraphSnapshot> published = new AtomicReference<>();

    @Nullable
    private Thread captureThread;
    @Nullable
    private BuildState build;

    /**
     * Creates a detached cache that performs no provider reads until {@link #advance(long)}.
     *
     * @param source   narrow server-thread provider view
     * @param nanoTime monotonic time source, injectable for deterministic budget tests
     */
    public TrinityCraftingGraphRebuilder(TrinityCraftingGraphCaptureSource source, LongSupplier nanoTime) {
        this.source = source;
        this.nanoTime = nanoTime;
    }

    /**
     * Advances capture until the supplied nanosecond budget is spent or a complete snapshot can publish.
     *
     * @param budgetNanos positive per-call server-thread budget
     * @return bounded progress result
     */
    public AdvanceResult advance(long budgetNanos) {
        if (budgetNanos <= 0L) {
            throw new IllegalArgumentException("A Trinity graph rebuild budget must be positive");
        }
        assertCaptureThread();
        try {
            return advanceCapture(budgetNanos);
        } catch (RuntimeException | Error failure) {
            this.build = null;
            throw failure;
        }
    }

    private AdvanceResult advanceCapture(long budgetNanos) {
        long observedRevision = checkedRevision();
        TrinityCraftingGraphSnapshot current = this.published.get();
        if (this.build == null && current != null && current.revision() == observedRevision) {
            return new AdvanceResult(AdvanceStatus.CURRENT, observedRevision, current.patterns().size());
        }
        long startedAt = this.nanoTime.getAsLong();
        boolean performedWork = false;
        if (this.build == null) {
            if (!beginBuild(observedRevision)) {
                long restartedRevision = checkedRevision();
                return new AdvanceResult(AdvanceStatus.RESTARTED, restartedRevision, 0);
            }
            performedWork = true;
        } else if (this.build.revision != observedRevision) {
            return restartAt(observedRevision);
        }

        while (true) {
            BuildState active = this.build;
            long currentRevision = checkedRevision();
            if (currentRevision != active.revision) {
                return restartAt(currentRevision);
            }
            if (performedWork && elapsedSince(startedAt) >= budgetNanos) {
                return progress(active);
            }

            if (active.patternsForKey == null) {
                if (active.keyIndex >= active.craftableKeys.size()) {
                    return publishCompleted(active);
                }
                active.primaryOutput = active.craftableKeys.get(active.keyIndex);
                active.patternsForKey = List.copyOf(this.source.capturePatternsFor(active.primaryOutput));
                active.patternIndex = 0;
                performedWork = true;
                currentRevision = checkedRevision();
                if (currentRevision != active.revision) {
                    return restartAt(currentRevision);
                }
                if (active.patternsForKey.isEmpty()) {
                    active.finishKey();
                }
                continue;
            }

            if (active.patternIndex >= active.patternsForKey.size()) {
                active.finishKey();
                continue;
            }

            IPatternDetails details = active.patternsForKey.get(active.patternIndex);
            TrinityPatternPublicationSignature publication = TrinityPatternPublicationSignature.capture(details);
            if (!publication.outputs().getFirst().what().equals(active.primaryOutput)) {
                throw new IllegalArgumentException(
                        "A Trinity graph pattern must be indexed by its primary output");
            }
            TrinityPatternIdentity identity = TrinityPatternIdentity.capture(publication, active.registries);
            TrinityCraftingGraphPattern captured = new TrinityCraftingGraphPattern(identity, publication);

            currentRevision = checkedRevision();
            if (currentRevision != active.revision) {
                return restartAt(currentRevision);
            }
            TrinityCraftingGraphPattern duplicate = active.patterns.putIfAbsent(identity, captured);
            if (duplicate != null && !duplicate.equals(captured)) {
                throw new IllegalStateException("Canonical Trinity pattern identity collision for " + identity);
            }
            active.patternIndex++;
            performedWork = true;
        }
    }

    /**
     * Returns the last complete snapshot without observing or exposing an in-progress build.
     *
     * @return empty before the first complete publication
     */
    public Optional<TrinityCraftingGraphSnapshot> publishedSnapshot() {
        return Optional.ofNullable(this.published.get());
    }

    private AdvanceResult publishCompleted(BuildState completed) {
        long currentRevision = checkedRevision();
        if (currentRevision != completed.revision) {
            return restartAt(currentRevision);
        }
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(completed.revision, List.copyOf(completed.patterns.values()));
        currentRevision = checkedRevision();
        if (currentRevision != completed.revision) {
            return restartAt(currentRevision);
        }
        this.published.set(snapshot);
        this.build = null;
        return new AdvanceResult(AdvanceStatus.PUBLISHED, snapshot.revision(), snapshot.patterns().size());
    }

    private AdvanceResult restartAt(long revision) {
        this.build = null;
        return new AdvanceResult(AdvanceStatus.RESTARTED, revision, 0);
    }

    private boolean beginBuild(long revision) {
        HolderLookup.Provider registries = this.source.registries();
        List<AEKey> craftableKeys = List.copyOf(this.source.captureCraftableKeys());
        if (checkedRevision() != revision) {
            return false;
        }
        this.build = new BuildState(revision, registries, craftableKeys);
        return true;
    }

    private AdvanceResult progress(BuildState active) {
        return new AdvanceResult(AdvanceStatus.IN_PROGRESS, active.revision, active.patterns.size());
    }

    private long checkedRevision() {
        long revision = this.source.revision();
        if (revision < 0L) {
            throw new IllegalArgumentException("A Trinity graph source revision cannot be negative");
        }
        return revision;
    }

    private long elapsedSince(long startedAt) {
        long elapsed = this.nanoTime.getAsLong() - startedAt;
        if (elapsed < 0L) {
            throw new IllegalStateException(
                    "A Trinity graph rebuild clock must be monotonic within one bounded advance");
        }
        return elapsed;
    }

    private void assertCaptureThread() {
        Thread current = Thread.currentThread();
        if (this.captureThread == null) {
            this.captureThread = current;
        } else if (this.captureThread != current) {
            throw new IllegalStateException("A Trinity graph rebuild must remain on its server capture thread");
        }
    }

    /**
     * Mutable state confined to the one thread allowed to call {@link #advance(long)}.
     */
    private static final class BuildState {

        private final long revision;
        private final HolderLookup.Provider registries;
        private final List<AEKey> craftableKeys;
        private final TreeMap<TrinityPatternIdentity, TrinityCraftingGraphPattern> patterns = new TreeMap<>();

        private int keyIndex;
        private int patternIndex;
        @Nullable
        private AEKey primaryOutput;
        @Nullable
        private List<IPatternDetails> patternsForKey;

        private BuildState(long revision,
                           HolderLookup.Provider registries,
                           List<AEKey> craftableKeys) {
            this.revision = revision;
            this.registries = registries;
            this.craftableKeys = craftableKeys;
        }

        private void finishKey() {
            this.keyIndex++;
            this.patternIndex = 0;
            this.primaryOutput = null;
            this.patternsForKey = null;
        }
    }
}
