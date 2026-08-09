package com.fish_dan_.data_energistics.common.multiblock.preview;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.ResolvedJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructureSelection;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.Predicates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class MultiblockPreviewModelGameTest {

    private static final ResourceLocation CONTROLLER_ID = ResourceLocation.parse("data_energistics:test_controller");
    private static final PreviewPredicateKey REPEATED_PREDICATE = new PreviewPredicateKey(1, 0, 0);

    private MultiblockPreviewModelGameTest() {}

    @TestHolder("multiblock_preview_spec_derives_repeat_ranges_and_normalizes_tier_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void substructureSpecDerivesRepeatRangesAndNormalizesTierOrder(GameTestHelper helper) {
        PreviewTierDomain casing = tierDomain("casing", 1);
        PreviewTierDomain core = tierDomain("core", 2);
        Map<String, Integer> reversedTiers = new LinkedHashMap<>();
        reversedTiers.put("core", 2);
        reversedTiers.put("casing", 1);
        SubstructureSelection defaults = new SubstructureSelection(
                List.of(1, 2, 1),
                reversedTiers,
                Map.of(REPEATED_PREDICATE, 0));

        SubstructurePreviewSpec spec = new SubstructurePreviewSpec(
                definition(CONTROLLER_ID, "main"),
                Component.literal("Main"),
                List.of(casing, core),
                defaults);

        helper.assertValueEqual(spec.id(), "main", "The structure id must come from the definition key");
        helper.assertValueEqual(spec.variantIndexes(), List.of(0),
                "A single-shape structure must still expose the explicit variant-zero domain");
        helper.assertValueEqual(spec.defaults().variantIndex(), 0,
                "A single-shape structure must explicitly default to variant zero");
        helper.assertValueEqual(
                List.copyOf(spec.defaults().tierSelections().keySet()),
                List.of("casing", "core"),
                "Default tiers must follow domain order");
        helper.assertValueEqual(spec.repeatRanges().size(), 3, "Every pattern segment must expose a repeat range");
        helper.assertValueEqual(spec.repeatRanges().get(0).min(), 1, "The leading segment must be fixed");
        helper.assertValueEqual(spec.repeatRanges().get(1).min(), 1, "The repeatable segment minimum must be retained");
        helper.assertValueEqual(spec.repeatRanges().get(1).max(), 3, "The repeatable segment maximum must be retained");
        helper.assertValueEqual(spec.repeatRanges().get(2).max(), 1, "The trailing segment must be fixed");
        helper.assertValueEqual(spec.tierDomain("core"), core, "Tier lookup must return the declared domain");
        assertUnsupportedOperation(
                helper,
                () -> spec.defaults().tierSelections().put("other", 1),
                "Normalized default tiers must be immutable");
        helper.succeed();
    }

    @TestHolder("multiblock_preview_spec_rejects_selections_outside_definition_domains")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void substructureSpecRejectsSelectionsOutsideDefinitionDomains(GameTestHelper helper) {
        PreviewTierDomain domain = tierDomain("core", 1);
        ResolvedJsonMultiBlockDefinition definition = definition(CONTROLLER_ID, "main");

        assertIllegalArgument(helper, () -> new SubstructurePreviewSpec(
                definition,
                Component.literal("Main"),
                List.of(domain),
                new SubstructureSelection(1, List.of(1, 1, 1), Map.of("core", 1), Map.of())),
                "Variant defaults must stay within the declared domain");
        assertIllegalArgument(helper, () -> new SubstructurePreviewSpec(
                definition,
                Component.literal("Main"),
                List.of(domain),
                new SubstructureSelection(List.of(1, 1), Map.of("core", 1), Map.of())),
                "Repeat selections must match the pattern segment count");
        assertIllegalArgument(helper, () -> new SubstructurePreviewSpec(
                definition,
                Component.literal("Main"),
                List.of(domain),
                new SubstructureSelection(List.of(1, 4, 1), Map.of("core", 1), Map.of())),
                "Repeat selections must stay within the pattern range");
        assertIllegalArgument(helper, () -> new SubstructurePreviewSpec(
                definition,
                Component.literal("Main"),
                List.of(domain),
                new SubstructureSelection(List.of(1, 1, 1), Map.of(), Map.of())),
                "Every declared tier must have a default selection");
        assertIllegalArgument(helper, () -> new SubstructurePreviewSpec(
                definition,
                Component.literal("Main"),
                List.of(domain),
                new SubstructureSelection(List.of(1, 1, 1), Map.of("core", 3), Map.of())),
                "Tier defaults must stay within the declared domain");
        assertIllegalArgument(helper, () -> new SubstructurePreviewSpec(
                definition,
                Component.literal("Main"),
                List.of(domain),
                new SubstructureSelection(
                        List.of(1, 1, 1),
                        Map.of("core", 1),
                        Map.of(new PreviewPredicateKey(3, 0, 0), 0))),
                "Candidate defaults must address a pattern predicate");
        assertIllegalArgument(helper, () -> new SubstructureSelection(
                List.of(1, 1, 1),
                Map.of("core", 1),
                Map.of(REPEATED_PREDICATE, -1)),
                "Candidate indexes must be non-negative");
        helper.succeed();
    }

    @TestHolder("multiblock_preview_spec_preserves_substructure_order_and_validates_ownership")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void multiblockSpecPreservesSubstructureOrderAndValidatesOwnership(GameTestHelper helper) {
        SubstructurePreviewSpec main = substructure(CONTROLLER_ID, "main", 1);
        SubstructurePreviewSpec cpu = substructure(CONTROLLER_ID, "cpu", 2);
        List<SubstructurePreviewSpec> mutableSubstructures = new ArrayList<>(List.of(main, cpu));
        AEItemKey ownerKey = AEItemKey.of(Items.CRAFTING_TABLE);

        MultiblockPreviewSpec spec = new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Controller"),
                ownerKey,
                7L,
                mutableSubstructures);
        mutableSubstructures.clear();

        helper.assertValueEqual(
                spec.substructures().stream().map(SubstructurePreviewSpec::id).toList(),
                List.of("main", "cpu"),
                "Substructures must retain declaration order and copy caller storage");
        helper.assertValueEqual(spec.substructure("cpu"), cpu, "Substructure lookup must return the matching spec");
        helper.assertValueEqual(spec.definitionRevision(), 7L, "The definition revision must be retained");
        assertUnsupportedOperation(
                helper,
                () -> spec.substructures().add(main),
                "Published substructures must be immutable");
        assertIllegalArgument(helper, () -> spec.substructure("missing"), "Unknown substructure ids must be rejected");
        assertIllegalArgument(helper, () -> new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Controller"),
                ownerKey,
                -1L,
                List.of(main)), "Definition revisions must be non-negative");
        assertIllegalArgument(helper, () -> new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Controller"),
                ownerKey,
                7L,
                List.of(main, main)), "Duplicate substructure ids must be rejected");
        assertIllegalArgument(helper, () -> new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Controller"),
                ownerKey,
                7L,
                List.of(substructure(ResourceLocation.parse("data_energistics:other"), "other", 1))),
                "Every substructure must belong to the preview controller");
        helper.succeed();
    }

    @TestHolder("preview_selection_initial_uses_spec_order_and_immutable_defaults")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void initialSelectionUsesSpecOrderAndImmutableDefaults(GameTestHelper helper) {
        MultiblockPreviewSpec spec = spec(CONTROLLER_ID, 4L, List.of("main", "cpu"));

        PreviewSelection selection = PreviewSelection.initial(spec);
        PreviewSelection equivalent = PreviewSelection.initial(spec);

        helper.assertValueEqual(selection.controllerId(), CONTROLLER_ID, "The selection must retain its controller id");
        helper.assertValueEqual(selection.definitionRevision(), 4L, "The selection must retain its revision");
        helper.assertValueEqual(selection.activeSubstructureId(), "main", "The first substructure must be active");
        helper.assertValueEqual(selection, equivalent, "Equivalent initial selections must compare equal");
        helper.assertValueEqual(selection.hashCode(), equivalent.hashCode(), "Equal selections must share a hash code");
        helper.assertValueEqual(
                List.copyOf(selection.substructureSelections().keySet()),
                List.of("main", "cpu"),
                "Selections must follow spec order");
        helper.assertValueEqual(
                selection.selection("main").repeatCounts(),
                List.of(1, 1, 1),
                "Main must use its repeat defaults");
        helper.assertValueEqual(
                selection.selection("cpu").repeatCounts(),
                List.of(1, 2, 1),
                "CPU must use its independent repeat defaults");
        assertUnsupportedOperation(
                helper,
                () -> selection.substructureSelections().put("other", selection.activeSelection()),
                "Substructure selections must be immutable");
        assertUnsupportedOperation(
                helper,
                () -> selection.activeSelection().repeatCounts().add(2),
                "Repeat selections must be immutable");
        helper.succeed();
    }

    @TestHolder("preview_selection_updates_only_active_substructure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void updatesCopyOnlyTheActiveSubstructureAndSwitchingRetainsIndependentState(GameTestHelper helper) {
        MultiblockPreviewSpec spec = variantSpec(CONTROLLER_ID, 4L, List.of("main", "cpu"));
        PreviewSelection initial = PreviewSelection.initial(spec);

        PreviewSelection updatedCpu = initial.select("cpu")
                .withVariantIndex(1)
                .withRepeat(1, 3)
                .withTier("core", 2)
                .withCandidate(REPEATED_PREDICATE, 1);
        PreviewSelection updatedMain = updatedCpu.select("main")
                .withVariantIndex(1)
                .withTier("core", 2);
        PreviewSelection returnedCpu = updatedMain.select("cpu");

        helper.assertTrue(initial != updatedCpu, "An update must return a new selection instance");
        helper.assertFalse(initial.equals(updatedCpu), "An updated selection must not equal its source");
        helper.assertValueEqual(
                initial.selection("main").repeatCounts(),
                List.of(1, 1, 1),
                "Updating CPU must not alter initial main state");
        helper.assertValueEqual(
                initial.selection("cpu").repeatCounts(),
                List.of(1, 2, 1),
                "Updating CPU must not alter initial CPU state");
        helper.assertValueEqual(
                initial.selection("cpu").tierSelections().get("core"),
                1,
                "Initial CPU tier must remain unchanged");
        helper.assertValueEqual(
                returnedCpu.activeSelection().variantIndex(),
                1,
                "Switching away and back must retain the CPU shape variant");
        helper.assertValueEqual(
                returnedCpu.activeSelection().repeatCounts(),
                List.of(1, 3, 1),
                "Switching away and back must retain CPU repeats");
        helper.assertValueEqual(
                returnedCpu.activeSelection().tierSelections().get("core"),
                2,
                "Switching away and back must retain CPU tiers");
        helper.assertValueEqual(
                returnedCpu.activeSelection().candidateSelections().get(REPEATED_PREDICATE),
                1,
                "Switching away and back must retain CPU candidate overrides");
        helper.assertValueEqual(
                returnedCpu.selection("main").variantIndex(),
                1,
                "Main must retain its independent shape variant");
        helper.assertValueEqual(
                returnedCpu.selection("main").tierSelections().get("core"),
                2,
                "Main must retain its independent tier update");
        helper.succeed();
    }

    @TestHolder("preview_selection_migrates_repeats_across_different_variant_layouts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void migratesRepeatsAcrossDifferentVariantLayouts(GameTestHelper helper) {
        PreviewSelection initial = PreviewSelection.initial(variantMigrationSpec());
        PreviewSelection target = initial.withVariantIndex(1);
        PreviewSelection returned = target.withVariantIndex(0);

        helper.assertValueEqual(initial.activeSelection().repeatCounts(), List.of(1, 3, 1),
                "Variant zero must start with its three-unit repeat selection");
        helper.assertValueEqual(target.activeSelection().repeatCounts(), List.of(1, 2),
                "An incompatible target repeat must fall back to its minimum and removed units must be dropped");
        helper.assertValueEqual(returned.activeSelection().repeatCounts(), List.of(1, 2, 1),
                "Returning to a longer layout must preserve legal repeats and initialize new units from their minimum");
        helper.succeed();
    }

    @TestHolder("preview_selection_clears_shape_local_candidates_when_switching_variant")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void clearsShapeLocalCandidatesAndPreservesTier(GameTestHelper helper) {
        PreviewSelection configured = PreviewSelection.initial(variantMigrationSpec())
                .withTier("core", 2)
                .withCandidate(REPEATED_PREDICATE, 0);
        PreviewSelection target = configured.withVariantIndex(1);

        helper.assertValueEqual(configured.activeSelection().candidateSelections().get(REPEATED_PREDICATE), 0,
                "The source variant must retain its explicit candidate override before switching");
        helper.assertValueEqual(target.activeSelection().candidateSelections(), Map.of(),
                "Candidate coordinates and indexes from another shape must not leak into the target variant");
        helper.assertValueEqual(target.activeSelection().tierSelections().get("core"), 2,
                "Variant migration must preserve independent tier selection");
        helper.assertTrue(target.withVariantIndex(1) == target,
                "Selecting the active variant again must be an idempotent no-op");
        helper.succeed();
    }

    @TestHolder("preview_selection_rejects_unknown_or_out_of_range_values")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void updatesRejectUnknownOrOutOfRangeValues(GameTestHelper helper) {
        PreviewSelection selection = PreviewSelection.initial(spec(CONTROLLER_ID, 4L, List.of("main", "cpu")));

        assertIllegalArgument(helper, () -> selection.select("missing"), "Unknown active structures must be rejected");
        assertIllegalArgument(helper, () -> selection.withVariantIndex(-1),
                "Negative variant indexes must be rejected");
        assertIllegalArgument(helper, () -> selection.withVariantIndex(1),
                "Out-of-range variant indexes must be rejected");
        assertIllegalArgument(helper, () -> selection.withRepeat(-1, 1), "Negative repeat indexes must be rejected");
        assertIllegalArgument(helper, () -> selection.withRepeat(3, 1), "Out-of-range repeat indexes must be rejected");
        assertIllegalArgument(helper, () -> selection.withRepeat(1, 4), "Out-of-range repeat counts must be rejected");
        assertIllegalArgument(helper, () -> selection.withTier("missing", 1), "Unknown tier domains must be rejected");
        assertIllegalArgument(helper, () -> selection.withTier("core", 3), "Out-of-range tiers must be rejected");
        assertIllegalArgument(
                helper,
                () -> selection.withCandidate(REPEATED_PREDICATE, -1),
                "Negative candidate indexes must be rejected");
        assertIllegalArgument(
                helper,
                () -> selection.withCandidate(new PreviewPredicateKey(3, 0, 0), 0),
                "Unknown predicate positions must be rejected");
        assertIllegalArgument(helper, () -> selection.selection("missing"), "Unknown selection lookups must be rejected");
        helper.succeed();
    }

    @TestHolder("preview_selection_validates_controller_revision_and_substructure_spec")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void validationRejectsControllerRevisionAndSubstructureSpecMismatch(GameTestHelper helper) {
        MultiblockPreviewSpec original = spec(CONTROLLER_ID, 4L, List.of("main", "cpu"));
        PreviewSelection selection = PreviewSelection.initial(original);
        MultiblockPreviewSpec wrongController = spec(
                ResourceLocation.parse("data_energistics:other_controller"),
                4L,
                List.of("main", "cpu"));
        MultiblockPreviewSpec wrongRevision = spec(CONTROLLER_ID, 5L, List.of("main", "cpu"));
        MultiblockPreviewSpec reversedSubstructures = spec(CONTROLLER_ID, 4L, List.of("cpu", "main"));

        selection.validateAgainst(original);
        assertIllegalArgument(
                helper,
                () -> selection.validateAgainst(wrongController),
                "Selections must reject a different controller");
        assertIllegalArgument(
                helper,
                () -> selection.validateAgainst(wrongRevision),
                "Selections must reject a different definition revision");
        assertIllegalArgument(
                helper,
                () -> selection.validateAgainst(reversedSubstructures),
                "Selections must reject a different ordered substructure contract");
        helper.succeed();
    }

    private static MultiblockPreviewSpec spec(ResourceLocation controllerId,
                                              long revision,
                                              List<String> substructureIds) {
        List<SubstructurePreviewSpec> substructures = new ArrayList<>();
        for (String id : substructureIds) {
            int defaultRepeat = "cpu".equals(id) ? 2 : 1;
            substructures.add(substructure(controllerId, id, defaultRepeat));
        }
        return new MultiblockPreviewSpec(
                controllerId,
                Component.literal("Controller"),
                AEItemKey.of(Items.CRAFTING_TABLE),
                revision,
                substructures);
    }

    private static MultiblockPreviewSpec variantSpec(ResourceLocation controllerId,
                                                     long revision,
                                                     List<String> substructureIds) {
        List<SubstructurePreviewSpec> substructures = new ArrayList<>();
        for (String id : substructureIds) {
            int defaultRepeat = "cpu".equals(id) ? 2 : 1;
            SubstructurePreviewSpec base = substructure(controllerId, id, defaultRepeat);
            substructures.add(new SubstructurePreviewSpec(
                    List.of(base.definition(), base.definition()),
                    base.title(),
                    base.tierDomains(),
                    base.defaults()));
        }
        return new MultiblockPreviewSpec(
                controllerId,
                Component.literal("Controller"),
                AEItemKey.of(Items.CRAFTING_TABLE),
                revision,
                substructures);
    }

    private static MultiblockPreviewSpec variantMigrationSpec() {
        PreviewTierDomain core = tierDomain("core", 1);
        JsonMultiBlockStructureKey key = new JsonMultiBlockStructureKey(CONTROLLER_ID, "main");
        SubstructurePreviewSpec substructure = new SubstructurePreviewSpec(
                List.of(
                        new ResolvedJsonMultiBlockDefinition(key, repeatedPattern()),
                        new ResolvedJsonMultiBlockDefinition(key, shorterVariantPattern())),
                Component.literal("main"),
                List.of(core),
                new SubstructureSelection(
                        0,
                        List.of(1, 3, 1),
                        Map.of("core", core.defaultValue()),
                        Map.of()));
        return new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("Controller"),
                AEItemKey.of(Items.CRAFTING_TABLE),
                4L,
                List.of(substructure));
    }

    private static SubstructurePreviewSpec substructure(ResourceLocation controllerId,
                                                        String id,
                                                        int defaultRepeat) {
        PreviewTierDomain core = tierDomain("core", 1);
        return new SubstructurePreviewSpec(
                definition(controllerId, id),
                Component.literal(id),
                List.of(core),
                new SubstructureSelection(
                        List.of(1, defaultRepeat, 1),
                        Map.of("core", core.defaultValue()),
                        Map.of()));
    }

    private static PreviewTierDomain tierDomain(String id, int defaultValue) {
        return new PreviewTierDomain(
                id,
                Component.literal(id),
                List.of(
                        tierOption(1, "minecraft:iron_block"),
                        tierOption(2, "minecraft:gold_block")),
                defaultValue);
    }

    private static PreviewTierOption tierOption(int value, String blockId) {
        return new PreviewTierOption(value, Component.literal("tier " + value), ResourceLocation.parse(blockId));
    }

    private static ResolvedJsonMultiBlockDefinition definition(ResourceLocation controllerId, String id) {
        return new ResolvedJsonMultiBlockDefinition(
                new JsonMultiBlockStructureKey(controllerId, id),
                repeatedPattern());
    }

    private static BlockPattern repeatedPattern() {
        return FactoryBlockPattern.start()
                .aisle("~")
                .beginRepeatable()
                .aisle("X")
                .endRepeatable(1, 3)
                .aisle("Y")
                .where('~', Predicates.any())
                .where('X', Predicates.blocks(Blocks.IRON_BLOCK))
                .where('Y', Predicates.blocks(Blocks.GOLD_BLOCK))
                .build();
    }

    private static BlockPattern shorterVariantPattern() {
        return FactoryBlockPattern.start()
                .aisle("~Z")
                .beginRepeatable()
                .aisle("XX")
                .endRepeatable(2, 2)
                .where('Z', Predicates.blocks(Blocks.GOLD_BLOCK))
                .where('X', Predicates.blocks(Blocks.IRON_BLOCK))
                .build();
    }

    private static void assertIllegalArgument(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(
                    exception.getMessage() != null && !exception.getMessage().isBlank(),
                    message + " and explain why");
            return;
        }
        helper.fail(message);
    }

    private static void assertUnsupportedOperation(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (UnsupportedOperationException exception) {
            return;
        }
        helper.fail(message);
    }
}
