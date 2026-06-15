package com.fish_dan_.data_energistics.common.multiblock.vertical;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalMultiBlockRuntimeBindingTest {

    @Test
    void bindsInvalidatesAndRebindsControllerAndParts() {
        VerticalMultiBlockDefinition<String> definition = definition();
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        place(world, definition, new VerticalMultiBlockPos(0, 0, 0), 3);
        TestController controller = new TestController();
        TestPart part = new TestPart();
        VerticalMultiBlockRuntimeBinding<String> binding = new VerticalMultiBlockRuntimeBinding<>(
                new VerticalMultiBlockScanner<>(pos -> world.getOrDefault(pos, "AIR")));
        VerticalMultiBlockRuntimeBinding.PartLookup partLookup = VerticalMultiBlockRuntimeBinding.fromSinglePart(
                new VerticalMultiBlockPos(1, 1, 0),
                part);

        assertTrue(binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup));
        assertTrue(controller.verticalMultiBlock$isFormed());
        assertEquals(3, controller.verticalMultiBlock$getCurrentHeight());
        assertEquals(List.of("formed:test:runtime:3"), controller.events);
        assertEquals(List.of("added:test:runtime"), part.events);

        world.remove(new VerticalMultiBlockPos(1, 1, 0));
        assertFalse(binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup));
        assertFalse(controller.verticalMultiBlock$isFormed());
        assertEquals(List.of("formed:test:runtime:3", "invalid:No valid vertical multiblock match"), controller.events);
        assertEquals(List.of("added:test:runtime", "removed"), part.events);

        world.put(new VerticalMultiBlockPos(1, 1, 0), "M2");
        assertTrue(binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup));
        assertTrue(controller.verticalMultiBlock$isFormed());
        assertEquals(List.of(
                "formed:test:runtime:3",
                "invalid:No valid vertical multiblock match",
                "formed:test:runtime:3"), controller.events);
        assertEquals(List.of("added:test:runtime", "removed", "added:test:runtime"), part.events);
    }

    @Test
    void rejectsMismatchedControllerDefinitionId() {
        VerticalMultiBlockRuntimeBinding<String> binding = new VerticalMultiBlockRuntimeBinding<>(
                new VerticalMultiBlockScanner<>(pos -> "AIR"));
        TestController controller = new TestController("test:other");

        assertThrows(IllegalStateException.class, () -> binding.requestRecheck(
                controller,
                definition(),
                new VerticalMultiBlockPos(0, 0, 0),
                VerticalMultiBlockRuntimeBinding.emptyPartLookup()));
    }

    private static VerticalMultiBlockDefinition<String> definition() {
        return VerticalMultiBlockDefinition.<String>builder("test:runtime")
                .bottomLayer(layer("C", "B1"))
                .middleLayer(layer("M1", "M2"))
                .topLayer(layer("T1", "T2"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(3, 3)
                .build();
    }

    private static VerticalMultiBlockLayer<String> layer(String left, String right) {
        return VerticalMultiBlockLayer.ofRows(
                List.of(VerticalMultiBlockPredicate.state(left), VerticalMultiBlockPredicate.state(right)));
    }

    private static void place(Map<VerticalMultiBlockPos, String> world,
                              VerticalMultiBlockDefinition<String> definition,
                              VerticalMultiBlockPos origin,
                              int height) {
        placeLayer(world, definition.bottomLayer(), origin, 0, "C", "B1");
        for (int y = 1; y < height - 1; y++) {
            placeLayer(world, definition.middleLayer(), origin, y, "M1", "M2");
        }
        placeLayer(world, definition.topLayer(), origin, height - 1, "T1", "T2");
    }

    private static void placeLayer(Map<VerticalMultiBlockPos, String> world,
                                   VerticalMultiBlockLayer<String> layer,
                                   VerticalMultiBlockPos origin,
                                   int y,
                                   String left,
                                   String right) {
        for (int z = 0; z < layer.depth(); z++) {
            for (int x = 0; x < layer.width(); x++) {
                VerticalMultiBlockPos pos = origin.offset(new VerticalMultiBlockPos(x, y, z));
                world.put(pos, x == 0 ? left : right);
            }
        }
    }

    private static final class TestController implements VerticalMultiBlockController {

        private final List<String> events = new ArrayList<>();
        private final String definitionId;
        private VerticalMultiBlockRuntimeState state = VerticalMultiBlockRuntimeState.unformed();

        private TestController() {
            this("test:runtime");
        }

        private TestController(String definitionId) {
            this.definitionId = definitionId;
        }

        @Override
        public String verticalMultiBlock$getDefinitionId() {
            return this.definitionId;
        }

        @Override
        public void verticalMultiBlock$onStructureFormed(VerticalMultiBlockContext<?> context) {
            this.events.add("formed:" + context.definition().id() + ":" + context.height());
        }

        @Override
        public void verticalMultiBlock$onStructureInvalid(String reason) {
            this.events.add("invalid:" + reason);
        }

        @Override
        public void verticalMultiBlock$requestRecheck() {
            this.events.add("request");
        }

        @Override
        public VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState() {
            return this.state;
        }

        @Override
        public void verticalMultiBlock$setRuntimeState(VerticalMultiBlockRuntimeState state) {
            this.state = state;
        }
    }

    private static final class TestPart implements VerticalMultiBlockPart {

        private final List<String> events = new ArrayList<>();

        @Override
        public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller, VerticalMultiBlockContext<?> context) {
            this.events.add("added:" + context.definition().id());
        }

        @Override
        public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller) {
            this.events.add("removed");
        }
    }
}
