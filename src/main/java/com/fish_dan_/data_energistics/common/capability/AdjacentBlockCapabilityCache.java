package com.fish_dan_.data_energistics.common.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Retains NeoForge capability caches for the six blocks adjacent to one fixed origin.
 *
 * @param <T> capability value type
 */
public final class AdjacentBlockCapabilityCache<T> {

    private final BlockCapability<T, Direction> capability;
    private final ServerLevel level;
    private final BlockPos origin;
    private final BooleanSupplier isValid;
    private final Map<Direction, BlockCapabilityCache<T, Direction>> caches = new EnumMap<>(Direction.class);

    /**
     * Creates a lazily populated cache set whose listeners live only while the owning machine is valid.
     *
     * @param capability capability queried on adjacent blocks
     * @param level      server level that owns the target blocks
     * @param origin     fixed position of the querying machine
     * @param isValid    listener lifetime supplied by the owning machine
     */
    public AdjacentBlockCapabilityCache(
                                        BlockCapability<T, Direction> capability,
                                        ServerLevel level,
                                        BlockPos origin,
                                        BooleanSupplier isValid) {
        this.capability = capability;
        this.level = level;
        this.origin = origin.immutable();
        this.isValid = isValid;
    }

    /**
     * Resolves the capability exposed toward the origin on one adjacent side.
     *
     * @param side direction from the origin to the target block
     * @return current capability, or {@code null} when the target does not expose it
     */
    @Nullable
    public T get(Direction side) {
        return this.caches.computeIfAbsent(side, this::createCache).getCapability();
    }

    /**
     * Resolves all capabilities on the requested sides while preserving side iteration order.
     *
     * @param sides enabled target sides
     * @return immutable list of currently exposed capabilities
     */
    public List<T> getAll(Iterable<Direction> sides) {
        List<T> resolved = new ArrayList<>();
        for (Direction side : sides) {
            T value = get(side);
            if (value != null) {
                resolved.add(value);
            }
        }
        return resolved.isEmpty() ? List.of() : List.copyOf(resolved);
    }

    /**
     * Resolves capabilities together with their stable direction keys.
     *
     * @param sides enabled target sides
     * @return immutable direction-to-capability mapping
     */
    public Map<Direction, T> getAllBySide(Iterable<Direction> sides) {
        Map<Direction, T> resolved = new EnumMap<>(Direction.class);
        for (Direction side : sides) {
            T value = get(side);
            if (value != null) {
                resolved.put(side, value);
            }
        }
        return resolved.isEmpty() ? Map.of() : Map.copyOf(resolved);
    }

    private BlockCapabilityCache<T, Direction> createCache(Direction side) {
        return BlockCapabilityCache.create(
                this.capability,
                this.level,
                this.origin.relative(side),
                side.getOpposite(),
                this.isValid,
                () -> {});
    }
}
