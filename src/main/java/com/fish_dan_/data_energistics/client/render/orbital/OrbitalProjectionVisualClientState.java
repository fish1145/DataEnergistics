package com.fish_dan_.data_energistics.client.render.orbital;

import com.fish_dan_.data_energistics.network.orbital.projection.OrbitalProjectionVisualsPayload;
import com.fish_dan_.data_energistics.orbital.projection.OrbitalProjectionVisualSnapshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/** Client cache for complete, dimension-scoped primary projection baselines. */
public final class OrbitalProjectionVisualClientState {

    private static long revision = -1L;
    private static ResourceLocation dimensionId = Level.OVERWORLD.location();
    private static List<OrbitalProjectionVisualSnapshot> projections = List.of();
    private static long pendingRevision = -1L;
    private static ResourceLocation pendingDimension = Level.OVERWORLD.location();
    private static int pendingBatchCount;
    private static int pendingTotalCount;
    private static final Int2ObjectOpenHashMap<List<OrbitalProjectionVisualSnapshot>> pendingBatches = new Int2ObjectOpenHashMap<>();

    private OrbitalProjectionVisualClientState() {}

    /** Publishes a complete baseline only after every batch for a newer revision has arrived. */
    public static void receive(OrbitalProjectionVisualsPayload payload) {
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
        if (payload.revision() != pendingRevision || !payload.dimensionId().equals(pendingDimension) || payload.batchCount() != pendingBatchCount || payload.totalCount() != pendingTotalCount) {
            return;
        }
        pendingBatches.putIfAbsent(payload.batchIndex(), payload.projections());
        if (pendingBatches.size() != pendingBatchCount) {
            return;
        }

        ArrayList<OrbitalProjectionVisualSnapshot> complete = new ArrayList<>(pendingTotalCount);
        for (int index = 0; index < pendingBatchCount; index++) {
            if (!pendingBatches.containsKey(index)) {
                return;
            }
            complete.addAll(pendingBatches.get(index));
        }
        if (complete.size() != pendingTotalCount) {
            return;
        }
        revision = pendingRevision;
        dimensionId = pendingDimension;
        projections = List.copyOf(complete);
        pendingBatches.clear();
    }

    /** Clears projections when the client leaves the server. */
    public static void clear() {
        revision = -1L;
        dimensionId = Level.OVERWORLD.location();
        projections = List.of();
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

    public static List<OrbitalProjectionVisualSnapshot> projections() {
        return projections;
    }
}
