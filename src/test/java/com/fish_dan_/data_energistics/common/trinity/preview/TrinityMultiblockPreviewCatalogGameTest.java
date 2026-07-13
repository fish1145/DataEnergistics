package com.fish_dan_.data_energistics.common.trinity.preview;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinitionRegistrySnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalogSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellRole;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierOption;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewProjection;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewProjectionImpl;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.modularmc.mdl.api.multiblock.PatternUnit;
import com.modularmc.mdl.api.multiblock.RepeatRange;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end common-layer acceptance coverage for Trinity's live preview catalog generation.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityMultiblockPreviewCatalogGameTest {

    private static final ResourceLocation CABLE_BUS = ResourceLocation.parse("ae2:cable_bus");
    private static final ResourceLocation FLUIX_COVERED_CABLE = ResourceLocation.parse("ae2:fluix_covered_cable");
    private static final ResourceLocation TRINITY_ACCESS_HATCH = ResourceLocation.parse("data_energistics:trinity_access_hatch");
    private static final ResourceLocation QUARTZ_VIBRANT_GLASS = ResourceLocation.parse("ae2:quartz_vibrant_glass");
    private static final int CHILD_CELLS_PER_REPEAT = 198;
    private static final long CHILD_MATERIALS_PER_REPEAT = 36L;
    private static final long CHILD_CORES_PER_REPEAT = 16L;

    private TrinityMultiblockPreviewCatalogGameTest() {}

    @TestHolder("trinity_multiblock_preview_catalog_projects_live_definitions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void projectsLiveDefinitions(GameTestHelper helper) {
        JsonMultiBlockDefinitionRegistrySnapshot definitions = ModVerticalMultiBlocks.JSON_MULTI_BLOCKS.snapshot();
        MultiblockPreviewCatalogSnapshot catalog = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot();
        MultiblockPreviewSpec spec = catalog.require(ModVerticalMultiBlocks.trinityDataCoreId());

        helper.assertValueEqual(
                catalog.definitionRevision(),
                definitions.revision(),
                "Preview catalog must use the current atomic JSON definition generation");
        helper.assertValueEqual(
                spec.definitionRevision(),
                definitions.revision(),
                "Trinity preview spec must retain the catalog definition generation");
        helper.assertValueEqual(
                spec.substructures().stream().map(SubstructurePreviewSpec::id).toList(),
                List.of("main", "cpu", "crafting"),
                "Trinity substructures must retain main/cpu/crafting presentation order");

        SubstructurePreviewSpec mainSpec = spec.substructure("main");
        SubstructurePreviewSpec cpuSpec = spec.substructure("cpu");
        SubstructurePreviewSpec craftingSpec = spec.substructure("crafting");
        helper.assertTrue(
                mainSpec.repeatRanges().stream().allMatch(range -> range.min() == range.max()),
                "Trinity main structure must not expose a variable repeat unit");
        int cpuVariableUnit = requireVariableUnit(helper, cpuSpec, "CPU");
        requireVariableUnit(helper, craftingSpec, "crafting");

        assertTierDomain(helper, mainSpec, TrinityAutoBuildBlockMap.STORAGE_CORE, 10);
        assertTierDomain(helper, cpuSpec, TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 10);
        assertTierDomain(helper, craftingSpec, TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 3);

        StructurePreviewProjection projection = new StructurePreviewProjectionImpl();
        PreviewSelection initial = PreviewSelection.initial(spec);
        StructurePreviewSnapshot main = projection.project(spec, initial);
        PreviewSelection cpuSelection = initial.select("cpu");
        StructurePreviewSnapshot cpu = projection.project(spec, cpuSelection);
        StructurePreviewSnapshot crafting = projection.project(spec, initial.select("crafting"));
        assertController(helper, spec, main, "main");
        assertController(helper, spec, cpu, "cpu");
        assertController(helper, spec, crafting, "crafting");
        assertMainSpecialCandidates(helper, main);

        List<ResourceLocation> cpuTierIds = TrinityAutoBuildBlockMap.categories().get(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE);
        ResourceLocation firstCpuCore = cpuTierIds.getFirst();
        ResourceLocation lastCpuCore = cpuTierIds.getLast();
        long initialCoreAmount = materialAmount(cpu, firstCpuCore);
        helper.assertTrue(initialCoreAmount > 0L, "Default CPU projection must require its first-tier core");

        PreviewSelection upgradedSelection = cpuSelection.withTier(
                TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE,
                cpuTierIds.size());
        StructurePreviewSnapshot upgraded = projection.project(spec, upgradedSelection);
        helper.assertValueEqual(
                materialAmount(upgraded, lastCpuCore),
                initialCoreAmount,
                "Changing CPU tier must preserve the number of selected core cells");
        helper.assertValueEqual(
                materialAmount(upgraded, firstCpuCore),
                0L,
                "Changing CPU tier must remove the previous core material identity");
        helper.assertValueEqual(
                upgraded.cells().size(),
                cpu.cells().size(),
                "Changing CPU tier must not change projected cell count");
        helper.assertValueEqual(
                totalMaterialAmount(upgraded),
                totalMaterialAmount(cpu),
                "Changing CPU tier must not change total material count");

        PreviewSelection repeatedSelection = upgradedSelection.withRepeat(cpuVariableUnit, 2);
        StructurePreviewSnapshot repeated = projection.project(spec, repeatedSelection);
        helper.assertValueEqual(
                repeated.cells().size(),
                upgraded.cells().size() + CHILD_CELLS_PER_REPEAT,
                "A second CPU repeat must add one complete 9x22 source layer");
        helper.assertValueEqual(
                totalMaterialAmount(repeated),
                totalMaterialAmount(upgraded) + CHILD_MATERIALS_PER_REPEAT,
                "A second CPU repeat must add all 36 concrete structure materials");
        helper.assertValueEqual(
                materialAmount(repeated, lastCpuCore),
                materialAmount(upgraded, lastCpuCore) + CHILD_CORES_PER_REPEAT,
                "A second CPU repeat must add its 4x4 selected core field");

        MultiblockRecipeView recipe = MultiblockRecipeView.from(spec, repeated);
        helper.assertValueEqual(recipe.inputs(), repeated.materials(),
                "Ordinary recipe inputs must be the current aggregate materials");
        helper.assertValueEqual(recipe.output().key(), spec.ownerOutput(),
                "Ordinary recipe output must be the Trinity owner item");
        helper.assertValueEqual(recipe.output().amount(), 1L,
                "Ordinary recipe owner output must have amount one");
        helper.succeed();
    }

    private static int requireVariableUnit(GameTestHelper helper,
                                           SubstructurePreviewSpec substructure,
                                           String label) {
        List<Integer> variableUnits = new ArrayList<>();
        for (int index = 0; index < substructure.repeatRanges().size(); index++) {
            RepeatRange range = substructure.repeatRanges().get(index);
            if (range.min() != range.max()) {
                variableUnits.add(index);
            }
        }
        helper.assertValueEqual(variableUnits.size(), 1,
                label + " structure must expose exactly one variable repeat unit");
        int variableUnit = variableUnits.getFirst();
        RepeatRange range = substructure.repeatRanges().get(variableUnit);
        helper.assertValueEqual(range.min(), 1, label + " repeat minimum must be one");
        helper.assertValueEqual(range.max(), 12, label + " repeat maximum must be twelve");
        PatternUnit unit = substructure.definition().pattern().getLayout().units().get(variableUnit);
        int cellsPerRepeat = Math.multiplyExact(
                Math.multiplyExact(
                        substructure.definition().pattern().getLayout().width(),
                        substructure.definition().pattern().getLayout().height()),
                unit.depth());
        helper.assertValueEqual(cellsPerRepeat, CHILD_CELLS_PER_REPEAT,
                label + " variable unit must retain its 9x22 one-layer shape");
        return variableUnit;
    }

    private static void assertTierDomain(GameTestHelper helper,
                                         SubstructurePreviewSpec substructure,
                                         String category,
                                         int expectedOptions) {
        PreviewTierDomain domain = substructure.tierDomain(category);
        List<ResourceLocation> expectedIds = TrinityAutoBuildBlockMap.categories().get(category);
        helper.assertValueEqual(domain.options().size(), expectedOptions,
                "Trinity preview tier option count must match auto-build category " + category);
        helper.assertValueEqual(
                domain.options().stream().map(PreviewTierOption::blockId).toList(),
                expectedIds,
                "Trinity preview tier ids must come from auto-build category " + category);
    }

    private static void assertController(GameTestHelper helper,
                                         MultiblockPreviewSpec spec,
                                         StructurePreviewSnapshot snapshot,
                                         String label) {
        List<PreviewCellSnapshot> controllers = snapshot.cells().stream()
                .filter(cell -> cell.predicate().role() == PreviewCellRole.CONTROLLER)
                .toList();
        helper.assertValueEqual(controllers.size(), 1,
                label + " projection must contain exactly one controller cell");
        helper.assertValueEqual(controllers.getFirst().relativePosition(), BlockPos.ZERO,
                label + " controller cell must stay anchored at BlockPos.ZERO");
        helper.assertFalse(controllers.getFirst().predicate().role().contributesMaterial(),
                label + " controller role must not contribute recipe material");
        helper.assertTrue(snapshot.materials().stream().noneMatch(material -> material.key().equals(spec.ownerOutput())),
                label + " projection materials must exclude the controller owner output");
        long contributingCells = snapshot.cells().stream()
                .filter(cell -> cell.predicate().role().contributesMaterial())
                .map(cell -> cell.predicate().selectedCandidate().orElseThrow())
                .filter(PreviewCandidate::concrete)
                .count();
        helper.assertValueEqual(totalMaterialAmount(snapshot), contributingCells,
                label + " aggregate material amount must equal its concrete material cells");
    }

    private static void assertMainSpecialCandidates(GameTestHelper helper, StructurePreviewSnapshot main) {
        boolean cablePairFound = main.cells().stream()
                .flatMap(cell -> cell.predicate().candidates().stream())
                .anyMatch(candidate -> candidate.concrete() &&
                        CABLE_BUS.equals(blockId(candidate)) &&
                        FLUIX_COVERED_CABLE.equals(itemId(candidate)));
        helper.assertTrue(cablePairFound,
                "Main preview must pair the cable bus render state with the fluix covered cable placement item");

        PreviewPredicateSnapshot accessPredicate = main.cells().stream()
                .map(PreviewCellSnapshot::predicate)
                .filter(predicate -> predicate.candidates().size() == 2)
                .filter(predicate -> predicate.candidates().stream()
                        .map(TrinityMultiblockPreviewCatalogGameTest::blockId)
                        .toList()
                        .containsAll(List.of(TRINITY_ACCESS_HATCH, QUARTZ_VIBRANT_GLASS)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Main preview did not expose the access hatch and vibrant glass candidates"));
        helper.assertValueEqual(blockId(accessPredicate.candidates().getFirst()), TRINITY_ACCESS_HATCH,
                "Access hatch must be the first default candidate");
        helper.assertValueEqual(blockId(accessPredicate.candidates().get(1)), QUARTZ_VIBRANT_GLASS,
                "Vibrant glass must remain the alternate access-position candidate");
        helper.assertValueEqual(accessPredicate.selectedCandidate().orElseThrow(),
                accessPredicate.candidates().getFirst(),
                "Access hatch must be selected by default");
    }

    private static ResourceLocation blockId(PreviewCandidate candidate) {
        return BuiltInRegistries.BLOCK.getKey(candidate.state().orElseThrow().getBlock());
    }

    private static ResourceLocation itemId(PreviewCandidate candidate) {
        return BuiltInRegistries.ITEM.getKey(candidate.placementKey().orElseThrow().getItem());
    }

    private static long materialAmount(StructurePreviewSnapshot snapshot, ResourceLocation itemId) {
        return snapshot.materials().stream()
                .filter(material -> itemId.equals(BuiltInRegistries.ITEM.getKey(material.key().getItem())))
                .mapToLong(PreviewMaterial::amount)
                .sum();
    }

    private static long totalMaterialAmount(StructurePreviewSnapshot snapshot) {
        return snapshot.materials().stream().mapToLong(PreviewMaterial::amount).sum();
    }
}
