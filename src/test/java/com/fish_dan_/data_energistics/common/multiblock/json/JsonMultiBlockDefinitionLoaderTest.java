package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.PatternCandidate;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.PatternMatchContext;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import com.modularmc.mdl.api.multiblock.util.RelativeDirection;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class JsonMultiBlockDefinitionLoaderTest {

    private static final BlockPos CONTROLLER = new BlockPos(0, 0, 0);
    private static final String MISSING_BLOCK_ID = "data_energistics:missing_block_for_json_multiblock_test";
    private static final String MISSING_BLOCKS_ID = "data_energistics:missing_blocks_for_json_multiblock_test";
    private static final Set<String> DIRECTION_PROPERTY_NAMES = Set.of("facing", "horizontal_facing", "axis");
    private static final String MINIMAL_JSON_WITH_METADATA = "{\"metadata\":{\"display_name\":\"multiblock.data_energistics.trinity_data_core\"},\"aisles\":[{\"slices\":[[\"~\"]]}]}";
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

    @TestHolder("json_multiblock_loader_parses_bundled_trinity_data_core_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesBundledTrinityDataCoreStructure(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_data_core/main"),
                bundledJsonReader("/data/data_energistics/multiblock/trinity_data_core/main.json"));
        BlockPattern pattern = definition.pattern();
        JsonObject root = JsonParser.parseReader(bundledJsonReader("/data/data_energistics/multiblock/trinity_data_core/main.json"))
                .getAsJsonObject();

        helper.assertValueEqual(
                definition.key(),
                JsonMultiBlockStructureKey.main(resource("trinity_data_core")),
                "Bundled Trinity Data Core JSON should resolve to the main trinity_data_core structure key");
        helper.assertTrue(
                definition.displayNameTranslationKey().isPresent(),
                "Bundled Trinity Data Core JSON should expose structure display metadata");
        helper.assertValueEqual(
                definition.displayNameTranslationKey().orElseThrow(),
                "multiblock.data_energistics.trinity_data_core",
                "Bundled Trinity Data Core display metadata should resolve to the structure lang key");
        helper.assertValueEqual(
                definition.compartmentTypes().size(),
                0,
                "Bundled Trinity Data Core should not declare compartment roles yet");
        helper.assertValueEqual(
                definition.replaceableCompartmentTypes().size(),
                1,
                "Bundled Trinity Data Core should declare the dedicated access hatch symbol");
        helper.assertTrue(
                definition.replaceableCompartmentTypes().containsKey("@"),
                "Bundled Trinity Data Core should allow replacements only at the dedicated access hatch symbol");
        helper.assertFalse(
                definition.replaceableCompartmentTypes().containsKey("H"),
                "Bundled Trinity Data Core H symbol should remain ordinary quartz vibrant glass");
        Set<CompartmentType> accessHatchTypes = definition.replaceableCompartmentTypes().getOrDefault("@", Set.of());
        helper.assertTrue(
                accessHatchTypes.contains(CompartmentType.TRINITY_ACCESS),
                "Bundled Trinity Data Core dedicated access hatch symbol should allow the Trinity access hatch");
        helper.assertFalse(
                accessHatchTypes.contains(CompartmentType.INPUT),
                "Bundled Trinity Data Core dedicated access hatch symbol should not accept input warehouses");
        helper.assertFalse(
                accessHatchTypes.contains(CompartmentType.OUTPUT),
                "Bundled Trinity Data Core dedicated access hatch symbol should not accept output warehouses");
        helper.assertFalse(
                accessHatchTypes.contains(CompartmentType.ME_INPUT),
                "Bundled Trinity Data Core dedicated access hatch symbol should not accept ME input warehouses");
        helper.assertFalse(
                accessHatchTypes.contains(CompartmentType.ME_OUTPUT),
                "Bundled Trinity Data Core dedicated access hatch symbol should not accept ME output warehouses");
        helper.assertFalse(
                accessHatchTypes.contains(CompartmentType.PATTERN_BUFFER),
                "Bundled Trinity Data Core dedicated access hatch symbol should not accept pattern buffer warehouses");
        helper.assertTrue(
                !definition.replaceableCompartmentTypes().containsKey("Y"),
                "Bundled Trinity Data Core should not allow compartments to replace plain glass");
        assertIntArrayEqual(helper, pattern.getDimensions(), new int[] { 32, 28, 27 },
                "Bundled Trinity Data Core dimensions should match the shipped main JSON resource");
        helper.assertValueEqual(pattern.structureSlices.length, 32, "Bundled Trinity Data Core should use one aisle per exported front layer");
        helper.assertValueEqual(pattern.aisleRepetitions.length, 32, "Each Trinity Data Core aisle should be a fixed non-repeatable unit");
        assertAllIntValuesEqual(helper, pattern.unitDepths, 1, "Each Trinity Data Core aisle unit should contain exactly one slice");
        assertAllIntPairValuesEqual(helper, pattern.aisleRepetitions, 1, "Each Trinity Data Core aisle unit should repeat exactly once");
        helper.assertValueEqual(pattern.structureDir.charDir(), RelativeDirection.LEFT, "Main structure char axis should point left");
        helper.assertValueEqual(pattern.structureDir.stringDir(), RelativeDirection.UP, "Main structure row axis should point up");
        helper.assertValueEqual(pattern.structureDir.aisleDir(), RelativeDirection.FRONT, "Main structure aisle axis should point front");
        helper.assertValueEqual(
                pattern.structureSlices[24][1],
                "            M@M            ",
                "Bundled Trinity Data Core should allow Trinity access replacement directly below the host");
        helper.assertValueEqual(
                pattern.structureSlices[24][2],
                "            M~M            ",
                "Bundled Trinity Data Core should map the exported controller to the host position");
        helper.assertValueEqual(
                pattern.structureSlices[24][3],
                "            MHM            ",
                "Bundled Trinity Data Core should keep the quartz vibrant glass directly above the host fixed");
        helper.assertValueEqual(
                pattern.structureSlices[0][0],
                "                           ",
                "Bundled Trinity Data Core should retain empty exported boundary rows");
        helper.assertTrue(
                pattern.structureSlices[0][27].charAt(0) == ' ',
                "The upper-left WorldEdit corner marker should export as an unconstrained space");
        helper.assertTrue(
                pattern.structureSlices[31][0].charAt(26) == ' ',
                "The lower-right WorldEdit corner marker should export as an unconstrained space");
        helper.assertValueEqual(pattern.getCenterOffset().x(), 13, "Controller X offset should match the exported host column");
        helper.assertValueEqual(pattern.getCenterOffset().y(), 2, "Controller Y offset should match the GregTech bottom-to-top JSON row");
        helper.assertValueEqual(pattern.getCenterOffset().z(), 24, "Controller Z offset should match the exported host aisle");
        helper.assertValueEqual(pattern.getCenterOffset().minZ(), 24, "Controller Z min offset should match the placeholder aisle");
        helper.assertValueEqual(pattern.getCenterOffset().maxZ(), 24, "Controller Z max offset should match the placeholder aisle");
        helper.assertValueEqual(countSymbol(pattern, 'Z'), 1176, "Main Trinity Data Core should expose all storage core slots through Z");
        helper.assertValueEqual(countSymbol(pattern, '@'), 1, "Main Trinity Data Core should expose exactly one access hatch replacement slot");
        helper.assertValueEqual(countSymbol(pattern, 'H'), 37, "Main Trinity Data Core should keep all other quartz vibrant glass slots fixed");
        helper.assertValueEqual(countSymbol(pattern, ' '), 21525, "Main Trinity Data Core should leave exported air unconstrained");
        helper.assertValueEqual(countSymbol(pattern, '#'), 12, "Main Trinity Data Core should retain all covered cable positions");
        helper.assertValueEqual(countSymbol(pattern, 'D'), 160, "Main Trinity Data Core should retain the re-exported framework shell");
        helper.assertValueEqual(countSymbol(pattern, 'P'), 34, "Main Trinity Data Core should retain every spatial pylon");
        helper.assertValueEqual(countSymbol(pattern, 'W'), 53, "Main Trinity Data Core should retain every sky stone slab");
        helper.assertValueEqual(countSymbol(pattern, 'K'), 58, "Main Trinity Data Core should retain every sky stone wall");
        ItemStack firstAccessPlacementCandidate = predicateForFirstSymbol(pattern, '@').placementCandidates().getFirst();
        helper.assertTrue(
                firstAccessPlacementCandidate.is(ModBlocks.TRINITY_ACCESS_HATCH.get().asItem()),
                "Auto-build should prefer placing the Trinity access hatch in @ slots before falling back to quartz glass");
        JsonObject structureDir = root.getAsJsonObject("metadata").getAsJsonObject("structure_dir");
        helper.assertValueEqual(structureDir.get("char").getAsString(), "left", "Main JSON should map chars to left");
        helper.assertValueEqual(structureDir.get("string").getAsString(), "up", "Main JSON should map rows to height");
        helper.assertValueEqual(structureDir.get("aisle").getAsString(), "front", "Main JSON should map aisles to front");
        helper.assertFalse(
                hasJsonStringValue(root, "minecraft:air"),
                "Main JSON should export schematic air as unconstrained spaces");
        helper.assertFalse(
                hasJsonStringValue(root, "mdlib:air"),
                "Main JSON should not publish an explicit air predicate");
        JsonObject predicates = root.getAsJsonObject("predicates");
        for (String symbol : predicates.keySet()) {
            helper.assertTrue(
                    symbol.length() == 1 && countSymbol(pattern, symbol.charAt(0)) > 0,
                    "Main JSON predicate '" + symbol + "' should be used by the exported structure");
        }
        JsonObject cablePredicate = predicates.getAsJsonObject("#");
        helper.assertValueEqual(
                cablePredicate.get("type").getAsString(),
                "data_energistics:placement_items",
                "Main covered cables should retain their placement-item predicate");
        helper.assertValueEqual(
                cablePredicate.has("item"),
                false,
                "Main covered cables should not require only the Fluix variant");
        JsonArray coveredCables = cablePredicate.getAsJsonArray("items");
        helper.assertValueEqual(coveredCables.size(), 17,
                "Main covered cables should accept every AE2 covered cable color");
        helper.assertValueEqual(
                coveredCables.get(0).getAsString(),
                "ae2:fluix_covered_cable",
                "Main covered cables should preserve Fluix as the default placement candidate");
        Set<String> coveredCableIds = new LinkedHashSet<>();
        coveredCables.forEach(element -> coveredCableIds.add(element.getAsString()));
        helper.assertValueEqual(coveredCableIds.size(), 17,
                "Main covered cable candidates should not contain duplicates");
        helper.assertTrue(coveredCableIds.contains("ae2:red_covered_cable"),
                "Main covered cables should accept colored variants");
        helper.assertTrue(
                coveredCableIds.stream().allMatch(id -> id.startsWith("ae2:") && id.endsWith("_covered_cable")),
                "Main covered cable candidates must exclude smart, glass, and dense cables");
        JsonObject quartzVibrantGlassPredicate = predicates.getAsJsonObject("H");
        JsonObject replaceableQuartzVibrantGlassPredicate = predicates.getAsJsonObject("@");
        helper.assertValueEqual(replaceableQuartzVibrantGlassPredicate, quartzVibrantGlassPredicate, "Dedicated access hatch symbol should match the same quartz vibrant glass block as H");
        JsonObject smoothQuartzSlabPredicate = root.getAsJsonObject("predicates").getAsJsonObject("N");
        helper.assertValueEqual(smoothQuartzSlabPredicate.get("type").getAsString(), "mdlib:blocks", "Main smooth quartz slab should match by block id only");
        helper.assertFalse(smoothQuartzSlabPredicate.has("properties"), "Main smooth quartz slab should not keep slab type or waterlogged states");
        JsonObject skyStoneSlabPredicate = root.getAsJsonObject("predicates").getAsJsonObject("W");
        helper.assertValueEqual(skyStoneSlabPredicate.get("type").getAsString(), "mdlib:blocks", "Main sky stone slab should match by block id only");
        helper.assertFalse(skyStoneSlabPredicate.has("properties"), "Main sky stone slab should not keep slab type or waterlogged states");
        JsonObject quartzSlabPredicate = root.getAsJsonObject("predicates").getAsJsonObject("$");
        helper.assertValueEqual(quartzSlabPredicate.get("type").getAsString(), "mdlib:blocks", "Main quartz slab should match by block id only");
        helper.assertFalse(quartzSlabPredicate.has("properties"), "Main quartz slab should not keep slab type or waterlogged states");
        assertOnlyDirectionProperties(helper, root, "Main Trinity Data Core predicates");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_limits_trinity_data_core_access_hatch_replacement_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void limitsTrinityDataCoreAccessHatchReplacementSlots(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_data_core/main"),
                bundledJsonReader("/data/data_energistics/multiblock/trinity_data_core/main.json"));
        BlockPattern pattern = definition.pattern();
        BlockPos hostPos = new BlockPos(200, 64, -200);
        Direction frontFacing = Direction.EAST;
        Map<BlockPos, BlockState> states = mainStructureStates(pattern, hostPos, frontFacing);
        StructureWorldView originalWorld = world(states);

        StructureMatchResult originalResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                originalWorld,
                hostPos,
                frontFacing,
                "main");
        helper.assertTrue(originalResult.matched(), "Generated Trinity Data Core main structure should match");

        helper.assertValueEqual(countSymbol(pattern, '@'), 1,
                "Trinity Data Core main structure should expose exactly one access hatch slot");
        BlockPos accessPatternPos = firstSymbol(pattern, '@');
        BlockPos controllerPatternPos = firstSymbol(pattern, '~');
        helper.assertValueEqual(accessPatternPos,
                controllerPatternPos.below(),
                "The sole access hatch slot should remain directly below the controller");
        BlockPos accessSlot = mapPatternPosition(pattern, accessPatternPos, hostPos, frontFacing, Direction.NORTH);
        Map<BlockPos, BlockState> accessStates = new LinkedHashMap<>(states);
        accessStates.put(accessSlot, ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState());
        StructureMatchResult accessResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                world(accessStates),
                hostPos,
                frontFacing,
                "main");
        helper.assertTrue(accessResult.matched(), "Dedicated @ slot should accept the Trinity access hatch");
        helper.assertValueEqual(
                JsonMultiBlockCompartmentPredicate.declaredCompartments(accessResult.context()).get(accessSlot),
                CompartmentType.TRINITY_ACCESS,
                "Dedicated @ slot should be declared as the Trinity access hatch");

        BlockPos fixedGlassSlot = mapPatternPosition(pattern, firstSymbol(pattern, 'H'), hostPos, frontFacing, Direction.NORTH);
        Map<BlockPos, BlockState> fixedGlassStates = new LinkedHashMap<>(states);
        fixedGlassStates.put(fixedGlassSlot, ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState());
        StructureMatchResult fixedGlassResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                world(fixedGlassStates),
                hostPos,
                frontFacing,
                "main");
        helper.assertFalse(fixedGlassResult.matched(), "Ordinary H quartz glass slots should not accept access hatches");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_parses_bundled_trinity_data_core_cpu_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesBundledTrinityDataCoreCpuStructure(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_data_core/cpu"),
                bundledJsonReader("/data/data_energistics/multiblock/trinity_data_core/cpu.json"));
        BlockPattern pattern = definition.pattern();

        helper.assertValueEqual(
                definition.key(),
                new JsonMultiBlockStructureKey(resource("trinity_data_core"), "cpu"),
                "Bundled Trinity Data Core CPU JSON should resolve to the cpu child structure key");
        assertIntArrayEqual(helper, pattern.getDimensions(), new int[] { 8, 22, 9 },
                "Bundled Trinity Data Core CPU dimensions should map width/depth/height from the new export");
        helper.assertValueEqual(expandedDepth(pattern), 19, "CPU child expanded aisle depth should express the variable height");
        helper.assertValueEqual(pattern.structureSlices.length, 8, "CPU child should keep the manually corrected height units");
        helper.assertValueEqual(pattern.aisleRepetitions.length, 8, "CPU child should keep eight height repeat units");
        assertIntArrayEqual(helper, pattern.unitDepths, new int[] { 1, 1, 1, 1, 1, 1, 1, 1 },
                "Each CPU child repeat unit should contain one slice");
        assertIntPairArrayEqual(helper, pattern.aisleRepetitions, new int[][] {
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 12 },
                { 1, 1 }
        }, "CPU child repeat bounds should allow one to twelve repeated height layers");
        helper.assertValueEqual(pattern.structureDir.charDir(), RelativeDirection.RIGHT, "CPU child char axis should point right");
        helper.assertValueEqual(pattern.structureDir.stringDir(), RelativeDirection.FRONT, "CPU child row axis should point front");
        helper.assertValueEqual(pattern.structureDir.aisleDir(), RelativeDirection.UP, "CPU child aisle axis should point up");
        helper.assertValueEqual(pattern.getCenterOffset().x(), 0, "CPU child controller X offset should be the mirrored host column");
        helper.assertValueEqual(pattern.getCenterOffset().y(), 21, "CPU child controller row should be the exported front host row");
        helper.assertValueEqual(pattern.getCenterOffset().z(), 2, "CPU child compressed controller aisle should match the exported host height");
        helper.assertValueEqual(
                pattern.structureSlices[2][21].charAt(0),
                '~',
                "CPU child host anchor should mirror to the exported local coordinate (0, 21, 2)");
        helper.assertValueEqual(
                pattern.structureSlices[1][1].substring(4, 8),
                "CCCC",
                "CPU child mirrored core row should map the imported core placeholder area to C symbols");
        helper.assertValueEqual(
                pattern.structureSlices[0][0],
                "         ",
                "CPU child should leave the four shared main-structure cube corners to the main JSON");
        helper.assertValueEqual(
                pattern.structureSlices[0][5],
                "         ",
                "CPU child should not duplicate shared main-structure cube corners");
        helper.assertValueEqual(pattern.getCenterOffset().minZ(), 2, "CPU child controller min aisle should match the exported host height");
        helper.assertValueEqual(pattern.getCenterOffset().maxZ(), 2, "CPU child controller max aisle should match the exported host height");
        helper.assertValueEqual(countSymbol(pattern, 'A'), 60, "CPU child should omit the four shared main-structure cube corners");
        helper.assertValueEqual(countSymbol(pattern, 'C'), 96, "CPU child compressed core units should expose six core layers");
        helper.assertValueEqual(countExpandedSymbol(pattern, 'C'), 272, "CPU child expanded structure should expose all merged storage core positions");
        helper.assertValueEqual(
                pattern.structureSlices[5][5],
                "   AEEEEA",
                "CPU child slab row should match the manually corrected slab unit");
        helper.assertValueEqual(countSymbol(pattern, 'E'), 4, "CPU child should expose only the four schematic slab positions");
        BlockPos hostPos = new BlockPos(-81, -44, 8);
        helper.assertValueEqual(
                mapPatternPosition(pattern, new BlockPos(0, 21, 2), hostPos, Direction.EAST, Direction.NORTH),
                hostPos,
                "CPU child controller symbol should map directly to the main host position");
        helper.assertValueEqual(
                mapPatternPosition(pattern, new BlockPos(1, 21, 2), hostPos, Direction.EAST, Direction.NORTH),
                hostPos.relative(Direction.SOUTH),
                "CPU child local x should expand to the mirrored right side of the supplied front");
        helper.assertValueEqual(
                mapPatternPosition(pattern, new BlockPos(4, 1, 1), hostPos, Direction.SOUTH, Direction.NORTH, true),
                hostPos.offset(4, -1, -20),
                "CPU child should map the first C slot to the mirrored right CPU bay and one block below the host in flipped context");

        JsonObject root = JsonParser.parseReader(bundledJsonReader("/data/data_energistics/multiblock/trinity_data_core/cpu.json"))
                .getAsJsonObject();
        JsonObject structureDir = root.getAsJsonObject("metadata").getAsJsonObject("structure_dir");
        helper.assertValueEqual(structureDir.get("char").getAsString(), "right", "CPU child JSON should map chars to right");
        helper.assertValueEqual(structureDir.get("string").getAsString(), "front", "CPU child JSON should map rows to front");
        helper.assertValueEqual(structureDir.get("aisle").getAsString(), "up", "CPU child JSON should map aisles to height");
        JsonObject cpuMetadata = root.getAsJsonObject("metadata").getAsJsonObject("cpu_core");
        helper.assertValueEqual(cpuMetadata.get("core_slot_start_y").getAsInt(), 0, "CPU child core slot start should include the bottom layer");
        helper.assertValueEqual(cpuMetadata.get("core_slot_end_y").getAsInt(), 16, "CPU child core slot end should match the last core layer");
        helper.assertValueEqual(cpuMetadata.get("repeat_start_y").getAsInt(), 3, "CPU child repeat start should match the repeated section");
        helper.assertValueEqual(cpuMetadata.get("repeat_end_y").getAsInt(), 15, "CPU child repeat end should match the repeated section");
        helper.assertValueEqual(cpuMetadata.get("max_repeat_count").getAsInt(), 13, "CPU child repeat count should map the 13 repeated layers");
        helper.assertValueEqual(cpuMetadata.get("max_threads").getAsInt(), 256, "CPU child max threads should be the mapped thread cap");
        JsonObject cpuCorePredicate = root.getAsJsonObject("predicates").getAsJsonObject("C");
        helper.assertTrue(
                hasJsonStringValue(cpuCorePredicate.get("blocks"),
                        "data_energistics:me_digital_merged_storage_core_256m"),
                "CPU child core predicate should allow the 256M merged storage core");
        helper.assertFalse(
                hasJsonStringValue(cpuCorePredicate.get("blocks"), "ae2:pattern_provider"),
                "CPU child core predicate should not allow the imported pattern provider placeholder");
        helper.assertFalse(
                hasJsonStringValue(root, "minecraft:air"),
                "CPU child JSON should leave exported air as MDLib any spaces instead of minecraft:air predicates");
        helper.assertFalse(
                hasJsonStringValue(root, "mdlib:air"),
                "CPU child JSON should not use explicit MDLib air predicates");
        helper.assertFalse(
                hasJsonStringValue(root, "ae2:pattern_provider[push_direction=all]"),
                "CPU child JSON should not keep the exported pattern provider placeholder as a predicate value");
        helper.assertFalse(
                hasJsonStringValue(root, "ae2:not_so_mysterious_cube"),
                "CPU child JSON should map not-so-mysterious-cube export markers to framework symbols");
        JsonObject slabPredicate = root.getAsJsonObject("predicates").getAsJsonObject("E");
        helper.assertValueEqual(slabPredicate.get("type").getAsString(), "mdlib:blocks", "CPU child slab predicate should match by block id only");
        helper.assertFalse(slabPredicate.has("properties"), "CPU child slab predicate should not keep slab type or waterlogged states");
        helper.assertFalse(slabPredicate.has("block_states"), "CPU child slab predicate should not export block state predicates");
        assertOnlyDirectionProperties(helper, root, "CPU child predicates");
        StructureMatchResult filledCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                cpuStructureWorld(
                        pattern,
                        hostPos,
                        Direction.EAST,
                        ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_256M.get().defaultBlockState()),
                hostPos,
                Direction.EAST,
                "cpu");
        helper.assertTrue(filledCoreResult.matched(), "CPU child structure should match when all C slots are merged storage cores");
        StructureMatchResult rotatedCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                cpuStructureWorld(
                        pattern,
                        hostPos,
                        Direction.SOUTH,
                        ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_256M.get().defaultBlockState()),
                hostPos,
                Direction.EAST,
                "cpu");
        helper.assertFalse(rotatedCoreResult.matched(), "CPU child exact match should not rotate away from the main structure front");
        StructureMatchResult airCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                cpuStructureWorld(pattern, hostPos, Direction.EAST, Blocks.AIR.defaultBlockState()),
                hostPos,
                Direction.EAST,
                "cpu");
        helper.assertFalse(airCoreResult.matched(), "CPU child structure should reject air in a C core slot");
        StructureMatchResult patternProviderCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                cpuStructureWorld(pattern, hostPos, Direction.EAST, block("ae2:pattern_provider").defaultBlockState()),
                hostPos,
                Direction.EAST,
                "cpu");
        helper.assertFalse(patternProviderCoreResult.matched(), "CPU child structure should reject the imported pattern provider placeholder");
        StructureMatchResult flippedSouthResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                cpuStructureWorld(
                        pattern,
                        hostPos,
                        Direction.SOUTH,
                        true,
                        ModBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_256M.get().defaultBlockState()),
                hostPos,
                Direction.SOUTH,
                true,
                "cpu");
        helper.assertTrue(flippedSouthResult.matched(), "CPU child should match the exported position for south-facing flipped hosts");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_parses_bundled_trinity_data_core_crafting_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parsesBundledTrinityDataCoreCraftingStructure(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_data_core/crafting"),
                bundledJsonReader("/data/data_energistics/multiblock/trinity_data_core/crafting.json"));
        BlockPattern pattern = definition.pattern();

        helper.assertValueEqual(
                definition.key(),
                new JsonMultiBlockStructureKey(resource("trinity_data_core"), "crafting"),
                "Bundled Trinity Data Core crafting JSON should resolve to the crafting child structure key");
        assertIntArrayEqual(helper, pattern.getDimensions(), new int[] { 8, 22, 9 },
                "Crafting child dimensions should map compressed height, front depth, and local width from carft.schem");
        helper.assertValueEqual(expandedDepth(pattern), 19, "Crafting child expanded aisle depth should preserve carft.schem height");
        helper.assertValueEqual(pattern.structureSlices.length, 8, "Crafting child should compress repeated height units");
        helper.assertValueEqual(pattern.aisleRepetitions.length, 8, "Crafting child should keep eight height repeat units");
        assertIntArrayEqual(helper, pattern.unitDepths, new int[] { 1, 1, 1, 1, 1, 1, 1, 1 },
                "Each crafting child repeat unit should contain one slice");
        assertIntPairArrayEqual(helper, pattern.aisleRepetitions, new int[][] {
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 1 },
                { 1, 12 },
                { 1, 1 }
        }, "Crafting child repeat bounds should allow one to twelve repeated height layers");
        helper.assertValueEqual(pattern.structureDir.charDir(), RelativeDirection.RIGHT, "Crafting child char axis should point right");
        helper.assertValueEqual(pattern.structureDir.stringDir(), RelativeDirection.FRONT, "Crafting child row axis should point front");
        helper.assertValueEqual(pattern.structureDir.aisleDir(), RelativeDirection.UP, "Crafting child aisle axis should point up");
        helper.assertValueEqual(pattern.getCenterOffset().x(), 8, "Crafting child controller X offset should be the host column after inserting the inner gap");
        helper.assertValueEqual(pattern.getCenterOffset().y(), 21, "Crafting child controller row should be the exported front host row");
        helper.assertValueEqual(pattern.getCenterOffset().z(), 2, "Crafting child compressed controller aisle should match the exported host height");
        helper.assertValueEqual(pattern.getCenterOffset().minZ(), 2, "Crafting child controller min aisle should match the exported host height");
        helper.assertValueEqual(pattern.getCenterOffset().maxZ(), 2, "Crafting child controller max aisle should match the exported host height");
        helper.assertValueEqual(countSymbol(pattern, '~'), 1, "Crafting child should use the Trinity Data Core only as the host anchor");
        helper.assertValueEqual(countSymbol(pattern, 'A'), 60, "Crafting child should omit the four shared main-structure cube corners");
        helper.assertValueEqual(countSymbol(pattern, 'P'), 96, "Crafting child compressed core units should expose six pattern core layers");
        helper.assertValueEqual(countExpandedSymbol(pattern, 'P'), 272, "Crafting child expanded structure should expose all pattern core positions");
        helper.assertValueEqual(
                firstSymbol(pattern, 'P'),
                new BlockPos(1, 1, 1),
                "Crafting child first pattern core slot should keep the body in its original local column");
        helper.assertValueEqual(
                pattern.structureSlices[0][0],
                "         ",
                "Crafting child should leave the four shared main-structure cube corners to the main JSON");
        helper.assertValueEqual(
                pattern.structureSlices[0][5],
                "         ",
                "Crafting child should not duplicate shared main-structure cube corners");
        BlockPos hostPos = new BlockPos(-142, -43, 83);
        helper.assertValueEqual(
                mapPatternPosition(pattern, new BlockPos(8, 21, 2), hostPos, Direction.EAST, Direction.NORTH),
                hostPos,
                "Crafting child controller symbol should map directly to the main host position");
        helper.assertValueEqual(
                mapPatternPosition(pattern, new BlockPos(7, 21, 2), hostPos, Direction.EAST, Direction.NORTH),
                hostPos.relative(Direction.NORTH),
                "Crafting child local x should expand to the mirrored left side of the supplied front");
        helper.assertValueEqual(
                mapPatternPosition(pattern, new BlockPos(1, 1, 1), hostPos, Direction.SOUTH, Direction.NORTH, true),
                hostPos.offset(-7, -1, -20),
                "Crafting child should map the first P slot to the mirrored left pattern bay and one block below the host in flipped context");

        JsonObject root = JsonParser.parseReader(bundledJsonReader("/data/data_energistics/multiblock/trinity_data_core/crafting.json"))
                .getAsJsonObject();
        JsonObject structureDir = root.getAsJsonObject("metadata").getAsJsonObject("structure_dir");
        helper.assertValueEqual(structureDir.get("char").getAsString(), "right", "Crafting child JSON should map chars to right");
        helper.assertValueEqual(structureDir.get("string").getAsString(), "front", "Crafting child JSON should map rows to front");
        helper.assertValueEqual(structureDir.get("aisle").getAsString(), "up", "Crafting child JSON should map aisles to height");
        JsonObject predicates = root.getAsJsonObject("predicates");
        helper.assertFalse(predicates.has("~"), "Crafting child should not add an extra host predicate or anchor");
        JsonObject patternCorePredicate = predicates.getAsJsonObject("P");
        helper.assertTrue(
                hasJsonStringValue(patternCorePredicate.get("blocks"),
                        "data_energistics:me_digital_pattern_processing_core"),
                "Crafting child pattern predicate should allow the ordinary pattern processing core");
        helper.assertTrue(
                hasJsonStringValue(patternCorePredicate.get("blocks"),
                        "data_energistics:extended_me_digital_pattern_processing_core"),
                "Crafting child pattern predicate should allow the extended pattern processing core");
        helper.assertTrue(
                hasJsonStringValue(patternCorePredicate.get("blocks"),
                        "data_energistics:overlimit_me_digital_pattern_processing_core"),
                "Crafting child pattern predicate should allow the overlimit pattern processing core");
        helper.assertFalse(
                hasJsonStringValue(root, "minecraft:air"),
                "Crafting child JSON should leave exported air as MDLib any spaces instead of minecraft:air predicates");
        helper.assertFalse(
                hasJsonStringValue(root, "mdlib:air"),
                "Crafting child JSON should not use explicit MDLib air predicates");
        helper.assertFalse(
                hasJsonStringValue(root, "ae2:pattern_provider"),
                "Crafting child JSON should not keep the exported pattern provider placeholder block id");
        helper.assertFalse(
                hasJsonStringValue(root, "ae2:pattern_provider[push_direction=all]"),
                "Crafting child JSON should not keep the exported pattern provider placeholder state");
        helper.assertFalse(
                hasJsonStringValue(root, "ae2:not_so_mysterious_cube"),
                "Crafting child JSON should map not-so-mysterious-cube export markers to framework symbols");
        JsonObject slabPredicate = predicates.getAsJsonObject("E");
        helper.assertValueEqual(slabPredicate.get("type").getAsString(), "mdlib:blocks", "Crafting child slab predicate should match by block id only");
        helper.assertFalse(slabPredicate.has("properties"), "Crafting child slab predicate should not keep slab type or waterlogged states");
        helper.assertFalse(slabPredicate.has("block_states"), "Crafting child slab predicate should not export block state predicates");
        assertOnlyDirectionProperties(helper, root, "Crafting child predicates");

        StructureMatchResult ordinaryCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                craftingStructureWorld(
                        pattern,
                        hostPos,
                        Direction.EAST,
                        ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState()),
                hostPos,
                Direction.EAST,
                "crafting");
        helper.assertTrue(ordinaryCoreResult.matched(), "Crafting child should match ordinary pattern processing cores");
        StructureMatchResult extendedCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                craftingStructureWorld(
                        pattern,
                        hostPos,
                        Direction.EAST,
                        ModBlocks.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState()),
                hostPos,
                Direction.EAST,
                "crafting");
        helper.assertTrue(extendedCoreResult.matched(), "Crafting child should match extended pattern processing cores");
        StructureMatchResult overlimitCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                craftingStructureWorld(
                        pattern,
                        hostPos,
                        Direction.EAST,
                        ModBlocks.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState()),
                hostPos,
                Direction.EAST,
                "crafting");
        helper.assertTrue(overlimitCoreResult.matched(), "Crafting child should match overlimit pattern processing cores");
        StructureMatchResult rotatedCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                craftingStructureWorld(
                        pattern,
                        hostPos,
                        Direction.SOUTH,
                        ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState()),
                hostPos,
                Direction.EAST,
                "crafting");
        helper.assertFalse(rotatedCoreResult.matched(), "Crafting child exact match should not rotate away from the main structure front");
        StructureMatchResult airCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                craftingStructureWorld(pattern, hostPos, Direction.EAST, Blocks.AIR.defaultBlockState()),
                hostPos,
                Direction.EAST,
                "crafting");
        helper.assertFalse(airCoreResult.matched(), "Crafting child should reject air in a P core slot");
        StructureMatchResult patternProviderCoreResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                craftingStructureWorld(pattern, hostPos, Direction.EAST, block("ae2:pattern_provider").defaultBlockState()),
                hostPos,
                Direction.EAST,
                "crafting");
        helper.assertFalse(patternProviderCoreResult.matched(), "Crafting child should reject the imported pattern provider placeholder");
        StructureMatchResult flippedSouthResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                craftingStructureWorld(
                        pattern,
                        hostPos,
                        Direction.SOUTH,
                        true,
                        ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState()),
                hostPos,
                Direction.SOUTH,
                true,
                "crafting");
        helper.assertTrue(flippedSouthResult.matched(), "Crafting child should match the exported position for south-facing flipped hosts");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_downgrades_missing_block_predicate_to_any")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void downgradesMissingBlockPredicateToAny(GameTestHelper helper) {
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

        StructureMatchResult nonAirResult = matchController(
                definitions.get(key).pattern(),
                Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(nonAirResult.matched(), "Downgraded missing block predicate should match any non-air block at A");
        StructureMatchResult airResult = matchController(definitions.get(key).pattern(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(airResult.matched(), "Downgraded missing block predicate should still match air at A");
        helper.succeed();
    }

    @TestHolder("json_multiblock_parse_downgrades_missing_blocks_predicate_to_any")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parseDowngradesMissingBlocksPredicateToAny(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("missing_blocks_predicate"),
                new StringReader(jsonWithMissingBlocksPredicate()));

        StructureMatchResult nonAirResult = matchController(definition.pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(nonAirResult.matched(), "Downgraded missing block in blocks array should match any non-air block at A");
        StructureMatchResult airResult = matchController(definition.pattern(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(airResult.matched(), "Downgraded missing block in blocks array should still match air at A");
        helper.succeed();
    }

    @TestHolder("json_multiblock_parse_downgrades_missing_block_state_predicate_to_any")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void parseDowngradesMissingBlockStatePredicateToAny(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("missing_block_state_predicate"),
                new StringReader(jsonWithMissingBlockStatePredicate()));

        StructureMatchResult nonAirResult = matchController(definition.pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(nonAirResult.matched(), "Downgraded missing block state predicate should match any non-air block at A");
        StructureMatchResult airResult = matchController(definition.pattern(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(airResult.matched(), "Downgraded missing block state predicate should still match air at A");
        helper.succeed();
    }

    @TestHolder("json_multiblock_block_state_predicate_matches_declared_properties_only")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blockStatePredicateMatchesDeclaredPropertiesOnly(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("partial_block_state_predicate"),
                new StringReader(jsonWithPartialBlockStatePredicate()));

        BlockState matchingState = Blocks.FURNACE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, true);
        TraceabilityPredicate predicate = definition.pattern().getPredicate(0, 0, 0);
        helper.assertTrue(
                predicate.blockStateCandidates().contains(Blocks.FURNACE.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)),
                "Block state predicate should expose the JSON-declared preferred state for auto-build");
        StructureMatchResult matchingResult = matchController(definition.pattern(), matchingState);
        helper.assertTrue(
                matchingResult.matched(),
                "Block state predicate should accept actual states with extra undeclared properties");

        BlockState wrongFacingState = Blocks.FURNACE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.LIT, true);
        StructureMatchResult wrongFacingResult = matchController(definition.pattern(), wrongFacingState);
        helper.assertFalse(
                wrongFacingResult.matched(),
                "Block state predicate should still reject mismatched declared properties");
        helper.succeed();
    }

    @TestHolder("json_multiblock_slab_block_state_predicate_matches_block_only")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void slabBlockStatePredicateMatchesBlockOnly(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("slab_block_state_predicate"),
                new StringReader(jsonWithSlabBlockStatePredicate()));

        BlockState bottomWaterloggedSlab = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM)
                .setValue(BlockStateProperties.WATERLOGGED, true);
        StructureMatchResult slabResult = matchController(definition.pattern(), bottomWaterloggedSlab);
        helper.assertTrue(slabResult.matched(), "Slab predicates should ignore type and waterlogged states");

        StructureMatchResult wrongBlockResult = matchController(definition.pattern(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        helper.assertFalse(wrongBlockResult.matched(), "Slab predicates should still reject a different block id");
        helper.succeed();
    }

    @TestHolder("json_multiblock_placement_item_predicate_exposes_explicit_item_candidates")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void placementItemPredicateExposesExplicitItemCandidates(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("placement_item_predicate"),
                new StringReader(jsonWithPlacementItemPredicate()));

        TraceabilityPredicate predicate = definition.pattern().getPredicate(0, 0, 0);
        helper.assertTrue(
                predicate.blockCandidates().contains(Blocks.GLASS),
                "Placement item predicate should preserve delegate block candidates");
        helper.assertTrue(
                predicate.placementCandidates().stream().anyMatch(stack -> stack.is(Items.STICK)),
                "Placement item predicate should expose explicit non-block item candidates");
        StructureMatchResult result = matchController(definition.pattern(), Blocks.GLASS.defaultBlockState());
        helper.assertTrue(result.matched(), "Placement item predicate should still match through its delegate");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_converts_air_block_predicate_to_any")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void convertsAirBlockPredicateToAny(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("air_block_predicate"),
                new StringReader(jsonWithAirBlocksPredicate()));

        StructureMatchResult result = matchController(definition.pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(result.matched(), "minecraft:air should be normalized to any and accept non-air blocks");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_converts_mdlib_air_predicate_to_any")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void convertsMdlibAirPredicateToAny(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("mdlib_air_predicate"),
                new StringReader(jsonWithMdlibAirPredicate()));

        StructureMatchResult result = matchController(definition.pattern(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(result.matched(), "mdlib:air should be normalized to any and accept non-air blocks");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_maps_space_symbol_to_any")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsSpaceSymbolToAny(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("space_symbol_predicate"),
                new StringReader(jsonWithSpacePredicate()));
        BlockPattern pattern = definition.pattern();
        BlockPos spacePos = mapPatternPosition(
                pattern,
                new BlockPos(1, 0, 0),
                CONTROLLER,
                Direction.NORTH,
                Direction.NORTH);

        StructureMatchResult nonAirResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                world(Map.of(
                        CONTROLLER, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                        spacePos, Blocks.GOLD_BLOCK.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertTrue(nonAirResult.matched(), "Space cells should be forced to any and accept non-air blocks");

        StructureMatchResult airResult = JsonMultiBlockPatternMatcher.matchExact(
                pattern,
                world(Map.of(CONTROLLER, Blocks.DIAMOND_BLOCK.defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertTrue(airResult.matched(), "Space cells should be forced to any and accept air");
        helper.succeed();
    }

    @TestHolder("json_multiblock_loader_strips_display_metadata_before_mdlib_parse")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void stripsDisplayMetadataBeforeMdlibParse(GameTestHelper helper) {
        JsonMultiBlockDefinition definition = new MdlibJsonMultiBlockDefinitionLoader().parse(
                resource("trinity_data_core"),
                new StringReader(MINIMAL_JSON_WITH_METADATA));

        helper.assertValueEqual(
                definition.displayNameTranslationKey().orElseThrow(),
                "multiblock.data_energistics.trinity_data_core",
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
                new StringReader(jsonWithReplaceableCompartment("A", "input", "trinity_access")));

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
                        replaceablePos, ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState())),
                CONTROLLER,
                Direction.NORTH,
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME);
        helper.assertTrue(accessHatch.matched(), "Allowed Trinity access hatch should replace glass");
        helper.assertValueEqual(
                JsonMultiBlockCompartmentPredicate.declaredCompartments(accessHatch.context()).get(replaceablePos),
                CompartmentType.TRINITY_ACCESS,
                "Allowed access hatch replacement should be recorded for binder validation");
        helper.succeed();
    }

    @TestHolder("json_multiblock_replaceable_compartment_preserves_declared_placement_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void replaceableCompartmentPreservesDeclaredPlacementOrder(GameTestHelper helper) {
        JsonObject predicateJson = JsonParser.parseString(
                "{\"compartments\":[\"input\",\"trinity_access\"]," +
                        "\"predicate\":{\"type\":\"mdlib:blocks\",\"block\":\"minecraft:glass\"}}")
                .getAsJsonObject();
        JsonMultiBlockReplaceableCompartmentPredicate predicate = JsonMultiBlockReplaceableCompartmentPredicate.fromJson(predicateJson);
        List<ItemStack> candidates = predicate.placementCandidates();
        helper.assertValueEqual(candidates.size(), 3, "Replacement candidates should contain two compartments and the delegate block");
        helper.assertTrue(
                candidates.get(0).is(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().asItem()),
                "First declared compartment should remain the first placement candidate");
        helper.assertTrue(
                candidates.get(1).is(ModBlocks.TRINITY_ACCESS_HATCH.get().asItem()),
                "Second declared compartment should remain the second placement candidate");
        helper.assertTrue(
                candidates.get(2).is(Items.GLASS),
                "Original block should remain the final placement fallback");

        List<PatternCandidate> pairedCandidates = predicate.patternCandidates();
        helper.assertValueEqual(pairedCandidates.size(), 3,
                "Replacement state and placement candidates should remain paired");
        helper.assertValueEqual(
                pairedCandidates.get(0).previewState(),
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState(),
                "First declared compartment should remain the first preview state");
        helper.assertTrue(
                pairedCandidates.get(0).placementStack().is(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().asItem()),
                "First declared compartment state should retain its placement item");
        helper.assertValueEqual(
                pairedCandidates.get(1).previewState(),
                ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState(),
                "Second declared compartment should remain the second preview state");
        helper.assertTrue(
                pairedCandidates.get(1).placementStack().is(ModBlocks.TRINITY_ACCESS_HATCH.get().asItem()),
                "Second declared compartment state should retain its placement item");
        helper.assertValueEqual(
                pairedCandidates.get(2).previewState(),
                Blocks.GLASS.defaultBlockState(),
                "Original block state should remain the final preview fallback");
        helper.assertTrue(pairedCandidates.get(2).placementStack().is(Items.GLASS),
                "Original block state should retain its placement item");
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

    @TestHolder("json_multiblock_compartment_binder_binds_trinity_access_hatch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentBinderBindsTrinityAccessHatch(GameTestHelper helper) {
        JsonDeclaredCompartmentBinder binder = new JsonDeclaredCompartmentBinder();
        TestCompartmentHost host = new TestCompartmentHost();
        BlockPos hatchPos = new BlockPos(1, 0, 0);
        TrinityAccessHatchBlockEntity hatch = new TrinityAccessHatchBlockEntity(
                hatchPos,
                ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState());
        StructureMatchResult result = StructureMatchResult.success(
                false,
                Direction.NORTH,
                List.of(hatchPos),
                new PatternMatchContext());
        StructureWorldView world = world(
                Map.of(hatchPos, ModBlocks.TRINITY_ACCESS_HATCH.get().defaultBlockState()),
                Map.of(hatchPos, hatch));
        Map<BlockPos, CompartmentType> declaredCompartments = Map.of(hatchPos, CompartmentType.TRINITY_ACCESS);

        PatternDiagnostic diagnostic = binder.validate(world, result, declaredCompartments);
        if (diagnostic != null) {
            helper.fail("Trinity access hatch should validate as a declared compartment: " + diagnostic.message());
            return;
        }

        binder.bind(world, "main", host, declaredCompartments);
        helper.assertValueEqual(
                host.compartmentHost$getCompartments("main"),
                List.of(hatch),
                "Binder should register the Trinity access hatch with the host");
        helper.assertValueEqual(hatch.compartmentHost(), host, "Bound Trinity access hatch should remember its host");
        helper.assertValueEqual(
                hatch.compartmentStructureName(),
                "main",
                "Bound Trinity access hatch should remember the structure name");

        binder.unbind("main", host);
        helper.assertTrue(
                host.compartmentHost$getCompartments("main").isEmpty(),
                "Binder unbind should remove the Trinity access hatch from the host");
        helper.assertTrue(hatch.compartmentHost() == null, "Unbound Trinity access hatch should clear its host");
        helper.assertTrue(
                hatch.compartmentStructureName() == null,
                "Unbound Trinity access hatch should clear the structure name");
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

    private static String jsonWithMdlibAirPredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:air\"}}}";
    }

    private static String jsonWithSpacePredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~ \"]]}],\"predicates\":{\" \":{\"type\":\"mdlib:blocks\",\"block\":\"minecraft:stone\"}}}";
    }

    private static String jsonWithMissingBlockStatePredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:block_states\",\"block\":\"%s\",\"properties\":{\"facing\":\"north\"}}}}"
                .formatted(MISSING_BLOCK_ID);
    }

    private static String jsonWithPartialBlockStatePredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:block_states\",\"block\":\"minecraft:furnace\",\"properties\":{\"facing\":\"north\"}}}}";
    }

    private static String jsonWithSlabBlockStatePredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"mdlib:block_states\",\"block\":\"minecraft:smooth_quartz_slab\",\"properties\":{\"type\":\"top\",\"waterlogged\":\"false\"}}}}";
    }

    private static String jsonWithPlacementItemPredicate() {
        return "{\"aisles\":[{\"slices\":[[\"~\"]]}],\"predicates\":{\"~\":{\"type\":\"data_energistics:placement_items\",\"item\":\"minecraft:stick\",\"predicate\":{\"type\":\"mdlib:blocks\",\"block\":\"minecraft:glass\"}}}}";
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

    private static StructureWorldView cpuStructureWorld(BlockPattern pattern,
                                                        BlockPos controllerPos,
                                                        Direction frontFacing,
                                                        BlockState coreSlotState) {
        return cpuStructureWorld(pattern, controllerPos, frontFacing, false, coreSlotState);
    }

    private static StructureWorldView cpuStructureWorld(BlockPattern pattern,
                                                        BlockPos controllerPos,
                                                        Direction frontFacing,
                                                        boolean flipped,
                                                        BlockState coreSlotState) {
        return childStructureWorld(
                pattern,
                controllerPos,
                frontFacing,
                flipped,
                symbol -> cpuStructureState(symbol, coreSlotState));
    }

    private static StructureWorldView craftingStructureWorld(BlockPattern pattern,
                                                             BlockPos controllerPos,
                                                             Direction frontFacing,
                                                             BlockState patternCoreState) {
        return craftingStructureWorld(pattern, controllerPos, frontFacing, false, patternCoreState);
    }

    private static StructureWorldView craftingStructureWorld(BlockPattern pattern,
                                                             BlockPos controllerPos,
                                                             Direction frontFacing,
                                                             boolean flipped,
                                                             BlockState patternCoreState) {
        return childStructureWorld(
                pattern,
                controllerPos,
                frontFacing,
                flipped,
                symbol -> craftingStructureState(symbol, patternCoreState));
    }

    private static Map<BlockPos, BlockState> mainStructureStates(BlockPattern pattern,
                                                                 BlockPos controllerPos,
                                                                 Direction frontFacing) {
        return structureStates(
                pattern,
                controllerPos,
                frontFacing,
                (z, y, x) -> defaultCandidateState(pattern, z, y, x));
    }

    private static StructureWorldView childStructureWorld(BlockPattern pattern,
                                                          BlockPos controllerPos,
                                                          Direction frontFacing,
                                                          Function<Character, BlockState> stateFactory) {
        return childStructureWorld(pattern, controllerPos, frontFacing, false, stateFactory);
    }

    private static StructureWorldView childStructureWorld(BlockPattern pattern,
                                                          BlockPos controllerPos,
                                                          Direction frontFacing,
                                                          boolean flipped,
                                                          Function<Character, BlockState> stateFactory) {
        return world(structureStates(
                pattern,
                controllerPos,
                frontFacing,
                flipped,
                (z, y, x) -> stateFactory.apply(pattern.structureSlices[z][y].charAt(x))));
    }

    private interface PatternStateFactory {

        BlockState create(int z, int y, int x);
    }

    private static Map<BlockPos, BlockState> structureStates(BlockPattern pattern,
                                                             BlockPos controllerPos,
                                                             Direction frontFacing,
                                                             PatternStateFactory stateFactory) {
        return structureStates(pattern, controllerPos, frontFacing, false, stateFactory);
    }

    private static Map<BlockPos, BlockState> structureStates(BlockPattern pattern,
                                                             BlockPos controllerPos,
                                                             Direction frontFacing,
                                                             boolean flipped,
                                                             PatternStateFactory stateFactory) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        int expandedZ = 0;
        for (int unitIndex = 0; unitIndex < pattern.aisleRepetitions.length; unitIndex++) {
            int start = pattern.unitStarts[unitIndex];
            int depth = pattern.unitDepths[unitIndex];
            int repeat = pattern.aisleRepetitions[unitIndex][1];
            for (int repeatIndex = 0; repeatIndex < repeat; repeatIndex++) {
                for (int inner = 0; inner < depth; inner++) {
                    String[] slice = pattern.structureSlices[start + inner];
                    int localZ = expandedZ + inner;
                    for (int y = 0; y < slice.length; y++) {
                        String row = slice[y];
                        for (int x = 0; x < row.length(); x++) {
                            char symbol = row.charAt(x);
                            if (symbol == ' ' || symbol == '~') {
                                continue;
                            }
                            BlockPos localPos = new BlockPos(x, y, localZ);
                            states.put(
                                    mapPatternPosition(pattern, localPos, controllerPos, frontFacing, Direction.NORTH, flipped),
                                    stateFactory.create(start + inner, y, x));
                        }
                    }
                }
                expandedZ += depth;
            }
        }
        return states;
    }

    private static BlockState defaultCandidateState(BlockPattern pattern, int z, int y, int x) {
        TraceabilityPredicate predicate = pattern.getPredicate(z, y, x);
        List<BlockState> candidates = predicate.blockStateCandidates();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No block-state candidate for pattern symbol " + pattern.structureSlices[z][y].charAt(x));
        }
        return candidates.getFirst();
    }

    private static BlockState cpuStructureState(char symbol, BlockState coreSlotState) {
        return switch (symbol) {
            case 'A' -> ModBlocks.DATA_FRAMEWORK.get().defaultBlockState();
            case 'B' -> block("ae2:quartz_glass").defaultBlockState();
            case 'C' -> coreSlotState;
            case 'E' -> block("ae2:smooth_sky_stone_slab").defaultBlockState();
            default -> throw new IllegalArgumentException("Unsupported CPU structure symbol: " + symbol);
        };
    }

    private static BlockState craftingStructureState(char symbol, BlockState patternCoreState) {
        return switch (symbol) {
            case 'A' -> ModBlocks.DATA_FRAMEWORK.get().defaultBlockState();
            case 'B' -> block("ae2:quartz_glass").defaultBlockState();
            case 'P' -> patternCoreState;
            case 'E' -> block("ae2:smooth_sky_stone_slab").defaultBlockState();
            default -> throw new IllegalArgumentException("Unsupported crafting structure symbol: " + symbol);
        };
    }

    private static Block block(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (!location.equals(BuiltInRegistries.BLOCK.getKey(block))) {
            throw new IllegalStateException("Missing test block: " + id);
        }
        return block;
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

    private static void assertIntPairArrayEqual(GameTestHelper helper, int[][] actual, int[][] expected, String message) {
        helper.assertValueEqual(actual.length, expected.length, message + " length");
        for (int i = 0; i < expected.length; i++) {
            helper.assertValueEqual(actual[i].length, expected[i].length, message + " pair length index " + i);
            for (int j = 0; j < expected[i].length; j++) {
                helper.assertValueEqual(actual[i][j], expected[i][j], message + " index " + i + "/" + j);
            }
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

    private static int countExpandedSymbol(BlockPattern pattern, char symbol) {
        int count = 0;
        for (int unitIndex = 0; unitIndex < pattern.aisleRepetitions.length; unitIndex++) {
            int start = pattern.unitStarts[unitIndex];
            int depth = pattern.unitDepths[unitIndex];
            int repeat = pattern.aisleRepetitions[unitIndex][1];
            for (int inner = 0; inner < depth; inner++) {
                count += countSymbol(pattern.structureSlices[start + inner], symbol) * repeat;
            }
        }
        return count;
    }

    private static BlockPos firstSymbol(BlockPattern pattern, char symbol) {
        for (int z = 0; z < pattern.structureSlices.length; z++) {
            String[] slice = pattern.structureSlices[z];
            for (int y = 0; y < slice.length; y++) {
                int x = slice[y].indexOf(symbol);
                if (x >= 0) {
                    return new BlockPos(x, y, z);
                }
            }
        }
        throw new IllegalStateException("Pattern does not contain symbol '" + symbol + "'");
    }

    private static TraceabilityPredicate predicateForFirstSymbol(BlockPattern pattern, char symbol) {
        BlockPos pos = firstSymbol(pattern, symbol);
        return pattern.getPredicate(pos.getZ(), pos.getY(), pos.getX());
    }

    private static int countSymbol(String[] slice, char symbol) {
        int count = 0;
        for (String row : slice) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == symbol) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int expandedDepth(BlockPattern pattern) {
        int depth = 0;
        for (int unitIndex = 0; unitIndex < pattern.aisleRepetitions.length; unitIndex++) {
            depth += pattern.unitDepths[unitIndex] * pattern.aisleRepetitions[unitIndex][1];
        }
        return depth;
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
        return mapPatternPosition(pattern, localPosition, hostPosition, frontFacing, upwardsFacing, false);
    }

    private static BlockPos mapPatternPosition(BlockPattern pattern,
                                               BlockPos localPosition,
                                               BlockPos hostPosition,
                                               Direction frontFacing,
                                               Direction upwardsFacing,
                                               boolean flipped) {
        int upOffset = localPosition.getY() - pattern.getCenterOffset().y();
        int leftOffset = localPosition.getX() - pattern.getCenterOffset().x();
        int forwardOffset = localPosition.getZ() - pattern.getCenterOffset().minZ();
        int actualUp = projectOffset(pattern.structureDir.stringDir(), upOffset, leftOffset, forwardOffset);
        int actualLeft = projectOffset(pattern.structureDir.charDir(), upOffset, leftOffset, forwardOffset);
        int actualForward = projectOffset(pattern.structureDir.aisleDir(), upOffset, leftOffset, forwardOffset);
        return RelativeDirection.offsetPos(
                hostPosition,
                frontFacing,
                upwardsFacing,
                flipped,
                actualUp,
                actualLeft,
                actualForward);
    }

    private static int projectOffset(RelativeDirection direction, int upOffset, int leftOffset, int forwardOffset) {
        return switch (direction) {
            case UP -> upOffset;
            case DOWN -> -upOffset;
            case LEFT -> leftOffset;
            case RIGHT -> -leftOffset;
            case FRONT -> forwardOffset;
            case BACK -> -forwardOffset;
        };
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

    private static void assertOnlyDirectionProperties(GameTestHelper helper, JsonObject root, String message) {
        JsonObject predicates = root.getAsJsonObject("predicates");
        for (Map.Entry<String, JsonElement> predicateEntry : predicates.entrySet()) {
            JsonObject predicate = predicateEntry.getValue().getAsJsonObject();
            JsonObject properties = predicate.getAsJsonObject("properties");
            if (properties == null) {
                continue;
            }
            for (String propertyName : properties.keySet()) {
                helper.assertTrue(
                        DIRECTION_PROPERTY_NAMES.contains(propertyName),
                        message + " should only keep direction property keys, but '" + predicateEntry.getKey() +
                                "' kept '" + propertyName + "'");
            }
        }
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
