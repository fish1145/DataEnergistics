package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class JsonMultiBlockDefinitionLoaderTest {

    private JsonMultiBlockDefinitionLoaderTest() {}

    @TestHolder("json_multiblock_path_maps_top_level_file_to_main_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsTopLevelFileToMainStructure(GameTestHelper helper) {
        JsonMultiBlockStructureKey key = JsonMultiBlockResourceKeyResolver.resolve(resource("data_framework_column"));

        helper.assertValueEqual(
                key.machineId().toString(),
                "data_energistics:data_framework_column",
                "Top-level file should map to the machine id path");
        helper.assertValueEqual(
                key.structureName(),
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME,
                "Top-level file should map to the main structure");
        helper.succeed();
    }

    @TestHolder("json_multiblock_path_maps_nested_file_to_named_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsNestedFileToNamedStructure(GameTestHelper helper) {
        JsonMultiBlockStructureKey key = JsonMultiBlockResourceKeyResolver.resolve(resource("data_framework_column/side"));

        helper.assertValueEqual(
                key.machineId().toString(),
                "data_energistics:data_framework_column",
                "Nested file parent path should map to the machine id path");
        helper.assertValueEqual(key.structureName(), "side", "Nested file name should map to the structure name");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_rejects_duplicate_main_path_aliases")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsDuplicateMainPathAliases(GameTestHelper helper) {
        JsonMultiBlockDefinitionLoader loader = new JsonMultiBlockDefinitionLoaderImpl();
        Map<ResourceLocation, String> resources = new LinkedHashMap<>();
        resources.put(resource("data_framework_column"), minimalJson());
        resources.put(resource("data_framework_column/main"), minimalJson());

        assertThrows(
                helper,
                IllegalStateException.class,
                () -> loader.load(resources),
                "Loader should reject two resources that resolve to the same structure key");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_parses_valid_mdlib_json")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesValidMdlibJson(GameTestHelper helper) {
        JsonMultiBlockDefinitionLoader loader = new JsonMultiBlockDefinitionLoaderImpl();
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = loader.load(Map.of(
                resource("data_framework_column"),
                minimalJson()));

        JsonMultiBlockStructureKey key = JsonMultiBlockStructureKey.main(resource("data_framework_column"));
        helper.assertValueEqual(definitions.size(), 1, "Loader should return the parsed definition");
        helper.assertTrue(definitions.containsKey(key), "Loader should key the parsed definition by resource path");
        helper.assertTrue(definitions.get(key).pattern() != null, "Parsed definition should contain an MDLib pattern");
        helper.succeed();
    }

    @TestHolder("json_multiblock_structure_name_rejects_slashes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void structureNameRejectsSlashes(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalArgumentException.class,
                () -> new JsonMultiBlockStructureKey(resource("data_framework_column"), "side/main"),
                "Structure name should fail fast when it contains a slash");
        helper.succeed();
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, path);
    }

    private static String minimalJson() {
        return """
                {
                "aisles": [
                	{
                	"slices": [
                		[
                		"~"
                		]
                	]
                	}
                ]
                }
                """;
    }

    private static <T extends Throwable> void assertThrows(GameTestHelper helper,
                                                           Class<T> expectedType,
                                                           Runnable action,
                                                           String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            helper.fail(message + ": expected " + expectedType.getSimpleName() + " but caught " +
                    thrown.getClass().getSimpleName() + " (" + thrown.getMessage() + ")");
        }
        helper.fail(message + ": expected " + expectedType.getSimpleName() + " but no exception was thrown");
    }
}
