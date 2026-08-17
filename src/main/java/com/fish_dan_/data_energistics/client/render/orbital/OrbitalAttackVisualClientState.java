package com.fish_dan_.data_energistics.client.render.orbital;

import com.fish_dan_.data_energistics.network.orbital.visual.OrbitalAttackVisualsPayload;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackVisualSnapshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Client cache for the latest public orbital visual baseline; renderer code can consume it without server access. */
public final class OrbitalAttackVisualClientState {

    private static long revision = -1L;
    private static ResourceLocation dimensionId = Level.OVERWORLD.location();
    private static List<OrbitalAttackVisualSnapshot> attacks = List.of();
    private static long pendingRevision = -1L;
    private static ResourceLocation pendingDimension = Level.OVERWORLD.location();
    private static int pendingBatchCount;
    private static int pendingTotalCount;
    private static final Map<Integer, List<OrbitalAttackVisualSnapshot>> pendingBatches = new HashMap<>();

    private OrbitalAttackVisualClientState() {}

    /** Publishes only newer baselines, making delayed packets harmless. */
    public static void receive(OrbitalAttackVisualsPayload payload) {
        if (payload.revision() <= revision) {
            return;
        }
        if (payload.revision() > pendingRevision) {
            pendingRevision = payload.revision();
            pendingDimension = payload.dimensionId();
            pendingBatchCount = payload.batchCount();
            pendingTotalCount = payload.totalCount();
            pendingBatches.clear();
        }
        if (payload.revision() != pendingRevision
                || !payload.dimensionId().equals(pendingDimension)
                || payload.batchCount() != pendingBatchCount
                || payload.totalCount() != pendingTotalCount) {
            return;
        }
        pendingBatches.putIfAbsent(payload.batchIndex(), payload.attacks());
        if (pendingBatches.size() != pendingBatchCount) {
            return;
        }
        ArrayList<OrbitalAttackVisualSnapshot> complete = new ArrayList<>(pendingTotalCount);
        for (int index = 0; index < pendingBatchCount; index++) {
            List<OrbitalAttackVisualSnapshot> batch = pendingBatches.get(index);
            if (batch == null) {
                return;
            }
            complete.addAll(batch);
        }
        if (complete.size() != pendingTotalCount) {
            return;
        }
        revision = pendingRevision;
        dimensionId = pendingDimension;
        attacks = List.copyOf(complete);
        pendingBatches.clear();
    }

    /** Clears visuals when the client disconnects or changes dimension. */
    public static void clear() {
        revision = -1L;
        dimensionId = Level.OVERWORLD.location();
        attacks = List.of();
        pendingRevision = -1L;
        pendingDimension = Level.OVERWORLD.location();
        pendingBatchCount = 0;
        pendingTotalCount = 0;
        pendingBatches.clear();
    }

    public static long revision() {
        return revision;
    }

    public static ResourceLocation dimensionId() {
        return dimensionId;
    }

    public static List<OrbitalAttackVisualSnapshot> attacks() {
        return attacks;
    }
}
