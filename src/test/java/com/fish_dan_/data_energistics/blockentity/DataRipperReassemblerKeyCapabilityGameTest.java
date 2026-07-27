package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.GenericStack;
import appeng.util.SettingsFrom;

import java.util.EnumSet;
import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerKeyCapabilityGameTest {

    private static final BlockPos SOURCE_POS = new BlockPos(2, 2, 2);
    private static final BlockPos TARGET_POS = SOURCE_POS.east();

    private DataRipperReassemblerKeyCapabilityGameTest() {}

    @TestHolder("data_reassembler_accepts_direct_key_from_digital_storage_depot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void acceptsDirectKeyFromDigitalStorageDepot(GameTestHelper helper) {
        helper.setBlock(SOURCE_POS, ModBlocks.DIGITAL_STORAGE_DEPOT.get());
        helper.setBlock(TARGET_POS, ModBlocks.DATA_RIPPER_REASSEMBLER.get());

        DigitalStorageDepotBlockEntity depot = requireDepot(helper, SOURCE_POS);
        DataRipperReassemblerBlockEntity reassembler = requireReassembler(helper, TARGET_POS);
        var keyInventory = helper.getLevel().getCapability(
                AECapabilities.GENERIC_INTERNAL_INV,
                helper.absolutePos(TARGET_POS),
                Direction.WEST);
        helper.assertTrue(keyInventory != null, "The reassembler must expose a generic key inventory");
        helper.assertValueEqual(keyInventory.size(), DataRipperReassemblerBlockEntity.KEY_SLOT_COUNT,
                "The reassembler capability must expose its key input and output slots");

        long inserted = depot.getExternalKeyInventory().insert(0, DataFlowKey.of(), 100L, Actionable.MODULATE);
        helper.assertValueEqual(inserted, 100L, "The depot must accept the test Data Flow");
        configureDepotKeyOutput(depot, Direction.EAST);
        depot.setAutoExportMode(DataExtractorAutoExportMode.CONTAINER);

        depot.serverTick();

        GenericStack received = reassembler.getKeyInputStack();
        helper.assertTrue(received != null && DataFlowKey.of().equals(received.what()),
                "The reassembler key input must receive Data Flow directly");
        helper.assertValueEqual(received.amount(), 100L,
                "The reassembler key input must receive the complete amount");
        helper.assertTrue(depot.getKeyStack(0) == null,
                "The depot key slot must clear after the direct transfer");
        helper.succeed();
    }

    @TestHolder("data_reassembler_exports_key_output_to_generic_inventory")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exportsKeyOutputToGenericInventory(GameTestHelper helper) {
        helper.setBlock(SOURCE_POS, ModBlocks.DATA_RIPPER_REASSEMBLER.get());
        helper.setBlock(TARGET_POS, ModBlocks.DATA_MIMETIC_FIELD.get());

        DataRipperReassemblerBlockEntity reassembler = requireReassembler(helper, SOURCE_POS);
        DataMimeticFieldBlockEntity mimeticField = requireMimeticField(helper, TARGET_POS);
        reassembler.getKeyOutputMenuInventory().setItemDirect(
                0,
                GenericStack.wrapInItemStack(DataFlowKey.of(), 100L));
        configureReassemblerOutput(reassembler, Direction.EAST);
        reassembler.getConfigManager().putSetting(Settings.AUTO_EXPORT, YesNo.YES);

        reassembler.serverTick();

        GenericStack received = mimeticField.getKeyInputStack();
        helper.assertTrue(received != null && DataFlowKey.of().equals(received.what()),
                "The adjacent generic key slot must receive the reassembler output directly");
        helper.assertValueEqual(received.amount(), 100L,
                "The adjacent generic key slot must receive the complete reassembler output");
        helper.assertTrue(reassembler.getKeyOutputStack() == null,
                "The reassembler key output must clear after the direct transfer");
        helper.succeed();
    }

    @TestHolder("data_reassembler_keeps_typed_output_sides_independent")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsTypedOutputSidesIndependent(GameTestHelper helper) {
        helper.setBlock(SOURCE_POS, ModBlocks.DATA_RIPPER_REASSEMBLER.get());
        DataRipperReassemblerBlockEntity reassembler = requireReassembler(helper, SOURCE_POS);

        Set<Direction> itemSides = EnumSet.of(Direction.NORTH, Direction.WEST);
        Set<Direction> fluidSides = EnumSet.of(Direction.SOUTH);
        Set<Direction> keySides = EnumSet.of(Direction.UP, Direction.EAST);
        configureOutputSides(reassembler, DigitalStorageDepotOutputType.ITEMS, itemSides);
        configureOutputSides(reassembler, DigitalStorageDepotOutputType.FLUIDS, fluidSides);
        configureOutputSides(reassembler, DigitalStorageDepotOutputType.KEYS, keySides);

        var settings = reassembler.exportSettings(SettingsFrom.MEMORY_CARD, null);
        configureOutputSides(reassembler, DigitalStorageDepotOutputType.ITEMS, Set.of());
        configureOutputSides(reassembler, DigitalStorageDepotOutputType.FLUIDS, Set.of());
        configureOutputSides(reassembler, DigitalStorageDepotOutputType.KEYS, Set.of());
        reassembler.importSettings(SettingsFrom.MEMORY_CARD, settings, null);

        helper.assertValueEqual(reassembler.getOutputSides(DigitalStorageDepotOutputType.ITEMS), itemSides,
                "The memory card must restore only the configured item output sides");
        helper.assertValueEqual(reassembler.getOutputSides(DigitalStorageDepotOutputType.FLUIDS), fluidSides,
                "The memory card must restore only the configured fluid output sides");
        helper.assertValueEqual(reassembler.getOutputSides(DigitalStorageDepotOutputType.KEYS), keySides,
                "The memory card must restore only the configured key output sides");
        helper.succeed();
    }

    @TestHolder("data_reassembler_migrates_legacy_output_sides_to_all_types")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void migratesLegacyOutputSidesToAllTypes(GameTestHelper helper) {
        helper.setBlock(SOURCE_POS, ModBlocks.DATA_RIPPER_REASSEMBLER.get());
        DataRipperReassemblerBlockEntity reassembler = requireReassembler(helper, SOURCE_POS);

        CompoundTag legacyData = new CompoundTag();
        ListTag sides = new ListTag();
        sides.add(StringTag.valueOf(Direction.NORTH.getName()));
        legacyData.put("output_sides", sides);
        reassembler.loadTag(legacyData, helper.getLevel().registryAccess());
        for (DigitalStorageDepotOutputType outputType : DigitalStorageDepotOutputType.values()) {
            helper.assertValueEqual(reassembler.getOutputSides(outputType), EnumSet.of(Direction.NORTH),
                    "A legacy output mask must migrate to " + outputType);
        }

        legacyData.put("output_sides", new ListTag());
        reassembler.loadTag(legacyData, helper.getLevel().registryAccess());
        for (DigitalStorageDepotOutputType outputType : DigitalStorageDepotOutputType.values()) {
            helper.assertTrue(reassembler.getOutputSides(outputType).isEmpty(),
                    "A deliberately empty legacy output mask must stay empty for " + outputType);
        }
        helper.succeed();
    }

    private static void configureDepotKeyOutput(DigitalStorageDepotBlockEntity depot, Direction outputSide) {
        for (Direction direction : Direction.values()) {
            depot.setOutputSideEnabled(DigitalStorageDepotOutputType.KEYS, direction, direction == outputSide);
        }
    }

    private static void configureReassemblerOutput(DataRipperReassemblerBlockEntity reassembler,
                                                   Direction outputSide) {
        configureOutputSides(
                reassembler,
                DigitalStorageDepotOutputType.KEYS,
                EnumSet.of(outputSide));
    }

    private static void configureOutputSides(DataRipperReassemblerBlockEntity reassembler,
                                             DigitalStorageDepotOutputType outputType,
                                             Set<Direction> outputSides) {
        for (Direction direction : Direction.values()) {
            reassembler.setOutputSideEnabled(
                    outputType,
                    direction,
                    outputSides.contains(direction));
        }
    }

    private static DigitalStorageDepotBlockEntity requireDepot(GameTestHelper helper, BlockPos position) {
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof DigitalStorageDepotBlockEntity depot) {
            return depot;
        }
        throw new GameTestAssertException("Placed digital storage depot has no matching block entity");
    }

    private static DataRipperReassemblerBlockEntity requireReassembler(GameTestHelper helper, BlockPos position) {
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof DataRipperReassemblerBlockEntity reassembler) {
            return reassembler;
        }
        throw new GameTestAssertException("Placed data reassembler has no matching block entity");
    }

    private static DataMimeticFieldBlockEntity requireMimeticField(GameTestHelper helper, BlockPos position) {
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof DataMimeticFieldBlockEntity mimeticField) {
            return mimeticField;
        }
        throw new GameTestAssertException("Placed data mimetic field has no matching block entity");
    }
}
