package com.fish_dan_.data_energistics.common.multiblock.vertical;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class VerticalMultiBlockRegistryTest {

    private VerticalMultiBlockRegistryTest() {}

    @TestHolder("vertical_multiblock_registry_registers_definitions_by_id")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void registersDefinitionsById(GameTestHelper helper) {
        VerticalMultiBlockRegistry<String> registry = new VerticalMultiBlockRegistry<>();
        VerticalMultiBlockDefinition<String> definition = definition("test:vertical");

        registry.register(definition);

        helper.assertValueEqual(registry.size(), 1, "Registry should contain the registered definition");
        helper.assertTrue(registry.get("test:vertical").isPresent(), "Registry should return the definition by id");
        helper.succeed();
    }

    @TestHolder("vertical_multiblock_registry_duplicate_definition_id_fails_fast")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void duplicateDefinitionIdFailsFast(GameTestHelper helper) {
        VerticalMultiBlockRegistry<String> registry = new VerticalMultiBlockRegistry<>();
        registry.register(definition("test:vertical"));

        assertThrows(
                helper,
                IllegalStateException.class,
                () -> registry.register(definition("test:vertical")),
                "Registering duplicate definition id should fail fast");
        helper.succeed();
    }

    @TestHolder("vertical_multiblock_registry_missing_templates_fail_fast")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void missingTemplatesFailFast(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalStateException.class,
                () -> VerticalMultiBlockDefinition.<String>builder("test:missing")
                        .bottomLayer(layer("A"))
                        .topLayer(layer("A"))
                        .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                        .build(),
                "Building a definition without all required layers should fail fast");
        helper.succeed();
    }

    private static VerticalMultiBlockDefinition<String> definition(String id) {
        return VerticalMultiBlockDefinition.<String>builder(id)
                .bottomLayer(layer("B"))
                .middleLayer(layer("M"))
                .topLayer(layer("T"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(2, 4)
                .build();
    }

    private static VerticalMultiBlockLayer<String> layer(String state) {
        return VerticalMultiBlockLayer.ofRows(List.of(VerticalMultiBlockPredicate.state(state)));
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
}
