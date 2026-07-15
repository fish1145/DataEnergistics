package com.fish_dan_.data_energistics.common.trinity;

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
        initialized = true;
    }
}
