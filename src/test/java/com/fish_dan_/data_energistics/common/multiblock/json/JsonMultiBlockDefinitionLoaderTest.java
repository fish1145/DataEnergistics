package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.modularmc.mdl.api.multiblock.BlockPattern;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class JsonMultiBlockDefinitionLoaderTest {

    private static final String MINIMAL_JSON_WITH_METADATA = "{\"metadata\":{\"display_name\":\"multiblock.data_energistics.digital_construct_flower\"},\"aisles\":[{\"slices\":[[\"~\"]]}]}";

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

    @TestHolder("json_multiblock_loader_parses_bundled_digital_construct_flower_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesBundledDigitalConstructFlowerStructure(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new JsonMultiBlockDefinitionLoaderImpl().parse(
                resource("digital_construct_flower"),
                bundledJsonReader("/data/data_energistics/multiblock/digital_construct_flower.json"));
        BlockPattern pattern = definition.pattern();

        helper.assertValueEqual(
                definition.key(),
                JsonMultiBlockStructureKey.main(resource("digital_construct_flower")),
                "Bundled Digital Construct Flower JSON should resolve to the main digital_construct_flower structure key");
        helper.assertTrue(
                definition.displayNameTranslationKey().isPresent(),
                "Bundled Digital Construct Flower JSON should expose structure display metadata");
        helper.assertValueEqual(
                definition.displayNameTranslationKey().orElseThrow(),
                "multiblock.data_energistics.digital_construct_flower",
                "Bundled Digital Construct Flower display metadata should resolve to the structure lang key");
        assertIntArrayEqual(helper, pattern.getDimensions(), new int[] { 19, 21, 19 },
                "Bundled Digital Construct Flower dimensions should match the WorldEdit schematic");
        helper.assertValueEqual(pattern.getCenterOffset().x(), 9, "Controller X offset should match the placeholder");
        helper.assertValueEqual(pattern.getCenterOffset().y(), 9, "Controller Y offset should match the flipped JSON row");
        helper.assertValueEqual(pattern.getCenterOffset().minZ(), 17, "Controller Z offset should match the placeholder aisle");
        helper.assertValueEqual(pattern.getCenterOffset().maxZ(), 17, "Controller Z max offset should match the placeholder aisle");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_strips_display_metadata_before_mdlib_parse")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void stripsDisplayMetadataBeforeMdlibParse(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new JsonMultiBlockDefinitionLoaderImpl().parse(
                resource("digital_construct_flower"),
                new StringReader(MINIMAL_JSON_WITH_METADATA));

        helper.assertValueEqual(
                definition.displayNameTranslationKey().orElseThrow(),
                "multiblock.data_energistics.digital_construct_flower",
                "Loader should expose display metadata without passing it into MDLib pattern parsing");
        helper.assertTrue(definition.pattern() != null, "Loader should still parse the MDLib pattern");
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

    private static InputStreamReader bundledJsonReader(String path) {
        InputStream stream = JsonMultiBlockDefinitionLoaderTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled test resource: " + path);
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static void assertIntArrayEqual(GameTestHelper helper, int[] actual, int[] expected, String message) {
        helper.assertValueEqual(actual.length, expected.length, message + " length");
        for (int i = 0; i < expected.length; i++) {
            helper.assertValueEqual(actual[i], expected[i], message + " index " + i);
        }
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
