package com.fish_dan_.data_energistics.menu.patternencoding;

import net.minecraft.resources.ResourceLocation;

import appeng.parts.encoding.EncodingMode;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;
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

    private static final Reference2ObjectMap<Object, PatternEncodingPreferenceSession> SESSIONS = new Reference2ObjectOpenHashMap<>();

    private long nextOutgoingSequence;
    private long lastAcknowledgedSequence;
    private long lastAcceptedSequence = -1L;
    private long revision;
    @Nullable
    private PatternEncodingRankingContext rankingContext;
    private List<ResourceLocation> viewerWorkstationIds = List.of();
    @Nullable
    private ResourceLocation confirmedWorkstation;
    @Nullable
    private EncodingMode deferredSnapshotMode;
    private boolean deferredSnapshotWaitsForTick;
    private final Object2ObjectMap<PatternEncodingRankingContext, Object2LongMap<String>> leafCountsByContext = new Object2ObjectLinkedOpenHashMap<>();

    private PatternEncodingPreferenceSession() {}

    /**
     * Returns or creates the session associated with one live menu instance.
     */
    public static PatternEncodingPreferenceSession forMenu(Object menu) {
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
                if (SESSIONS.containsKey(menu)) {
                    SESSIONS.remove(menu).clear();
                }
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
        setViewerRecipeScope(context, List.of());
    }

    /**
     * Returns the ephemeral workstation item IDs advertised for the current viewer transfer.
     */
    public List<ResourceLocation> viewerWorkstationIds() {
        return this.viewerWorkstationIds;
    }

    /**
     * Atomically updates the recipe-type learning key and its non-persistent viewer workstation condition.
     */
    public void setViewerRecipeScope(@Nullable PatternEncodingRankingContext context,
                                     List<ResourceLocation> workstationIds) {
        if (context == null && !workstationIds.isEmpty()) {
            throw new IllegalArgumentException("Viewer workstations require a pattern ranking context");
        }
        List<ResourceLocation> canonicalWorkstations = context == null ? List.of() :
                new PatternEncodingViewerRecipeScope(context, workstationIds).workstationIds();
        if (Objects.equals(this.rankingContext, context) &&
                this.viewerWorkstationIds.equals(canonicalWorkstations)) {
            return;
        }
        this.rankingContext = context;
        this.viewerWorkstationIds = canonicalWorkstations;
        incrementRevision();
    }

    /**
     * Defers a transfer-generated client snapshot until the menu has synchronized to the transfer's target mode.
     */
    public void deferSnapshotUntil(EncodingMode expectedMode) {
        this.deferredSnapshotMode = Objects.requireNonNull(expectedMode, "Expected menu mode cannot be null");
        this.deferredSnapshotWaitsForTick = true;
    }

    /**
     * Consumes one deferred snapshot after at least one client tick and only when the menu reached its expected mode.
     */
    public boolean consumeDeferredSnapshotIfReady(EncodingMode currentMode) {
        Objects.requireNonNull(currentMode, "Current menu mode cannot be null");
        if (this.deferredSnapshotMode == null) {
            return false;
        }
        if (this.deferredSnapshotWaitsForTick) {
            this.deferredSnapshotWaitsForTick = false;
            return false;
        }
        if (this.deferredSnapshotMode != currentMode) {
            return false;
        }
        this.deferredSnapshotMode = null;
        return true;
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
    public void confirmWorkstation(ResourceLocation workstationId) {
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
    public void replaceLeafCounts(Object2LongMap<String> counts) {
        if (this.rankingContext == null) {
            if (!counts.isEmpty()) {
                throw new IllegalStateException("Pattern preference leaf counts require a ranking context");
            }
            return;
        }
        Object2LongMap<String> currentCounts = this.leafCountsByContext.computeIfAbsent(
                this.rankingContext, ignored -> newLeafCounts());
        boolean changed = false;
        for (Object2LongMap.Entry<String> entry : counts.object2LongEntrySet()) {
            long incoming = entry.getLongValue();
            if (incoming > currentCounts.getLong(entry.getKey())) {
                currentCounts.put(entry.getKey(), incoming);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        incrementRevision();
    }

    /**
     * Returns an immutable copy of the currently accepted leaf counts.
     */
    public Object2LongMap<String> leafCounts() {
        if (this.rankingContext == null) {
            return Object2LongMaps.emptyMap();
        }
        var counts = findLeafCounts(this.rankingContext);
        return counts == null ? Object2LongMaps.emptyMap() :
                Object2LongMaps.unmodifiable(new Object2LongLinkedOpenHashMap<>(counts));
    }

    /**
     * Increments a server-authoritative leaf count with long saturation.
     */
    public long incrementLeafCount(PatternEncodingRankingContext context,
                                   String digest) {
        if (digest.isBlank()) {
            throw new IllegalArgumentException("Pattern preference leaf digest must not be blank");
        }
        Object2LongMap<String> contextCounts = this.leafCountsByContext.computeIfAbsent(
                context, ignored -> newLeafCounts());
        long current = contextCounts.getLong(digest);
        long updated = current < 0L ? 1L : current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
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
        this.viewerWorkstationIds = List.of();
        this.confirmedWorkstation = null;
        this.deferredSnapshotMode = null;
        this.deferredSnapshotWaitsForTick = false;
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

    private static Object2LongMap<String> newLeafCounts() {
        Object2LongMap<String> counts = new Object2LongLinkedOpenHashMap<>();
        counts.defaultReturnValue(-1L);
        return counts;
    }

    @SuppressWarnings("ConstantConditions") // fastutil returns its null default value for an absent context.
    private @Nullable Object2LongMap<String> findLeafCounts(PatternEncodingRankingContext context) {
        return this.leafCountsByContext.get(context);
    }
}
