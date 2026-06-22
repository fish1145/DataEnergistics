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
                List.of("formed:test:runtime:3"),
                "Controller should receive one formed event after initial bind");
        helper.assertValueEqual(
                part.events,
                List.of("added:test:runtime"),
                "Part should be added to the controller after initial bind");

        world.remove(new VerticalMultiBlockPos(1, 1, 0));
        helper.assertFalse(
                binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "requestRecheck should reject the structure after a matched part is removed");
        helper.assertFalse(controller.verticalMultiBlock$isFormed(), "Controller should be unformed after invalidation");
        helper.assertValueEqual(
                controller.events,
                List.of("formed:test:runtime:3", "invalid:No valid vertical multiblock match"),
                "Controller should receive an invalidation event when the match breaks");
        helper.assertValueEqual(
                part.events,
                List.of("added:test:runtime", "removed"),
                "Part should be removed from the controller when the match breaks");

        world.put(new VerticalMultiBlockPos(1, 1, 0), "M2");
        helper.assertTrue(
                binding.requestRecheck(controller, definition, new VerticalMultiBlockPos(0, 0, 0), partLookup),
                "requestRecheck should reform the structure after the matched part is restored");
        helper.assertTrue(controller.verticalMultiBlock$isFormed(), "Controller should be formed after rebinding");
        helper.assertValueEqual(
                controller.events,
                List.of(
                        "formed:test:runtime:3",
                        "invalid:No valid vertical multiblock match",
                        "formed:test:runtime:3"),
                "Controller should record formed, invalid, and formed events in order");
        helper.assertValueEqual(
                part.events,
                List.of("added:test:runtime", "removed", "added:test:runtime"),
                "Part should record add, remove, and add events in order");
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
