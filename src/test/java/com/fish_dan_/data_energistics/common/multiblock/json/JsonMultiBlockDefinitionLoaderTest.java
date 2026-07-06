package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MeStorageAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.PatternMatchContext;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import com.modularmc.mdl.api.multiblock.util.RelativeDirection;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class JsonMultiBlockDefinitionLoaderTest {

    private static final BlockPos CONTROLLER = new BlockPos(0, 0, 0);
    private static final String MISSING_BLOCK_ID = "data_energistics:missing_block_for_json_multiblock_test";
    private static final String MISSING_BLOCKS_ID = "data_energistics:missing_blocks_for_json_multiblock_test";
    private static final String MINIMAL_JSON_WITH_METADATA = "{\"metadata\":{\"display_name\":\"multiblock.data_energistics.trinity_digital_core\"},\"aisles\":[{\"slices\":[[\"~\"]]}]}";
    private static final String MINIMAL_JSON_WITH_COMPARTMENTS = "{\"metadata\":{\"compartments\":{\"I\":\"input\",\"O\":\"output\",\"M\":\"me_input\",\"N\":\"me_output\",\"P\":\"pattern_buffer\"}},\"aisles\":[{\"slices\":[[\"~IOMNP\"]]}]}";

    private JsonMultiBlockDefinitionLoaderTest() {}

    @TestHolder("json_multiblock_path_maps_top_level_file_to_main_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsTopLevelFileToMainStructure(GameTestHelper helper) {
        JsonMultiBlockStructureKey key = JsonMultiBlockResourceKeyResolver.resolve(resource("sample_multiblock"));

        helper.assertValueEqual(
                key.machineId().toString(),
                "data_energistics:sample_multiblock",
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
        JsonMultiBlockStructureKey key = JsonMultiBlockResourceKeyResolver.resolve(resource("sample_multiblock/side"));

        helper.assertValueEqual(
                key.machineId().toString(),
                "data_energistics:sample_multiblock",
                "Nested file parent path should map to the machine id path");
        helper.assertValueEqual(key.structureName(), "side", "Nested file name should map to the structure name");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_rejects_duplicate_main_path_aliases")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsDuplicateMainPathAliases(GameTestHelper helper) {
        JsonMultiBlockDefinitionLoader loader = new MdlibJsonMultiBlockDefinitionLoader();
        Map<ResourceLocation, String> resources = new LinkedHashMap<>();
        resources.put(resource("sample_multiblock"), minimalJson());
        resources.put(resource("sample_multiblock/main"), minimalJson());

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
        JsonMultiBlockDefinitionLoader loader = new MdlibJsonMultiBlockDefinitionLoader();
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = loader.load(Map.of(
                resource("sample_multiblock"),
                minimalJson()));

        JsonMultiBlockStructureKey key = JsonMultiBlockStructureKey.main(resource("sample_multiblock"));
        helper.assertValueEqual(definitions.size(), 1, "Loader should return the parsed definition");
        helper.assertTrue(definitions.containsKey(key), "Loader should key the parsed definition by resource path");
        helper.assertTrue(definitions.get(key).pattern() != null, "Parsed definition should contain an MDLib pattern");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_parses_bundled_trinity_digital_core_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesBundledTrinityDigitalCoreStructure(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_digital_core/main"),
                bundledJsonReader("/data/data_energistics/multiblock/trinity_digital_core/main.json"));
        BlockPattern pattern = definition.pattern();
        JsonObject root = JsonParser.parseReader(bundledJsonReader("/data/data_energistics/multiblock/trinity_digital_core/main.json"))
                .getAsJsonObject();

        helper.assertValueEqual(
                definition.key(),
                JsonMultiBlockStructureKey.main(resource("trinity_digital_core")),
                "Bundled Trinity Digital Core JSON should resolve to the main trinity_digital_core structure key");
        helper.assertTrue(
                definition.displayNameTranslationKey().isPresent(),
                "Bundled Trinity Digital Core JSON should expose structure display metadata");
        helper.assertValueEqual(
                definition.displayNameTranslationKey().orElseThrow(),
                "multiblock.data_energistics.trinity_digital_core",
                "Bundled Trinity Digital Core display metadata should resolve to the structure lang key");
        helper.assertValueEqual(
                definition.compartmentTypes().size(),
                0,
                "Bundled Trinity Digital Core should not declare compartment roles yet");
        helper.assertValueEqual(
                definition.replaceableCompartmentTypes().size(),
                1,
                "Bundled Trinity Digital Core should declare replaceable quartz glass symbols");
        helper.assertTrue(
                definition.replaceableCompartmentTypes().getOrDefault("H", Set.of()).contains(CompartmentType.ME_STORAGE_ACCESS),
                "Bundled Trinity Digital Core H symbol should allow the ME storage access hatch");
        helper.assertTrue(
                !definition.replaceableCompartmentTypes().containsKey("Y"),
                "Bundled Trinity Digital Core should not allow compartments to replace plain glass");
        assertIntArrayEqual(helper, pattern.getDimensions(), new int[] { 27, 28, 32 },
                "Bundled Trinity Digital Core dimensions should match the WorldEdit schematic");
        helper.assertValueEqual(pattern.structureSlices.length, 27, "Bundled Trinity Digital Core should use one GregTech aisle per schematic depth layer");
        helper.assertValueEqual(pattern.aisleRepetitions.length, 27, "Each Trinity Digital Core aisle should be a fixed non-repeatable unit");
        assertAllIntValuesEqual(helper, pattern.unitDepths, 1, "Each Trinity Digital Core aisle unit should contain exactly one slice");
        assertAllIntPairValuesEqual(helper, pattern.aisleRepetitions, 1, "Each Trinity Digital Core aisle unit should repeat exactly once");
        helper.assertValueEqual(
                pattern.structureSlices[13][2],
                "       ~CCG   L       L         ",
                "Bundled Trinity Digital Core should map the exported controller to the pattern center row");
        helper.assertValueEqual(
                pattern.structureSlices[13][3],
                "       HCCCG  LLLLLLLLL         ",
                "Bundled Trinity Digital Core should preserve the new exported body around the controller");
        helper.assertValueEqual(
                pattern.structureSlices[0][0],
                "                                ",
                "Bundled Trinity Digital Core should retain empty exported boundary rows");
        helper.assertValueEqual(pattern.getCenterOffset().x(), 7, "Controller X offset should match the mapped JSON placeholder column");
        helper.assertValueEqual(pattern.getCenterOffset().y(), 2, "Controller Y offset should match the GregTech bottom-to-top JSON row");
        helper.assertValueEqual(pattern.getCenterOffset().z(), 13, "Controller Z offset should match the mapped JSON placeholder aisle");
        helper.assertValueEqual(pattern.getCenterOffset().minZ(), 13, "Controller Z min offset should match the placeholder aisle");
        helper.assertValueEqual(pattern.getCenterOffset().maxZ(), 13, "Controller Z max offset should match the placeholder aisle");
        helper.assertValueEqual(countSymbol(pattern, 'Z'), 1176, "Main Trinity Digital Core should expose all storage core slots through Z");
        helper.assertValueEqual(countSymbol(pattern, 'd'), 0, "Main Trinity Digital Core should not keep the duplicate storage symbol d");
        helper.assertTrue(
                !root.getAsJsonObject("predicates").has("d"),
                "Main Trinity Digital Core predicates should not keep the duplicate storage symbol d");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_parses_bundled_trinity_digital_core_cpu_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesBundledTrinityDigitalCoreCpuStructure(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_digital_core/cpu"),
                bundledJsonReader("/data/data_energistics/multiblock/trinity_digital_core/cpu.json"));
        BlockPattern pattern = definition.pattern();

        helper.assertValueEqual(
                definition.key(),
                new JsonMultiBlockStructureKey(resource("trinity_digital_core"), "cpu"),
                "Bundled Trinity Digital Core CPU JSON should resolve to the cpu child structure key");
        assertIntArrayEqual(helper, pattern.getDimensions(), new int[] { 9, 19, 32 },
                "Bundled Trinity Digital Core CPU dimensions should include the host anchor and child body");
        helper.assertValueEqual(pattern.structureSlices.length, 9, "CPU child structure should use one aisle per local Z layer");
        helper.assertValueEqual(pattern.aisleRepetitions.length, 9, "Each CPU child aisle should be fixed");
        assertAllIntValuesEqual(helper, pattern.unitDepths, 1, "Each CPU child aisle unit should contain one slice");
        assertAllIntPairValuesEqual(helper, pattern.aisleRepetitions, 1, "Each CPU child aisle unit should repeat once");
        helper.assertValueEqual(pattern.getCenterOffset().x(), 0, "CPU child controller X offset should match the host anchor");
        helper.assertValueEqual(pattern.getCenterOffset().y(), 1, "CPU child controller Y offset should match the host anchor");
        helper.assertValueEqual(pattern.getCenterOffset().z(), 8, "CPU child controller Z offset should match the host anchor");
        helper.assertValueEqual(pattern.getCenterOffset().minZ(), 8, "CPU child controller min Z offset should match the host anchor");
        helper.assertValueEqual(pattern.getCenterOffset().maxZ(), 8, "CPU child controller max Z offset should match the host anchor");
        helper.assertValueEqual(countSymbol(pattern, 'C'), 272, "CPU child should expose 272 merged storage core positions");
        BlockPos firstCpuBlock = firstNonAnchorSymbol(pattern);
        helper.assertValueEqual(firstCpuBlock, new BlockPos(26, 0, 0), "CPU child first entity should be at the exported left-back-bottom corner");
        helper.assertValueEqual(
                mapPatternPosition(pattern, firstCpuBlock, new BlockPos(-81, -44, 8), Direction.EAST, Direction.NORTH),
                new BlockPos(-89, -45, -18),
                "CPU child first entity should map to the WorldEdit-selected left-back-bottom corner");

        JsonObject root = JsonParser.parseReader(bundledJsonReader("/data/data_energistics/multiblock/trinity_digital_core/cpu.json"))
                .getAsJsonObject();
        JsonObject cpuMetadata = root.getAsJsonObject("metadata").getAsJsonObject("cpu_core");
        helper.assertValueEqual(cpuMetadata.get("repeat_start_y").getAsInt(), 5, "CPU child repeat start should match the repeated section");
        helper.assertValueEqual(cpuMetadata.get("repeat_end_y").getAsInt(), 17, "CPU child repeat end should match the repeated section");
        helper.assertValueEqual(cpuMetadata.get("max_repeat_count").getAsInt(), 13, "CPU child repeat count should map the 13 repeated layers");
        helper.assertValueEqual(cpuMetadata.get("max_threads").getAsInt(), 256, "CPU child max threads should be the mapped thread cap");
        JsonObject cpuCorePredicate = root.getAsJsonObject("predicates").getAsJsonObject("C");
        helper.assertTrue(
                hasJsonStringValue(cpuCorePredicate.get("blocks"),
                        "data_energistics:me_digital_merged_storage_core_256m"),
                "CPU child core predicate should allow the 256M merged storage core");
        helper.assertTrue(
                hasJsonStringValue(cpuCorePredicate.get("blocks"),
                        "minecraft:air"),
                "CPU child core predicate should allow empty core slots without forming a full CPU");
        helper.assertFalse(
                hasJsonStringValue(root, "ae2:256k_crafting_storage"),
                "CPU child JSON should not keep AE2 crafting storage as a predicate value");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_downgrades_missing_block_predicate_to_air")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void downgradesMissingBlockPredicateToAir(GameTestHelper helper) {
        JsonMultiBlockDefinitionLoader loader = new MdlibJsonMultiBlockDefinitionLoader();
        Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = loader.load(Map.of(
                resource("missing_block_predicate"),
                jsonWithMissingBlockPredicate()));

        JsonMultiBlockStructureKey key = JsonMultiBlockStructureKey.main(resource("missing_block_predicate"));
        helper.assertValueEqual(
                definitions.size(),
                1,
                "Loader should keep JSON multiblocks whose missing block predicates were downgraded");
        helper.assertTrue(definitions.containsKey(key), "Loader should return the downgraded definition");

        StructureMatchResult nonAirResult = matchController(definitions.get(key).pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertFalse(nonAirResult.matched(), "Downgraded missing block predicate should reject non-air blocks at A");
        StructureMatchResult airResult = matchController(definitions.get(key).pattern(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(airResult.matched(), "Downgraded missing block predicate should match air at A");
        helper.succeed();
    }

    @TestHolder("json_multiblock_parse_downgrades_missing_blocks_predicate_to_air")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parseDowngradesMissingBlocksPredicateToAir(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("missing_blocks_predicate"),
                new StringReader(jsonWithMissingBlocksPredicate()));

        StructureMatchResult nonAirResult = matchController(definition.pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertFalse(nonAirResult.matched(), "Downgraded missing block in blocks array should reject non-air blocks at A");
        StructureMatchResult airResult = matchController(definition.pattern(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(airResult.matched(), "Downgraded missing block in blocks array should match air at A");
        helper.succeed();
    }

    @TestHolder("json_multiblock_parse_downgrades_missing_block_state_predicate_to_air")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parseDowngradesMissingBlockStatePredicateToAir(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("missing_block_state_predicate"),
                new StringReader(jsonWithMissingBlockStatePredicate()));

        StructureMatchResult nonAirResult = matchController(definition.pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertFalse(nonAirResult.matched(), "Downgraded missing block state predicate should reject non-air blocks at A");
        StructureMatchResult airResult = matchController(definition.pattern(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(airResult.matched(), "Downgraded missing block state predicate should match air at A");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_treats_air_block_predicate_as_existing")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void treatsAirBlockPredicateAsExisting(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("air_block_predicate"),
                new StringReader(jsonWithAirBlocksPredicate()));

        StructureMatchResult result = matchController(definition.pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertFalse(result.matched(), "minecraft:air should remain a blocks predicate and reject non-air blocks");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_strips_display_metadata_before_mdlib_parse")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void stripsDisplayMetadataBeforeMdlibParse(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_digital_core"),
                new StringReader(MINIMAL_JSON_WITH_METADATA));

        helper.assertValueEqual(
                definition.displayNameTranslationKey().orElseThrow(),
                "multiblock.data_energistics.trinity_digital_core",
                "Loader should expose display metadata without passing it into MDLib pattern parsing");
        helper.assertTrue(definition.pattern() != null, "Loader should still parse the MDLib pattern");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_parses_compartment_metadata")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesCompartmentMetadata(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("compartment_metadata"),
                new StringReader(MINIMAL_JSON_WITH_COMPARTMENTS));

        helper.assertValueEqual(definition.compartmentTypes().size(), 5, "Loader should expose all compartment metadata entries");
        helper.assertValueEqual(definition.compartmentTypes().get("I"), CompartmentType.INPUT, "I should map to input");
        helper.assertValueEqual(definition.compartmentTypes().get("O"), CompartmentType.OUTPUT, "O should map to output");
        helper.assertValueEqual(definition.compartmentTypes().get("M"), CompartmentType.ME_INPUT, "M should map to ME input");
        helper.assertValueEqual(definition.compartmentTypes().get("N"), CompartmentType.ME_OUTPUT, "N should map to ME output");
        helper.assertValueEqual(definition.compartmentTypes().get("P"), CompartmentType.PATTERN_BUFFER, "P should map to pattern buffer");
        helper.assertTrue(definition.pattern() != null, "Compartment metadata should be stripped before MDLib parsing");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_rejects_unknown_compartment_type")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsUnknownCompartmentType(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalArgumentException.class,
                () -> new MdlibJsonMultiBlockDefinitionLoader().parse(
                        resource("bad_compartment_type"),
                        new StringReader("{\"metadata\":{\"compartments\":{\"I\":\"unknown\"}},\"aisles\":[{\"slices\":[[\"~\"]]}]}")),
                "Loader should reject unknown compartment types");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_rejects_invalid_compartment_symbol")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsInvalidCompartmentSymbol(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalArgumentException.class,
                () -> new MdlibJsonMultiBlockDefinitionLoader().parse(
                        resource("bad_compartment_symbol"),
                        new StringReader("{\"metadata\":{\"compartments\":{\"II\":\"input\"}},\"aisles\":[{\"slices\":[[\"~\"]]}]}")),
                "Loader should reject compartment symbols that are not one character");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_rejects_empty_compartment_symbol")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsEmptyCompartmentSymbol(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalArgumentException.class,
                () -> new MdlibJsonMultiBlockDefinitionLoader().parse(
                        resource("empty_compartment_symbol"),
                        new StringReader("{\"metadata\":{\"compartments\":{\"\":\"input\"}},\"aisles\":[{\"slices\":[[\"~\"]]}]}")),
                "Loader should reject empty compartment symbols");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_rejects_duplicate_compartment_symbol")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsDuplicateCompartmentSymbol(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalArgumentException.class,
                () -> new MdlibJsonMultiBlockDefinitionLoader().parse(
                        resource("duplicate_compartment_symbol"),
                        new StringReader("{\"metadata\":{\"compartments\":{\"I\":\"input\",\"I\":\"output\"}},\"aisles\":[{\"slices\":[[\"~I\"]]}]}")),
                "Loader should reject duplicate compartment symbols before tree parsing overwrites them");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_validator_accepts_matching_type")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentValidatorAcceptsMatchingType(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("compartment_validator_match"),
                new StringReader("{\"metadata\":{\"compartments\":{\"I\":\"input\"}},\"aisles\":[{\"slices\":[[\"~I\"]]}]}"));

        helper.assertTrue(
                JsonMultiBlockCompartmentValidator.matchesDeclaredType(
                        definition,
                        "I",
                        ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState()),
                "Input compartment symbol should accept input compartment block");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_validator_rejects_mismatched_type")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentValidatorRejectsMismatchedType(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("compartment_validator_mismatch"),
                new StringReader("{\"metadata\":{\"compartments\":{\"I\":\"input\"}},\"aisles\":[{\"slices\":[[\"~I\"]]}]}"));

        helper.assertFalse(
                JsonMultiBlockCompartmentValidator.matchesDeclaredType(
                        definition,
                        "I",
                        ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState()),
                "Input compartment symbol should reject output compartment block");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_validator_ignores_undeclared_symbol")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentValidatorIgnoresUndeclaredSymbol(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("compartment_validator_undeclared"),
                new StringReader("{\"metadata\":{\"compartments\":{\"I\":\"input\"}},\"aisles\":[{\"slices\":[[\"~I\"]]}]}"));

        helper.assertTrue(
                JsonMultiBlockCompartmentValidator.matchesDeclaredType(
                        definition,
                        "A",
                        Blocks.STONE.defaultBlockState()),
                "Undeclared symbols should not be compartment-restricted");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_metadata_restricts_mdlib_match_to_declared_type")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentMetadataRestrictsMdlibMatchToDeclaredType(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("compartment_predicate_match"),
                new StringReader("{\"metadata\":{\"compartments\":{\"I\":\"input\"}},\"aisles\":[{\"slices\":[[\"~I\"]]}]}"));

        BlockPos compartmentPos = new BlockPos(-1, 0, 0);
        StructureMatchResult matched = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world(Map.of(
                        CONTROLLER, Blocks.STONE.defaultBlockState(),
                        compartmentPos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertTrue(matched.matched(), "Declared input compartment should match the input compartment block");

        StructureMatchResult mismatched = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world(Map.of(
                        CONTROLLER, Blocks.STONE.defaultBlockState(),
                        compartmentPos, ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertFalse(mismatched.matched(), "Declared input compartment should reject the output compartment block");
        helper.succeed();
    }

    @TestHolder("json_multiblock_replaceable_compartment_accepts_original_block_and_allowed_roles")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void replaceableCompartmentAcceptsOriginalBlockAndAllowedRoles(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("replaceable_compartment"),
                new StringReader(jsonWithReplaceableCompartment("A", "input", "me_storage_access")));

        BlockPos replaceablePos = new BlockPos(-1, 0, 0);
        StructureMatchResult originalBlock = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world(Map.of(
                        CONTROLLER, Blocks.STONE.defaultBlockState(),
                        replaceablePos, Blocks.GLASS.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertTrue(originalBlock.matched(), "Original glass predicate should still match");

        StructureMatchResult inputCompartment = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world(Map.of(
                        CONTROLLER, Blocks.STONE.defaultBlockState(),
                        replaceablePos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertTrue(inputCompartment.matched(), "Allowed input compartment should replace glass");
        helper.assertValueEqual(
                JsonMultiBlockCompartmentPredicate.declaredCompartments(inputCompartment.context()).get(replaceablePos),
                CompartmentType.INPUT,
                "Allowed input replacement should be recorded for binder validation");

        StructureMatchResult accessHatch = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world(Map.of(
                        CONTROLLER, Blocks.STONE.defaultBlockState(),
                        replaceablePos, ModBlocks.ME_STORAGE_ACCESS_HATCH.get().defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertTrue(accessHatch.matched(), "Allowed ME storage access hatch should replace glass");
        helper.assertValueEqual(
                JsonMultiBlockCompartmentPredicate.declaredCompartments(accessHatch.context()).get(replaceablePos),
                CompartmentType.ME_STORAGE_ACCESS,
                "Allowed access hatch replacement should be recorded for binder validation");
        helper.succeed();
    }

    @TestHolder("json_multiblock_replaceable_compartment_rejects_disallowed_roles_and_non_matching_blocks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void replaceableCompartmentRejectsDisallowedRolesAndNonMatchingBlocks(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("replaceable_compartment_reject"),
                new StringReader(jsonWithReplaceableCompartment("A", "input")));

        BlockPos replaceablePos = new BlockPos(-1, 0, 0);
        StructureMatchResult outputCompartment = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world(Map.of(
                        CONTROLLER, Blocks.STONE.defaultBlockState(),
                        replaceablePos, ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertFalse(outputCompartment.matched(), "Disallowed output compartment should not replace glass");

        StructureMatchResult nonGlass = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world(Map.of(
                        CONTROLLER, Blocks.STONE.defaultBlockState(),
                        replaceablePos, Blocks.GOLD_BLOCK.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertFalse(nonGlass.matched(), "Non-matching normal block should still fail the original predicate");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_binder_reports_invalid_runtime_parts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentBinderReportsInvalidRuntimeParts(GameTestHelper helper) {
        JsonDeclaredCompartmentBinder binder = new JsonDeclaredCompartmentBinder();
        BlockPos compartmentPos = new BlockPos(1, 0, 0);
        StructureMatchResult result = StructureMatchResult.success(
                false,
                Direction.NORTH,
                List.of(compartmentPos),
                new PatternMatchContext());

        PatternDiagnostic missing = binder.validate(
                world(Map.of(compartmentPos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState())),
                result,
                Map.of(compartmentPos, CompartmentType.INPUT));
        assertDiagnosticCode(helper, missing, "compartment_part_missing", "Missing compartment block entity should fail");

        CompartmentBlockEntity outputPart = new CompositeWarehouseBlockEntity(
                compartmentPos,
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        PatternDiagnostic mismatched = binder.validate(
                world(
                        Map.of(compartmentPos, ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState()),
                        Map.of(compartmentPos, outputPart)),
                result,
                Map.of(compartmentPos, CompartmentType.INPUT));
        assertDiagnosticCode(
                helper,
                mismatched,
                "compartment_part_mismatch",
                "Runtime compartment type mismatch should fail");

        CompartmentBlockEntity inputPart = new CompositeWarehouseBlockEntity(
                compartmentPos,
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        PatternDiagnostic undeclared = binder.validate(
                world(
                        Map.of(compartmentPos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState()),
                        Map.of(compartmentPos, inputPart)),
                result,
                Map.of());
        assertDiagnosticCode(
                helper,
                undeclared,
                "compartment_part_undeclared",
                "Matched but undeclared compartment should fail");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_binder_binds_ensures_and_unbinds_declared_parts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentBinderBindsEnsuresAndUnbindsDeclaredParts(GameTestHelper helper) {
        JsonDeclaredCompartmentBinder binder = new JsonDeclaredCompartmentBinder();
        TestCompartmentHost host = new TestCompartmentHost();
        BlockPos compartmentPos = new BlockPos(1, 0, 0);
        CompartmentBlockEntity inputPart = new CompositeWarehouseBlockEntity(
                compartmentPos,
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        StructureWorldView world = world(
                Map.of(compartmentPos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState()),
                Map.of(compartmentPos, inputPart));
        Map<BlockPos, CompartmentType> declaredCompartments = Map.of(compartmentPos, CompartmentType.INPUT);

        binder.bind(world, "main", host, declaredCompartments);
        helper.assertValueEqual(
                host.compartmentHost$getCompartments("main"),
                List.of(inputPart),
                "Binder should register declared runtime compartment parts with the host");
        helper.assertValueEqual(inputPart.compartmentHost(), host, "Bound compartment part should remember its host");
        helper.assertValueEqual(
                inputPart.compartmentStructureName(),
                "main",
                "Bound compartment part should remember the structure name");

        binder.ensureBound(world, "main", host, declaredCompartments);
        helper.assertValueEqual(
                host.compartmentHost$getCompartments("main"),
                List.of(inputPart),
                "ensureBound should keep an already registered compartment without duplicates");

        binder.unbind("main", host);
        helper.assertTrue(
                host.compartmentHost$getCompartments("main").isEmpty(),
                "Binder unbind should remove declared compartments from the host");
        helper.assertTrue(inputPart.compartmentHost() == null, "Unbound compartment part should clear its host");
        helper.assertTrue(
                inputPart.compartmentStructureName() == null,
                "Unbound compartment part should clear its structure name");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_binder_binds_me_storage_access_hatch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentBinderBindsMeStorageAccessHatch(GameTestHelper helper) {
        JsonDeclaredCompartmentBinder binder = new JsonDeclaredCompartmentBinder();
        TestCompartmentHost host = new TestCompartmentHost();
        BlockPos hatchPos = new BlockPos(1, 0, 0);
        MeStorageAccessHatchBlockEntity hatch = new MeStorageAccessHatchBlockEntity(
                hatchPos,
                ModBlocks.ME_STORAGE_ACCESS_HATCH.get().defaultBlockState());
        StructureMatchResult result = StructureMatchResult.success(
                false,
                Direction.NORTH,
                List.of(hatchPos),
                new PatternMatchContext());
        StructureWorldView world = world(
                Map.of(hatchPos, ModBlocks.ME_STORAGE_ACCESS_HATCH.get().defaultBlockState()),
                Map.of(hatchPos, hatch));
        Map<BlockPos, CompartmentType> declaredCompartments = Map.of(hatchPos, CompartmentType.ME_STORAGE_ACCESS);

        PatternDiagnostic diagnostic = binder.validate(world, result, declaredCompartments);
        if (diagnostic != null) {
            helper.fail("ME storage access hatch should validate as a declared compartment: " + diagnostic.message());
            return;
        }

        binder.bind(world, "main", host, declaredCompartments);
        helper.assertValueEqual(
                host.compartmentHost$getCompartments("main"),
                List.of(hatch),
                "Binder should register the ME storage access hatch with the host");
        helper.assertValueEqual(hatch.compartmentHost(), host, "Bound ME storage access hatch should remember its host");
        helper.assertValueEqual(
                hatch.compartmentStructureName(),
                "main",
                "Bound ME storage access hatch should remember the structure name");

        binder.unbind("main", host);
        helper.assertTrue(
                host.compartmentHost$getCompartments("main").isEmpty(),
                "Binder unbind should remove the ME storage access hatch from the host");
        helper.assertTrue(hatch.compartmentHost() == null, "Unbound ME storage access hatch should clear its host");
        helper.assertTrue(
                hatch.compartmentStructureName() == null,
                "Unbound ME storage access hatch should clear the structure name");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_binder_ensure_replaces_stale_same_position_part")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentBinderEnsureReplacesStaleSamePositionPart(GameTestHelper helper) {
        JsonDeclaredCompartmentBinder binder = new JsonDeclaredCompartmentBinder();
        TestCompartmentHost host = new TestCompartmentHost();
        BlockPos compartmentPos = new BlockPos(1, 0, 0);
        CompartmentBlockEntity stalePart = new CompositeWarehouseBlockEntity(
                compartmentPos,
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity currentPart = new CompositeWarehouseBlockEntity(
                compartmentPos,
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        StructureWorldView currentWorld = world(
                Map.of(compartmentPos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState()),
                Map.of(compartmentPos, currentPart));
        Map<BlockPos, CompartmentType> declaredCompartments = Map.of(compartmentPos, CompartmentType.INPUT);

        stalePart.compartment$bindToHost("main", host);

        binder.ensureBound(currentWorld, "main", host, declaredCompartments);

        helper.assertValueEqual(
                host.compartmentHost$getCompartments("main"),
                List.of(currentPart),
                "ensureBound should replace stale registered parts at the declared position");
        helper.assertTrue(stalePart.compartmentHost() == null, "Stale same-position part should be unbound");
        helper.assertValueEqual(currentPart.compartmentHost(), host, "Current world part should be bound");
        helper.assertValueEqual(
                currentPart.compartmentStructureName(),
                "main",
                "Current world part should remember the structure name");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_binder_ensure_removes_no_longer_declared_part")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentBinderEnsureRemovesNoLongerDeclaredPart(GameTestHelper helper) {
        JsonDeclaredCompartmentBinder binder = new JsonDeclaredCompartmentBinder();
        TestCompartmentHost host = new TestCompartmentHost();
        BlockPos compartmentPos = new BlockPos(1, 0, 0);
        CompartmentBlockEntity stalePart = new CompositeWarehouseBlockEntity(
                compartmentPos,
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());

        stalePart.compartment$bindToHost("main", host);

        binder.ensureBound(
                world(Map.of(compartmentPos, Blocks.STONE.defaultBlockState())),
                "main",
                host,
                Map.of());

        helper.assertTrue(
                host.compartmentHost$getCompartments("main").isEmpty(),
                "ensureBound should remove stale parts that are no longer declared by JSON");
        helper.assertTrue(stalePart.compartmentHost() == null, "No-longer-declared part should be unbound");
        helper.succeed();
    }

    @TestHolder("json_multiblock_compartment_binder_fails_fast_for_invalid_declared_parts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentBinderFailsFastForInvalidDeclaredParts(GameTestHelper helper) {
        JsonDeclaredCompartmentBinder binder = new JsonDeclaredCompartmentBinder();
        TestCompartmentHost host = new TestCompartmentHost();
        BlockPos compartmentPos = new BlockPos(1, 0, 0);
        Map<BlockPos, CompartmentType> declaredCompartments = Map.of(compartmentPos, CompartmentType.INPUT);
        StructureWorldView missingWorld = world(
                Map.of(compartmentPos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState()));
        CompartmentBlockEntity outputPart = new CompositeWarehouseBlockEntity(
                compartmentPos,
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        StructureWorldView mismatchedWorld = world(
                Map.of(compartmentPos, ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState()),
                Map.of(compartmentPos, outputPart));

        assertIllegalStateThrows(
                helper,
                () -> binder.bind(missingWorld, "main", host, declaredCompartments),
                "bind should fail fast when the declared compartment block entity is missing");
        assertIllegalStateThrows(
                helper,
                () -> binder.bind(mismatchedWorld, "main", host, declaredCompartments),
                "bind should fail fast when the declared compartment type mismatches");
        assertIllegalStateThrows(
                helper,
                () -> binder.ensureBound(missingWorld, "main", host, declaredCompartments),
                "ensureBound should fail fast when the declared compartment block entity is missing");
        assertIllegalStateThrows(
                helper,
                () -> binder.ensureBound(mismatchedWorld, "main", host, declaredCompartments),
                "ensureBound should fail fast when the declared compartment type mismatches");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_rejects_unused_compartment_symbol")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsUnusedCompartmentSymbol(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalArgumentException.class,
                () -> new MdlibJsonMultiBlockDefinitionLoader().parse(
                        resource("unused_compartment_symbol"),
                        new StringReader("{\"metadata\":{\"compartments\":{\"I\":\"input\"}},\"aisles\":[{\"slices\":[[\"~\"]]}]}")),
                "Loader should reject compartment symbols that are not present in the pattern");
        helper.succeed();
    }

    @TestHolder("json_multiblock_structure_name_rejects_slashes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void structureNameRejectsSlashes(GameTestHelper helper) {
        assertThrows(
                helper,
                IllegalArgumentException.class,
                () -> new JsonMultiBlockStructureKey(resource("sample_multiblock"), "side/main"),
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

    private static String jsonWithMissingBlockPredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:blocks\",\"block\":\"%s\"}}}"
                .formatted(MISSING_BLOCK_ID);
    }

    private static String jsonWithMissingBlocksPredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:blocks\",\"blocks\":[\"minecraft:stone\",\"%s\"]}}}"
                .formatted(MISSING_BLOCKS_ID);
    }

    private static String jsonWithAirBlocksPredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:blocks\",\"blocks\":[\"minecraft:air\"]}}}";
    }

    private static String jsonWithMissingBlockStatePredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:block_states\",\"block\":\"%s\",\"properties\":{\"facing\":\"north\"}}}}"
                .formatted(MISSING_BLOCK_ID);
    }

    private static String jsonWithReplaceableCompartment(String symbol, String... compartmentTypes) {
        String types = String.join(
                ",",
                Stream.of(compartmentTypes)
                        .map(type -> "\"" + type + "\"")
                        .toList());
        return String.join(
                "\n",
                "{",
                "  \"metadata\": {",
                "    \"replaceable_compartments\": {",
                "      \"%s\": [%s]",
                "    }",
                "  },",
                "  \"aisles\": [",
                "    {",
                "      \"slices\": [",
                "        [",
                "          \"~%s\"",
                "        ]",
                "      ]",
                "    }",
                "  ],",
                "  \"predicates\": {",
                "    \"%s\": {",
                "      \"type\": \"mdlib:blocks\",",
                "      \"block\": \"minecraft:glass\"",
                "    }",
                "  }",
                "}")
                .formatted(symbol, types, symbol, symbol);
    }

    private static StructureMatchResult matchController(BlockPattern pattern, BlockState state) {
        return JsonMultiBlockPatternMatcher.match(
                pattern,
                world(Map.of(CONTROLLER, state)),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
    }

    private static StructureWorldView world(Map<BlockPos, BlockState> states) {
        return world(states, Map.of());
    }

    private static StructureWorldView world(Map<BlockPos, BlockState> states, Map<BlockPos, BlockEntity> blockEntities) {
        return new StructureWorldView() {

            @Override
            public boolean isLoaded(BlockPos pos) {
                return true;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return blockEntities.get(pos);
            }

            @Override
            public HolderLookup.Provider registryAccess() {
                return HolderLookup.Provider.create(Stream.empty());
            }
        };
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

    private static void assertAllIntValuesEqual(GameTestHelper helper, int[] actual, int expected, String message) {
        for (int i = 0; i < actual.length; i++) {
            helper.assertValueEqual(actual[i], expected, message + " index " + i);
        }
    }

    private static void assertAllIntPairValuesEqual(GameTestHelper helper, int[][] actual, int expected, String message) {
        for (int i = 0; i < actual.length; i++) {
            helper.assertValueEqual(actual[i].length, 2, message + " pair length index " + i);
            helper.assertValueEqual(actual[i][0], expected, message + " min repeat index " + i);
            helper.assertValueEqual(actual[i][1], expected, message + " max repeat index " + i);
        }
    }

    private static int countSymbol(BlockPattern pattern, char symbol) {
        int count = 0;
        for (String[] slice : pattern.structureSlices) {
            for (String row : slice) {
                for (int index = 0; index < row.length(); index++) {
                    if (row.charAt(index) == symbol) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static BlockPos firstNonAnchorSymbol(BlockPattern pattern) {
        for (int z = 0; z < pattern.structureSlices.length; z++) {
            String[] slice = pattern.structureSlices[z];
            for (int y = 0; y < slice.length; y++) {
                String row = slice[y];
                for (int x = 0; x < row.length(); x++) {
                    char symbol = row.charAt(x);
                    if (symbol != ' ' && symbol != '~') {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        throw new IllegalStateException("Pattern does not contain a non-anchor symbol");
    }

    private static BlockPos mapPatternPosition(BlockPattern pattern,
                                               BlockPos localPosition,
                                               BlockPos hostPosition,
                                               Direction frontFacing,
                                               Direction upwardsFacing) {
        return RelativeDirection.offsetPos(
                hostPosition,
                frontFacing,
                upwardsFacing,
                false,
                localPosition.getY() - pattern.getCenterOffset().y(),
                localPosition.getX() - pattern.getCenterOffset().x(),
                localPosition.getZ() - pattern.getCenterOffset().z());
    }

    private static boolean hasJsonStringValue(JsonElement element, String expected) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return expected.equals(element.getAsString());
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (hasJsonStringValue(child, expected)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (hasJsonStringValue(entry.getValue(), expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertDiagnosticCode(GameTestHelper helper,
                                             @Nullable PatternDiagnostic diagnostic,
                                             String expectedCodePath,
                                             String message) {
        if (diagnostic == null) {
            helper.fail(message + ": expected diagnostic " + expectedCodePath + " but validation passed");
            return;
        }
        helper.assertValueEqual(diagnostic.code().getPath(), expectedCodePath, message);
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

    private static void assertIllegalStateThrows(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException ignored) {
            return;
        } catch (Throwable thrown) {
            helper.fail(message + ": expected IllegalStateException but caught " + thrown);
            return;
        }
        helper.fail(message + ": expected IllegalStateException but no exception was thrown");
    }

    private static final class TestCompartmentHost implements CompartmentHost {

        private final CompartmentHostState compartments = new CompartmentHostState();

        @Override
        public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {
            this.compartments.addCompartment(structureName, part);
        }

        @Override
        public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {
            this.compartments.removeCompartment(structureName, part);
        }

        @Override
        public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
            return this.compartments.compartments(structureName);
        }
    }
}
