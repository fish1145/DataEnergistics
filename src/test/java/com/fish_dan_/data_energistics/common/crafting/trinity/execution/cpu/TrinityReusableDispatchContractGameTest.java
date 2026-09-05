package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution.Work;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.OutputContract;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Submission;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSettlement;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityReusableDispatchContractGameTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000043");
    private static final ResourceLocation RULE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "dispatch_contract_tool");
    private static final AEItemKey PATTERN = AEItemKey.of(Items.CRAFTING_TABLE);
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey PRODUCT = AEItemKey.of(Items.IRON_NUGGET);
    private static final TrinityPatternIdentity PUBLICATION = new TrinityPatternIdentity("dispatch-definition", "dispatch-publication");
    private static final Target TARGET = new Target("dispatch-contract-native", CountedCraftingTarget.route("dispatch-contract-native"), Optional.empty());

    private TrinityReusableDispatchContractGameTest() {}

    @TestHolder("trinity_reusable_external_recipes_keep_fluid_material_validation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void externalRecipesKeepFluidMaterialValidation(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        List<TrinityBoundPatternInput> bindings = List.of(toolBinding(0, unchanged(), 1),
                new TrinityBoundPatternInput(1, 0, new GenericStack(water, 1000), 1, null));
        TestPattern pattern = new TestPattern(bindings);
        helper.assertTrue(NativeReusableCrafting.matches(pattern, List.of(stack(tool(0), 1), new GenericStack(water, 1000)),
                IntSet.of(0), Optional.empty(), helper.getLevel()), "External reusable providers may consume their validated fluid keys");
        helper.assertTrue(!NativeReusableCrafting.matches(pattern,
                List.of(stack(tool(0), 1), new GenericStack(AEFluidKey.of(Fluids.LAVA), 1000)), IntSet.of(0), Optional.empty(), helper.getLevel()),
                "Reusable authorization cannot bypass the ordinary fluid input validator");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_offer_shares_one_balance_for_consumed_and_held_identical_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void offerSharesOneBalanceForConsumedAndHeldIdenticalKeys(GameTestHelper helper) {
        List<TrinityBoundPatternInput> bindings = List.of(toolBinding(0, unchanged(), 1), ordinary(1, tool(0), 1));
        TrinityReusableRecipe recipe = recipe(bindings);
        KeyCounter available = inventory(tool(0), 1000);
        var offered = recipe.offer(1000, available, ignored -> 0);
        helper.assertValueEqual(offered.count(), 999L, "One physical tool leaves only 999 same-key material units");
        helper.assertValueEqual(amount(offered.addedTools(), tool(0)), 1L, "The shared balance reserves exactly one held unit");
        helper.assertValueEqual(available.get(tool(0)), 1000L, "Read-only offer calculation cannot consume real CPU inventory");
        var resident = recipe.offer(1000, available, ignored -> 1);
        helper.assertValueEqual(resident.count(), 1000L, "A separately resident tool permits all 1000 material units to be consumed");
        helper.assertTrue(resident.addedTools().isEmpty(), "Resident amount is not transferred again");
        helper.assertValueEqual(recipe.offer(1, new KeyCounter(), ignored -> 1).count(), 0L,
                "A resident tool cannot also pay for a missing ordinary same-key material");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_unchanged_offer_uses_one_tool_for_one_thousand_operations")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unchangedOfferUsesOneToolForOneThousandOperations(GameTestHelper helper) {
        TrinityReusableRecipe recipe = recipe(List.of(toolBinding(0, unchanged(), 1)));
        var offered = recipe.offer(1000, inventory(tool(0), 1), ignored -> 0);
        helper.assertValueEqual(offered.count(), 1000L, "One unchanged physical tool covers the complete 1000-operation offer");
        helper.assertValueEqual(offered.addedTools(), List.of(slot(0, tool(0), 1)), "Offer transfers one tool rather than count times the sample");
        var continuation = recipe.offer(1000, new KeyCounter(), ignored -> 1);
        helper.assertValueEqual(continuation.count(), 1000L, "Resident unchanged tool remains reusable across the next offer");
        helper.assertTrue(continuation.addedTools().isEmpty(), "Continuation does not claim a second CPU-owned tool");
        helper.assertValueEqual(recipe.offer(1000, new KeyCounter(), ignored -> 0).count(), 0L, "Missing actual startup tool rejects the offer");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_changing_offer_is_limited_by_exact_physical_state_units")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void changingOfferIsLimitedByExactPhysicalStateUnits(GameTestHelper helper) {
        TrinityReusableRecipe recipe = recipe(List.of(toolBinding(0, damageRule(0, 100), 2)));
        KeyCounter available = inventory(tool(0), 5);
        available.add(tool(1), 500);
        var offered = recipe.offer(1000, available, ignored -> 0);
        helper.assertValueEqual(offered.count(), 2L, "Five D0 units support only two operations requiring two D0 tools each");
        helper.assertValueEqual(amount(offered.addedTools(), tool(0)), 4L, "Remaining durability does not replace exact-state physical quantities");
        helper.assertValueEqual(amount(offered.addedTools(), tool(1)), 0L, "Different remaining damage cannot satisfy the D0 firing");
        var resident = recipe.offer(1000, available, ignored -> 2);
        helper.assertValueEqual(resident.count(), 3L, "Two free resident D0 units add one operation to the exact-state offer");
        helper.assertValueEqual(amount(resident.addedTools(), tool(0)), 4L, "Only the missing units are actually offered for transfer");
        helper.assertValueEqual(available.get(tool(0)), 5L, "Offering does not consume the CPU's fifth unused tool");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_accounting_and_settlement_follow_resident_successors_between_firings")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void accountingAndSettlementFollowResidentSuccessorsBetweenFirings(GameTestHelper helper) {
        Chain chain = chainedFirings(helper);
        Settlement settlement = chainSettlement(List.of(stack(tool(1), 2), stack(tool(2), 2), stack(MATERIAL, 2)), false);
        KeyCounter returned = new KeyCounter();
        helper.assertTrue(chain.ledger().settle(settlement, ReusableCpuSettlement.fingerprint(settlement, helper.getLevel().registryAccess()), actual -> {
            ReusableCpuSettlement.verify(chain.ledger().session(SESSION), actual);
            actual.returnedAssets().forEach(asset -> returned.add(asset.what(), asset.amount()));
        }), "Receipt identity gate and actual-asset verification accept the complete transition chain");
        helper.assertValueEqual(chain.accounting().waiting, 8L, "Waiting expectations are registered once for both accepted firing batches");
        helper.assertValueEqual(chain.accounting().accounted, 8L, "Accepted work is accounted exactly once");
        helper.assertValueEqual(chain.accounting().completed, 6L, "Observed native completion is distinct from accepted count");
        helper.assertValueEqual(returned.get(tool(1)), 2L, "Cancelled D1 firings retain their two actual D1 units");
        helper.assertValueEqual(returned.get(tool(2)), 2L, "Two executed D1 firings produce exactly two D2 successors");
        helper.assertValueEqual(returned.get(MATERIAL), 2L, "Only materials for cancelled operations are returned");
        helper.assertValueEqual(returned.get(tool(0)), 0L, "Initial D0 tools are not synthesized at final settlement");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_settlement_exhaustion_and_cancelled_materials_conserve_actual_assets")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void settlementExhaustionAndCancelledMaterialsConserveActualAssets(GameTestHelper helper) {
        List<TrinityBoundPatternInput> bindings = List.of(toolBinding(0, damageRule(0, 1), 1), ordinary(1, MATERIAL, 1));
        ReusableCpuSessionLedger ledger = ledger(bindings);
        Accounting accounting = new Accounting();
        book(ledger, bindings, 2, inventoryWithMaterial(tool(0), 2, 2), 0, 1, accounting, helper);
        Settlement settlement = settlement(List.of(stack(tool(0), 1), stack(MATERIAL, 1)), 1,
                List.of(new AppendReceipt(0, 2, 1, 1)));
        KeyCounter returned = new KeyCounter();
        helper.assertTrue(ledger.settle(settlement, ReusableCpuSettlement.fingerprint(settlement, helper.getLevel().registryAccess()), actual -> {
            ReusableCpuSettlement.verify(ledger.session(SESSION), actual);
            actual.returnedAssets().forEach(asset -> returned.add(asset.what(), asset.amount()));
        }), "One actual exhaustion plus one cancelled operation is fully explained");
        helper.assertValueEqual(returned.get(tool(0)), 1L, "Only the tool for the unexecuted operation survives");
        helper.assertValueEqual(returned.get(MATERIAL), 1L, "Unused material is returned once");
        helper.assertValueEqual(accounting.completed, 1L, "Exhaustion is a real completed native operation");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_invalid_components_or_exhaustion_cannot_deposit_assets")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void invalidComponentsOrExhaustionCannotDepositAssets(GameTestHelper helper) {
        List<TrinityBoundPatternInput> bindings = List.of(toolBinding(0, damageRule(0, 2), 1), ordinary(1, MATERIAL, 1));
        ReusableCpuSessionLedger ledger = ledger(bindings);
        book(ledger, bindings, 2, inventoryWithMaterial(tool(0), 2, 2), 0, 1, new Accounting(), helper);
        var before = ledger.snapshot();
        ItemStack renamed = tool(1).toStack();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("different actual component"));
        Settlement wrongComponents = settlement(List.of(stack(tool(0), 1), stack(AEItemKey.of(renamed), 1), stack(MATERIAL, 1)), 0,
                List.of(new AppendReceipt(0, 2, 1, 1)));
        KeyCounter deposited = new KeyCounter();
        expectState(helper, () -> ledger.settle(wrongComponents, ReusableCpuSettlement.fingerprint(wrongComponents, helper.getLevel().registryAccess()), actual -> {
            ReusableCpuSettlement.verify(ledger.session(SESSION), actual);
            actual.returnedAssets().forEach(asset -> deposited.add(asset.what(), asset.amount()));
        }), "A successor with altered components must fail asset conservation");
        Settlement inventedExhaustion = settlement(List.of(stack(tool(0), 1), stack(tool(1), 1), stack(MATERIAL, 1)), 1,
                List.of(new AppendReceipt(0, 2, 1, 1)));
        expectState(helper, () -> ledger.settle(inventedExhaustion, ReusableCpuSettlement.fingerprint(inventedExhaustion, helper.getLevel().registryAccess()), actual -> {
            ReusableCpuSettlement.verify(ledger.session(SESSION), actual);
            actual.returnedAssets().forEach(asset -> deposited.add(asset.what(), asset.amount()));
        }), "A legal surviving use cannot also be counted as exhaustion");
        helper.assertTrue(deposited.isEmpty(), "Rejected asset verification occurs before inventory deposition");
        helper.assertValueEqual(ledger.snapshot(), before, "Known-invalid returns do not acknowledge or mutate custody");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_duplicate_receipt_cannot_exhaust_one_physical_tool_twice")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void duplicateReceiptCannotExhaustOnePhysicalToolTwice(GameTestHelper helper) {
        List<TrinityBoundPatternInput> bindings = List.of(toolBinding(0, damageRule(0, 1), 1));
        ReusableCpuSessionLedger ledger = ledger(bindings);
        book(ledger, bindings, 1, inventory(tool(0), 1), 0, 1, new Accounting(), helper);
        AppendReceipt receipt = new AppendReceipt(0, 1, 1, 0);
        Settlement valid = settlement(List.of(), 1, List.of(receipt));
        String fingerprint = ReusableCpuSettlement.fingerprint(valid, helper.getLevel().registryAccess());
        Settlement duplicate = settlement(List.of(), 2, List.of(receipt, receipt));
        int[] verificationCalls = { 0 };
        var before = ledger.snapshot();
        expectState(helper, () -> ledger.settle(duplicate, fingerprint, actual -> {
            verificationCalls[0]++;
            ReusableCpuSettlement.verify(ledger.session(SESSION), actual);
        }), "Receipt protocol gate must reject a duplicate sequence before checking its claimed double exhaustion");
        helper.assertValueEqual(verificationCalls[0], 0, "Invalid receipt identity never reaches the asset receiver");
        helper.assertValueEqual(ledger.snapshot(), before, "Duplicate receipt cannot consume or acknowledge custody twice");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_settlement_fingerprint_is_split_and_order_independent_for_replay")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void settlementFingerprintIsSplitAndOrderIndependentForReplay(GameTestHelper helper) {
        Chain chain = chainedFirings(helper);
        Settlement aggregated = chainSettlement(List.of(stack(tool(1), 2), stack(tool(2), 2), stack(MATERIAL, 2)), false);
        Settlement split = chainSettlement(List.of(stack(MATERIAL, 1), stack(tool(2), 1), stack(tool(1), 1),
                stack(tool(2), 1), stack(MATERIAL, 1), stack(tool(1), 1)), true);
        String first = ReusableCpuSettlement.fingerprint(aggregated, helper.getLevel().registryAccess());
        String second = ReusableCpuSettlement.fingerprint(split, helper.getLevel().registryAccess());
        helper.assertValueEqual(second, first, "Stack splitting, asset order and receipt order do not change replay identity");
        int[] deposits = { 0 };
        KeyCounter returned = new KeyCounter();
        helper.assertTrue(chain.ledger().settle(aggregated, first, actual -> {
            ReusableCpuSettlement.verify(chain.ledger().session(SESSION), actual);
            deposits[0]++;
            actual.returnedAssets().forEach(asset -> returned.add(asset.what(), asset.amount()));
        }), "First actual return is accepted");
        helper.assertTrue(chain.ledger().settle(split, second, actual -> {
            deposits[0]++;
            actual.returnedAssets().forEach(asset -> returned.add(asset.what(), asset.amount()));
        }), "Equivalent differently split return is recognized as replay");
        helper.assertValueEqual(deposits[0], 1, "Replay cannot deposit the same physical assets twice");
        helper.assertValueEqual(returned.get(tool(1)), 2L, "Resident D1 quantity is not doubled by replay");
        helper.assertValueEqual(returned.get(tool(2)), 2L, "Resident D2 quantity is not doubled by replay");
        helper.assertValueEqual(returned.get(MATERIAL), 2L, "Cancelled materials are not doubled by replay");
        helper.succeed();
    }

    private static Chain chainedFirings(GameTestHelper helper) {
        List<TrinityBoundPatternInput> initial = List.of(toolBinding(0, damageRule(0, 100), 1), ordinary(1, MATERIAL, 1));
        ReusableCpuSessionLedger ledger = ledger(initial);
        Accounting accounting = new Accounting();
        book(ledger, initial, 4, inventoryWithMaterial(tool(0), 4, 4), 0, 4, accounting, helper);
        List<TrinityBoundPatternInput> next = List.of(toolBinding(0, damageRule(1, 100), 1), ordinary(1, MATERIAL, 1));
        long sequence = book(ledger, next, 4, inventory(MATERIAL, 4), 4, 2, accounting, helper);
        helper.assertTrue(ledger.session(SESSION).submission(sequence).physicalInputs().stream().noneMatch(input -> input.slot() == 0),
                "Second firing batch registers only new materials, preserving the resident tool provenance");
        return new Chain(ledger, accounting);
    }

    private static long book(ReusableCpuSessionLedger ledger, List<TrinityBoundPatternInput> bindings, long count,
                             KeyCounter available, long residentTools, long completed, Accounting accounting, GameTestHelper helper) {
        TrinityReusableRecipe recipe = recipe(bindings);
        var offer = recipe.offer(count, available, ignored -> residentTools);
        helper.assertValueEqual(offer.count(), count, "Fixture's real offer must cover the requested exact firing count");
        List<SlotStack> physical = new ObjectArrayList<>(offer.addedTools());
        for (var input : recipe.inputs()) {
            for (GenericStack material : input.consumedPerOperation()) {
                physical.add(slot(input.slot(), material.what(), Math.multiplyExact(material.amount(), count)));
            }
        }
        Work work = new Work(0, 0, 0, PUBLICATION, PRODUCT, 0, count, false, bindings);
        Submission submission = new Submission(work, count, count, 0, new OutputContract(List.of(stack(PRODUCT, 1)),
                recipe.ordinaryRemainders(), List.of(), List.of()), physical, false, false, false, 0);
        long sequence = ledger.prepare(SESSION, submission);
        ledger.registerWaiting(SESSION, sequence, registered -> accounting.waiting += registered.count());
        ledger.registerWaiting(SESSION, sequence, registered -> accounting.waiting += registered.count());
        ledger.transferred(SESSION, sequence);
        helper.assertTrue(ledger.account(SESSION, sequence, accepted -> accounting.accounted += accepted.count()), "Transferred work is accounted once");
        helper.assertTrue(!ledger.account(SESSION, sequence, accepted -> accounting.accounted += accepted.count()), "Repeated accounting is a no-op");
        ledger.observeCompleted(SESSION, sequence, completed, delta -> accounting.completed += delta);
        ledger.observeCompleted(SESSION, sequence, completed, delta -> accounting.completed += delta);
        return sequence;
    }

    private static ReusableCpuSessionLedger ledger(List<TrinityBoundPatternInput> bindings) {
        ReusableCpuSessionLedger ledger = new ReusableCpuSessionLedger(OWNER);
        ledger.open(SESSION, JOB, TARGET, PATTERN, PUBLICATION, bindings);
        return ledger;
    }

    private static Settlement chainSettlement(List<GenericStack> returned, boolean reverseReceipts) {
        AppendReceipt first = new AppendReceipt(0, 4, 4, 0);
        AppendReceipt second = new AppendReceipt(1, 4, 2, 2);
        return settlement(returned, 0, reverseReceipts ? List.of(second, first) : List.of(first, second));
    }

    private static Settlement settlement(List<GenericStack> returned, long exhausted, List<AppendReceipt> receipts) {
        return new Settlement(SESSION, JOB, OWNER.toString(), TARGET.persistentIdentity(), 0, returned, List.of(), exhausted, receipts, Optional.empty());
    }

    private static TrinityReusableRecipe recipe(List<TrinityBoundPatternInput> bindings) {
        return new TrinityReusableRecipe(new TestPattern(bindings), bindings, Optional.empty());
    }

    private static TrinityBoundPatternInput toolBinding(int slot, ReusableInputRule rule, long units) {
        var transition = rule.advance(rule.initialKey(), 1);
        return new TrinityBoundPatternInput(slot, 0, stack(rule.initialKey(), units), 1, transition.successor(), rule, transition.byproducts());
    }

    private static TrinityBoundPatternInput ordinary(int slot, AEKey key, long amount) {
        return new TrinityBoundPatternInput(slot, 0, stack(key, amount), 1, null);
    }

    private static ReusableInputRule unchanged() {
        return ReusableInputRule.unchanged(RULE_ID, 1, tool(0));
    }

    private static ReusableInputRule damageRule(int damage, int boundary) {
        return ReusableInputRule.fixedDamage(RULE_ID, 1, tool(damage), 1, boundary, List.of());
    }

    private static AEItemKey tool(int damage) {
        ItemStack tool = new ItemStack(Items.IRON_AXE);
        tool.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(tool);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static SlotStack slot(int slot, AEKey key, long amount) {
        return new SlotStack(slot, stack(key, amount));
    }

    private static KeyCounter inventory(AEKey key, long amount) {
        KeyCounter inventory = new KeyCounter();
        inventory.add(key, amount);
        return inventory;
    }

    private static KeyCounter inventoryWithMaterial(AEKey tool, long tools, long materials) {
        KeyCounter inventory = inventory(tool, tools);
        inventory.add(MATERIAL, materials);
        return inventory;
    }

    private static long amount(List<SlotStack> assets, AEKey key) {
        return assets.stream().filter(asset -> asset.stack().what().equals(key)).mapToLong(asset -> asset.stack().amount()).sum();
    }

    private static void expectState(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail(message);
    }

    private static final class Accounting {

        private long waiting;
        private long accounted;
        private long completed;
    }

    private record Chain(ReusableCpuSessionLedger ledger, Accounting accounting) {}

    private record TestPattern(List<TrinityBoundPatternInput> bindings) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return PATTERN;
        }

        @Override
        public IInput[] getInputs() {
            return bindings.stream().map(ExactInput::new).toArray(IInput[]::new);
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(stack(PRODUCT, 1));
        }
    }

    private record ExactInput(TrinityBoundPatternInput binding) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { binding.template() };
        }

        @Override
        public long getMultiplier() {
            return binding.multiplier();
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return binding.template().what().equals(key);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return binding.remainingKey();
        }
    }
}
