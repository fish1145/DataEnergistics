package com.fish_dan_.data_energistics.common.trinity.pattern;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/**
 * Identifies one pattern slot independently of the pattern core's current position in a formed structure.
 *
 * @param hostId stable identifier of the owning Trinity host
 * @param coreId stable identifier carried by the pattern core
 * @param slot   zero-based slot in that core
 */
public record PatternRoute(UUID hostId, UUID coreId, int slot) {

    private static final String HOST_ID_TAG = "host_id";
    private static final String CORE_ID_TAG = "core_id";
    private static final String SLOT_TAG = "slot";

    public PatternRoute {
        if (slot < 0) {
            throw new IllegalArgumentException("Pattern route slot must not be negative");
        }
    }

    /**
     * Serializes this route without coupling it to a pattern definition.
     */
    public CompoundTag writeToTag() {
        CompoundTag data = new CompoundTag();
        data.putUUID(HOST_ID_TAG, this.hostId);
        data.putUUID(CORE_ID_TAG, this.coreId);
        data.putInt(SLOT_TAG, this.slot);
        return data;
    }

    /**
     * Restores a route and rejects incomplete or malformed persisted state.
     */
    public static PatternRoute readFromTag(CompoundTag data) {
        if (!data.hasUUID(HOST_ID_TAG) || !data.hasUUID(CORE_ID_TAG) || !data.contains(SLOT_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Pattern route NBT is incomplete");
        }
        try {
            return new PatternRoute(data.getUUID(HOST_ID_TAG), data.getUUID(CORE_ID_TAG), data.getInt(SLOT_TAG));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Pattern route NBT is invalid", exception);
        }
    }
}
