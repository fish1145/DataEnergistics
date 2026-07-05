package com.fish_dan_.data_energistics.common.multiblock.vertical;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class VerticalMultiBlockRuntimeBindingTest {

    private VerticalMultiBlockRuntimeBindingTest() {}

    @TestHolder("vertical_multiblock_runtime_binding_binds_invalidates_and_rebinds_controller_and_parts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void bindsInvalidatesAndRebindsControllerAndParts(GameTestHelper helper) {
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

        helper.assertTrue(
                binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "Initial requestRecheck should form the runtime structure");
        helper.assertTrue(controller.verticalMultiBlock$isFormed(), "Controller should be formed after the initial bind");
        helper.assertValueEqual(controller.verticalMultiBlock$getCurrentHeight(), 3, "Controller should expose the formed height");
        helper.assertValueEqual(
                controller.events,
                List.of("formed:test:runtime:main:3"),
                "Controller should receive one formed event after initial bind");
        helper.assertValueEqual(
                controller.verticalMultiBlock$getRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME).structureName(),
                VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME,
                "Controller state should record the default structure name");
        helper.assertValueEqual(
                part.events,
                List.of("added:test:runtime:main"),
                "Part should be added to the controller after initial bind");

        world.remove(new VerticalMultiBlockPos(1, 1, 0));
        helper.assertFalse(
                binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "requestRecheck should reject the structure after a matched part is removed");
        helper.assertFalse(controller.verticalMultiBlock$isFormed(), "Controller should be unformed after invalidation");
        helper.assertValueEqual(
                controller.events,
                List.of("formed:test:runtime:main:3", "invalid:main:No valid vertical multiblock match"),
                "Controller should receive an invalidation event when the match breaks");
        helper.assertValueEqual(
                part.events,
                List.of("added:test:runtime:main", "removed:main"),
                "Part should be removed from the controller when the match breaks");

        world.put(new VerticalMultiBlockPos(1, 1, 0), "M2");
        helper.assertTrue(
                binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "requestRecheck should reform the structure after the matched part is restored");
        helper.assertTrue(controller.verticalMultiBlock$isFormed(), "Controller should be formed after rebinding");
        helper.assertValueEqual(
                controller.events,
                List.of(
                        "formed:test:runtime:main:3",
                        "invalid:main:No valid vertical multiblock match",
                        "formed:test:runtime:main:3"),
                "Controller should record formed, invalid, and formed events in order");
        helper.assertValueEqual(
                part.events,
                List.of("added:test:runtime:main", "removed:main", "added:test:runtime:main"),
                "Part should record add, remove, and add events in order");
        helper.succeed();
    }

    @TestHolder("vertical_multiblock_runtime_binding_distinguishes_structure_names_in_events")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void distinguishesStructureNamesInEvents(GameTestHelper helper) {
        VerticalMultiBlockDefinition<String> mainDefinition = definition();
        VerticalMultiBlockDefinition<String> auxDefinition = definition("aux");
        Map<VerticalMultiBlockPos, String> world = new HashMap<>();
        place(world, mainDefinition, new VerticalMultiBlockPos(0, 0, 0), 3);
        TestController controller = new TestController();
        TestPart part = new TestPart();
        VerticalMultiBlockRuntimeBinding<String> binding = new VerticalMultiBlockRuntimeBinding<>(
                new VerticalMultiBlockScanner<>(pos -> world.getOrDefault(pos, "AIR")));
        VerticalMultiBlockRuntimeBinding.PartLookup partLookup = VerticalMultiBlockRuntimeBinding.fromSinglePart(
                new VerticalMultiBlockPos(1, 1, 0),
                part);

        helper.assertTrue(
                binding.requestRecheck(controller, mainDefinition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "Main definition should form");
        helper.assertValueEqual(
                controller.verticalMultiBlock$getRuntimeState("main").structureName(),
                "main",
                "Runtime state should record the main structure name");
        helper.assertTrue(controller.verticalMultiBlock$isFormed("main"), "Main structure should be formed");

        helper.assertTrue(
                binding.requestRecheck(controller, auxDefinition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "Aux definition with the same id should form");
        helper.assertValueEqual(
                controller.verticalMultiBlock$getRuntimeState("aux").structureName(),
                "aux",
                "Runtime state should record the aux structure name after rebinding");
        helper.assertTrue(controller.verticalMultiBlock$isFormed("main"), "Main structure should remain formed after aux bind");
        helper.assertTrue(controller.verticalMultiBlock$isFormed("aux"), "Aux structure should be formed after aux bind");
        helper.assertValueEqual(
                controller.verticalMultiBlock$getFormedStructureNames(),
                Set.of("main", "aux"),
                "Controller should track both formed structures");

        world.remove(new VerticalMultiBlockPos(1, 1, 0));
        helper.assertFalse(
                binding.requestRecheck(controller, auxDefinition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "Aux definition should invalidate when a matched part is removed");
        helper.assertTrue(controller.verticalMultiBlock$isFormed("main"), "Main structure should remain formed after aux invalidation");
        helper.assertFalse(controller.verticalMultiBlock$isFormed("aux"), "Aux structure should be unformed after aux invalidation");

        helper.assertValueEqual(
                controller.events,
                List.of(
                        "formed:test:runtime:main:3",
                        "formed:test:runtime:aux:3",
                        "invalid:aux:No valid vertical multiblock match"),
                "Controller events should carry the matched structure name");
        helper.assertValueEqual(
                part.events,
                List.of(
                        "added:test:runtime:main",
                        "added:test:runtime:aux",
                        "removed:aux"),
                "Part events should only remove the invalidated structure name");
        helper.succeed();
    }

    @TestHolder("vertical_multiblock_runtime_binding_reports_failed_scan_definition_structure_name")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reportsFailedScanDefinitionStructureName(GameTestHelper helper) {
        VerticalMultiBlockDefinition<String> auxDefinition = definition("aux");
        VerticalMultiBlockRuntimeBinding<String> binding = new VerticalMultiBlockRuntimeBinding<>(
                new VerticalMultiBlockScanner<>(pos -> "AIR"));
        TestController controller = new TestController();
        TestPart part = new TestPart();

        helper.assertFalse(
                binding.requestRecheck(
                        controller,
                        auxDefinition,
                        new VerticalMultiBlockPos(0, 0, 0),
                        VerticalMultiBlockRuntimeBinding.fromSinglePart(new VerticalMultiBlockPos(1, 1, 0), part)),
                "Aux definition should fail to form against an empty world");
        helper.assertFalse(controller.verticalMultiBlock$isFormed(), "Controller should remain unformed after failed scan");
        helper.assertValueEqual(
                controller.events,
                List.of("invalid:aux:No valid vertical multiblock match"),
                "Invalid event should carry the failed scan definition structure name");
        helper.assertValueEqual(
                part.events,
                List.of(),
                "No previous formed parts should be removed from an unformed controller");
        helper.succeed();
    }

    @TestHolder("vertical_multiblock_runtime_binding_rejects_mismatched_controller_definition_id")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsMismatchedControllerDefinitionId(GameTestHelper helper) {
        VerticalMultiBlockRuntimeBinding<String> binding = new VerticalMultiBlockRuntimeBinding<>(
                new VerticalMultiBlockScanner<>(pos -> "AIR"));
        TestController controller = new TestController("test:other");

        assertThrows(
                helper,
                IllegalStateException.class,
                () -> binding.requestRecheck(
                        controller,
                        definition(),
                        new VerticalMultiBlockPos(0, 0, 0),
                        VerticalMultiBlockRuntimeBinding.emptyPartLookup()),
                "Mismatched controller definition id should fail fast");
        helper.succeed();
    }

    private static VerticalMultiBlockDefinition<String> definition() {
        return definition(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME);
    }

    private static VerticalMultiBlockDefinition<String> definition(String structureName) {
        return VerticalMultiBlockDefinition.<String>builder("test:runtime")
                .structureName(structureName)
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

    private static <T extends Throwable> void assertThrows(
                                                           GameTestHelper helper,
                                                           Class<T> expectedType,
                                                           Runnable action,
                                                           String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            helper.fail(message + ": expected " + expectedType.getSimpleName() + " but caught " + thrown.getClass().getSimpleName() + " (" + thrown.getMessage() + ")");
        }
        helper.fail(message + ": expected " + expectedType.getSimpleName() + " but no exception was thrown");
    }

    private static final class TestController implements VerticalMultiBlockController {

        private final List<String> events = new ArrayList<>();
        private final String definitionId;
        private final Map<String, VerticalMultiBlockRuntimeState> states = new HashMap<>();

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
            this.events.add("legacy-formed:" + context.definition().id());
        }

        @Override
        public void verticalMultiBlock$onStructureFormed(String structureName, VerticalMultiBlockContext<?> context) {
            this.events.add("formed:" + context.definition().id() + ":" + structureName + ":" + context.height());
        }

        @Override
        public void verticalMultiBlock$onStructureInvalid(String reason) {
            this.events.add("legacy-invalid:" + reason);
        }

        @Override
        public void verticalMultiBlock$onStructureInvalid(String structureName, String reason) {
            this.events.add("invalid:" + structureName + ":" + reason);
        }

        @Override
        public void verticalMultiBlock$requestRecheck() {
            this.events.add("request");
        }

        @Override
        public VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState() {
            return verticalMultiBlock$getRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME);
        }

        @Override
        public void verticalMultiBlock$setRuntimeState(VerticalMultiBlockRuntimeState state) {
            verticalMultiBlock$setRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME, state);
        }

        @Override
        public VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState(String structureName) {
            return this.states.getOrDefault(structureName, VerticalMultiBlockRuntimeState.unformed());
        }

        @Override
        public void verticalMultiBlock$setRuntimeState(String structureName, VerticalMultiBlockRuntimeState state) {
            if (state.formed()) {
                this.states.put(structureName, state);
            } else {
                this.states.remove(structureName);
            }
        }

        @Override
        public Set<String> verticalMultiBlock$getFormedStructureNames() {
            return Set.copyOf(this.states.keySet());
        }

        @Override
        public Map<String, VerticalMultiBlockRuntimeState> verticalMultiBlock$getRuntimeStates() {
            return Map.copyOf(this.states);
        }
    }

    private static final class TestPart implements VerticalMultiBlockPart {

        private final List<String> events = new ArrayList<>();

        @Override
        public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller, VerticalMultiBlockContext<?> context) {
            this.events.add("legacy-added:" + context.definition().id());
        }

        @Override
        public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                         String structureName,
                                                         VerticalMultiBlockContext<?> context) {
            this.events.add("added:" + context.definition().id() + ":" + structureName);
        }

        @Override
        public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller) {
            this.events.add("legacy-removed");
        }

        @Override
        public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller, String structureName) {
            this.events.add("removed:" + structureName);
        }
    }
}
