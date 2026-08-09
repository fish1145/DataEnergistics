package com.fish_dan_.data_energistics.common.multiblock.transfer;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewProjectionImpl;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferState;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferTarget;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeySlotFilter;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class PatternEncodingMultiblockTransferGameTest {

    private static final ResourceLocation TRANSACTION_CONTROLLER = ResourceLocation.parse("data_energistics:pattern_transfer_test");
    private static final long TRANSACTION_REVISION = 17L;

    private PatternEncodingMultiblockTransferGameTest() {}

    @TestHolder("multiblock_pattern_transfer_reconstructs_current_recipe_and_rejects_stale_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reconstructsCurrentRecipeAndRejectsStaleIdentity(GameTestHelper helper) {
        MultiblockPreviewSpec spec = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                .require(ModVerticalMultiBlocks.trinityDataCoreId());
        PreviewSelection selection = PreviewSelection.initial(spec);
        StructurePreviewSnapshot snapshot = new StructurePreviewProjectionImpl().project(spec, selection);
        MultiblockRecipeView current = MultiblockRecipeView.from(spec, snapshot);
        ProjectionFingerprint fingerprint = current.projectionFingerprint();
        PatternEncodingMultiblockTransferImpl transfer = new PatternEncodingMultiblockTransferImpl();

        MultiblockRecipeView rebuilt = transfer.resolveRecipe(request(current.registeredRecipeId(), fingerprint));
        helper.assertValueEqual(rebuilt, current, "Current recipe identity must reconstruct exactly");

        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                current.registeredRecipeId(),
                copyFingerprint(
                        fingerprint,
                        fingerprint.controllerId(),
                        fingerprint.definitionRevision() + 1L,
                        fingerprint.structureKey(),
                        fingerprint.variantIndex(),
                        fingerprint.repeatCounts(),
                        fingerprint.tierSelections(),
                        fingerprint.candidateSelections()))),
                "A stale definition revision must be rejected");

        ResourceLocation unknownController = ResourceLocation.parse("data_energistics:missing_transfer_controller");
        ProjectionFingerprint unknownControllerFingerprint = copyFingerprint(
                fingerprint,
                unknownController,
                fingerprint.definitionRevision(),
                new JsonMultiBlockStructureKey(
                        unknownController,
                        fingerprint.structureKey().structureName()),
                fingerprint.variantIndex(),
                fingerprint.repeatCounts(),
                fingerprint.tierSelections(),
                fingerprint.candidateSelections());
        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                MultiblockRecipeView.registeredRecipeIdFor(unknownController),
                unknownControllerFingerprint)), "An unknown controller must be rejected");

        ProjectionFingerprint unknownStructure = copyFingerprint(
                fingerprint,
                fingerprint.controllerId(),
                fingerprint.definitionRevision(),
                new JsonMultiBlockStructureKey(fingerprint.controllerId(), "missing"),
                fingerprint.variantIndex(),
                fingerprint.repeatCounts(),
                fingerprint.tierSelections(),
                fingerprint.candidateSelections());
        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                current.registeredRecipeId(),
                unknownStructure)), "An unknown named structure must be rejected");

        ProjectionFingerprint unknownVariant = copyFingerprint(
                fingerprint,
                fingerprint.controllerId(),
                fingerprint.definitionRevision(),
                fingerprint.structureKey(),
                1_000_000,
                fingerprint.repeatCounts(),
                fingerprint.tierSelections(),
                fingerprint.candidateSelections());
        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                current.registeredRecipeId(),
                unknownVariant)), "An unknown structure variant must be rejected");

        List<Integer> invalidRepeats = new ArrayList<>(fingerprint.repeatCounts());
        invalidRepeats.add(1);
        ProjectionFingerprint unknownRepeat = copyFingerprint(
                fingerprint,
                fingerprint.controllerId(),
                fingerprint.definitionRevision(),
                fingerprint.structureKey(),
                fingerprint.variantIndex(),
                invalidRepeats,
                fingerprint.tierSelections(),
                fingerprint.candidateSelections());
        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                current.registeredRecipeId(),
                unknownRepeat)), "An unknown repeat unit must be rejected");

        Map<String, Integer> invalidTiers = new LinkedHashMap<>(fingerprint.tierSelections());
        if (!invalidTiers.isEmpty()) {
            invalidTiers.remove(invalidTiers.keySet().iterator().next());
        }
        invalidTiers.put("missing", 1);
        ProjectionFingerprint unknownTier = copyFingerprint(
                fingerprint,
                fingerprint.controllerId(),
                fingerprint.definitionRevision(),
                fingerprint.structureKey(),
                fingerprint.variantIndex(),
                fingerprint.repeatCounts(),
                invalidTiers,
                fingerprint.candidateSelections());
        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                current.registeredRecipeId(),
                unknownTier)), "An unknown tier domain must be rejected");

        Map<PreviewPredicateKey, Integer> invalidCandidates = new LinkedHashMap<>(fingerprint.candidateSelections());
        invalidCandidates.put(new PreviewPredicateKey(1_000_000, 1_000_000, 1_000_000), 0);
        ProjectionFingerprint unknownCandidate = copyFingerprint(
                fingerprint,
                fingerprint.controllerId(),
                fingerprint.definitionRevision(),
                fingerprint.structureKey(),
                fingerprint.variantIndex(),
                fingerprint.repeatCounts(),
                fingerprint.tierSelections(),
                invalidCandidates);
        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                current.registeredRecipeId(),
                unknownCandidate)), "An unknown predicate candidate key must be rejected");

        assertIllegalArgument(helper, () -> transfer.resolveRecipe(request(
                ResourceLocation.parse("data_energistics:multiblock/wrong"),
                fingerprint)), "A mismatched registered recipe id must be rejected");
        helper.succeed();
    }

    @TestHolder("multiblock_pattern_transfer_fills_81_inputs_and_clears_output_tail")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void fillsEightyOneInputsAndClearsOutputTail(GameTestHelper helper) {
        List<PreviewMaterial> inputs = materials(81, "success");
        PreviewMaterial output = material(Items.CRAFTING_TABLE, "success-output", 1L);
        TestTarget target = new TestTarget(81, 27, true, null);
        target.mode = EncodingMode.PROCESSING;
        target.transferState = sourceState("success");
        fillInventory(target.inputs, material(Items.DIRT, "old-input", 2L));
        fillInventory(target.outputs, material(Items.DIRT, "old-output", 3L));

        new PatternEncodingMultiblockTransferImpl().applyRecipe(target, recipe(inputs, output));

        helper.assertValueEqual(
                target.mode,
                EncodingMode.PROCESSING,
                "Successful transfer must select Processing mode");
        helper.assertTrue(
                target.transferState.isClear(),
                "Successful transfer must clear stale source, key, fluid, and display state");
        helper.assertValueEqual(target.inputs.beginBatchCount, 1, "Input writes must use one batch");
        helper.assertValueEqual(target.outputs.beginBatchCount, 1, "Output writes must use one batch");
        for (int slot = 0; slot < inputs.size(); slot++) {
            helper.assertValueEqual(
                    target.inputs.getStack(slot),
                    stack(inputs.get(slot)),
                    "Every one of the 81 input slots must be written exactly");
        }
        helper.assertValueEqual(target.outputs.getStack(0), stack(output), "The owner output must occupy slot zero");
        for (int slot = 1; slot < target.outputs.size(); slot++) {
            helper.assertTrue(target.outputs.getStack(slot) == null, "Unused output tail slots must be cleared");
        }
        helper.assertFalse(target.invalidated, "A successful transfer must keep the menu valid");
        helper.succeed();
    }

    @TestHolder("multiblock_pattern_transfer_preflight_rejects_capacity_filter_and_amount_without_batch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preflightRejectsCapacityFilterAndAmountWithoutBatch(GameTestHelper helper) {
        PatternEncodingMultiblockTransferImpl transfer = new PatternEncodingMultiblockTransferImpl();
        PreviewMaterial output = material(Items.CRAFTING_TABLE, "preflight-output", 1L);

        TestTarget eightyTwoInputs = new TestTarget(81, 27, true, null);
        assertPreflightFailureUnchanged(
                helper,
                transfer,
                eightyTwoInputs,
                recipe(materials(82, "too-many"), output),
                "82 inputs must fail atomically against an 81-slot target");

        TestTarget smallerCapacity = new TestTarget(4, 1, true, null);
        assertPreflightFailureUnchanged(
                helper,
                transfer,
                smallerCapacity,
                recipe(materials(5, "smaller"), output),
                "The target's real input size must override the protocol maximum");

        TestTarget missingOutputCapacity = new TestTarget(1, 0, true, null);
        assertPreflightFailureUnchanged(
                helper,
                transfer,
                missingOutputCapacity,
                recipe(materials(1, "no-output"), output),
                "A target without a real output slot must fail before batching");

        TestTarget filtered = new TestTarget(1, 1, true, (slot, key) -> false);
        assertPreflightFailureUnchanged(
                helper,
                transfer,
                filtered,
                recipe(materials(1, "filtered"), output),
                "The real slot filter must reject a disallowed input before batching");

        TestTarget boundedAmount = new TestTarget(1, 1, false, null);
        MultiblockRecipeView oversizedAmount = recipe(
                List.of(material(Items.STONE, "oversized", 65L)),
                output);
        assertPreflightFailureUnchanged(
                helper,
                transfer,
                boundedAmount,
                oversizedAmount,
                "The real inventory max amount must reject silent clamping before batching");
        helper.succeed();
    }

    @TestHolder("multiblock_pattern_transfer_write_failure_restores_mode_and_every_slot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void writeFailureRestoresModeAndEverySlot(GameTestHelper helper) {
        TestTarget target = new TestTarget(3, 2, true, null);
        fillInventory(target.inputs, material(Items.DIRT, "rollback-input", 2L));
        fillInventory(target.outputs, material(Items.DIRT, "rollback-output", 3L));
        GenericStack[] originalInputs = snapshot(target.inputs);
        GenericStack[] originalOutputs = snapshot(target.outputs);
        target.outputs.armFailures(2);

        Throwable primary = assertRuntimeFailure(helper, () -> new PatternEncodingMultiblockTransferImpl().applyRecipe(
                target,
                recipe(materials(2, "forward-failure"),
                        material(Items.CRAFTING_TABLE, "forward-output", 1L))),
                "An injected forward write failure must escape after rollback");

        helper.assertTrue(
                primary.getMessage() != null && primary.getMessage().contains("Injected setStack failure 2"),
                "The original forward failure must remain primary");
        helper.assertValueEqual(primary.getSuppressed().length, 0, "A complete rollback must add no failure");
        helper.assertValueEqual(target.mode, EncodingMode.CRAFTING, "The original mode must be restored");
        assertInventoryEquals(helper, target.inputs, originalInputs, "Every input slot must be restored");
        assertInventoryEquals(helper, target.outputs, originalOutputs, "Every output slot must be restored");
        helper.assertFalse(target.invalidated, "A complete rollback must keep the menu valid");

        TestTarget publishedTarget = new TestTarget(3, 2, true, null);
        PatternEncodingMultiblockTransferState publishedState = sourceState("published");
        publishedTarget.transferState = publishedState;
        fillInventory(publishedTarget.inputs, material(Items.DIRT, "published-input", 2L));
        fillInventory(publishedTarget.outputs, material(Items.DIRT, "published-output", 3L));
        GenericStack[] publishedInputs = snapshot(publishedTarget.inputs);
        GenericStack[] publishedOutputs = snapshot(publishedTarget.outputs);
        publishedTarget.inputs.armEndBatchFailure();

        Throwable publicationFailure = assertRuntimeFailure(helper,
                () -> new PatternEncodingMultiblockTransferImpl().applyRecipe(
                        publishedTarget,
                        recipe(materials(2, "publication-failure"),
                                material(Items.CRAFTING_TABLE, "publication-output", 1L))),
                "An endBatch failure after selecting Processing mode must roll back the publication");

        helper.assertTrue(
                publicationFailure.getMessage() != null &&
                        publicationFailure.getMessage().contains("Injected endBatch failure"),
                "The publication failure must remain primary");
        helper.assertValueEqual(
                publishedTarget.mode,
                EncodingMode.CRAFTING,
                "A publication failure must restore the pre-transfer mode");
        helper.assertValueEqual(
                publishedTarget.transferState,
                publishedState,
                "A publication failure must restore every source-specific field");
        assertInventoryEquals(
                helper,
                publishedTarget.inputs,
                publishedInputs,
                "A publication failure must restore every input slot");
        assertInventoryEquals(
                helper,
                publishedTarget.outputs,
                publishedOutputs,
                "A publication failure must restore every output slot");
        helper.assertFalse(
                publishedTarget.invalidated,
                "A complete rollback after publication failure must keep the menu valid");

        TestTarget stateFailureTarget = new TestTarget(3, 2, true, null);
        PatternEncodingMultiblockTransferState stateBeforeFailure = sourceState("state-failure");
        stateFailureTarget.transferState = stateBeforeFailure;
        fillInventory(stateFailureTarget.inputs, material(Items.DIRT, "state-input", 2L));
        fillInventory(stateFailureTarget.outputs, material(Items.DIRT, "state-output", 3L));
        GenericStack[] stateFailureInputs = snapshot(stateFailureTarget.inputs);
        GenericStack[] stateFailureOutputs = snapshot(stateFailureTarget.outputs);
        stateFailureTarget.failNextStateClear = true;

        Throwable stateFailure = assertRuntimeFailure(helper,
                () -> new PatternEncodingMultiblockTransferImpl().applyRecipe(
                        stateFailureTarget,
                        recipe(materials(2, "state-failure"),
                                material(Items.CRAFTING_TABLE, "state-failure-output", 1L))),
                "A partial source-state clear must roll back the complete transaction");

        helper.assertTrue(
                stateFailure.getMessage() != null && stateFailure.getMessage().contains("Injected state clear failure"),
                "The source-state clear failure must remain primary");
        helper.assertValueEqual(
                stateFailureTarget.transferState,
                stateBeforeFailure,
                "A failed source-state clear must restore all remembered transfer state");
        helper.assertValueEqual(
                stateFailureTarget.mode,
                EncodingMode.CRAFTING,
                "A failed source-state clear must restore the original mode");
        assertInventoryEquals(
                helper,
                stateFailureTarget.inputs,
                stateFailureInputs,
                "A failed source-state clear must restore every input slot");
        assertInventoryEquals(
                helper,
                stateFailureTarget.outputs,
                stateFailureOutputs,
                "A failed source-state clear must restore every output slot");
        helper.assertFalse(
                stateFailureTarget.invalidated,
                "A complete rollback after source-state failure must keep the menu valid");
        helper.succeed();
    }

    @TestHolder("multiblock_pattern_transfer_rollback_failure_is_suppressed_and_invalidates_target")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rollbackFailureIsSuppressedAndInvalidatesTarget(GameTestHelper helper) {
        TestTarget target = new TestTarget(3, 2, true, null);
        fillInventory(target.inputs, material(Items.DIRT, "invalid-input", 2L));
        fillInventory(target.outputs, material(Items.DIRT, "invalid-output", 3L));
        target.outputs.armFailures(2, 3);

        Throwable primary = assertRuntimeFailure(helper, () -> new PatternEncodingMultiblockTransferImpl().applyRecipe(
                target,
                recipe(materials(2, "rollback-failure"),
                        material(Items.CRAFTING_TABLE, "rollback-output", 1L))),
                "An incomplete rollback must still throw the original forward failure");

        helper.assertTrue(
                primary.getMessage() != null && primary.getMessage().contains("Injected setStack failure 2"),
                "The forward failure must remain primary after rollback failure");
        helper.assertTrue(primary.getSuppressed().length >= 1, "Rollback failures must be attached as suppressed");
        helper.assertTrue(
                Arrays.stream(primary.getSuppressed())
                        .anyMatch(failure -> failure.getCause() != null &&
                                failure.getCause().getMessage() != null &&
                                failure.getCause().getMessage().contains("Injected setStack failure 3")),
                "The injected rollback failure must remain reachable as a suppressed cause");
        helper.assertTrue(target.invalidated, "An incomplete rollback must invalidate the target menu");
        helper.succeed();
    }

    private static MultiblockPatternTransferRequest request(ResourceLocation registeredRecipeId,
                                                            ProjectionFingerprint fingerprint) {
        return new MultiblockPatternTransferRequest(19, registeredRecipeId, fingerprint);
    }

    private static ProjectionFingerprint copyFingerprint(ProjectionFingerprint source,
                                                         ResourceLocation controllerId,
                                                         long revision,
                                                         JsonMultiBlockStructureKey structureKey,
                                                         int variantIndex,
                                                         List<Integer> repeatCounts,
                                                         Map<String, Integer> tiers,
                                                         Map<PreviewPredicateKey, Integer> candidates) {
        if (source == null) {
            throw new IllegalArgumentException("Source fingerprint cannot be null");
        }
        return new ProjectionFingerprint(
                controllerId,
                revision,
                structureKey,
                variantIndex,
                repeatCounts,
                tiers,
                candidates);
    }

    private static MultiblockRecipeView recipe(List<PreviewMaterial> inputs, PreviewMaterial output) {
        ProjectionFingerprint fingerprint = new ProjectionFingerprint(
                TRANSACTION_CONTROLLER,
                TRANSACTION_REVISION,
                new JsonMultiBlockStructureKey(TRANSACTION_CONTROLLER, "main"),
                0,
                List.of(1),
                Map.of(),
                Map.of());
        return new MultiblockRecipeView(
                MultiblockRecipeView.registeredRecipeIdFor(TRANSACTION_CONTROLLER),
                TRANSACTION_CONTROLLER,
                "main",
                TRANSACTION_REVISION,
                fingerprint,
                inputs,
                output);
    }

    private static PatternEncodingMultiblockTransferState sourceState(String suffix) {
        return new PatternEncodingMultiblockTransferState(
                ResourceLocation.parse("data_energistics:data_ripper_reassembler"),
                ResourceLocation.parse("data_energistics:data_ripper_reassembler"),
                stack(material(Items.DIAMOND, suffix + "-key-input", 2L)),
                stack(material(Items.EMERALD, suffix + "-key-output", 3L)),
                List.of(stack(material(Items.WATER_BUCKET, suffix + "-fluid-input", 4L))),
                List.of(stack(material(Items.LAVA_BUCKET, suffix + "-fluid-output", 5L))),
                "{test:\"" + suffix + "-input\"}",
                "{test:\"" + suffix + "-output\"}");
    }

    private static List<PreviewMaterial> materials(int count, String prefix) {
        List<PreviewMaterial> materials = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            materials.add(material(Items.STONE, prefix + "-" + index, 1L));
        }
        return List.copyOf(materials);
    }

    private static PreviewMaterial material(Item item, String name, long amount) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalStateException("Test item did not produce an AE item key: " + item);
        }
        return new PreviewMaterial(key, amount);
    }

    private static GenericStack stack(PreviewMaterial material) {
        return new GenericStack(material.key(), material.amount());
    }

    private static void fillInventory(TrackingConfigInventory inventory, PreviewMaterial material) {
        GenericStack stack = stack(material);
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, stack);
        }
    }

    private static GenericStack[] snapshot(ConfigInventory inventory) {
        GenericStack[] snapshot = new GenericStack[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            snapshot[slot] = inventory.getStack(slot);
        }
        return snapshot;
    }

    private static void assertPreflightFailureUnchanged(GameTestHelper helper,
                                                        PatternEncodingMultiblockTransferImpl transfer,
                                                        TestTarget target,
                                                        MultiblockRecipeView recipe,
                                                        String message) {
        if (target.inputs.size() > 0) {
            fillInventory(target.inputs, material(Items.DIRT, message + " input", 2L));
        }
        if (target.outputs.size() > 0) {
            fillInventory(target.outputs, material(Items.DIRT, message + " output", 3L));
        }
        GenericStack[] originalInputs = snapshot(target.inputs);
        GenericStack[] originalOutputs = snapshot(target.outputs);

        assertIllegalArgument(helper, () -> transfer.applyRecipe(target, recipe), message);

        helper.assertValueEqual(target.inputs.beginBatchCount, 0, message + ": input batch must not begin");
        helper.assertValueEqual(target.outputs.beginBatchCount, 0, message + ": output batch must not begin");
        helper.assertValueEqual(target.mode, EncodingMode.CRAFTING, message + ": mode must remain unchanged");
        assertInventoryEquals(helper, target.inputs, originalInputs, message + ": inputs must remain unchanged");
        assertInventoryEquals(helper, target.outputs, originalOutputs, message + ": outputs must remain unchanged");
    }

    private static void assertInventoryEquals(GameTestHelper helper,
                                              ConfigInventory inventory,
                                              GenericStack[] expected,
                                              String message) {
        helper.assertValueEqual(inventory.size(), expected.length, message + ": size must match");
        for (int slot = 0; slot < expected.length; slot++) {
            helper.assertValueEqual(inventory.getStack(slot), expected[slot], message + " at slot " + slot);
        }
    }

    private static void assertIllegalArgument(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(
                    exception.getMessage() != null && !exception.getMessage().isBlank(),
                    message + " and explain the rejected request");
            return;
        }
        helper.fail(message);
    }

    private static Throwable assertRuntimeFailure(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            return failure;
        }
        helper.fail(message);
        throw new IllegalStateException(message);
    }

    private static final class TestTarget implements PatternEncodingMultiblockTransferTarget {

        private final TrackingConfigInventory inputs;
        private final TrackingConfigInventory outputs;
        private EncodingMode mode = EncodingMode.CRAFTING;
        private PatternEncodingMultiblockTransferState transferState = PatternEncodingMultiblockTransferState.cleared();
        private boolean failNextStateClear;
        private boolean invalidated;

        private TestTarget(int inputSize,
                           int outputSize,
                           boolean allowOverstacking,
                           AEKeySlotFilter inputFilter) {
            this.inputs = new TrackingConfigInventory(inputSize, inputFilter, allowOverstacking);
            this.outputs = new TrackingConfigInventory(outputSize, null, allowOverstacking);
        }

        @Override
        public void data_energistics$requestMultiblockTransfer(MultiblockRecipeView recipe) {
            throw new AssertionError("Server-side transfer must not invoke the client request entry");
        }

        @Override
        public ConfigInventory data_energistics$getMultiblockTransferInputInventory() {
            return this.inputs;
        }

        @Override
        public ConfigInventory data_energistics$getMultiblockTransferOutputInventory() {
            return this.outputs;
        }

        @Override
        public EncodingMode data_energistics$getMultiblockTransferEncodingMode() {
            return this.mode;
        }

        @Override
        public void data_energistics$setMultiblockTransferEncodingMode(EncodingMode mode) {
            if (mode == null) {
                throw new IllegalArgumentException("Test encoding mode cannot be null");
            }
            this.mode = mode;
        }

        @Override
        public PatternEncodingMultiblockTransferState data_energistics$snapshotMultiblockTransferState() {
            return this.transferState;
        }

        @Override
        public void data_energistics$clearMultiblockTransferState() {
            this.transferState = PatternEncodingMultiblockTransferState.cleared();
            if (this.failNextStateClear) {
                this.failNextStateClear = false;
                throw new IllegalStateException("Injected state clear failure");
            }
        }

        @Override
        public void data_energistics$restoreMultiblockTransferState(
                                                                    PatternEncodingMultiblockTransferState state) {
            this.transferState = state;
        }

        @Override
        public void data_energistics$invalidateMultiblockTransferTarget() {
            this.invalidated = true;
        }
    }

    private static final class TrackingConfigInventory extends ConfigInventory {

        private final Set<Integer> failingWriteCalls = new HashSet<>();
        private int writeCall;
        private int beginBatchCount;
        private boolean failuresArmed;
        private boolean failNextEndBatch;

        private TrackingConfigInventory(int size,
                                        AEKeySlotFilter filter,
                                        boolean allowOverstacking) {
            super(
                    AEKeyTypes.getAll(),
                    filter,
                    GenericStackInv.Mode.CONFIG_STACKS,
                    size,
                    null,
                    allowOverstacking);
        }

        private void armFailures(int... writeCalls) {
            this.failingWriteCalls.clear();
            for (int writeCall : writeCalls) {
                this.failingWriteCalls.add(writeCall);
            }
            this.writeCall = 0;
            this.failuresArmed = true;
        }

        private void armEndBatchFailure() {
            this.failNextEndBatch = true;
        }

        @Override
        public void beginBatch() {
            this.beginBatchCount++;
            super.beginBatch();
        }

        @Override
        public void endBatch() {
            super.endBatch();
            if (this.failNextEndBatch) {
                this.failNextEndBatch = false;
                throw new IllegalStateException("Injected endBatch failure");
            }
        }

        @Override
        public void setStack(int slot, GenericStack stack) {
            if (this.failuresArmed) {
                this.writeCall++;
                if (this.failingWriteCalls.remove(this.writeCall)) {
                    throw new IllegalStateException("Injected setStack failure " + this.writeCall);
                }
            }
            super.setStack(slot, stack);
        }
    }
}
