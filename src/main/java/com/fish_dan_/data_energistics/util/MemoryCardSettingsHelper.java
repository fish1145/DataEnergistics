package com.fish_dan_.data_energistics.util;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;
import java.util.Set;

public final class MemoryCardSettingsHelper {

    public static final int ALL_DIRECTIONS_MASK = 63;

    private MemoryCardSettingsHelper() {}

    public static int encodeSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            if (side != null) {
                mask |= 1 << side.ordinal();
            }
        }
        return mask;
    }

    public static Set<Direction> decodeSides(int mask) {
        EnumSet<Direction> sides = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.values()) {
            if ((mask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    public static boolean replaceSides(Set<Direction> target, int mask) {
        Set<Direction> updatedSides = decodeSides(mask);
        if (target.equals(updatedSides)) {
            return false;
        }

        target.clear();
        target.addAll(updatedSides);
        return true;
    }

    public static boolean readBoolean(CompoundTag data, String key, boolean fallback) {
        return data.contains(key) ? data.getBoolean(key) : fallback;
    }

    public static int readInt(CompoundTag data, String key, int fallback) {
        return data.contains(key) ? data.getInt(key) : fallback;
    }

    public static String readString(CompoundTag data, String key, String fallback) {
        return data.contains(key) ? data.getString(key) : fallback;
    }

    public static <E extends Enum<E>> E readEnum(CompoundTag data, String key, E fallback, Class<E> enumClass) {
        if (!data.contains(key)) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumClass, data.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
