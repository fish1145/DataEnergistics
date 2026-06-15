package com.fish_dan_.data_energistics.common.multiblock.vertical;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalMultiBlockScannerTest {

    @Test
    void detectsValidHeightWithinBounds() {
        VerticalMultiBlockDefinition<String> definition = squareDefinition(3, 4);
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        place(world, definition, new VerticalMultiBlockPos(10, 64, 10), VerticalMultiBlockDirection.NORTH, 3);

        Optional<VerticalMultiBlockContext<String>> result = scan(world, definition, new VerticalMultiBlockPos(10, 64, 10));

        assertTrue(result.isPresent());
        assertEquals(3, result.orElseThrow().height());
        assertEquals(12, result.orElseThrow().matchedPositions().size());
    }

    @Test
    void rejectsStructuresBelowMinimumHeight() {
        VerticalMultiBlockDefinition<String> definition = squareDefinition(3, 4);
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        place(world, definition, new VerticalMultiBlockPos(0, 0, 0), VerticalMultiBlockDirection.NORTH, 2);

        assertFalse(scan(world, definition, new VerticalMultiBlockPos(0, 0, 0)).isPresent());
    }

    @Test
    void rejectsStructuresAboveMaximumHeight() {
        VerticalMultiBlockDefinition<String> definition = squareDefinition(3, 4);
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        place(world, definition, new VerticalMultiBlockPos(0, 0, 0), VerticalMultiBlockDirection.NORTH, 5);

        assertFalse(scan(world, definition, new VerticalMultiBlockPos(0, 0, 0)).isPresent());
    }

    @Test
    void prefersLongestValidMatchWhenTemplatesOverlap() {
        VerticalMultiBlockDefinition<String> definition = uniformDefinition(2, 4);
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        place(world, definition, new VerticalMultiBlockPos(0, 0, 0), VerticalMultiBlockDirection.NORTH, 4);

        Optional<VerticalMultiBlockContext<String>> result = scan(world, definition, new VerticalMultiBlockPos(0, 0, 0));

        assertTrue(result.isPresent());
        assertEquals(4, result.orElseThrow().height());
    }

    @Test
    void detectsAllHorizontalDirectionsWithRectangularLayers() {
        for (VerticalMultiBlockDirection direction : VerticalMultiBlockDirection.horizontal()) {
            VerticalMultiBlockDefinition<String> definition = rectangularDefinition(3, 4);
            Map<VerticalMultiBlockPos, String> world = new HashMap<>();
            VerticalMultiBlockPos origin = new VerticalMultiBlockPos(20, 30, 40);
            place(world, definition, origin, direction, 4);
            VerticalMultiBlockPos controller = origin.offset(direction.rotate(new VerticalMultiBlockPos(0, 0, 0), definition.width(), definition.depth()));

            Optional<VerticalMultiBlockContext<String>> result = scan(world, definition, controller);

            assertTrue(result.isPresent(), "Direction failed: " + direction);
            assertEquals(direction, result.orElseThrow().direction());
        }
    }

    @Test
    void invalidatesAndRecoversWhenMatchedBlockChanges() {
        VerticalMultiBlockDefinition<String> definition = squareDefinition(3, 4);
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        VerticalMultiBlockPos origin = new VerticalMultiBlockPos(0, 0, 0);
        place(world, definition, origin, VerticalMultiBlockDirection.NORTH, 3);
        VerticalMultiBlockPos changedBlock = new VerticalMultiBlockPos(1, 1, 1);
        String originalState = world.get(changedBlock);

        assertTrue(scan(world, definition, origin).isPresent());

        world.remove(changedBlock);
        assertFalse(scan(world, definition, origin).isPresent());

        world.put(changedBlock, originalState);
        assertTrue(scan(world, definition, origin).isPresent());
    }

    @Test
    void acceptsOnlyCandidateThatMatchesDefinition() {
        VerticalMultiBlockDefinition<String> definition = VerticalMultiBlockDefinition.<String>builder("test:candidates")
                .bottomLayer(squareLayer("C", "B1", "B2", "B3"))
                .middleLayer(squareLayer("M1", "M2", "M3", "M4"))
                .topLayer(squareLayer("T1", "T2", "T3", "T4"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0), new VerticalMultiBlockPos(1, 0, 1)))
                .heightRange(3, 3)
                .build();
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        VerticalMultiBlockPos origin = new VerticalMultiBlockPos(5, 5, 5);
        place(world, definition, origin, VerticalMultiBlockDirection.NORTH, 3);

        Optional<VerticalMultiBlockContext<String>> result = scan(world, definition, origin);

        assertTrue(result.isPresent());
        assertEquals(origin, result.orElseThrow().origin());
    }

    private static Optional<VerticalMultiBlockContext<String>> scan(Map<VerticalMultiBlockPos, String> world,
                                                                    VerticalMultiBlockDefinition<String> definition,
                                                                    VerticalMultiBlockPos controller) {
        return new VerticalMultiBlockScanner<>(pos -> world.getOrDefault(pos, "AIR")).scan(definition, controller);
    }

    private static void place(Map<VerticalMultiBlockPos, String> world,
                              VerticalMultiBlockDefinition<String> definition,
                              VerticalMultiBlockPos origin,
                              VerticalMultiBlockDirection direction,
                              int height) {
        placeLayer(world, definition.bottomLayer(), definition, origin, direction, 0);
        for (int y = 1; y < height - 1; y++) {
            placeLayer(world, definition.middleLayer(), definition, origin, direction, y);
        }
        placeLayer(world, definition.topLayer(), definition, origin, direction, height - 1);
    }

    private static void placeLayer(Map<VerticalMultiBlockPos, String> world,
                                   VerticalMultiBlockLayer<String> layer,
                                   VerticalMultiBlockDefinition<String> definition,
                                   VerticalMultiBlockPos origin,
                                   VerticalMultiBlockDirection direction,
                                   int y) {
        for (int z = 0; z < layer.depth(); z++) {
            for (int x = 0; x < layer.width(); x++) {
                VerticalMultiBlockPos local = new VerticalMultiBlockPos(x, y, z);
                VerticalMultiBlockPos worldPos = origin.offset(direction.rotate(local, definition.width(), definition.depth()));
                world.put(worldPos, expectedStateAt(layer, x, z));
            }
        }
    }

    private static String expectedStateAt(VerticalMultiBlockLayer<String> layer, int x, int z) {
        for (String state : List.of(
                "A",
                "C",
                "B",
                "B1",
                "B2",
                "B3",
                "B4",
                "B5",
                "M",
                "M1",
                "M2",
                "M3",
                "M4",
                "M5",
                "M6",
                "T",
                "T1",
                "T2",
                "T3",
                "T4",
                "T5",
                "T6")) {
            if (layer.predicateAt(x, z).matches(state, new VerticalMultiBlockPos(x, 0, z))) {
                return state;
            }
        }
        throw new IllegalStateException("Unsupported test predicate at " + x + "," + z);
    }

    private static VerticalMultiBlockDefinition<String> squareDefinition(int minHeight, int maxHeight) {
        return VerticalMultiBlockDefinition.<String>builder("test:vertical")
                .bottomLayer(squareLayer("C", "B1", "B2", "B3"))
                .middleLayer(squareLayer("M1", "M2", "M3", "M4"))
                .topLayer(squareLayer("T1", "T2", "T3", "T4"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(minHeight, maxHeight)
                .build();
    }

    private static VerticalMultiBlockDefinition<String> uniformDefinition(int minHeight, int maxHeight) {
        return VerticalMultiBlockDefinition.<String>builder("test:uniform")
                .bottomLayer(squareLayer("A", "A", "A", "A"))
                .middleLayer(squareLayer("A", "A", "A", "A"))
                .topLayer(squareLayer("A", "A", "A", "A"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(minHeight, maxHeight)
                .build();
    }

    private static VerticalMultiBlockDefinition<String> rectangularDefinition(int minHeight, int maxHeight) {
        return VerticalMultiBlockDefinition.<String>builder("test:rectangular")
                .bottomLayer(rectangularLayer("C", "B1", "B2", "B3", "B4", "B5"))
                .middleLayer(rectangularLayer("M1", "M2", "M3", "M4", "M5", "M6"))
                .topLayer(rectangularLayer("T1", "T2", "T3", "T4", "T5", "T6"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(minHeight, maxHeight)
                .build();
    }

    private static VerticalMultiBlockLayer<String> squareLayer(String northWest, String northEast, String southWest, String southEast) {
        return VerticalMultiBlockLayer.ofRows(
                List.of(VerticalMultiBlockPredicate.state(northWest), VerticalMultiBlockPredicate.state(northEast)),
                List.of(VerticalMultiBlockPredicate.state(southWest), VerticalMultiBlockPredicate.state(southEast)));
    }

    private static VerticalMultiBlockLayer<String> rectangularLayer(String firstRowLeft,
                                                                    String firstRowMiddle,
                                                                    String firstRowRight,
                                                                    String secondRowLeft,
                                                                    String secondRowMiddle,
                                                                    String secondRowRight) {
        return VerticalMultiBlockLayer.ofRows(
                List.of(
                        VerticalMultiBlockPredicate.state(firstRowLeft),
                        VerticalMultiBlockPredicate.state(firstRowMiddle),
                        VerticalMultiBlockPredicate.state(firstRowRight)),
                List.of(
                        VerticalMultiBlockPredicate.state(secondRowLeft),
                        VerticalMultiBlockPredicate.state(secondRowMiddle),
                        VerticalMultiBlockPredicate.state(secondRowRight)));
    }
}
