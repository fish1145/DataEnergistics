package com.fish_dan_.data_energistics.menu.common;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;
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
    @Nullable
    private ResourceLocation confirmedWorkstation;
    private final Map<PatternEncodingRankingContext, Map<String, Long>> leafCountsByContext = new LinkedHashMap<>();

    private PatternEncodingPreferenceSession() {}

    /**
     * Returns or creates the session associated with one live menu instance.
     */
    public static PatternEncodingPreferenceSession forMenu(@NotNull Object menu) {
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(menu, ignored -> new PatternEncodingPreferenceSession());
        }
    }

    /**
     * Drops all protocol state associated with a closed menu.
     */
    public static void clearForMenu(@Nullable Object menu) {
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
        if (!canAcceptIncomingSequence(sequence)) {
            return false;
        }
        this.lastAcceptedSequence = sequence;
        return true;
    }

    /**
     * Checks a server snapshot sequence without consuming it while the snapshot contents are validated.
     */
    public boolean canAcceptIncomingSequence(long sequence) {
        return sequence > 0L && sequence > this.lastAcceptedSequence;
    }

    /**
     * Returns the server-validated recipe-type context, if one is known.
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
     * Seeds the last workstation held by server-owned menu state.
     *
     * <p>
     * Client preference snapshots must never call this method. The value is only a prior confirmation hint and is
     * accepted later only when it is still present in the exact current candidate set.
     * </p>
     */
    public void initializeConfirmedWorkstation(@Nullable ResourceLocation workstationId) {
        this.confirmedWorkstation = workstationId;
    }

    /**
     * Confirms the workstation selected by a server-side upload preflight after the inventory commit succeeds.
     */
    public void confirmWorkstation(@NotNull ResourceLocation workstationId) {
        this.confirmedWorkstation = workstationId;
    }

    /**
     * Returns the last server-confirmed workstation, if one has been established.
     */
    @Nullable
    public ResourceLocation confirmedWorkstation() {
        return this.confirmedWorkstation;
    }

    /**
     * Applies one validated absolute-count snapshot without lowering server-authoritative successes.
     */
    public void replaceLeafCounts(@NotNull Map<String, Long> counts) {
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
     * Increments a server-authoritative leaf count with long saturation.
     */
    public long incrementLeafCount(@NotNull PatternEncodingRankingContext context,
                                   @NotNull String digest) {
        if (digest.isBlank()) {
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
        this.confirmedWorkstation = null;
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
