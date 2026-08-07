package com.fish_dan_.data_energistics.menu.common;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds connection-scoped preference protocol state for one open pattern menu.
 *
 * <p>
 * The state is deliberately attached to the menu object instead of a player NBT tree. It therefore disappears with
 * the menu and cannot leak a sequence or an uncommitted snapshot into another server session.
 * </p>
 */
public final class PatternEncodingPreferenceSession {

    private static final Map<Object, PatternEncodingPreferenceSession> SESSIONS = new IdentityHashMap<>();

    private long nextOutgoingSequence;
    private long lastAcknowledgedSequence;
    private long lastAcceptedSequence = -1L;
    private long revision;
    @Nullable
    private PatternEncodingRankingContext rankingContext;
    private final Map<PatternEncodingRankingContext, Map<String, Long>> leafCountsByContext = new LinkedHashMap<>();

    private PatternEncodingPreferenceSession() {}

    /**
     * Returns or creates the session associated with one live menu instance.
     */
    public static PatternEncodingPreferenceSession forMenu(Object menu) {
        if (menu == null) {
            throw new IllegalArgumentException("Pattern preference session menu must not be null");
        }
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(menu, ignored -> new PatternEncodingPreferenceSession());
        }
    }

    /**
     * Drops all protocol state associated with a closed menu.
     */
    public static void clearForMenu(Object menu) {
        if (menu != null) {
            synchronized (SESSIONS) {
                SESSIONS.remove(menu);
            }
        }
    }

    /**
     * Returns the next strictly increasing client snapshot sequence.
     */
    public long nextOutgoingSequence() {
        if (this.nextOutgoingSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Pattern preference sequence exhausted");
        }
        return ++this.nextOutgoingSequence;
    }

    /**
     * Accepts an acknowledgement only for a snapshot that was sent and not acknowledged before.
     */
    public boolean acceptAcknowledgement(long sequence) {
        if (sequence < 0L || sequence > this.nextOutgoingSequence || sequence <= this.lastAcknowledgedSequence) {
            return false;
        }
        this.lastAcknowledgedSequence = sequence;
        return true;
    }

    /**
     * Accepts a server snapshot sequence exactly once and rejects reordered packets.
     */
    public boolean acceptIncomingSequence(long sequence) {
        if (sequence <= 0L || sequence <= this.lastAcceptedSequence) {
            return false;
        }
        this.lastAcceptedSequence = sequence;
        return true;
    }

    /**
     * Returns the latest accepted sequence for diagnostics.
     */
    public long lastAcceptedSequence() {
        return this.lastAcceptedSequence;
    }

    /**
     * Returns the server-validated category/workstation-set context, if one is known.
     */
    @Nullable
    public PatternEncodingRankingContext rankingContext() {
        return this.rankingContext;
    }

    /**
     * Sets the exact context used by subsequent provider-history snapshots.
     */
    public void setRankingContext(@Nullable PatternEncodingRankingContext context) {
        if (Objects.equals(this.rankingContext, context)) {
            return;
        }
        this.rankingContext = context;
        incrementRevision();
    }

    /**
     * Applies one validated absolute-count snapshot without lowering server-authoritative successes.
     */
    public void replaceLeafCounts(Map<String, Long> counts) {
        if (counts == null) {
            throw new IllegalArgumentException("Pattern preference leaf counts must not be null");
        }
        if (this.rankingContext == null) {
            if (!counts.isEmpty()) {
                throw new IllegalStateException("Pattern preference leaf counts require a ranking context");
            }
            return;
        }
        Map<String, Long> currentCounts = this.leafCountsByContext.getOrDefault(
                this.rankingContext, Map.of());
        Map<String, Long> merged = new LinkedHashMap<>(currentCounts);
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0L) {
                throw new IllegalArgumentException("Pattern preference leaf count is invalid");
            }
            merged.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        if (currentCounts.equals(merged)) {
            return;
        }
        this.leafCountsByContext.put(this.rankingContext, merged);
        incrementRevision();
    }

    /**
     * Returns an immutable copy of the currently accepted leaf counts.
     */
    public Map<String, Long> leafCounts() {
        if (this.rankingContext == null) {
            return Map.of();
        }
        return Map.copyOf(this.leafCountsByContext.getOrDefault(this.rankingContext, Map.of()));
    }

    /**
     * Returns one count or zero for a leaf not present in the current snapshot.
     */
    public long leafCount(String digest) {
        return leafCounts().getOrDefault(digest, 0L);
    }

    /**
     * Increments a server-authoritative leaf count with long saturation.
     */
    public long incrementLeafCount(PatternEncodingRankingContext context, String digest) {
        if (context == null) {
            throw new IllegalArgumentException("Pattern preference ranking context must not be null");
        }
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("Pattern preference leaf digest must not be blank");
        }
        Map<String, Long> contextCounts = this.leafCountsByContext.computeIfAbsent(
                context, ignored -> new LinkedHashMap<>());
        long current = contextCounts.getOrDefault(digest, 0L);
        long updated = current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
        contextCounts.put(digest, updated);
        incrementRevision();
        return updated;
    }

    /**
     * Returns a monotonic revision used to invalidate provider ordering.
     */
    public long revision() {
        return this.revision;
    }

    /**
     * Clears state while retaining no references to a previous menu context.
     */
    public void clear() {
        this.rankingContext = null;
        this.leafCountsByContext.clear();
        this.nextOutgoingSequence = 0L;
        this.lastAcknowledgedSequence = 0L;
        this.lastAcceptedSequence = -1L;
        this.revision = 0L;
    }

    private void incrementRevision() {
        if (this.revision != Long.MAX_VALUE) {
            this.revision++;
        }
    }
}
