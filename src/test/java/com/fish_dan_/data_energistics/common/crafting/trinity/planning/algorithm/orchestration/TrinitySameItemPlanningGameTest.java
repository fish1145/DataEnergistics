package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.dynamic.EncodedPatternDynamicOutput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinitySameItemPlanningGameTest {

    private static final TrinityPlanningLimits LIMITS = new TrinityPlanningLimits(128, 1_024, 10_000, 10_000);

    private TrinitySameItemPlanningGameTest() {}

    @TestHolder("same_item_marker_connects_component_distinct_recipes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void connectsComponentDistinctRecipes(GameTestHelper helper) {
        AEItemKey producerOutput = namedPaper("producer");
        AEItemKey consumerInput = namedPaper("consumer");
        AEItemKey fuel = AEItemKey.of(Items.COAL);
        AEItemKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphSnapshot graph = graph(helper,
                processing(List.of(new GenericStack(fuel, 1L)), new GenericStack(producerOutput, 1L), true),
                processing(List.of(new GenericStack(consumerInput, 1L)), new GenericStack(target, 1L), false));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = plan(
                graph,
                target,
                TrinityPlanningInventory.finite(Map.of(fuel, BigInteger.ONE)));

        helper.assertTrue(result.successful(),
                "A marked processing output must connect to a downstream same-item input with different components");
        TrinityCraftingPlan plan = result.value();
        helper.assertTrue(plan.sameItemPolicy().allowsSameItem(consumerInput),
                "The marked primary output must authorise its registered item domain");
        helper.assertValueEqual(plan.sameItemPolicy().normalizeKey(consumerInput), producerOutput,
                "A non-target item domain must retain its deterministic marked representative");
        helper.succeed();
    }

    @TestHolder("same_item_planning_combines_distinct_inventory_variants")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void combinesDistinctInventoryVariants(GameTestHelper helper) {
        AEItemKey producerOutput = namedPaper("producer");
        AEItemKey consumerInput = namedPaper("consumer");
        AEItemKey firstStock = namedPaper("stock-a");
        AEItemKey secondStock = namedPaper("stock-b");
        AEItemKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphSnapshot graph = graph(helper,
                processing(List.of(new GenericStack(AEItemKey.of(Items.COAL), 1L)),
                        new GenericStack(producerOutput, 1L), true),
                processing(List.of(new GenericStack(consumerInput, 2L)), new GenericStack(target, 1L), false));
        TrinityPlanningInventory inventory = TrinityPlanningInventory.finite(Map.of(
                firstStock, BigInteger.ONE,
                secondStock, BigInteger.ONE));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = plan(graph, target, inventory);

        helper.assertTrue(result.successful(),
                "Different physical component variants must combine to satisfy one logical downstream demand");
        TrinityCraftingPlan plan = result.value();
        AEKey representative = plan.sameItemPolicy().normalizeKey(consumerInput);
        helper.assertValueEqual(plan.initialExpectedInputs().get(representative), BigInteger.valueOf(2L),
                "Initial inventory must be reserved once through the logical representative");
        helper.assertValueEqual(plan.initialExpectedInputs().size(), 1,
                "Physical inventory variants must not be duplicated in the executable plan");
        helper.succeed();
    }

    @TestHolder("same_item_unmarked_output_keeps_component_exactness")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsUnmarkedOutputExact(GameTestHelper helper) {
        AEItemKey producerOutput = namedPaper("producer");
        AEItemKey consumerInput = namedPaper("consumer");
        AEItemKey fuel = AEItemKey.of(Items.COAL);
        AEItemKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphSnapshot graph = graph(helper,
                processing(List.of(new GenericStack(fuel, 1L)), new GenericStack(producerOutput, 1L), false),
                processing(List.of(new GenericStack(consumerInput, 1L)), new GenericStack(target, 1L), false));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = plan(
                graph,
                target,
                TrinityPlanningInventory.finite(Map.of(fuel, BigInteger.ONE)));

        helper.assertTrue(!result.successful(),
                "Without the marker, component-distinct outputs and inputs must remain disconnected");
        helper.succeed();
    }

    private static TrinityAlgorithmResult<TrinityCraftingPlan> plan(TrinityCraftingGraphSnapshot graph,
                                                                    AEKey target,
                                                                    TrinityPlanningInventory inventory) {
        return TrinityGraphPlanner.create().plan(
                graph,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                inventory,
                LIMITS,
                TrinityPlanningControl.unbounded());
    }

    private static TrinityCraftingGraphSnapshot graph(GameTestHelper helper, IPatternDetails... patterns) {
        return new TrinityCraftingGraphSnapshot(1L, List.of(patterns).stream()
                .map(pattern -> capture(helper, pattern))
                .toList());
    }

    private static TrinityCraftingGraphPattern capture(GameTestHelper helper, IPatternDetails pattern) {
        TrinityPatternPublicationSignature publication = TrinityPatternPublicationSignature.capture(pattern);
        return new TrinityCraftingGraphPattern(
                TrinityPatternIdentity.capture(publication, helper.getLevel().registryAccess()),
                publication);
    }

    private static IPatternDetails processing(List<GenericStack> inputs, GenericStack output, boolean sameItem) {
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output));
        EncodedPatternDynamicOutput.apply(encoded, sameItem);
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }

    private static AEItemKey namedPaper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }
}
