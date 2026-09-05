package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Input;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Tool;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolution;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityPatternCoreTier;
import com.fish_dan_.data_energistics.common.trinity.pattern.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.pattern.PersistentTrinityPatternCore;
import com.fish_dan_.data_energistics.common.trinity.pattern.RoutedCraftingPatternDetails;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.me.helpers.BaseActionSource;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityReusableCraftingHostGameTest {

    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "native_reusable_fixture");
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey PRODUCT = AEItemKey.of(Items.IRON_NUGGET);
    private static final AEItemKey SCRAP = AEItemKey.of(Items.STICK);

    private TrinityReusableCraftingHostGameTest() {}

    @TestHolder("trinity_reusable_core_native_remainders_and_work_index_survive_core_reload")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void nativeRemaindersAndWorkIndexSurviveCoreReload(GameTestHelper helper) {
        NativePattern pattern = new NativePattern();
        PersistentTrinityPatternCore core = core(pattern);
        helper.assertTrue(core.trySetPattern(0, pattern.getDefinition().toStack()), "Native fixture pattern installs through the real core API");
        PatternRoute route = new PatternRoute(UUID.randomUUID(), core.coreId(), 0);
        UUID session = UUID.randomUUID();
        TrinityReusableCraftingHost host = host(core, route, helper);
        admit(core, route, request(pattern, route, session, 4, 2, helper), host);
        helper.assertTrue(core.isSlotWorking(route.hostId(), 0), "Reusable-only work appears in the existing host slot index");
        core.reusableSlot(0).endpoint().tick(1, 2, host);
        helper.assertValueEqual(pattern.remainderCalls, 2, "Native getRemainingItems is called once per actual operation");
        CompoundTag saved = new CompoundTag();
        core.writeToTag(saved, helper.getLevel().registryAccess());
        PersistentTrinityPatternCore restored = core(pattern);
        restored.hydrateFromTag(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.isSlotWorking(route.hostId(), 0), "Reload reconstructs the combined work index");
        TrinityReusableCraftingHost resumed = host(restored, route, helper);
        restored.reusableSlot(0).endpoint().tick(2, 2, resumed);
        helper.assertValueEqual(pattern.remainderCalls, 4, "Native execution resumes from actual persisted tool damage");
        helper.assertValueEqual(restored.pendingOutputs(route).stream().filter(item -> item.key().equals(PRODUCT)).mapToLong(item -> item.amount()).sum(),
                4L, "Ordinary outputs use the existing core pending-output route");
        helper.assertValueEqual(restored.pendingOutputs(route).stream().filter(item -> item.key().equals(SCRAP)).mapToLong(item -> item.amount()).sum(),
                1L, "Only the actual exhausted tool produced scrap");
        helper.assertTrue(restored.pendingOutputs(route).stream().noneMatch(item -> item.key().getItem() == Items.IRON_AXE),
                "Resident tools never enter ordinary pending outputs");
        restored.reusableSlot(0).endpoint().close(session, resumed);
        List<Settlement> received = new ObjectArrayList<>();
        restored.reusableSlot(0).endpoint().settle(session, settlement -> received.add(settlement), resumed);
        helper.assertValueEqual(received.getFirst().returnedAssets(), List.of(new GenericStack(tool(1), 1)),
                "Four actual uses return the surviving D1 physical tool");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_core_mining_state_keeps_tools_and_unused_materials_for_directed_settlement")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void miningStateKeepsToolsAndUnusedMaterialsForDirectedSettlement(GameTestHelper helper) {
        NativePattern pattern = new NativePattern();
        PersistentTrinityPatternCore core = core(pattern);
        core.trySetPattern(0, pattern.getDefinition().toStack());
        PatternRoute route = new PatternRoute(UUID.randomUUID(), core.coreId(), 0);
        UUID session = UUID.randomUUID();
        TrinityReusableCraftingHost host = host(core, route, helper);
        admit(core, route, request(pattern, route, session, 2, 1, helper), host);
        core.reusableSlot(0).endpoint().tick(1, 1, host);
        CompoundTag miningItem = new CompoundTag();
        core.writeRetainedWorkToTag(miningItem, helper.getLevel().registryAccess());
        PersistentTrinityPatternCore moved = core(pattern);
        moved.hydrateFromTag(miningItem, helper.getLevel().registryAccess());
        helper.assertTrue(moved.pattern(0).isEmpty(), "Mined core state omits independently dropped patterns");
        TrinityReusableSlot resident = moved.reusableSlot(0);
        helper.assertTrue(resident.closeRequested(), "Restoring a mined pattern slot requests safe session closure");
        TrinityReusableCraftingHost movedHost = host(moved, route, helper);
        resident.closeSessions(movedHost);
        List<Settlement> received = new ObjectArrayList<>();
        helper.assertTrue(resident.endpoint().settle(session, settlement -> received.add(settlement), movedHost),
                "Old session settles without requiring the removed pattern to publish again");
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), MATERIAL), 1L, "Only actual unused material is returned");
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), tool(1)), 1L, "Core movement preserves actual tool damage");
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), tool(0)), 0L, "Movement cannot reconstruct the original tool");
        helper.assertValueEqual(received.getFirst().receipts().getFirst().cancelled(), 1L, "Unexecuted remainder is explicitly cancelled");
        helper.succeed();
    }

    @TestHolder("trinity_native_recipe_validation_allows_declared_tool_components_but_preserves_ordinary_no_substitution")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void nativeRecipeValidationAllowsDeclaredToolComponentsButPreservesOrdinaryNoSubstitution(GameTestHelper helper) {
        ResourceLocation recipeId = ResourceLocation.withDefaultNamespace("crafting_table");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElseThrow();
        if (!(holder.value() instanceof CraftingRecipe recipe)) {
            throw new IllegalStateException("Vanilla crafting table fixture is not a crafting recipe");
        }
        ItemStack[] inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);
        for (int slot : new int[] { 0, 1, 3, 4 }) {
            inputs[slot] = new ItemStack(Items.OAK_PLANKS);
        }
        ItemStack encoded = AEItems.CRAFTING_PATTERN.stack();
        AECraftingPattern.encode(encoded, new RecipeHolder<>(recipeId, recipe), inputs, new ItemStack(Items.CRAFTING_TABLE), false, false);
        AECraftingPattern pattern = new AECraftingPattern(AEItemKey.of(encoded), helper.getLevel());
        ItemStack changed = new ItemStack(Items.OAK_PLANKS);
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("actual successor component"));
        List<GenericStack> exact = List.of(new GenericStack(AEItemKey.of(changed), 4));
        helper.assertTrue(!pattern.getInputs()[0].isValid(exact.getFirst().what(), helper.getLevel()), "Encoded pattern rejects changed exact components");
        helper.assertTrue(!NativeReusableCrafting.matches(pattern, exact, IntSets.emptySet(), Optional.of(recipeId), helper.getLevel()),
                "Ordinary forbidden substitutions must remain rejected");
        helper.assertTrue(NativeReusableCrafting.matches(pattern, exact, new IntOpenHashSet(new int[] { 0 }), Optional.of(recipeId), helper.getLevel()),
                "Explicit tool slot is validated through the real recipe with actual successor components");
        List<GenericStack> invalid = List.of(new GenericStack(AEItemKey.of(Items.STONE), 4));
        helper.assertTrue(!NativeReusableCrafting.matches(pattern, invalid, new IntOpenHashSet(new int[] { 0 }), Optional.of(recipeId), helper.getLevel()),
                "Declaring a reusable slot cannot bypass the native recipe's real ingredient checks");
        helper.succeed();
    }

    private static PersistentTrinityPatternCore core(NativePattern pattern) {
        return new PersistentTrinityPatternCore(TrinityPatternCoreTier.STANDARD.patternCapacity(),
                stack -> !stack.isEmpty() && AEItemKey.of(stack).equals(pattern.getDefinition()) ? pattern : null,
                ignored -> Optional.of(new TrinityPatternRecipeIdResolution(RECIPE, RECIPE)), ignored -> {});
    }

    private static TrinityReusableCraftingHost host(PersistentTrinityPatternCore core, PatternRoute route, GameTestHelper helper) {
        return new TrinityReusableCraftingHost(core, route, helper.getLevel(), () -> true);
    }

    private static ReusableCraftingRequest request(NativePattern pattern, PatternRoute route, UUID session, long operations,
                                                   long tools, GameTestHelper helper) {
        String target = TrinityReusableSlot.targetIdentity(route.coreId(), route.slot());
        ReusableInputRule rule = ReusableInputRule.fixedDamage(RECIPE, 1, tool(0), 1, 3, List.of(new GenericStack(SCRAP, 1)));
        return new ReusableCraftingRequest(session, UUID.randomUUID(), "cpu:core-host-test", 0,
                new Target(target, CountedCraftingTarget.route(target), Optional.empty()), new RoutedCraftingPatternDetails(route, pattern),
                List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.CPU_SUPPLIED, rule, Optional.empty()))),
                        new Input(1, List.of(new GenericStack(MATERIAL, 1)), Optional.empty())),
                List.of(new SlotStack(0, new GenericStack(tool(0), tools))), operations, Optional.of(RECIPE), new BaseActionSource(), helper.getLevel());
    }

    private static void admit(PersistentTrinityPatternCore core, PatternRoute route, ReusableCraftingRequest request, TrinityReusableCraftingHost host) {
        ReusableCraftingAdmission admission = core.prepareReusable(route, request, 0, host);
        if (admission == null) {
            throw new IllegalStateException("Real core host rejected its native fixture");
        }
        KeyCounter[] delivery = { new KeyCounter(), new KeyCounter() };
        admission.physicalInputs().forEach(stack -> delivery[stack.slot()].add(stack.stack().what(), stack.stack().amount()));
        if (!admission.commit(delivery)) {
            throw new IllegalStateException("Real core host failed to accept its physical inputs");
        }
    }

    private static AEItemKey tool(int damage) {
        ItemStack tool = new ItemStack(Items.IRON_AXE);
        tool.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(tool);
    }

    private static long assetAmount(List<GenericStack> assets, AEKey key) {
        return assets.stream().filter(stack -> stack.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }

    /** Native callback fixture exercises the same production host and actual getRemainingItems path. */
    private static final class NativePattern implements IMolecularAssemblerSupportedPattern {

        private int remainderCalls;

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new ExactInput(tool(0)), new ExactInput(MATERIAL) };
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(PRODUCT, 1));
        }

        @Override
        public boolean isItemValid(int slot, AEItemKey key, Level level) {
            return slot == 0 ? key.getItem() == Items.IRON_AXE : key.equals(MATERIAL);
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return slot == 0 || slot == 1;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor sink) {
            for (int slot = 0; slot < 2; slot++) {
                for (var entry : table[slot]) {
                    if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key) {
                        sink.set(slot, key.toStack());
                        table[slot].remove(key, 1);
                        break;
                    }
                }
            }
        }

        @Override
        public ItemStack assemble(CraftingInput input, Level level) {
            return input.size() == 2 && input.getItem(0).is(Items.IRON_AXE) && input.getItem(1).is(Items.IRON_INGOT) ?
                    new ItemStack(Items.IRON_NUGGET) : ItemStack.EMPTY;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            remainderCalls++;
            NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            ItemStack actualTool = input.getItem(0).copy();
            int damage = actualTool.getDamageValue() + 1;
            if (damage == 3) {
                result.set(0, new ItemStack(Items.STICK));
            } else {
                actualTool.set(DataComponents.DAMAGE, damage);
                result.set(0, actualTool);
            }
            return result;
        }
    }

    private record ExactInput(AEItemKey key) implements IMolecularAssemblerSupportedPattern.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(key, 1) };
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey actual, Level level) {
            return actual instanceof AEItemKey item && item.getItem() == key.getItem();
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return key.getItem() == Items.IRON_AXE ? tool(1) : null;
        }
    }
}
