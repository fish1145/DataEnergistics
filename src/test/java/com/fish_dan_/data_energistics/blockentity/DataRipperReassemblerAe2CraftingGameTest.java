package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.util.ConfigInventory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerAe2CraftingGameTest {

    private static final BlockPos TERMINAL_HOST_POS = new BlockPos(2, 2, 2);
    private static final BlockPos PATTERN_SINK_POS = new BlockPos(2, 2, 1);
    private static final BlockPos PATTERN_PROVIDER_POS = new BlockPos(2, 2, 2);
    private static final BlockPos ENERGY_CELL_POS = new BlockPos(2, 2, 3);
    private static final BlockPos DRIVE_POS = new BlockPos(1, 2, 3);
    private static final BlockPos CRAFTING_STORAGE_POS = new BlockPos(3, 2, 3);
    private static final BlockPos CRAFTING_ACCELERATOR_POS = new BlockPos(4, 2, 3);

    private DataRipperReassemblerAe2CraftingGameTest() {}

    @TestHolder("data_reassembler_accepts_direct_custom_key_from_real_ae2_job")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 300)
    public static void dataReassemblerAcceptsDirectCustomKeyFromRealAe2Job(GameTestHelper helper) {
        DataRipperReassemblerBlockEntity reassembler = placeReassembler(helper);
        PatternProviderBlockEntity provider = placePatternProvider(helper);
        DriveBlockEntity drive = placeDrive(helper);
        CraftingBlockEntity craftingStorage = placeCraftingCpu(helper);
        AEItemKey dataCrystal = itemKey(DEItems.DATA_CRYSTAL.toStack());
        ItemStack encodedPattern = dataCrystalProcessingPattern();
        AEItemKey wrappedDataFlow = wrappedKey(DataFlowKey.of(), 1_200L);
        IActionSource actionSource = IActionSource.ofMachine(provider);
        AtomicReference<Future<ICraftingPlan>> planFuture = new AtomicReference<>();
        AtomicReference<ICraftingPlan> completedPlan = new AtomicReference<>();

        drive.getInternalInventory().setItemDirect(0, DEItems.DATA_FLOW_CELL_1K.toStack());
        drive.getInternalInventory().setItemDirect(1, AEItems.ITEM_CELL_64K.stack());
        drive.getInternalInventory().setItemDirect(2, AEItems.FLUID_CELL_64K.stack());
        // AE2 excludes the requested output key from its planning inventory, so keep the recipe seed in the machine.
        reassembler.getStorageInventory().setItemDirect(
                DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT,
                DEItems.DATA_CRYSTAL.toStack(16));

        helper.startSequence()
                .thenWaitUntil(() -> awaitRealNetwork(helper, reassembler, provider, drive, craftingStorage))
                .thenExecute(() -> {
                    IGrid grid = requireGrid(provider);
                    insertIntoNetwork(helper, grid, DataFlowKey.of(), 2_400L);
                    insertIntoNetwork(helper, grid, itemKey(DEItems.DATA_DUST.toStack()), 32L);
                    insertIntoNetwork(helper, grid, AEFluidKey.of(Fluids.WATER), 100L);
                    provider.getLogic().getPatternInv().setItemDirect(0, encodedPattern);
                    provider.getLogic().updatePatterns();
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        !requireGrid(provider).getCraftingService().getCraftingFor(dataCrystal).isEmpty(),
                        "The real AE2 pattern provider has not published the Data Crystal pattern"))
                .thenExecute(() -> planFuture.set(requireGrid(provider)
                        .getCraftingService()
                        .beginCraftingCalculation(
                                helper.getLevel(),
                                () -> actionSource,
                                dataCrystal,
                                96L,
                                CalculationStrategy.REPORT_MISSING_ITEMS)))
                .thenWaitUntil(() -> completedPlan.set(awaitPlan(planFuture.get())))
                .thenExecute(() -> {
                    ICraftingPlan plan = completedPlan.get();
                    assertDirectCustomKeyPlan(helper, plan, wrappedDataFlow, dataCrystal);

                    IGrid grid = requireGrid(provider);
                    ICraftingCPU cpu = grid.getCraftingService().getCpus().stream()
                            .filter(candidate -> !candidate.isBusy())
                            .findFirst()
                            .orElseThrow(() -> new GameTestAssertException("The real AE2 crafting CPU is not idle"));
                    ICraftingSubmitResult result = grid.getCraftingService().submitJob(
                            plan,
                            null,
                            cpu,
                            true,
                            actionSource);
                    helper.assertTrue(result.successful(),
                            "The real AE2 crafting job must submit successfully: " + result.errorCode());
                })
                .thenWaitUntil(() -> assertReassemblerReceivedPatternInputs(helper, reassembler))
                .thenSucceed();
    }

    @TestHolder("standard_pattern_menu_encodes_direct_custom_key")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void standardPatternMenuEncodesDirectCustomKey(GameTestHelper helper) {
        CableBusBlockEntity partHost = placeCableBus(helper);
        IPart installedPart = partHost.addPart(
                AEParts.PATTERN_ENCODING_TERMINAL.get(),
                Direction.NORTH,
                null);
        if (!(installedPart instanceof PatternEncodingTerminalPart terminal)) {
            throw new GameTestAssertException("Failed to install a real AE2 pattern encoding terminal part");
        }

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        terminal.getGridNode() != null,
                        "The AE2 pattern terminal node is not ready"))
                .thenExecute(() -> {
                    PatternEncodingLogic logic = terminal.getLogic();
                    configureWrappedProcessingPattern(logic);

                    PatternEncodingTermMenu menu = new PatternEncodingTermMenu(
                            PatternEncodingTermMenu.TYPE,
                            0,
                            new Inventory(helper.makeMockPlayer(GameType.CREATIVE)),
                            terminal,
                            false);
                    menu.setMode(EncodingMode.PROCESSING);
                    menu.encode();

                    assertMenuEncodedDirectCustomKey(helper, logic, "The standard AE2 pattern menu");
                })
                .thenSucceed();
    }

    @TestHolder("data_reassembler_processing_pattern_normalizes_only_custom_wrappers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternNormalizesOnlyCustomWrappers(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(9);
        ConfigInventory outputs = configInventory(4);
        AEItemKey wrappedDataFlow = wrappedKey(DataFlowKey.of(), 1_200L);
        AEItemKey wrappedData = wrappedKey(DataKey.of(), 6L);
        GenericStack dataDust = new GenericStack(itemKey(DEItems.DATA_DUST.toStack()), 32L);
        GenericStack ordinaryItem = new GenericStack(itemKey(new ItemStack(Items.IRON_INGOT)), 16L);
        GenericStack water = new GenericStack(AEFluidKey.of(Fluids.WATER), 100L);
        GenericStack nonTargetWrapper = new GenericStack(wrappedKey(AEFluidKey.of(Fluids.LAVA), 125L), 2L);
        GenericStack directData = new GenericStack(DataKey.of(), 7L);
        GenericStack realisticInnerAmount = new GenericStack(wrappedKey(DataFlowKey.of(), 2_400L), 1L);
        GenericStack realisticOuterAmount = new GenericStack(wrappedKey(DataKey.of(), 1L), 30L);
        GenericStack dataCrystalOutput = new GenericStack(itemKey(DEItems.DATA_CRYSTAL.toStack()), 96L);
        GenericStack directDataFlow = new GenericStack(DataFlowKey.of(), 9L);
        GenericStack lava = new GenericStack(AEFluidKey.of(Fluids.LAVA), 250L);

        inputs.setStack(0, new GenericStack(wrappedDataFlow, 2L));
        inputs.setStack(1, dataDust);
        inputs.setStack(2, ordinaryItem);
        inputs.setStack(3, water);
        inputs.setStack(4, nonTargetWrapper);
        inputs.setStack(5, directData);
        inputs.setStack(6, realisticInnerAmount);
        inputs.setStack(7, realisticOuterAmount);
        outputs.setStack(0, dataCrystalOutput);
        outputs.setStack(1, new GenericStack(wrappedData, 5L));
        outputs.setStack(2, directDataFlow);
        outputs.setStack(3, lava);

        ItemStack encodedPattern = PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs);
        AEProcessingPattern decodedPattern = requireProcessingPattern(encodedPattern, helper.getLevel());
        List<GenericStack> sparseInputs = decodedPattern.getSparseInputs();
        List<GenericStack> sparseOutputs = decodedPattern.getSparseOutputs();

        helper.assertValueEqual(sparseInputs.size(), 9,
                "Encoding must preserve the complete sparse input layout and ordering");
        helper.assertValueEqual(sparseOutputs.size(), 4,
                "Encoding must preserve the complete sparse output layout and ordering");
        assertSparseStack(helper, sparseInputs, 0, DataFlowKey.of(), 2_400L,
                "Wrapped DataFlow input must normalize and multiply its amounts");
        assertSparseStack(helper, sparseInputs, 1, dataDust.what(), dataDust.amount(),
                "Ordinary item input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 2, ordinaryItem.what(), ordinaryItem.amount(),
                "Ordinary non-output item input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 3, water.what(), water.amount(),
                "Direct fluid input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 4, nonTargetWrapper.what(), nonTargetWrapper.amount(),
                "A wrapped non-target key must remain an item key");
        assertSparseStack(helper, sparseInputs, 5, directData.what(), directData.amount(),
                "Direct Data input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 6, DataFlowKey.of(), 2_400L,
                "A realistic outer-one wrapped DataFlow input must retain its inner amount");
        assertSparseStack(helper, sparseInputs, 7, DataKey.of(), 30L,
                "A realistic inner-one wrapped Data input must retain its outer amount");
        helper.assertTrue(sparseInputs.get(8) == null, "Sparse trailing input holes must remain in order");

        assertSparseStack(helper, sparseOutputs, 0, dataCrystalOutput.what(), dataCrystalOutput.amount(),
                "The requested Data Crystal output must remain unchanged");
        assertSparseStack(helper, sparseOutputs, 1, DataKey.of(), 30L,
                "Wrapped Data output must normalize and multiply its amounts");
        assertSparseStack(helper, sparseOutputs, 2, directDataFlow.what(), directDataFlow.amount(),
                "Direct DataFlow output must remain unchanged");
        assertSparseStack(helper, sparseOutputs, 3, lava.what(), lava.amount(),
                "Direct fluid output must remain unchanged");

        assertSparseStack(helper, inputs.toList(), 0, wrappedDataFlow, 2L,
                "Encoding must not mutate the source input inventory");
        assertSparseStack(helper, inputs.toList(), 6, realisticInnerAmount.what(), realisticInnerAmount.amount(),
                "Encoding must preserve the realistic outer-one source wrapper");
        assertSparseStack(helper, inputs.toList(), 7, realisticOuterAmount.what(), realisticOuterAmount.amount(),
                "Encoding must preserve the realistic inner-one source wrapper");
        assertSparseStack(helper, outputs.toList(), 1, wrappedData, 5L,
                "Encoding must not mutate the source output inventory");
        helper.succeed();
    }

    @TestHolder("data_reassembler_processing_pattern_rejects_invalid_wrapped_amounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternRejectsInvalidWrappedAmounts(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(1);
        ConfigInventory outputs = configInventory(1);
        GenericStack validInput = new GenericStack(itemKey(DEItems.DATA_DUST.toStack()), 1L);
        GenericStack validOutput = new GenericStack(itemKey(DEItems.DATA_CRYSTAL.toStack()), 1L);
        outputs.setStack(0, validOutput);

        inputs.setStack(0, new GenericStack(wrappedKey(DataFlowKey.of(), Long.MAX_VALUE), 2L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "Input amount overflow must reject the entire pattern");

        inputs.setStack(0, new GenericStack(wrappedKey(DataFlowKey.of(), 0L), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A non-positive wrapped input amount must reject the entire pattern");

        setRawStack(inputs, new GenericStack(wrappedKey(DataFlowKey.of(), 1L), -1L), helper.getLevel());
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A non-positive outer input amount must reject the entire pattern");

        setRawStack(inputs, new GenericStack(wrappedKey(DataFlowKey.of(), 1L), 0L), helper.getLevel());
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A zero outer input amount must reject the entire pattern");

        inputs.setStack(0, validInput);
        outputs.setStack(0, new GenericStack(wrappedKey(DataKey.of(), Long.MAX_VALUE), 2L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "Output amount overflow must reject the entire pattern");

        outputs.setStack(0, new GenericStack(wrappedKey(DataKey.of(), -1L), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A non-positive wrapped output amount must reject the entire pattern");
        helper.succeed();
    }

    @TestHolder("data_reassembler_processing_pattern_accepts_exact_long_boundary")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternAcceptsExactLongBoundary(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(1);
        ConfigInventory outputs = configInventory(2);
        inputs.setStack(0, new GenericStack(wrappedKey(DataFlowKey.of(), Long.MAX_VALUE), 1L));
        outputs.setStack(0, new GenericStack(itemKey(DEItems.DATA_CRYSTAL.toStack()), 1L));
        outputs.setStack(1, new GenericStack(wrappedKey(DataKey.of(), 1L), Long.MAX_VALUE));

        ItemStack encodedPattern = PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs);
        AEProcessingPattern decodedPattern = requireProcessingPattern(encodedPattern, helper.getLevel());
        helper.assertValueEqual(decodedPattern.getSparseInputs().size(), 1,
                "The exact long input boundary must preserve its sparse layout");
        helper.assertValueEqual(decodedPattern.getSparseOutputs().size(), 2,
                "The exact long output boundary must preserve its sparse layout");
        assertSparseStack(helper, decodedPattern.getSparseInputs(), 0, DataFlowKey.of(), Long.MAX_VALUE,
                "Long.MAX_VALUE times one must not be treated as input overflow");
        assertSparseStack(helper, decodedPattern.getSparseOutputs(), 1, DataKey.of(), Long.MAX_VALUE,
                "One times Long.MAX_VALUE must not be treated as output overflow");
        helper.succeed();
    }

    @TestHolder("data_reassembler_processing_pattern_preserves_empty_inventory_semantics")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternPreservesEmptyInventorySemantics(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(1);
        ConfigInventory outputs = configInventory(2);
        outputs.setStack(0, new GenericStack(itemKey(DEItems.DATA_CRYSTAL.toStack()), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "An all-empty input inventory must not produce a processing pattern");

        inputs.setStack(0, new GenericStack(itemKey(DEItems.DATA_DUST.toStack()), 1L));
        outputs.clear();
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "An all-empty output inventory must not produce a processing pattern");

        outputs.setStack(1, new GenericStack(itemKey(DEItems.DATA_CRYSTAL.toStack()), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A missing primary output must return null even when a later output slot is populated");
        helper.succeed();
    }

    private static DataRipperReassemblerBlockEntity placeReassembler(GameTestHelper helper) {
        helper.setBlock(PATTERN_SINK_POS, DEBlocks.DATA_RIPPER_REASSEMBLER.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.NORTH));
        BlockEntity blockEntity = helper.getBlockEntity(PATTERN_SINK_POS);
        if (blockEntity instanceof DataRipperReassemblerBlockEntity reassembler) {
            return reassembler;
        }
        throw new GameTestAssertException("Placed Data Reassembler has no matching block entity");
    }

    private static PatternProviderBlockEntity placePatternProvider(GameTestHelper helper) {
        helper.setBlock(PATTERN_PROVIDER_POS, AEBlocks.PATTERN_PROVIDER.block()
                .defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
        helper.setBlock(ENERGY_CELL_POS, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(PATTERN_PROVIDER_POS);
        if (blockEntity instanceof PatternProviderBlockEntity provider) {
            return provider;
        }
        throw new GameTestAssertException("Placed AE2 pattern provider has no matching block entity");
    }

    private static DriveBlockEntity placeDrive(GameTestHelper helper) {
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(DRIVE_POS);
        if (blockEntity instanceof DriveBlockEntity drive) {
            return drive;
        }
        throw new GameTestAssertException("Placed AE2 drive has no matching block entity");
    }

    private static CraftingBlockEntity placeCraftingCpu(GameTestHelper helper) {
        helper.setBlock(CRAFTING_STORAGE_POS, AEBlocks.CRAFTING_STORAGE_4K.block().defaultBlockState());
        helper.setBlock(CRAFTING_ACCELERATOR_POS, AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(CRAFTING_STORAGE_POS);
        if (blockEntity instanceof CraftingBlockEntity craftingStorage) {
            return craftingStorage;
        }
        throw new GameTestAssertException("Placed AE2 crafting storage has no matching block entity");
    }

    private static ItemStack dataCrystalProcessingPattern() {
        ConfigInventory inputs = configInventory(3);
        ConfigInventory outputs = configInventory(1);
        inputs.setStack(0, new GenericStack(itemKey(DEItems.DATA_DUST.toStack()), 32L));
        inputs.setStack(1, new GenericStack(AEFluidKey.of(Fluids.WATER), 100L));
        inputs.setStack(2, new GenericStack(wrappedKey(DataFlowKey.of(), 1_200L), 2L));
        outputs.setStack(0, new GenericStack(itemKey(DEItems.DATA_CRYSTAL.toStack()), 96L));

        ItemStack encodedPattern = PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs);
        if (encodedPattern == null || encodedPattern.isEmpty()) {
            throw new GameTestAssertException("Data Crystal processing pattern encoding returned no pattern");
        }
        return encodedPattern;
    }

    private static void awaitRealNetwork(
                                         GameTestHelper helper,
                                         DataRipperReassemblerBlockEntity reassembler,
                                         PatternProviderBlockEntity provider,
                                         DriveBlockEntity drive,
                                         CraftingBlockEntity craftingStorage) {
        helper.assertTrue(reassembler.isOnline(), "The Data Reassembler is not online on the real AE2 network");
        awaitRealNetwork(helper, provider, drive, craftingStorage);
    }

    private static void awaitRealNetwork(GameTestHelper helper,
                                         PatternProviderBlockEntity provider,
                                         DriveBlockEntity drive,
                                         CraftingBlockEntity craftingStorage) {
        helper.assertTrue(provider.getMainNode().isActive(), "The real AE2 pattern provider is not active");
        helper.assertTrue(drive.getMainNode().isActive(), "The real AE2 drive is not active");
        helper.assertTrue(craftingStorage.isFormed(), "The real AE2 crafting CPU has not formed");
        helper.assertTrue(craftingStorage.getMainNode().isActive(), "The real AE2 crafting CPU is not active");
        helper.assertTrue(!requireGrid(provider).getCraftingService().getCpus().isEmpty(),
                "The real AE2 crafting CPU has not been published");
    }

    private static IGrid requireGrid(PatternProviderBlockEntity provider) {
        IGrid grid = provider.getMainNode().getGrid();
        if (grid == null) {
            throw new GameTestAssertException("The real AE2 pattern provider has no grid");
        }
        return grid;
    }

    private static void insertIntoNetwork(GameTestHelper helper, IGrid grid, AEKey key, long amount) {
        long inserted = grid.getStorageService()
                .getInventory()
                .insert(key, amount, Actionable.MODULATE, IActionSource.empty());
        helper.assertValueEqual(inserted, amount, "The real AE2 network must store the complete ingredient: " + key);
    }

    private static ICraftingPlan awaitPlan(Future<ICraftingPlan> future) {
        if (future == null || !future.isDone()) {
            throw new GameTestAssertException("The real AE2 crafting plan is still calculating");
        }
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calculating the real AE2 crafting plan", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("The real AE2 crafting plan failed", exception.getCause());
        }
    }

    private static void assertDirectCustomKeyPlan(
                                                  GameTestHelper helper,
                                                  ICraftingPlan plan,
                                                  AEItemKey wrappedDataFlow,
                                                  AEItemKey dataCrystal) {
        helper.assertTrue(plan.missingItems().isEmpty(),
                "The real AE2 crafting plan must have no missing ingredients: " + plan.missingItems().keySet());
        helper.assertFalse(plan.simulation(), "The real AE2 crafting plan must not be a missing-item simulation");
        helper.assertValueEqual(plan.finalOutput().what(), dataCrystal,
                "The real AE2 crafting plan must target Data Crystal");
        helper.assertValueEqual(plan.finalOutput().amount(), 96L,
                "The real AE2 crafting plan must request the complete Data Crystal output");
        helper.assertValueEqual(plan.usedItems().get(DataFlowKey.of()), 2_400L,
                "The real AE2 crafting plan must consume direct DataFlow");
        helper.assertValueEqual(plan.usedItems().get(wrappedDataFlow), 0L,
                "The real AE2 crafting plan must not consume the wrapped AEK item");
        helper.assertValueEqual(plan.usedItems().get(itemKey(DEItems.DATA_DUST.toStack())), 32L,
                "The real AE2 crafting plan must consume Data Dust");
        helper.assertValueEqual(plan.usedItems().get(AEFluidKey.of(Fluids.WATER)), 100L,
                "The real AE2 crafting plan must consume water");
        helper.assertValueEqual(plan.patternTimes().size(), 1,
                "The real AE2 crafting plan must use one processing pattern");
        helper.assertValueEqual(plan.patternTimes().values().iterator().next(), 1L,
                "The real AE2 crafting plan must execute the processing pattern once");
    }

    private static void assertReassemblerReceivedPatternInputs(
                                                               GameTestHelper helper,
                                                               DataRipperReassemblerBlockEntity reassembler) {
        GenericStack keyInput = reassembler.getKeyInputStack();
        helper.assertTrue(keyInput != null, "The Data Reassembler has not received the direct DataFlow input");
        helper.assertValueEqual(keyInput.what(), DataFlowKey.of(),
                "The Data Reassembler must receive DataFlow as a direct key");
        helper.assertValueEqual(keyInput.amount(), 2_400L,
                "The Data Reassembler must receive the complete direct DataFlow amount");
        helper.assertValueEqual(countInputItem(reassembler, itemKey(DEItems.DATA_DUST.toStack())), 32L,
                "The Data Reassembler must receive Data Dust");
        helper.assertValueEqual(countInputItem(reassembler, itemKey(DEItems.DATA_CRYSTAL.toStack())), 16L,
                "The Data Reassembler must retain the preloaded Data Crystal recipe input");
        helper.assertValueEqual(AEFluidKey.of(reassembler.getFluidInputA()), AEFluidKey.of(Fluids.WATER),
                "The Data Reassembler must receive water");
        helper.assertValueEqual(reassembler.getFluidInputA().getAmount(), 100,
                "The Data Reassembler must receive the complete water amount");
        helper.assertTrue(reassembler.getProgress() > 0,
                "The Data Reassembler must start processing the submitted AE2 job");
    }

    private static long countInputItem(DataRipperReassemblerBlockEntity reassembler, AEItemKey expected) {
        long count = 0L;
        for (int slot = DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT; slot < DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT +
                DataRipperReassemblerBlockEntity.ITEM_INPUT_SLOT_COUNT; slot++) {
            ItemStack stack = reassembler.getStorageInventory().getStackInSlot(slot);
            if (expected.equals(AEItemKey.of(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ConfigInventory configInventory(int size) {
        return ConfigInventory.configStacks(size).allowOverstacking(true).build();
    }

    private static CableBusBlockEntity placeCableBus(GameTestHelper helper) {
        helper.setBlock(TERMINAL_HOST_POS, AEBlocks.CABLE_BUS.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(TERMINAL_HOST_POS);
        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            return cableBus;
        }
        throw new GameTestAssertException("Placed AE cable bus has no matching block entity");
    }

    private static void configureWrappedProcessingPattern(PatternEncodingLogic logic) {
        logic.setMode(EncodingMode.PROCESSING);
        logic.getEncodedInputInv().setStack(
                0,
                new GenericStack(wrappedKey(DataFlowKey.of(), 1_200L), 2L));
        logic.getEncodedOutputInv().setStack(
                0,
                new GenericStack(itemKey(DEItems.DATA_CRYSTAL.toStack()), 96L));
        logic.getEncodedPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack());
    }

    private static void assertMenuEncodedDirectCustomKey(
                                                         GameTestHelper helper,
                                                         PatternEncodingLogic logic,
                                                         String menuDescription) {
        ItemStack encodedPattern = logic.getEncodedPatternInv().getStackInSlot(0);
        AEProcessingPattern decodedPattern = requireProcessingPattern(encodedPattern, helper.getLevel());
        assertSparseStack(
                helper,
                decodedPattern.getSparseInputs(),
                0,
                DataFlowKey.of(),
                2_400L,
                menuDescription + " must encode the wrapped DataFlow input as a direct key");
        assertSparseStack(
                helper,
                decodedPattern.getSparseOutputs(),
                0,
                itemKey(DEItems.DATA_CRYSTAL.toStack()),
                96L,
                menuDescription + " must retain Data Crystal as the primary output");
    }

    private static AEItemKey wrappedKey(AEKey key, long innerAmount) {
        return itemKey(GenericStack.wrapInItemStack(key, innerAmount));
    }

    private static AEItemKey itemKey(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalArgumentException("Item stack has no AE item key: " + stack);
        }
        return key;
    }

    private static AEProcessingPattern requireProcessingPattern(ItemStack encodedPattern, ServerLevel level) {
        if (encodedPattern == null || encodedPattern.isEmpty()) {
            throw new GameTestAssertException("Processing pattern encoding returned no pattern");
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(encodedPattern, level);
        if (details instanceof AEProcessingPattern processingPattern) {
            return processingPattern;
        }
        throw new GameTestAssertException("Encoded stack did not decode as an AE2 processing pattern: " + details);
    }

    private static void assertSparseStack(
                                          GameTestHelper helper,
                                          List<GenericStack> stacks,
                                          int slot,
                                          AEKey expectedKey,
                                          long expectedAmount,
                                          String message) {
        GenericStack stack = stacks.get(slot);
        helper.assertTrue(stack != null, message + ": slot was empty");
        helper.assertValueEqual(stack.what(), expectedKey, message + ": key");
        helper.assertValueEqual(stack.amount(), expectedAmount, message + ": amount");
    }

    private static void setRawStack(ConfigInventory inventory, GenericStack stack, ServerLevel level) {
        ListTag encoded = new ListTag();
        encoded.add(GenericStack.writeTag(level.registryAccess(), stack));
        inventory.readFromTag(encoded, level.registryAccess());
    }
}
