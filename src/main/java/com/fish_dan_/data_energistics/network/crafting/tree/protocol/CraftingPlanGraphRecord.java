package com.fish_dan_.data_energistics.network.crafting.tree.protocol;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Cycle;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Header;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Node;

import java.util.Objects;

/**
 * Closed immutable network record family. Typed records preserve model validation across bounded batches;
 * they may cross the decode/game-thread boundary but have no independent execution semantics.
 */
public sealed interface CraftingPlanGraphRecord {

    record GraphHeader(Header header, int rootId) implements CraftingPlanGraphRecord {

        public GraphHeader {
            Objects.requireNonNull(header);
            if (rootId < 0) throw new IllegalArgumentException("Negative root id");
        }
    }

    record GraphNode(Node node) implements CraftingPlanGraphRecord {

        public GraphNode {
            Objects.requireNonNull(node);
        }
    }

    record GraphEdge(Edge edge) implements CraftingPlanGraphRecord {

        public GraphEdge {
            Objects.requireNonNull(edge);
        }
    }

    record GraphCycle(Cycle cycle) implements CraftingPlanGraphRecord {

        public GraphCycle {
            Objects.requireNonNull(cycle);
        }
    }
}
