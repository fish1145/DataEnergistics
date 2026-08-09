package com.fish_dan_.data_energistics.common.trinity.host;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuListStatus;

import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;

/** Registers immutable Trinity host snapshots with LDLib2's synchronization type system. */
public final class TrinityDataCoreSyncAccessors {

    private static boolean initialized;

    private TrinityDataCoreSyncAccessors() {}

    /** Registers each accessor exactly once before any Trinity menu constructs a {@code SyncValue}. */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(TrinityDataCoreStorageStatus.class)
                .codec(TrinityDataCoreStorageStatus.CODEC)
                .streamCodec(TrinityDataCoreStorageStatus.STREAM_CODEC)
                .build(), 100);
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(TrinityCpuListStatus.class)
                .codec(TrinityCpuListStatus.CODEC)
                .streamCodec(TrinityCpuListStatus.STREAM_CODEC)
                .build(), 100);
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(TrinityDataCoreHostStatus.class)
                .codec(TrinityDataCoreHostStatus.CODEC)
                .streamCodec(TrinityDataCoreHostStatus.STREAM_CODEC)
                .build(), 100);
        initialized = true;
    }
}
