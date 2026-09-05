package com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityInitialInputExtractor;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory.TrinityExactWorkingInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityAvailableAmount;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventorySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinitySameItemPolicyGameTest {

    private TrinitySameItemPolicyGameTest() {}

    @TestHolder("same_item_policy_merges_component_variants_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mergesComponentVariantsOnce(GameTestHelper helper) {
        AEItemKey representative = namedPaper("representative");
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        AEKey unrelated = AEItemKey.of(Items.DIAMOND);
        TrinitySameItemPolicy policy = TrinitySameItemPolicy.ofRepresentatives(List.of(representative));

        Map<AEKey, BigInteger> normalized = policy.normalizeAmounts(Map.of(
                representative, BigInteger.valueOf(2L),
                first, BigInteger.valueOf(3L),
                second, BigInteger.valueOf(5L),
                unrelated, BigInteger.valueOf(7L)));

        helper.assertValueEqual(normalized.get(representative), BigInteger.TEN,
                "All physical component variants must enter one logical pool exactly once");
        helper.assertValueEqual(normalized.get(unrelated), BigInteger.valueOf(7L),
                "Items without an authorised domain must remain exact");
        helper.assertValueEqual(normalized.size(), 2,
                "Normalisation must not retain duplicate physical aliases");
        helper.succeed();
    }

    @TestHolder("same_item_variant_retains_physical_pattern_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsPhysicalPatternIdentity(GameTestHelper helper) {
        AEItemKey representative = namedPaper("representative");
        AEItemKey physicalInput = namedPaper("input");
        AEItemKey physicalOutput = namedPaper("output");
        TrinityBoundPatternInput binding = new TrinityBoundPatternInput(
                0,
                0,
                new GenericStack(physicalInput, 1L),
                1L,
                null);
        TrinityPatternVariant exact = TrinityPatternVariant.create(
                new TrinityPatternIdentity("definition", "publication"),
                physicalOutput,
                0,
                List.of(0),
                List.of(binding),
                List.of(new GenericStack(physicalOutput, 2L)));

        TrinityPatternVariant normalized = exact.normalized(
                TrinitySameItemPolicy.ofRepresentatives(List.of(representative)));

        helper.assertValueEqual(normalized.primaryOutput(), physicalOutput,
                "Provider lookup must retain the raw primary output");
        helper.assertValueEqual(normalized.declaredOutputs().get(physicalOutput), BigInteger.valueOf(2L),
                "Pattern-declared outputs must retain complete components");
        helper.assertValueEqual(normalized.physicalInputs().get(physicalInput), BigInteger.ONE,
                "The exact physical input binding must remain available to execution");
        helper.assertValueEqual(normalized.inputs().get(representative), BigInteger.ONE,
                "Planner input balances must use the logical representative");
        helper.assertValueEqual(normalized.outputs().get(representative), BigInteger.valueOf(2L),
                "Planner output balances must use the logical representative");
        helper.assertValueEqual(normalized.netChange().get(representative), BigInteger.ONE,
                "Logical conservation must be calculated after domain projection");
        helper.succeed();
    }

    @TestHolder("same_item_inventory_does_not_duplicate_physical_amounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void doesNotDuplicatePhysicalAmounts(GameTestHelper helper) {
        AEItemKey representative = namedPaper("representative");
        AEItemKey finiteVariant = namedPaper("finite");
        AEItemKey unlimitedVariant = namedPaper("unlimited");
        TrinitySameItemPolicy policy = TrinitySameItemPolicy.ofRepresentatives(List.of(representative));

        TrinityPlanningInventory finite = new TrinityPlanningInventory(
                Map.of(representative, BigInteger.valueOf(2L), finiteVariant, BigInteger.valueOf(3L)),
                Set.of()).normalized(policy);
        helper.assertValueEqual(finite.finiteAmount(representative), BigInteger.valueOf(5L),
                "Each exact physical key must contribute to the pool once");
        helper.assertValueEqual(finite.finiteAmounts().size(), 1,
                "Physical aliases must not remain as duplicate planning balances");

        TrinityPlanningInventory unlimited = new TrinityPlanningInventory(
                Map.of(finiteVariant, BigInteger.valueOf(3L)),
                Set.of(unlimitedVariant)).normalized(policy);
        helper.assertTrue(unlimited.unlimited(representative),
                "One unlimited physical variant must make the logical pool unlimited");
        helper.assertValueEqual(unlimited.finiteAmount(representative), BigInteger.ZERO,
                "Unlimited domain membership must remove a duplicate finite balance");
        helper.succeed();
    }

    @TestHolder("same_item_execution_persists_logical_policy_and_raw_primary")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void persistsLogicalPolicyAndRawPrimary(GameTestHelper helper) {
        AEItemKey representative = namedPaper("target");
        AEItemKey physicalInput = namedPaper("input");
        AEItemKey physicalOutput = namedPaper("output");
        TrinitySameItemPolicy policy = TrinitySameItemPolicy.ofRepresentatives(List.of(representative));
        TrinityPatternIdentity identity = new TrinityPatternIdentity("definition", "publication");
        TrinityPlanPatternFiring firing = new TrinityPlanPatternFiring(
                identity,
                physicalOutput,
                0,
                BigInteger.ONE,
                Map.of(physicalInput, BigInteger.ONE),
                Map.of(physicalOutput, BigInteger.valueOf(2L)),
                Map.of(),
                List.of());
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(firing),
                Map.of(representative, BigInteger.ONE),
                Map.of(representative, BigInteger.ONE));
        TrinityCraftingPlan plan = TrinityCraftingPlan.builder()
                .finalOutput(new GenericStack(representative, 1L))
                .bytes(BigInteger.ZERO)
                .catalogRevision(1L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .sameItemPolicy(policy)
                .initialExpectedInputs(Map.of(representative, BigInteger.ONE))
                .patternFirings(Map.of(identity, BigInteger.ONE))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .targetNetChange(Map.of(representative, BigInteger.ONE))
                .build();

        CompoundTag saved = TrinityPlanExecution.create(plan, 10L).save(
                helper.getLevel().registryAccess(),
                10L);
        TrinityPlanExecution restored = TrinityPlanExecution.restore(
                saved,
                helper.getLevel().registryAccess(),
                20L);
        helper.assertValueEqual(restored.sameItemPolicy(), policy,
                "Saved execution must restore its logical same-item domains");
        helper.assertValueEqual(restored.pendingOutputs().get(representative), BigInteger.valueOf(2L),
                "Pending output accounting must use the logical representative");
        helper.assertValueEqual(restored.pollDispatchable(20L, Set.of(), ignored -> true, true)
                .orElseThrow()
                .primaryOutput(), physicalOutput,
                "Restored provider lookup must retain the raw primary output");

        CompoundTag schemaSix = saved.copy();
        schemaSix.putInt("schema_version", 6);
        schemaSix.remove("same_item_policy");
        for (Tag encodedStage : schemaSix.getList("stages", Tag.TAG_COMPOUND)) {
            for (Tag encodedFiring : ((CompoundTag) encodedStage).getList("firings", Tag.TAG_COMPOUND)) {
                ((CompoundTag) encodedFiring).remove("exact_bindings");
            }
        }
        TrinityPlanExecution restoredSix = TrinityPlanExecution.restore(
                schemaSix,
                helper.getLevel().registryAccess(),
                20L);
        helper.assertTrue(restoredSix.sameItemPolicy().isEmpty(),
                "Schema 6 execution must restore with exact-only semantics");

        CompoundTag schemaFive = schemaSix.copy();
        schemaFive.putInt("schema_version", 5);
        downgradeAmountsToLong(schemaFive);
        TrinityPlanExecution restoredFive = TrinityPlanExecution.restore(
                schemaFive,
                helper.getLevel().registryAccess(),
                20L);
        helper.assertTrue(restoredFive.sameItemPolicy().isEmpty(),
                "Schema 5 execution must restore with exact-only semantics");
        helper.succeed();
    }

    @TestHolder("same_item_snapshot_counts_each_physical_key_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void snapshotCountsEachPhysicalKeyOnce(GameTestHelper helper) {
        AEItemKey representative = namedPaper("representative");
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        TrinitySameItemPolicy policy = TrinitySameItemPolicy.ofRepresentatives(List.of(representative));
        FiniteStorage storage = new FiniteStorage(Map.of(first, 2L, second, 3L));

        TrinityPlanningInventorySnapshot snapshot = TrinityPlanningInventorySnapshot.capture(
                List.of(representative, first),
                policy,
                storage,
                IActionSource.empty(),
                ignored -> {});

        helper.assertValueEqual(snapshot.inventory().finiteAmount(representative), BigInteger.valueOf(5L),
                "A physical key present in graph keys and available stacks must still be counted once");
        helper.assertValueEqual(snapshot.inventory().finiteAmounts().size(), 1,
                "The captured planning inventory must contain only the logical representative");
        helper.succeed();
    }

    @TestHolder("same_item_initial_extraction_preserves_physical_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void initialExtractionPreservesPhysicalKeys(GameTestHelper helper) {
        AEItemKey representative = namedPaper("representative");
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        TrinitySameItemPolicy policy = TrinitySameItemPolicy.ofRepresentatives(List.of(representative));
        FiniteStorage storage = new FiniteStorage(Map.of(first, 1L, second, 1L));
        ListCraftingInventory physical = new ListCraftingInventory(ignored -> {});
        TrinityExactWorkingInventory exact = new TrinityExactWorkingInventory();

        GenericStack missing = TrinityInitialInputExtractor.reserveReplacement(
                Map.of(representative, BigInteger.valueOf(2L)),
                policy,
                storage,
                physical,
                exact,
                IActionSource.empty());

        helper.assertTrue(missing == null, "Two physical component variants must satisfy one logical reservation");
        helper.assertValueEqual(physical.list.get(first), 1L, "First physical key must remain intact in CPU ownership");
        helper.assertValueEqual(physical.list.get(second), 1L, "Second physical key must remain intact in CPU ownership");
        helper.assertValueEqual(physical.list.get(representative), 0L,
                "Initial extraction must not fabricate the logical representative");
        helper.assertTrue(storage.available().isEmpty(), "Successful extraction must consume each network unit once");
        helper.succeed();
    }

    @TestHolder("same_item_initial_extraction_rolls_back_all_physical_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void initialExtractionRollsBackAllPhysicalKeys(GameTestHelper helper) {
        AEItemKey representative = namedPaper("representative");
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        TrinitySameItemPolicy policy = TrinitySameItemPolicy.ofRepresentatives(List.of(representative));
        FiniteStorage storage = new FiniteStorage(Map.of(first, 1L, second, 1L));
        ListCraftingInventory physical = new ListCraftingInventory(ignored -> {});
        TrinityExactWorkingInventory exact = new TrinityExactWorkingInventory();

        GenericStack missing = TrinityInitialInputExtractor.reserveReplacement(
                Map.of(representative, BigInteger.valueOf(3L)),
                policy,
                storage,
                physical,
                exact,
                IActionSource.empty());

        helper.assertTrue(missing != null && missing.amount() == 1L,
                "An incomplete logical reservation must report its exact remainder");
        helper.assertTrue(physical.list.isEmpty(), "Failed extraction must remove all tentative CPU ownership");
        helper.assertValueEqual(storage.available().get(first), 1L, "First physical key must roll back");
        helper.assertValueEqual(storage.available().get(second), 1L, "Second physical key must roll back");
        helper.succeed();
    }

    private static AEItemKey namedPaper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }

    private static void downgradeAmountsToLong(CompoundTag root) {
        convertAmountEntries(root.getList("seed_reserve", Tag.TAG_COMPOUND));
        for (Tag encodedStage : root.getList("stages", Tag.TAG_COMPOUND)) {
            CompoundTag stage = (CompoundTag) encodedStage;
            convertAmountEntries(stage.getList("required_at_start", Tag.TAG_COMPOUND));
            convertAmountEntries(stage.getList("net_change", Tag.TAG_COMPOUND));
            for (Tag encodedFiring : stage.getList("firings", Tag.TAG_COMPOUND)) {
                CompoundTag firing = (CompoundTag) encodedFiring;
                convertBigIntegerField(firing, "planned_count");
                convertBigIntegerField(firing, "remaining_count");
                convertAmountEntries(firing.getList("outputs", Tag.TAG_COMPOUND));
            }
        }
        for (Tag encodedRepeat : root.getList("repeat_blocks", Tag.TAG_COMPOUND)) {
            CompoundTag repeat = (CompoundTag) encodedRepeat;
            convertBigIntegerField(repeat, "remaining_repetitions");
            convertBigIntegerField(repeat, "wave_count");
        }
    }

    private static void convertAmountEntries(ListTag entries) {
        for (Tag encodedEntry : entries) {
            convertBigIntegerField((CompoundTag) encodedEntry, "amount");
        }
    }

    private static void convertBigIntegerField(CompoundTag tag, String field) {
        tag.putLong(field, new BigInteger(tag.getByteArray(field)).longValueExact());
    }

    private static final class FiniteStorage implements MEStorage, FiniteNetworkStorageAccess {

        private final KeyCounter available = new KeyCounter();

        private FiniteStorage(Map<AEKey, Long> amounts) {
            amounts.forEach(this.available::add);
        }

        private KeyCounter available() {
            return this.available;
        }

        @Override
        public long storageStructureRevision() {
            return 0L;
        }

        @Override
        public TrinityAvailableAmount exactAvailability(AEKey what, IActionSource source) {
            return new TrinityAvailableAmount.Finite(BigInteger.valueOf(this.available.get(what)));
        }

        @Override
        public FiniteTransferResult transferFinite(AEKey what,
                                                   long amount,
                                                   IActionSource source,
                                                   FiniteTransferTarget target) {
            throw new UnsupportedOperationException("Finite transfer is not used by this inventory capture fixture");
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                this.available.add(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, this.available.get(what));
            if (mode == Actionable.MODULATE) {
                this.available.add(what, -extracted);
                this.available.removeZeros();
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.addAll(this.available);
        }

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }
    }
}
