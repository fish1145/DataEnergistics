package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.reusable;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule.Transition;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Input;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Tool;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.blockentity.patternprovider.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.me.helpers.BaseActionSource;
import appeng.util.SettingsFrom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class AdaptiveReusableCraftingGameTest {

    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "adaptive_resident_fixture");
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey PRODUCT = AEItemKey.of(Items.IRON_NUGGET);

    private AdaptiveReusableCraftingGameTest() {}

    @TestHolder("adaptive_reusable_clips_total_admission_and_replays_original_physical_payload")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void clipsTotalAdmissionAndReplaysOriginalPhysicalPayload(GameTestHelper helper) {
        AdaptiveReusableCraftingState state = new AdaptiveReusableCraftingState();
        NativeHost host = new NativeHost();
        UUID id = UUID.randomUUID();
        ReusableCraftingRequest original = request(state, id, 0, 100, 20, Optional.empty(), helper);
        ReusableCraftingAdmission admission = prepared(state, original, 2, host);
        helper.assertValueEqual(admission.count(), 2L, "Admission uses only the original remaining round capacity");
        helper.assertValueEqual(amount(admission.physicalInputs(), tool(0)), 1L, "Continuous two-operation batch needs one physical tool");
        helper.assertValueEqual(amount(admission.physicalInputs(), MATERIAL), 2L, "Materials are transferred as admitted totals");
        helper.assertValueEqual(state.pendingOperations(), 0L, "Preparing does not reserve the shared budget");
        admission.commit(delivery(admission, 2));
        helper.assertValueEqual(state.pendingOperations(), 2L, "Committed but unexecuted work is reserved separately");
        state.slot(0).endpoint().tick(1, 1, host);
        helper.assertValueEqual(state.pendingOperations(), 1L, "One actual completion releases one pending reservation");
        state = reload(state, helper);
        ReusableCraftingAdmission replay = prepared(state, original, 0, host);
        helper.assertTrue(replay.replay(), "Replay finds the original accepted sequence even with no new capacity");
        helper.assertValueEqual(replay.count(), 2L, "Replay preserves the actual clipped count");
        helper.assertTrue(replay.physicalInputs().isEmpty(), "Replay cannot request a second physical transfer");
        helper.assertTrue(replay.commit(delivery(replay, 2)), "Original sequence is acknowledged without re-executing");
        helper.assertValueEqual(state.pendingOperations(), 1L, "Replay does not reserve or complete work twice");
        helper.succeed();
    }

    @TestHolder("adaptive_reusable_exact_state_reservations_request_distinct_d0_tools")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exactStateReservationsRequestDistinctD0Tools(GameTestHelper helper) {
        AdaptiveReusableCraftingState state = new AdaptiveReusableCraftingState();
        NativeHost host = new NativeHost();
        UUID id = UUID.randomUUID();
        ReusableCraftingAdmission first = prepared(state, request(state, id, 0, 1, 5, Optional.of(tool(0)), helper), 8, host);
        helper.assertValueEqual(amount(first.physicalInputs(), tool(0)), 1L, "Five offered tools are clipped to the one admitted exact-state use");
        first.commit(delivery(first, 2));
        ReusableCraftingAdmission second = prepared(state, request(state, id, 1, 2, 5, Optional.of(tool(0)), helper), 7, host);
        helper.assertValueEqual(amount(second.physicalInputs(), tool(0)), 2L,
                "Already reserved D0 tool is excluded from the next exact-state batch");
        second.commit(delivery(second, 2));
        state.slot(0).endpoint().tick(1, 3, host);
        ReusableCraftingAdmission next = prepared(state, request(state, id, 2, 3, 5, Optional.of(tool(1)), helper), 8, host);
        helper.assertValueEqual(next.physicalInputs().stream().filter(input -> input.slot() == 0).count(), 0L,
                "Three resident D1 tools eliminate another physical transfer even when five are offered");
        helper.succeed();
    }

    @TestHolder("adaptive_reusable_state_handoff_freezes_source_and_preserves_actual_refunds")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void stateHandoffFreezesSourceAndPreservesActualRefunds(GameTestHelper helper) {
        AdaptiveReusableCraftingState source = new AdaptiveReusableCraftingState();
        NativeHost host = new NativeHost();
        UUID id = UUID.randomUUID();
        ReusableCraftingAdmission admission = prepared(source, request(source, id, 0, 2, 1, Optional.empty(), helper), 8, host);
        admission.commit(delivery(admission, 2));
        source.slot(0).endpoint().tick(1, 1, host);
        expectState(helper, source::ensureCanClear, "Unexported resident state cannot be cleared");
        CompoundTag item = source.prepareItemHandoff(helper.getLevel().registryAccess());
        helper.assertTrue(source.handoffPrepared(), "Physical handoff freezes source ownership");
        helper.assertTrue(source.locate(id) == null, "Frozen source cannot independently settle a second asset copy");
        source.ensureCanClear();
        AdaptiveReusableCraftingState unresolvedSource = reload(source, helper);
        helper.assertTrue(unresolvedSource.handoffPrepared(), "Uncertain source handoff remains quarantined after restart");
        AdaptiveReusableCraftingState placed = AdaptiveReusableCraftingState.readFromTag(item, helper.getLevel().registryAccess());
        helper.assertTrue(!placed.handoffPrepared() && placed.slot(0).closing(), "Placed item can close its carried session");
        placed.slot(0).close(host);
        List<Settlement> received = new ObjectArrayList<>();
        placed.slot(0).endpoint().settle(id, settlement -> received.add(settlement), host);
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), tool(1)), 1L, "Handoff returns actual D1 tool");
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), MATERIAL), 1L, "Only unused actual material is refunded");
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), tool(0)), 0L, "Original tool is not synthesized during movement");
        helper.succeed();
    }

    @TestHolder("adaptive_reusable_block_item_transfer_excludes_memory_cards_and_clears_source_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blockItemTransferExcludesMemoryCardsAndClearsSourceOnce(GameTestHelper helper) {
        AdaptiveReusableCraftingState owned = new AdaptiveReusableCraftingState();
        NativeHost host = new NativeHost();
        UUID id = UUID.randomUUID();
        ReusableCraftingAdmission admission = prepared(owned, request(owned, id, 0, 2, 1, Optional.empty(), helper), 8, host);
        admission.commit(delivery(admission, 2));
        owned.slot(0).endpoint().tick(1, 1, host);
        helper.setBlock(new BlockPos(1, 1, 1), DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get());
        AdaptivePatternProviderBlockEntity source = helper.getBlockEntity(new BlockPos(1, 1, 1));
        AdaptivePatternProviderLogic sourceLogic = (AdaptivePatternProviderLogic) source.getLogic();
        CompoundTag world = new CompoundTag();
        sourceLogic.writeToNBT(world, helper.getLevel().registryAccess());
        world.put(AdaptiveReusableCraftingState.NBT_KEY, owned.writeToTag(helper.getLevel().registryAccess()));
        sourceLogic.readFromNBT(world, helper.getLevel().registryAccess());
        DataComponentMap.Builder card = DataComponentMap.builder();
        source.exportSettings(SettingsFrom.MEMORY_CARD, card, null);
        var cardData = card.build().get(DataComponents.CUSTOM_DATA);
        helper.assertTrue(cardData == null || !cardData.contains(AdaptiveReusableCraftingState.NBT_KEY), "Memory cards cannot clone resident assets");
        DataComponentMap.Builder item = DataComponentMap.builder();
        source.exportSettings(SettingsFrom.DISMANTLE_ITEM, item, null);
        DataComponentMap payload = item.build();
        helper.assertTrue(payload.get(DataComponents.CUSTOM_DATA).contains(AdaptiveReusableCraftingState.NBT_KEY), "Dismantled block item carries the session");
        source.clearContent();
        source.clearContent();
        helper.assertTrue(sourceLogic.reusableSession(id).isEmpty(), "Source clear removes ownership exactly once");
        helper.setBlock(new BlockPos(3, 1, 1), DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get());
        AdaptivePatternProviderBlockEntity target = helper.getBlockEntity(new BlockPos(3, 1, 1));
        target.importSettings(SettingsFrom.DISMANTLE_ITEM, payload, null);
        AdaptivePatternProviderLogic targetLogic = (AdaptivePatternProviderLogic) target.getLogic();
        helper.assertTrue(targetLogic.reusableSession(id).isPresent(), "Placed block exposes carried session without the old pattern or mode");
        targetLogic.closeReusableSession(id);
        List<Settlement> received = new ObjectArrayList<>();
        helper.assertTrue(targetLogic.settleReusableSession(id, settlement -> received.add(settlement)), "Old session settles through production provider logic");
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), tool(1)), 1L, "Production item path retains actual tool components");
        helper.succeed();
    }

    @TestHolder("adaptive_reusable_meteorite_shares_legacy_round_budget_and_executes_native_recipe")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 120)
    public static void meteoriteSharesLegacyRoundBudgetAndExecutesNativeRecipe(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get());
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BlockPos absolutePosition = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().removeBlockEntity(absolutePosition);
        MeteoriteHostFixture block = new MeteoriteHostFixture(absolutePosition, helper.getLevel().getBlockState(absolutePosition));
        helper.getLevel().setBlockEntity(block);
        block.getProviderInventory().setItemDirect(0, AEBlocks.PATTERN_PROVIDER.stack());
        AECraftingPattern pattern = craftingTablePattern(helper);
        AdaptivePatternProviderLogic logic = (AdaptivePatternProviderLogic) block.getLogic();
        logic.getPatternInv().setItemDirect(0, pattern.getDefinition().toStack());
        UUID id = UUID.randomUUID();
        helper.runAfterDelay(10, () -> {
            for (int craft = 0; craft < 7; craft++) {
                KeyCounter ordinary = new KeyCounter();
                ordinary.add(AEItemKey.of(Items.OAK_PLANKS), 4);
                helper.assertTrue(logic.pushPattern(pattern, new KeyCounter[] { ordinary }), "Legacy native craft uses its original round allowance");
            }
            Target target = logic.reusableTargets(pattern, new BaseActionSource(), helper.getLevel()).getFirst();
            AEItemKey plank = AEItemKey.of(Items.OAK_PLANKS);
            ReusableInputRule rule = ReusableInputRule.transitions(RECIPE, 1, plank, List.of(new Transition(plank, null, List.of())));
            ReusableCraftingRequest request = new ReusableCraftingRequest(id, JOB, "cpu:real-adaptive", 0, target, pattern,
                    List.of(new Input(0, List.of(), Optional.of(new Tool(4, Ownership.CPU_SUPPLIED, rule, Optional.of(plank))))),
                    List.of(new SlotStack(0, new GenericStack(plank, 12))), 3, Optional.of(ResourceLocation.withDefaultNamespace("crafting_table")),
                    new BaseActionSource(), helper.getLevel());
            ReusableCraftingAdmission prepared = logic.prepareReusable(request);
            helper.assertTrue(prepared != null, "The final shared round slot can admit reusable work");
            helper.assertValueEqual(prepared.count(), 1L, "Seven legacy works leave capacity for only one new operation");
            helper.assertValueEqual(amount(prepared.physicalInputs(), plank), 4L, "Exact one-step state transfers only four actual units");
            helper.assertTrue(prepared.commit(delivery(prepared, 1)), "The remaining round capacity commits once");
            KeyCounter competing = new KeyCounter();
            competing.add(plank, 4);
            helper.assertTrue(!logic.pushPattern(pattern, new KeyCounter[] { competing }), "Legacy path cannot consume capacity already promised to the resident slot");
            helper.succeedWhen(() -> {
                helper.assertValueEqual(logic.reusableReceipt(id, 0).orElseThrow().completed(), 1L, "Actual native execution completes the admitted operation");
                logic.closeReusableSession(id);
                helper.assertTrue(logic.settleReusableSession(id, settlement -> {
                    helper.assertTrue(settlement.returnedAssets().isEmpty(), "Native exhausting recipe has no physical tool refund");
                    helper.assertValueEqual(settlement.exhaustedTools(), 4L, "Four actual native input units are accounted as exhausted");
                    return true;
                }), "Empty physical settlement still reaches the CPU");
            });
        });
    }

    private static AECraftingPattern craftingTablePattern(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace("crafting_table");
        var holder = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
        if (!(holder.value() instanceof CraftingRecipe recipe)) {
            throw new IllegalStateException("Vanilla crafting table fixture is not a crafting recipe");
        }
        ItemStack[] inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);
        for (int slot : new int[] { 0, 1, 3, 4 }) {
            inputs[slot] = new ItemStack(Items.OAK_PLANKS);
        }
        ItemStack encoded = AEItems.CRAFTING_PATTERN.stack();
        AECraftingPattern.encode(encoded, new RecipeHolder<>(id, recipe), inputs, new ItemStack(Items.CRAFTING_TABLE), false, false);
        return new AECraftingPattern(AEItemKey.of(encoded), helper.getLevel());
    }

    private static ReusableCraftingRequest request(AdaptiveReusableCraftingState state, UUID id, long sequence, long count,
                                                   long offered, Optional<AEItemKey> operationState, GameTestHelper helper) {
        AEItemKey initial = operationState.orElse(tool(0));
        ReusableInputRule rule = ReusableInputRule.fixedDamage(RECIPE, 1, initial, 1, 100, List.of());
        String target = state.targetIdentity(0);
        List<SlotStack> tools = offered == 0 ? List.of() : List.of(new SlotStack(0, new GenericStack(initial, offered)));
        return new ReusableCraftingRequest(id, JOB, "cpu:adaptive-fixture", sequence,
                new Target(target, CountedCraftingTarget.route(target), Optional.of(AdaptiveReusableCraftingState.MODE)), new TestPattern(),
                List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.CPU_SUPPLIED, rule, operationState))),
                        new Input(1, List.of(new GenericStack(MATERIAL, 1)), Optional.empty())),
                tools, count, Optional.of(RECIPE), new BaseActionSource(), helper.getLevel());
    }

    private static ReusableCraftingAdmission prepared(AdaptiveReusableCraftingState state, ReusableCraftingRequest request,
                                                      long available, Host host) {
        ReusableCraftingAdmission result = state.prepare(0, RECIPE, request, 0, available, host);
        if (result == null) {
            throw new IllegalStateException("Adaptive state unexpectedly rejected the fixture admission");
        }
        return result;
    }

    private static KeyCounter[] delivery(ReusableCraftingAdmission admission, int count) {
        KeyCounter[] result = new KeyCounter[count];
        for (int slot = 0; slot < count; slot++) {
            result[slot] = new KeyCounter();
        }
        admission.physicalInputs().forEach(input -> result[input.slot()].add(input.stack().what(), input.stack().amount()));
        return result;
    }

    private static AdaptiveReusableCraftingState reload(AdaptiveReusableCraftingState state, GameTestHelper helper) {
        return AdaptiveReusableCraftingState.readFromTag(state.writeToTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.IRON_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }

    private static long amount(List<SlotStack> assets, AEKey key) {
        return assets.stream().filter(asset -> asset.stack().what().equals(key)).mapToLong(asset -> asset.stack().amount()).sum();
    }

    private static long assetAmount(List<GenericStack> assets, AEKey key) {
        return assets.stream().filter(asset -> asset.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }

    private static void expectState(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail(message);
    }

    private static final class NativeHost implements Host {

        private final List<GenericStack> outputs = new ObjectArrayList<>();
        private int persistenceWrites;

        @Override
        public boolean isAvailable(Binding binding) {
            return true;
        }

        @Override
        public NativeResult execute(Binding binding, Operation operation) {
            List<GenericStack> actual = new ObjectArrayList<>();
            operation.tools().forEach(held -> {
                ItemStack stack = ((AEItemKey) held.stack().what()).toStack();
                stack.set(DataComponents.DAMAGE, stack.getDamageValue() + 1);
                actual.add(new GenericStack(AEItemKey.of(stack), held.stack().amount()));
            });
            return new NativeResult(true, List.of(new ToolOutcome(0, actual, List.of())), List.of(new GenericStack(PRODUCT, 1)), Optional.empty());
        }

        @Override
        public void acceptOutputs(Identity identity, List<GenericStack> produced) {
            outputs.addAll(produced);
        }

        @Override
        public void persistChanges() {
            persistenceWrites++;
        }
    }

    /** Supplies the production host capability without depending on an optional mod's item registration. */
    private static final class MeteoriteHostFixture extends AdaptivePatternProviderBlockEntity {

        private MeteoriteHostFixture(BlockPos position, BlockState state) {
            super(position, state);
        }

        @Override
        public boolean isMeteoriteProviderSelected() {
            return true;
        }
    }

    private static final class TestPattern implements IPatternDetails {

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
    }

    private record ExactInput(AEItemKey key) implements IPatternDetails.IInput {

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
            return template;
        }
    }
}
