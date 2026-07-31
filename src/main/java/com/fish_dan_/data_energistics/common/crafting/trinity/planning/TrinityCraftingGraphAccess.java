package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;

import java.util.Optional;

/**
 * Read-only grid boundary through which planning requests obtain the last complete immutable graph.
 */
public interface TrinityCraftingGraphAccess {

    /**
     * @return last revision-consistent graph, or empty while the first time-sliced capture is incomplete
     */
    Optional<TrinityCraftingGraphSnapshot> trinityCraftingGraphSnapshot();
}
