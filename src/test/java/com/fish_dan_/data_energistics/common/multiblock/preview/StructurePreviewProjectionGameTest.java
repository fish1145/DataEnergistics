package com.fish_dan_.data_energistics.common.multiblock.preview;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.ResolvedJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.item.OrderPackageTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.PatternCandidate;
import com.modularmc.mdl.api.multiblock.Predicates;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;

import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class StructurePreviewProjectionGameTest {

    private static final ResourceLocation CONTROLLER_ID = ResourceLocation.parse("data_energistics:structure_preview_projection_test");
    private static final String SUBSTRUCTURE_ID = "main";
    private static final long DEFINITION_REVISION = 23L;
    private static final PreviewPredicateKey MATERIAL_PREDICATE = new PreviewPredicateKey(0, 0, 1);

    private StructurePreviewProjectionGameTest() {}

    @TestHolder("structure_preview_projection_projects_controller_repeat_and_tier_materials")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void projectsControllerRepeatAndTierMaterials(GameTestHelper helper) {
        AEItemKey ownerKey = itemKey(Blocks.DIAMOND_BLOCK);
        AEItemKey ironKey = itemKey(Blocks.IRON_BLOCK);
        AEItemKey goldKey = itemKey(Blocks.GOLD_BLOCK);
        PreviewTierDomain tierDomain = new PreviewTierDomain(
                "tier",
                Component.literal("Tier"),
                List.of(
                        tierOption(1, "minecraft:iron_block"),
                        tierOption(2, "minecraft:gold_block")),
                1);
        MultiblockPreviewSpec spec = spec(
                repeatAndTierPattern(),
                ownerKey,
                List.of(tierDomain),
                new SubstructureSelection(List.of(1, 1), Map.of("tier", 1), Map.of()));
        StructurePreviewProjection projection = new StructurePreviewProjectionImpl();
        PreviewSelection initial = PreviewSelection.initial(spec);

        StructurePreviewSnapshot initialSnapshot = projection.project(spec, initial);
        StructurePreviewSnapshot repeatedSnapshot = projection.project(spec, initial.withRepeat(1, 2));
        StructurePreviewSnapshot goldSnapshot = projection.project(
                spec,
                initial.withRepeat(1, 2).withTier("tier", 2));

        PreviewCellSnapshot controller = controllerCell(initialSnapshot);
        PreviewCandidate controllerCandidate = controller.predicate().selectedCandidate().orElseThrow();
        helper.assertValueEqual(controller.predicate().role(), PreviewCellRole.CONTROLLER,
                "The controller anchor must be projected as a controller cell");
        helper.assertValueEqual(
                controllerCandidate.state().orElseThrow(),
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                "The controller anchor must render the owner block state");
        helper.assertValueEqual(
                controllerCandidate.placementKey().orElseThrow(),
                ownerKey,
                "The controller anchor must retain the owner item identity");
        helper.assertValueEqual(amountOf(initialSnapshot, ownerKey), 1L,
                "Only the matching non-controller cell must contribute the owner material identity");

        helper.assertValueEqual(initialSnapshot.cells().size(), 4,
                "The default repeat count must project one repeatable layer");
        helper.assertValueEqual(repeatedSnapshot.cells().size(), 6,
                "Increasing the repeat count must add one complete repeatable layer");
        helper.assertValueEqual(amountOf(initialSnapshot, ironKey), 1L,
                "The default iron tier must occur once initially");
        helper.assertValueEqual(amountOf(repeatedSnapshot, ownerKey), 1L,
                "Changing repeat count must not change fixed materials");
        helper.assertValueEqual(amountOf(repeatedSnapshot, ironKey), 2L,
                "Changing repeat count must change the repeated material amount");
        helper.assertValueEqual(amountOf(goldSnapshot, ownerKey), 1L,
                "Changing tier must leave unrelated fixed materials unchanged");
        helper.assertValueEqual(amountOf(goldSnapshot, ironKey), 0L,
                "Changing tier must remove the formerly selected tier material");
        helper.assertValueEqual(amountOf(goldSnapshot, goldKey), 2L,
                "Changing tier must replace every repeated tier material");

        MultiblockRecipeView recipe = MultiblockRecipeView.from(spec, goldSnapshot);
        helper.assertValueEqual(recipe.inputs().size(), goldSnapshot.materials().size(),
                "The controller must merge with a matching projected material instead of adding a duplicate slot");
        helper.assertValueEqual(amountOf(recipe.inputs(), ownerKey), 2L,
                "The ordinary recipe inputs must add one controller to the matching material amount");
        helper.assertValueEqual(amountOf(recipe.inputs(), goldKey), 2L,
                "The ordinary recipe inputs must retain the selected tier amount");
        ItemStack outputStack = recipe.output().key().toStack();
        helper.assertTrue(OrderPackageTarget.get().isOrderPackage(outputStack),
                "The ordinary recipe output must be an order package");
        helper.assertValueEqual(OrderPackageTarget.get().getTarget(outputStack).orElseThrow(), ownerKey,
                "The ordinary recipe output must target the preview owner");
        helper.assertValueEqual(recipe.output().amount(), 1L,
                "The order package output amount must be one");
        helper.assertValueEqual(recipe.substructureId(), SUBSTRUCTURE_ID,
                "The ordinary recipe view must retain the active substructure id");
        helper.assertValueEqual(recipe.definitionRevision(), DEFINITION_REVISION,
                "The ordinary recipe view must retain the projected definition revision");
        helper.assertValueEqual(
                recipe.registeredRecipeId(),
                MultiblockRecipeView.registeredRecipeIdFor(CONTROLLER_ID),
                "The registered recipe id must remain controller-level stable");
        helper.assertValueEqual(
                recipe.projectionFingerprint(),
                ProjectionFingerprint.from(goldSnapshot.selection()),
                "The recipe view must expose the current structured projection fingerprint");

        PreviewViewState allLayers = PreviewViewState.initial();
        PreviewViewState oneLayer = allLayers.showLogicalLayer(0);
        helper.assertValueEqual(goldSnapshot.visibleLayers(allLayers), goldSnapshot.layers(),
                "The explicit ALL view must retain every projected layer");
        helper.assertValueEqual(goldSnapshot.visibleLayers(oneLayer), List.of(goldSnapshot.layers().getFirst()),
                "A logical-layer view must filter only rendered layers");
        helper.assertValueEqual(amountOf(recipe.inputs(), ownerKey), 2L,
                "Changing visible layers must not remove the controller recipe input");
        helper.assertValueEqual(recipe.projectionFingerprint(), ProjectionFingerprint.from(goldSnapshot.selection()),
                "Visible layer state must not enter the projection fingerprint");
        helper.succeed();
    }

    @TestHolder("structure_preview_projection_selects_variant_definition")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void selectsVariantDefinition(GameTestHelper helper) {
        ResolvedJsonMultiBlockDefinition stoneVariant = definition(
                oneMaterialPattern(Predicates.blocks(Blocks.STONE)));
        ResolvedJsonMultiBlockDefinition goldVariant = definition(
                oneMaterialPattern(Predicates.blocks(Blocks.GOLD_BLOCK)));
        SubstructurePreviewSpec substructure = new SubstructurePreviewSpec(
                List.of(stoneVariant, goldVariant),
                Component.literal("Main"),
                List.of(),
                new SubstructureSelection(0, List.of(1), Map.of(), Map.of()));
        MultiblockPreviewSpec spec = new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Projection test"),
                itemKey(Blocks.DIAMOND_BLOCK),
                DEFINITION_REVISION,
                List.of(substructure));
        StructurePreviewProjection projection = new StructurePreviewProjectionImpl();
        PreviewSelection stoneSelection = PreviewSelection.initial(spec);
        PreviewSelection goldSelection = stoneSelection.withVariantIndex(1);

        StructurePreviewSnapshot stone = projection.project(spec, stoneSelection);
        StructurePreviewSnapshot gold = projection.project(spec, goldSelection);

        helper.assertValueEqual(stone.materials(), List.of(new PreviewMaterial(itemKey(Blocks.STONE), 1L)),
                "Variant zero must project its own shape materials");
        helper.assertValueEqual(gold.materials(), List.of(new PreviewMaterial(itemKey(Blocks.GOLD_BLOCK), 1L)),
                "Variant one must project its own shape materials");
        helper.assertFalse(
                ProjectionFingerprint.from(stoneSelection).equals(ProjectionFingerprint.from(goldSelection)),
                "Changing shape variant must change the projection fingerprint");
        helper.succeed();
    }

    @TestHolder("structure_preview_projection_preserves_explicit_candidate_order_and_applies_override")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesExplicitCandidateOrderAndAppliesOverride(GameTestHelper helper) {
        AEItemKey goldKey = itemKey(Blocks.GOLD_BLOCK);
        AEItemKey ironKey = itemKey(Blocks.IRON_BLOCK);
        MultiblockPreviewSpec spec = spec(
                oneMaterialPattern(candidatePredicate(List.of(
                        candidate(Blocks.GOLD_BLOCK, new ItemStack(Blocks.GOLD_BLOCK)),
                        candidate(Blocks.IRON_BLOCK, new ItemStack(Blocks.IRON_BLOCK))))),
                itemKey(Blocks.DIAMOND_BLOCK),
                List.of(),
                new SubstructureSelection(List.of(1), Map.of(), Map.of()));
        StructurePreviewProjection projection = new StructurePreviewProjectionImpl();
        PreviewSelection initial = PreviewSelection.initial(spec);

        StructurePreviewSnapshot defaultSnapshot = projection.project(spec, initial);
        PreviewPredicateSnapshot defaultPredicate = materialPredicate(defaultSnapshot);
        List<AEItemKey> placementOrder = defaultPredicate.candidates().stream()
                .map(candidate -> candidate.placementKey().orElseThrow())
                .toList();
        List<BlockState> stateOrder = defaultPredicate.candidates().stream()
                .map(candidate -> candidate.state().orElseThrow())
                .toList();
        helper.assertValueEqual(placementOrder, List.of(goldKey, ironKey),
                "Candidate order must retain the explicit pair declaration");
        helper.assertValueEqual(
                stateOrder,
                List.of(Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState()),
                "Render states must remain attached to their explicit placement pairs");
        helper.assertValueEqual(defaultSnapshot.materials(), List.of(new PreviewMaterial(goldKey, 1L)),
                "The first explicitly paired candidate must be selected by default");

        StructurePreviewSnapshot overriddenSnapshot = projection.project(
                spec,
                initial.withCandidate(MATERIAL_PREDICATE, 1));
        PreviewPredicateSnapshot overriddenPredicate = materialPredicate(overriddenSnapshot);
        helper.assertValueEqual(
                overriddenPredicate.selectedCandidate().orElseThrow().placementKey().orElseThrow(),
                ironKey,
                "A candidate override must select the requested paired placement item");
        helper.assertValueEqual(overriddenSnapshot.materials(), List.of(new PreviewMaterial(ironKey, 1L)),
                "A candidate override must update the projected material");
        helper.succeed();
    }

    @TestHolder("structure_preview_projection_aggregates_component_aware_materials_in_stable_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void aggregatesComponentAwareMaterialsInStableOrder(GameTestHelper helper) {
        ItemStack alphaStack = namedDiamond("alpha");
        ItemStack betaStack = namedDiamond("beta");
        AEItemKey alphaKey = itemKey(alphaStack);
        AEItemKey betaKey = itemKey(betaStack);
        BlockPattern pattern = FactoryBlockPattern.start()
                .aisle("~AAB")
                .where('A', candidatePredicate(List.of(candidate(Blocks.DIAMOND_BLOCK, alphaStack))))
                .where('B', candidatePredicate(List.of(candidate(Blocks.DIAMOND_BLOCK, betaStack))))
                .build();
        MultiblockPreviewSpec spec = spec(
                pattern,
                itemKey(Blocks.DIAMOND_BLOCK),
                List.of(),
                new SubstructureSelection(List.of(1), Map.of(), Map.of()));

        StructurePreviewSnapshot snapshot = new StructurePreviewProjectionImpl()
                .project(spec, PreviewSelection.initial(spec));

        helper.assertFalse(alphaKey.equals(betaKey),
                "Different custom-name components must produce different material identities");
        helper.assertValueEqual(
                snapshot.materials(),
                List.of(new PreviewMaterial(alphaKey, 2L), new PreviewMaterial(betaKey, 1L)),
                "Equal item and component identities must aggregate in first-occurrence order");
        helper.succeed();
    }

    @TestHolder("structure_preview_projection_rejects_invalid_and_ambiguous_candidate_selections")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsInvalidAndAmbiguousCandidateSelections(GameTestHelper helper) {
        StructurePreviewProjection projection = new StructurePreviewProjectionImpl();
        MultiblockPreviewSpec orderedSpec = spec(
                oneMaterialPattern(candidatePredicate(List.of(
                        candidate(Blocks.GOLD_BLOCK, new ItemStack(Blocks.GOLD_BLOCK)),
                        candidate(Blocks.IRON_BLOCK, new ItemStack(Blocks.IRON_BLOCK))))),
                itemKey(Blocks.DIAMOND_BLOCK),
                List.of(),
                new SubstructureSelection(List.of(1), Map.of(), Map.of()));
        PreviewSelection invalidOverride = PreviewSelection.initial(orderedSpec)
                .withCandidate(MATERIAL_PREDICATE, 2);
        assertIllegalState(
                helper,
                () -> projection.project(orderedSpec, invalidOverride),
                "An out-of-range candidate override must fail fast");

        MultiblockPreviewSpec ambiguousSpec = spec(
                oneMaterialPattern(legacyCandidatePredicate(
                        List.of(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK),
                        List.of(new ItemStack(Blocks.DIAMOND_BLOCK), new ItemStack(Blocks.EMERALD_BLOCK)))),
                itemKey(Blocks.DIAMOND_BLOCK),
                List.of(),
                new SubstructureSelection(List.of(1), Map.of(), Map.of()));
        assertIllegalState(
                helper,
                () -> projection.project(ambiguousSpec, PreviewSelection.initial(ambiguousSpec)),
                "An ambiguous many-to-many state and placement mapping must fail fast");
        helper.succeed();
    }

    private static MultiblockPreviewSpec spec(BlockPattern pattern,
                                              AEItemKey ownerKey,
                                              List<PreviewTierDomain> tierDomains,
                                              SubstructureSelection defaults) {
        ResolvedJsonMultiBlockDefinition definition = new ResolvedJsonMultiBlockDefinition(
                new JsonMultiBlockStructureKey(CONTROLLER_ID, SUBSTRUCTURE_ID),
                pattern);
        SubstructurePreviewSpec substructure = new SubstructurePreviewSpec(
                definition,
                Component.literal("Main"),
                tierDomains,
                defaults);
        return new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Projection test"),
                ownerKey,
                DEFINITION_REVISION,
                List.of(substructure));
    }

    private static ResolvedJsonMultiBlockDefinition definition(BlockPattern pattern) {
        return new ResolvedJsonMultiBlockDefinition(
                new JsonMultiBlockStructureKey(CONTROLLER_ID, SUBSTRUCTURE_ID),
                pattern);
    }

    private static BlockPattern repeatAndTierPattern() {
        return FactoryBlockPattern.start()
                .aisle("~S")
                .beginRepeatable()
                .aisle("T ")
                .endRepeatable(1, 2)
                .where('S', Predicates.blocks(Blocks.DIAMOND_BLOCK))
                .where('T', Predicates.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK))
                .build();
    }

    private static BlockPattern oneMaterialPattern(TraceabilityPredicate materialPredicate) {
        return FactoryBlockPattern.start()
                .aisle("~A")
                .where('A', materialPredicate)
                .build();
    }

    private static TraceabilityPredicate candidatePredicate(List<PatternCandidate> candidates) {
        return new TraceabilityPredicate(Predicates.custom(state -> true, candidates));
    }

    private static TraceabilityPredicate legacyCandidatePredicate(List<Block> stateBlocks,
                                                                  List<ItemStack> placementStacks) {
        return new TraceabilityPredicate(Predicates.custom(state -> true, () -> stateBlocks, () -> placementStacks));
    }

    private static PatternCandidate candidate(Block stateBlock, ItemStack placementStack) {
        return new PatternCandidate(stateBlock.defaultBlockState(), placementStack);
    }

    private static PreviewTierOption tierOption(int value, String blockId) {
        return new PreviewTierOption(
                value,
                Component.literal("Tier " + value),
                ResourceLocation.parse(blockId));
    }

    private static ItemStack namedDiamond(String name) {
        ItemStack stack = new ItemStack(Blocks.DIAMOND_BLOCK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static AEItemKey itemKey(Block block) {
        AEItemKey key = AEItemKey.of(block);
        if (key == null) {
            throw new IllegalStateException("Test block has no item key: " + block);
        }
        return key;
    }

    private static AEItemKey itemKey(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalStateException("Test stack has no item key: " + stack);
        }
        return key;
    }

    private static long amountOf(StructurePreviewSnapshot snapshot, AEItemKey key) {
        return amountOf(snapshot.materials(), key);
    }

    private static long amountOf(List<PreviewMaterial> materials, AEItemKey key) {
        return materials.stream()
                .filter(material -> material.key().equals(key))
                .mapToLong(PreviewMaterial::amount)
                .sum();
    }

    private static PreviewCellSnapshot controllerCell(StructurePreviewSnapshot snapshot) {
        return snapshot.cells().stream()
                .filter(cell -> cell.relativePosition().equals(BlockPos.ZERO))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing projected controller cell"));
    }

    private static PreviewPredicateSnapshot materialPredicate(StructurePreviewSnapshot snapshot) {
        return snapshot.cells().stream()
                .map(PreviewCellSnapshot::predicate)
                .filter(predicate -> predicate.key().equals(MATERIAL_PREDICATE))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing projected predicate " + MATERIAL_PREDICATE));
    }

    private static void assertIllegalState(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    message + " and explain the invalid projection");
            return;
        }
        helper.fail(message);
    }
}
