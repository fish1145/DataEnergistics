package com.fish_dan_.data_energistics.common.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.blockentity.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.CompartmentSlotLabel;
import com.fish_dan_.data_energistics.menu.CompositeWarehouseMenu;
import com.fish_dan_.data_energistics.menu.MeCompositeInputWarehouseMenu;
import com.fish_dan_.data_energistics.menu.MeCompositeOutputWarehouseMenu;
import com.fish_dan_.data_energistics.menu.MePatternBufferMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.IOptionalSlot;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CompartmentInventoryTest {

    private CompartmentInventoryTest() {}

    @TestHolder("compartment_inventory_capacity_cards_gate_writable_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capacityCardsGateWritableSlots(GameTestHelper helper) {
        CompartmentInventory inventory = CompartmentInventory.storage(3, () -> {}, () -> 2);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        helper.assertValueEqual(
                inventory.insert(0, iron, 1L, Actionable.MODULATE),
                1L,
                "Unlocked slot 0 should accept inserted keys");
        helper.assertValueEqual(
                inventory.insert(1, iron, 1L, Actionable.MODULATE),
                1L,
                "Unlocked slot 1 should accept inserted keys");
        helper.assertValueEqual(
                inventory.insert(2, iron, 1L, Actionable.MODULATE),
                0L,
                "Locked slot 2 should reject inserted keys");
        helper.assertValueEqual(inventory.getAmount(2), 0L, "Locked slot should remain empty");
        helper.succeed();
    }

    @TestHolder("compartment_inventory_fluid_slot_accepts_only_fluid_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void fluidSlotAcceptsOnlyFluidKeys(GameTestHelper helper) {
        CompartmentInventory inventory = CompartmentInventory.fluidConfig(() -> {});
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        inventory.setStack(0, new GenericStack(iron, 1L));
        helper.assertTrue(inventory.getKey(0) == null, "Fluid slot should reject item keys");

        inventory.setStack(0, new GenericStack(water, AEFluidKey.AMOUNT_BUCKET));
        helper.assertValueEqual(inventory.getKey(0), water, "Fluid slot should accept fluid keys");

        CompartmentInventory twoSlotInventory = CompartmentInventory.fluidConfig(() -> {}, 2);
        twoSlotInventory.setStack(1, new GenericStack(iron, 1L));
        helper.assertTrue(twoSlotInventory.getKey(1) == null, "Second fluid slot should reject item keys");
        twoSlotInventory.setStack(1, new GenericStack(water, AEFluidKey.AMOUNT_BUCKET));
        helper.assertValueEqual(twoSlotInventory.getKey(1), water, "Second fluid slot should accept fluid keys");
        helper.succeed();
    }

    @TestHolder("compartment_inventory_key_slot_accepts_wrapped_generic_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keySlotAcceptsWrappedGenericKeys(GameTestHelper helper) {
        CompartmentInventory inventory = CompartmentInventory.keyConfig(() -> {});

        inventory.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
        helper.assertTrue(inventory.getKey(0) == null, "Key slot should reject plain item keys");

        inventory.setStack(0, new GenericStack(DataKey.of(), 128L));

        helper.assertValueEqual(inventory.getKey(0), DataKey.of(), "Key slot should store the unwrapped AEKey");
        helper.assertValueEqual(inventory.getAmount(0), 128L, "Key slot should preserve long amounts");
        helper.succeed();
    }

    @TestHolder("compartment_inventory_key_slot_unwraps_generic_stack_item")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keySlotUnwrapsGenericStackItem(GameTestHelper helper) {
        CompartmentInventory inventory = CompartmentInventory.keyConfig(() -> {});
        AEItemKey wrappedKey = AEItemKey.of(GenericStack.wrapInItemStack(DataKey.of(), 4096L));

        inventory.setStack(0, new GenericStack(wrappedKey, 1L));

        helper.assertValueEqual(inventory.getKey(0), DataKey.of(), "Key slot should unwrap GenericStack item keys");
        helper.assertValueEqual(inventory.getAmount(0), 4096L, "Key slot should preserve wrapped GenericStack amount");
        helper.succeed();
    }

    @TestHolder("compartment_inventory_key_slot_accepts_wrapped_item_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keySlotAcceptsWrappedItemKeys(GameTestHelper helper) {
        CompartmentInventory inventory = CompartmentInventory.keyConfig(() -> {});
        AEItemKey itemKey = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey wrappedKey = AEItemKey.of(GenericStack.wrapInItemStack(itemKey, 2048L));

        inventory.setStack(0, new GenericStack(wrappedKey, 1L));

        helper.assertValueEqual(inventory.getKey(0), itemKey, "Key slot should unwrap wrapped item keys");
        helper.assertValueEqual(inventory.getAmount(0), 2048L, "Key slot should preserve wrapped item key amount");
        helper.succeed();
    }

    @TestHolder("compartment_inventory_key_slot_accepts_wrapped_fluid_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keySlotAcceptsWrappedFluidKeys(GameTestHelper helper) {
        CompartmentInventory inventory = CompartmentInventory.keyConfig(() -> {});
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        AEItemKey wrappedKey = AEItemKey.of(GenericStack.wrapInItemStack(water, AEFluidKey.AMOUNT_BUCKET * 128L));

        inventory.setStack(0, new GenericStack(wrappedKey, 1L));

        helper.assertValueEqual(inventory.getKey(0), water, "Key slot should unwrap wrapped fluid keys");
        helper.assertValueEqual(
                inventory.getAmount(0),
                AEFluidKey.AMOUNT_BUCKET * 128L,
                "Key slot should preserve wrapped fluid key amount");
        helper.succeed();
    }

    @TestHolder("compartment_storage_display_inventory_uses_stable_sorted_window")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storageDisplayInventoryUsesStableSortedWindow(GameTestHelper helper) {
        CompartmentStorage storage = new CompartmentStorageImpl(() -> {});
        storage.insert(DataKey.of(), 200L, false);
        storage.insert(AEItemKey.of(Items.IRON_INGOT), 3L, false);

        CompartmentStorageDisplayInventory display = new CompartmentStorageDisplayInventory(() -> storage, 2);

        GenericStack first = GenericStack.unwrapItemStack(display.getStackInSlot(0));
        GenericStack second = GenericStack.unwrapItemStack(display.getStackInSlot(1));
        if (first == null || second == null) {
            helper.fail("Display slots should wrap stored GenericStacks");
            return;
        }
        GenericStack firstAgain = GenericStack.unwrapItemStack(display.getStackInSlot(0));
        if (firstAgain == null) {
            helper.fail("Display slot should remain populated on repeated reads");
            return;
        }
        helper.assertValueEqual(firstAgain.what(), first.what(), "Display ordering should be stable across reads");
        helper.assertValueEqual(firstAgain.amount(), first.amount(), "Display amount should be stable across reads");
        boolean dataKeyVisible = (first.what().equals(DataKey.of()) && first.amount() == 200L) ||
                (second.what().equals(DataKey.of()) && second.amount() == 200L);
        boolean itemKeyVisible = first.what().equals(AEItemKey.of(Items.IRON_INGOT)) ||
                second.what().equals(AEItemKey.of(Items.IRON_INGOT));
        helper.assertTrue(dataKeyVisible, "Display window should contain the custom key with long amount");
        helper.assertTrue(itemKeyVisible, "Display window should contain the item key");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_capacity_cards_unlock_plain_warehouse_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capacityCardsUnlockPlainWarehouseSlots(GameTestHelper helper) {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        for (int capacityCards = 0; capacityCards <= 5; capacityCards++) {
            CompositeWarehouseBlockEntity input = compositeWarehouse(
                    ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
            installCapacityCards(input, capacityCards);
            int expectedRows = Math.min(
                    CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ROWS,
                    CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS + capacityCards);
            int expectedSlots = expectedRows * CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_COLUMNS;
            int expectedMainSlots = expectedRows * CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ITEM_COLUMNS;
            int expectedFluidSlots = expectedRows;
            int expectedKeySlots = expectedRows;

            helper.assertValueEqual(
                    input.configurableSlotLimit(),
                    CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_CONFIGURABLE_SLOTS,
                    "Plain input warehouse should expose the storage-bus style 7x9 config area");
            helper.assertValueEqual(
                    input.unlockedSlotCount(),
                    expectedSlots,
                    capacityCards + " capacity cards should unlock the expected 9-wide input rows");
            helper.assertValueEqual(
                    input.slotStorage().insert(expectedMainSlots - 1, iron, 1L, Actionable.MODULATE),
                    1L,
                    "Last unlocked plain input slot should accept inserts");
            if (expectedMainSlots < CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ITEM_SLOTS) {
                helper.assertValueEqual(
                        input.slotStorage().insert(expectedMainSlots, iron, 1L, Actionable.MODULATE),
                        0L,
                        "First locked plain input slot should reject inserts");
                helper.assertValueEqual(
                        input.slotStorage().getAmount(expectedMainSlots),
                        0L,
                        "First locked plain input slot should remain empty");
            }
            helper.assertValueEqual(
                    input.fluidConfig().insert(expectedFluidSlots - 1, AEFluidKey.of(Fluids.WATER), 1L, Actionable.MODULATE),
                    1L,
                    "Last unlocked plain input fluid slot should accept inserts");
            if (expectedFluidSlots < CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_FLUID_CONFIG_SLOTS) {
                helper.assertValueEqual(
                        input.fluidConfig().insert(expectedFluidSlots, AEFluidKey.of(Fluids.WATER), 1L, Actionable.MODULATE),
                        0L,
                        "First locked plain input fluid slot should reject inserts");
            }
            helper.assertValueEqual(
                    input.keyConfig().insert(expectedKeySlots - 1, DataKey.of(), 1L, Actionable.MODULATE),
                    1L,
                    "Last unlocked plain input key slot should accept inserts");
            if (expectedKeySlots < CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_KEY_CONFIG_SLOTS) {
                helper.assertValueEqual(
                        input.keyConfig().insert(expectedKeySlots, DataKey.of(), 1L, Actionable.MODULATE),
                        0L,
                        "First locked plain input key slot should reject inserts");
            }
        }

        CompositeWarehouseBlockEntity output = compositeWarehouse(
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        installCapacityCards(output);
        helper.assertValueEqual(
                output.configurableSlotLimit(),
                CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_CONFIGURABLE_SLOTS,
                "Plain output warehouse should expose 63 storage-bus style config slots");
        helper.assertValueEqual(
                output.unlockedSlotCount(),
                CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_CONFIGURABLE_SLOTS,
                "Five capacity cards should unlock every output config slot");
        helper.assertValueEqual(
                output.slotStorage().insert(CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ITEM_SLOTS - 1, iron, 1L, Actionable.MODULATE),
                1L,
                "Last plain output slot should accept inserts after five capacity cards");
        helper.assertValueEqual(
                output.slotStorage().insert(CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ITEM_SLOTS, iron, 1L, Actionable.MODULATE),
                0L,
                "Right composite column must not become a plain output slot");

        MeCompositeInputWarehouseBlockEntity meInput = meInputWarehouse();
        helper.assertValueEqual(meInput.configurableSlotLimit(), 25, "ME input warehouse should expose 25 marker groups");
        helper.assertValueEqual(meInput.unlockedSlotCount(), 25, "Capacity cards should not unlock hidden ME input groups");
        helper.assertValueEqual(
                meInput.markerInventory().insert(24, iron, 1L, Actionable.MODULATE),
                1L,
                "Last visible ME input marker slot should accept inserts");
        helper.assertValueEqual(
                meInput.markerInventory().insert(25, iron, 1L, Actionable.MODULATE),
                0L,
                "Hidden ME input marker slot should reject inserts");
        helper.assertValueEqual(
                meInput.meInputBuffer().insert(24, iron, 2L, Actionable.MODULATE),
                2L,
                "Last visible ME input buffer slot should accept pulled contents");
        helper.assertValueEqual(
                meInput.meInputBuffer().insert(25, iron, 2L, Actionable.MODULATE),
                0L,
                "Hidden ME input buffer slot should reject pulled contents");
        helper.assertValueEqual(meInput.storage().amount(iron), 2L, "Only visible ME input buffer contents should aggregate");

        MePatternBufferBlockEntity patternBuffer = patternBuffer();
        AEItemKey encodedPattern = AEItemKey.of(encodedProcessingPattern());
        helper.assertValueEqual(
                patternBuffer.configurableSlotLimit(),
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT,
                "Pattern buffer should expose the full 9x6 pattern area drawn by the texture");
        helper.assertValueEqual(
                patternBuffer.patternStorage().size(),
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT,
                "Pattern buffer pattern inventory should match the visible 9x6 area");
        helper.assertValueEqual(
                patternBuffer.unlockedSlotCount(),
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT,
                "Pattern buffer should unlock every visible pattern slot");
        helper.assertValueEqual(
                patternBuffer.patternStorage().insert(
                        MePatternBufferBlockEntity.PATTERN_SLOT_COUNT - 1,
                        encodedPattern,
                        1L,
                        Actionable.MODULATE),
                1L,
                "Last visible pattern slot should accept inserts");
        helper.assertValueEqual(
                patternBuffer.patternStorage().insert(
                        MePatternBufferBlockEntity.PATTERN_SLOT_COUNT,
                        iron,
                        1L,
                        Actionable.MODULATE),
                0L,
                "First non-texture pattern slot should reject inserts");
        helper.succeed();
    }

    @TestHolder("compartment_menu_locks_capacity_card_when_plain_warehouse_has_overflow")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void menuLocksCapacityCardWhenPlainWarehouseHasOverflow(GameTestHelper helper) {
        BlockPos warehousePos = new BlockPos(1, 1, 1);
        helper.setBlock(warehousePos, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(warehousePos));
        if (!(blockEntity instanceof CompositeWarehouseBlockEntity input)) {
            helper.fail("Expected a placed plain input warehouse block entity", warehousePos);
            return;
        }
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        var player = helper.makeMockPlayer(GameType.CREATIVE);

        int firstExpansionMainSlot = CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS *
                CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ITEM_COLUMNS;
        int firstExpansionCompositeSlot = CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS;

        installCapacityCards(input, 1);
        helper.assertValueEqual(
                input.slotStorage().insert(firstExpansionMainSlot, iron, 1L, Actionable.MODULATE),
                1L,
                "One capacity card should unlock the first storage-bus expansion row");

        CompositeWarehouseMenu menu = new CompositeWarehouseMenu(
                0,
                player.getInventory(),
                input);
        menu.broadcastChanges();
        var upgradeSlot = menu.getSlots(SlotSemantics.UPGRADE).get(0);

        helper.assertFalse(
                input.canRemoveCapacityCard(0),
                "Capacity card should be locked while removing it would strand configured storage slots");
        helper.assertFalse(
                upgradeSlot.mayPickup(player),
                "Capacity card slot should reject pickup while expansion slots contain configured contents");
        helper.assertValueEqual(
                input.slotStorage().getAmount(firstExpansionMainSlot),
                1L,
                "Capacity gating should keep expansion contents instead of clearing them");
        input.slotStorage().setStack(firstExpansionMainSlot, null);
        helper.assertValueEqual(
                input.fluidConfig().insert(firstExpansionCompositeSlot, AEFluidKey.of(Fluids.WATER), 1L, Actionable.MODULATE),
                1L,
                "One capacity card should unlock the first expanded fluid slot");

        helper.assertFalse(
                input.canRemoveCapacityCard(0),
                "Capacity card should stay locked while an expanded fluid slot contains configured contents");
        input.fluidConfig().setStack(firstExpansionCompositeSlot, null);

        helper.assertTrue(
                input.canRemoveCapacityCard(0),
                "Capacity card should unlock after the expansion slots are empty");
        helper.assertTrue(
                upgradeSlot.mayPickup(player),
                "Capacity card slot should allow pickup after removing overflow contents");

        helper.assertValueEqual(
                input.keyConfig().insert(firstExpansionCompositeSlot, DataKey.of(), 1L, Actionable.MODULATE),
                1L,
                "One capacity card should unlock the first expanded key slot");
        helper.assertFalse(
                input.canRemoveCapacityCard(0),
                "Capacity card should stay locked while an expanded key slot contains configured contents");
        helper.succeed();
    }

    @TestHolder("compartment_menu_plain_warehouse_fk_extension_slots_draw_optional_backgrounds")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void plainWarehouseFkExtensionSlotsDrawOptionalBackgrounds(GameTestHelper helper) {
        CompositeWarehouseBlockEntity input = compositeWarehouse(
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        CompositeWarehouseMenu menu = new CompositeWarehouseMenu(
                0,
                player.getInventory(),
                input);

        for (int row = CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS; row < CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ROWS; row++) {
            assertOptionalBackgroundSlot(helper, menu, CompartmentMenu.COMPARTMENT_FLUID, row, 0);
            assertOptionalBackgroundSlot(helper, menu, CompartmentMenu.COMPARTMENT_KEY, row, 1);
        }
        helper.succeed();
    }

    @TestHolder("pattern_buffer_pattern_storage_accepts_only_encoded_patterns")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void patternBufferPatternStorageAcceptsOnlyEncodedPatterns(GameTestHelper helper) {
        CompartmentInventory patterns = patternBuffer().patternStorage();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey encodedPattern = AEItemKey.of(encodedProcessingPattern());

        helper.assertValueEqual(
                patterns.insert(0, iron, 1L, Actionable.MODULATE),
                0L,
                "Pattern buffer pattern slots should reject ordinary items");
        patterns.setStack(0, new GenericStack(iron, 1L));
        helper.assertTrue(patterns.getKey(0) == null, "Direct pattern slot writes should reject ordinary item keys");
        helper.assertValueEqual(
                patterns.insert(0, encodedPattern, 1L, Actionable.MODULATE),
                1L,
                "Pattern buffer pattern slots should accept encoded patterns");
        helper.assertValueEqual(
                patterns.getKey(0),
                encodedPattern,
                "Pattern buffer pattern slot should store the encoded pattern key");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_plain_warehouse_unlocks_extra_fluid_slots_by_capacity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void plainWarehouseUnlocksExtraFluidSlotsByCapacity(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        AEFluidKey lava = AEFluidKey.of(Fluids.LAVA);

        CompositeWarehouseBlockEntity input = compositeWarehouse(
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        int firstExpansionFluidSlot = CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS;
        helper.assertValueEqual(
                input.fluidConfig().insert(0, water, 10L, Actionable.MODULATE),
                10L,
                "Plain warehouse first base fluid slot should accept fluid keys");
        helper.assertValueEqual(
                input.fluidConfig().insert(1, lava, 20L, Actionable.MODULATE),
                20L,
                "Plain warehouse second base fluid slot should accept fluid keys");
        helper.assertValueEqual(
                input.fluidConfig().insert(firstExpansionFluidSlot, water, 30L, Actionable.MODULATE),
                0L,
                "Plain warehouse should lock the first expansion fluid slot until a capacity card unlocks its row");
        helper.assertValueEqual(
                input.storage().amount(water),
                10L,
                "Plain warehouse should aggregate its first base fluid slot");
        helper.assertValueEqual(
                input.storage().amount(lava),
                20L,
                "Plain warehouse should aggregate its second base fluid slot");
        installCapacityCards(input, 1);
        helper.assertValueEqual(
                input.fluidConfig().insert(firstExpansionFluidSlot, water, 30L, Actionable.MODULATE),
                30L,
                "One capacity card should unlock the first expansion fluid slot in the F/K column");
        helper.assertValueEqual(
                input.storage().amount(water),
                40L,
                "Plain warehouse should aggregate unlocked expansion fluid slots");

        MePatternBufferBlockEntity patternBuffer = patternBuffer();
        patternBuffer.fluidConfig().insert(0, water, 10L, Actionable.MODULATE);
        patternBuffer.fluidConfig().insert(1, lava, 20L, Actionable.MODULATE);

        helper.assertValueEqual(
                patternBuffer.storage().amount(water),
                10L,
                "Pattern buffer should aggregate its first fluid composite slot");
        helper.assertValueEqual(
                patternBuffer.storage().amount(lava),
                20L,
                "Pattern buffer should aggregate the restored second fluid composite slot");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_upgrades_only_apply_to_plain_warehouses")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void upgradesOnlyApplyToPlainWarehouses(GameTestHelper helper) {
        CompositeWarehouseBlockEntity input = compositeWarehouse(
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompositeWarehouseBlockEntity output = compositeWarehouse(
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity meInput = compartment(ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity meOutput = compartment(ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity patternBuffer = compartment(ModBlocks.ME_PATTERN_BUFFER.get().defaultBlockState());

        helper.assertTrue(input.supportsUpgrades(), "Plain input warehouse should support capacity cards");
        helper.assertTrue(output.supportsUpgrades(), "Plain output warehouse should support capacity cards");
        helper.assertFalse(meInput.supportsUpgrades(), "ME input warehouse should not support upgrade cards");
        helper.assertFalse(meOutput.supportsUpgrades(), "ME output warehouse should not support upgrade cards");
        helper.assertFalse(patternBuffer.supportsUpgrades(), "Pattern buffer should not support upgrade cards");

        helper.assertTrue(
                input.getSubInventory(ISegmentedInventory.UPGRADES) != null,
                "Plain input warehouse should expose an upgrade sub-inventory");
        helper.assertTrue(
                meInput.getSubInventory(ISegmentedInventory.UPGRADES) == null,
                "ME input warehouse should not expose an upgrade sub-inventory");
        helper.assertTrue(
                meOutput.getSubInventory(ISegmentedInventory.UPGRADES) == null,
                "ME output warehouse should not expose an upgrade sub-inventory");
        helper.assertTrue(
                patternBuffer.getSubInventory(ISegmentedInventory.UPGRADES) == null,
                "Pattern buffer should not expose an upgrade sub-inventory");

        helper.assertValueEqual(
                Upgrades.getMaxInstallable(AEItems.CAPACITY_CARD, ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get()),
                5,
                "Plain input warehouse should allow capacity cards");
        helper.assertValueEqual(
                Upgrades.getMaxInstallable(AEItems.CAPACITY_CARD, ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get()),
                5,
                "Plain output warehouse should allow capacity cards");
        helper.assertValueEqual(
                Upgrades.getMaxInstallable(AEItems.CAPACITY_CARD, ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get()),
                0,
                "ME input warehouse should not allow capacity cards");
        helper.assertValueEqual(
                Upgrades.getMaxInstallable(AEItems.CAPACITY_CARD, ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get()),
                0,
                "ME output warehouse should not allow capacity cards");
        helper.assertValueEqual(
                Upgrades.getMaxInstallable(AEItems.CAPACITY_CARD, ModBlocks.ME_PATTERN_BUFFER.get()),
                0,
                "Pattern buffer should not allow capacity cards");
        helper.succeed();
    }

    @TestHolder("compartment_menus_create_player_inventory_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void menusCreatePlayerInventorySlots(GameTestHelper helper) {
        var playerInventory = helper.makeMockPlayer(GameType.CREATIVE).getInventory();

        assertPlayerInventorySlots(
                helper,
                new CompositeWarehouseMenu(
                        0,
                        playerInventory,
                        compositeWarehouse(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState())),
                "Plain warehouse menu");
        assertPlainWarehouseMainSlots(
                helper,
                new CompositeWarehouseMenu(
                        4,
                        playerInventory,
                        compositeWarehouse(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState())));
        assertPlayerInventorySlots(
                helper,
                new MeCompositeInputWarehouseMenu(
                        1,
                        playerInventory,
                        meInputWarehouse()),
                "ME input warehouse menu");
        assertMeInputWarehouseMainSlots(
                helper,
                new MeCompositeInputWarehouseMenu(
                        5,
                        playerInventory,
                        meInputWarehouse()));
        assertPlayerInventorySlots(
                helper,
                new MeCompositeOutputWarehouseMenu(
                        2,
                        playerInventory,
                        meOutputWarehouse()),
                "ME output warehouse menu");
        MePatternBufferMenu patternBufferMenu = new MePatternBufferMenu(
                3,
                playerInventory,
                patternBuffer());
        assertPlayerInventorySlots(helper, patternBufferMenu, "Pattern buffer menu");
        assertPatternBufferCompositeSlots(helper, patternBufferMenu);

        helper.succeed();
    }

    @TestHolder("compartment_block_entity_ae_connectivity_matches_type")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blockEntityAeConnectivityMatchesType(GameTestHelper helper) {
        CompartmentBlockEntity input = compartment(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity output = compartment(ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity patternBuffer = compartment(ModBlocks.ME_PATTERN_BUFFER.get().defaultBlockState());
        CompartmentBlockEntity meInput = compartment(
                ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity meOutput = compartment(
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());

        helper.assertFalse(input instanceof IGridConnectedBlockEntity, "Input warehouse should not be an AE grid host");
        helper.assertTrue(input.getGridConnectableSides(null).isEmpty(), "Input warehouse should not connect to AE");
        helper.assertValueEqual(
                input.getCableConnectionType(Direction.NORTH),
                AECableType.NONE,
                "Input warehouse should reject AE cables");
        helper.assertFalse(output instanceof IGridConnectedBlockEntity, "Output warehouse should not be an AE grid host");
        helper.assertTrue(output.getGridConnectableSides(null).isEmpty(), "Output warehouse should not connect to AE");
        helper.assertValueEqual(
                output.getCableConnectionType(Direction.NORTH),
                AECableType.NONE,
                "Output warehouse should reject AE cables");
        helper.assertTrue(
                !(patternBuffer instanceof IGridConnectedBlockEntity),
                "Pattern buffer should not be an AE grid host");
        helper.assertTrue(
                patternBuffer.getGridConnectableSides(null).isEmpty(),
                "Pattern buffer should not connect to AE");
        helper.assertValueEqual(
                patternBuffer.getCableConnectionType(Direction.NORTH),
                AECableType.NONE,
                "Pattern buffer should reject AE cables");

        helper.assertTrue(meInput instanceof IGridConnectedBlockEntity, "ME input warehouse should be an AE grid host");
        helper.assertTrue(
                meInput.getGridConnectableSides(null).contains(Direction.NORTH),
                "ME input warehouse should connect to AE on north side");
        helper.assertValueEqual(
                meInput.getCableConnectionType(Direction.NORTH),
                AECableType.COVERED,
                "ME input warehouse should expose covered AE cables");
        helper.assertTrue(meOutput instanceof IGridConnectedBlockEntity, "ME output warehouse should be an AE grid host");
        helper.assertTrue(
                meOutput.getGridConnectableSides(null).contains(Direction.NORTH),
                "ME output warehouse should connect to AE on north side");
        helper.assertValueEqual(
                meOutput.getCableConnectionType(Direction.NORTH),
                AECableType.COVERED,
                "ME output warehouse should expose covered AE cables");
        helper.succeed();
    }

    @TestHolder("compartment_output_storage_keeps_entries_beyond_display_window")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void outputStorageKeepsEntriesBeyondDisplayWindow(GameTestHelper helper) {
        CompartmentStorage storage = new CompartmentStorageImpl(() -> {});
        List<AEItemKey> keys = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .limit(37)
                .map(AEItemKey::of)
                .toList();

        helper.assertValueEqual(keys.size(), 37, "Test registry should provide more keys than the display window");
        for (int index = 0; index < keys.size(); index++) {
            storage.insert(keys.get(index), index + 1L, false);
        }

        CompartmentStorageDisplayInventory display = new CompartmentStorageDisplayInventory(() -> storage, 36);

        helper.assertValueEqual(storage.entries().size(), 37, "ME output backing storage should keep all entries");
        helper.assertValueEqual(display.size(), 36, "ME output display should expose the configured 36 slot window");
        helper.assertFalse(display.getStackInSlot(35).isEmpty(), "Last visible display slot should show stored contents");
        helper.assertTrue(display.getStackInSlot(36).isEmpty(), "Display should not expose entries outside its window");
        helper.succeed();
    }

    @TestHolder("compartment_output_storage_requires_bound_me_output")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void outputStorageRequiresBoundMeOutput(GameTestHelper helper) {
        TestCompartmentPart part = new TestCompartmentPart(CompartmentType.ME_OUTPUT);
        CompartmentStorage storage = new CompartmentStorageImpl(() -> {});
        CompartmentOutputStorage outputStorage = new CompartmentOutputStorage(
                part,
                storage,
                Component.literal("test output"));
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        helper.assertValueEqual(
                outputStorage.insert(iron, 5L, Actionable.MODULATE, IActionSource.empty()),
                0L,
                "Unbound output storage should reject inserts");

        part.bound = true;
        helper.assertValueEqual(
                outputStorage.insert(iron, 5L, Actionable.MODULATE, IActionSource.empty()),
                5L,
                "Bound ME output storage should accept inserts");
        helper.assertValueEqual(storage.amount(iron), 5L, "Accepted insert should land in map-backed storage");

        KeyCounter counter = new KeyCounter();
        outputStorage.getAvailableStacks(counter);
        helper.assertValueEqual(counter.get(iron), 5L, "Available stacks should expose map-backed amount");

        helper.assertValueEqual(
                outputStorage.extract(iron, 2L, Actionable.MODULATE, IActionSource.empty()),
                2L,
                "Bound ME output storage should allow extraction");
        helper.assertValueEqual(storage.amount(iron), 3L, "Extraction should update map-backed storage");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_output_storage_tracks_binding")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blockEntityOutputStorageTracksBinding(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        MeCompositeOutputWarehouseBlockEntity meOutput = meOutputWarehouse();

        helper.assertTrue(meOutput.outputStorage() == null, "Unbound ME output warehouse should not expose storage");
        meOutput.compartment$bindToHost("main", host);
        helper.assertTrue(meOutput.outputStorage() != null, "Bound ME output warehouse should expose storage");
        meOutput.compartment$unbindFromHost("main", host);
        helper.assertTrue(meOutput.outputStorage() == null, "Unbound ME output warehouse should stop exposing storage");

        CompartmentBlockEntity input = compartment(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity meInput = compartment(
                ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompartmentBlockEntity patternBuffer = compartment(ModBlocks.ME_PATTERN_BUFFER.get().defaultBlockState());

        input.compartment$bindToHost("main", host);
        meInput.compartment$bindToHost("main", host);
        patternBuffer.compartment$bindToHost("main", host);

        helper.assertTrue(input.outputStorage() == null, "Bound input warehouse should not expose ME storage");
        helper.assertTrue(meInput.outputStorage() == null, "Bound ME input warehouse should not expose ME storage");
        helper.assertTrue(patternBuffer.outputStorage() == null, "Bound pattern buffer should not expose ME storage");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_structure_storage_requires_binding")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blockEntityStructureStorageRequiresBinding(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        CompartmentBlockEntity input = compartment(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        CompartmentStorage structureStorage = input.compartmentStorage();

        helper.assertValueEqual(
                structureStorage.insert(iron, 4L, false),
                0L,
                "Unbound plain warehouse should reject structure-side writes");
        helper.assertValueEqual(input.storage().amount(iron), 0L, "Rejected structure write should not touch local state");

        input.compartment$bindToHost("main", host);
        helper.assertValueEqual(
                structureStorage.insert(iron, 4L, false),
                4L,
                "Bound plain warehouse should accept structure-side writes");
        helper.assertValueEqual(input.storage().amount(iron), 4L, "Accepted structure write should update local state");

        input.compartment$unbindFromHost("main", host);
        helper.assertValueEqual(
                structureStorage.insert(iron, 1L, false),
                0L,
                "Cached structure storage should stop accepting writes after invalidation");
        helper.assertValueEqual(
                structureStorage.amount(iron),
                0L,
                "Cached structure storage should hide local contents after invalidation");
        helper.assertValueEqual(input.storage().amount(iron), 4L, "Invalidation should not delete local configuration state");
        helper.succeed();
    }

    @TestHolder("compartment_block_entity_rebinding_removes_old_host_storage_access")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blockEntityRebindingRemovesOldHostStorageAccess(GameTestHelper helper) {
        TestCompartmentHost oldHost = new TestCompartmentHost();
        TestCompartmentHost newHost = new TestCompartmentHost();
        CompositeWarehouseBlockEntity output = compositeWarehouse(
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        CompartmentStorage oldOutputView = oldHost.compartmentHost$outputStorage("main");
        CompartmentStorage newOutputView = newHost.compartmentHost$outputStorage("alternate");

        output.compartment$bindToHost("main", oldHost);
        output.compartment$bindToHost("main", oldHost);

        helper.assertValueEqual(
                oldHost.compartmentHost$getCompartments("main"),
                List.of(output),
                "Repeated bind to the same host and structure should not duplicate the part");
        helper.assertValueEqual(
                oldOutputView.insert(iron, 2L, false),
                2L,
                "Old host output view should write while the part is bound there");

        output.compartment$bindToHost("alternate", newHost);

        helper.assertTrue(
                oldHost.compartmentHost$getCompartments("main").isEmpty(),
                "Rebinding should remove the part from the old host structure");
        helper.assertValueEqual(
                newHost.compartmentHost$getCompartments("alternate"),
                List.of(output),
                "Rebinding should register the part with the new host structure");
        helper.assertValueEqual(output.compartmentHost(), newHost, "Rebound part should remember the new host");
        helper.assertValueEqual(
                output.compartmentStructureName(),
                "alternate",
                "Rebound part should remember the new structure name");
        helper.assertValueEqual(
                oldOutputView.insert(iron, 3L, false),
                0L,
                "Old host aggregate output view should stop writing to the rebound part");
        helper.assertValueEqual(
                output.storage().amount(iron),
                2L,
                "Old host aggregate write after rebinding should not mutate the part storage");
        helper.assertValueEqual(
                newOutputView.insert(iron, 4L, false),
                4L,
                "New host aggregate output view should write to the rebound part");
        helper.assertValueEqual(output.storage().amount(iron), 6L, "New host write should reach the part storage");
        helper.succeed();
    }

    @TestHolder("compartment_host_role_accessors_filter_parts_and_storages")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentHostRoleAccessorsFilterPartsAndStorages(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        TestCompartmentPart input = new TestCompartmentPart(CompartmentType.INPUT);
        TestCompartmentPart output = new TestCompartmentPart(CompartmentType.OUTPUT);
        TestCompartmentPart meInput = new TestCompartmentPart(CompartmentType.ME_INPUT);
        TestCompartmentPart meOutput = new TestCompartmentPart(CompartmentType.ME_OUTPUT);
        TestCompartmentPart plainPattern = new TestCompartmentPart(CompartmentType.PATTERN_BUFFER);

        host.compartmentHost$addCompartment("main", input);
        host.compartmentHost$addCompartment("main", output);
        host.compartmentHost$addCompartment("main", meInput);
        host.compartmentHost$addCompartment("main", meOutput);
        host.compartmentHost$addCompartment("main", plainPattern);

        helper.assertValueEqual(
                host.compartmentHost$getCompartments("main", CompartmentType.INPUT),
                List.of(input),
                "Host role accessor should filter registered compartments by type");
        helper.assertValueEqual(
                host.compartmentHost$getInputStorages("main"),
                List.of(input.compartmentStorage(), meInput.compartmentStorage()),
                "Input storages should include only INPUT and ME_INPUT compartment storages");
        helper.assertValueEqual(
                host.compartmentHost$getOutputStorages("main"),
                List.of(output.compartmentStorage(), meOutput.compartmentStorage()),
                "Output storages should include only OUTPUT and ME_OUTPUT compartment storages");

        TestCompartmentHost patternHost = new TestCompartmentHost();
        TestCompartmentPart nonBufferPattern = new TestCompartmentPart(CompartmentType.PATTERN_BUFFER);
        TestPatternBufferPart patternBuffer = new TestPatternBufferPart(CompartmentType.PATTERN_BUFFER);
        TestPatternBufferPart wrongTypePatternBuffer = new TestPatternBufferPart(CompartmentType.INPUT);

        patternHost.compartmentHost$addCompartment("main", nonBufferPattern);
        patternHost.compartmentHost$addCompartment("main", patternBuffer);
        patternHost.compartmentHost$addCompartment("main", wrongTypePatternBuffer);

        helper.assertValueEqual(
                patternHost.compartmentHost$getPatternBuffers("main"),
                List.of(patternBuffer),
                "Pattern buffer accessor should require both PATTERN_BUFFER type and pattern buffer interface");
        helper.succeed();
    }

    @TestHolder("compartment_host_input_storage_aggregates_inputs")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentHostInputStorageAggregatesInputs(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        TestCompartmentPart input = new TestCompartmentPart(CompartmentType.INPUT);
        TestCompartmentPart meInput = new TestCompartmentPart(CompartmentType.ME_INPUT);
        TestCompartmentPart output = new TestCompartmentPart(CompartmentType.OUTPUT);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);

        input.compartmentStorage().insert(iron, 3L, false);
        meInput.compartmentStorage().insert(iron, 5L, false);
        meInput.compartmentStorage().insert(gold, 7L, false);
        output.compartmentStorage().insert(iron, 11L, false);

        host.compartmentHost$addCompartment("main", input);
        host.compartmentHost$addCompartment("main", meInput);
        host.compartmentHost$addCompartment("main", output);

        CompartmentStorage inputView = host.compartmentHost$inputStorage("main");
        helper.assertValueEqual(inputView.amount(iron), 8L, "Input view should aggregate matching input keys");
        helper.assertValueEqual(inputView.entries().getLong(iron), 8L, "Input entries should merge duplicate keys");
        helper.assertValueEqual(inputView.entries().getLong(gold), 7L, "Input entries should include ME input keys");

        helper.assertValueEqual(inputView.extract(iron, 6L, true), 6L, "Simulated extract should report available input");
        helper.assertValueEqual(input.compartmentStorage().amount(iron), 3L, "Simulated extract should not touch first input");
        helper.assertValueEqual(meInput.compartmentStorage().amount(iron), 5L, "Simulated extract should not touch second input");

        helper.assertValueEqual(inputView.extract(iron, 6L, false), 6L, "Input view should extract in backing order");
        helper.assertValueEqual(input.compartmentStorage().amount(iron), 0L, "Ordered extract should drain first input first");
        helper.assertValueEqual(meInput.compartmentStorage().amount(iron), 2L, "Ordered extract should continue into second input");
        helper.succeed();
    }

    @TestHolder("compartment_host_output_storage_writes_outputs_only")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentHostOutputStorageWritesOutputsOnly(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        TestCompartmentPart input = new TestCompartmentPart(CompartmentType.INPUT);
        TestCompartmentPart output = new TestCompartmentPart(CompartmentType.OUTPUT);
        TestCompartmentPart meOutput = new TestCompartmentPart(CompartmentType.ME_OUTPUT);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);

        input.compartmentStorage().insert(iron, 9L, false);
        host.compartmentHost$addCompartment("main", input);
        host.compartmentHost$addCompartment("main", output);
        host.compartmentHost$addCompartment("main", meOutput);

        CompartmentStorage outputView = host.compartmentHost$outputStorage("main");
        helper.assertValueEqual(outputView.amount(iron), 0L, "Output view should not include input storage contents");
        helper.assertTrue(outputView.entries().isEmpty(), "Output entries should exclude input-only contents");
        helper.assertValueEqual(outputView.insert(gold, 4L, false), 4L, "Output view should write to output storage");
        helper.assertValueEqual(output.compartmentStorage().amount(gold), 4L, "Output view should write to first output");
        helper.assertValueEqual(meOutput.compartmentStorage().amount(gold), 0L, "First output should satisfy the insert");
        helper.succeed();
    }

    @TestHolder("compartment_host_pattern_buffer_storage_aggregates_pattern_buffers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentHostPatternBufferStorageAggregatesPatternBuffers(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        MePatternBufferBlockEntity firstPatternBuffer = patternBuffer();
        MePatternBufferBlockEntity secondPatternBuffer = patternBuffer();
        MePatternBufferBlockEntity alternatePatternBuffer = patternBuffer();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        CompartmentStorage patternBufferStorage = host.compartmentHost$patternBufferStorage("main");

        helper.assertValueEqual(
                patternBufferStorage.insert(iron, 1L, false),
                0L,
                "Pattern buffer view should reject writes before any main pattern buffer is bound");

        firstPatternBuffer.compartment$bindToHost("main", host);
        secondPatternBuffer.compartment$bindToHost("main", host);
        alternatePatternBuffer.compartment$bindToHost("alternate", host);
        firstPatternBuffer.patternBufferStorage(0).insert(iron, 3L, false);
        secondPatternBuffer.patternBufferStorage(0).insert(iron, 5L, false);
        alternatePatternBuffer.patternBufferStorage(0).insert(iron, 11L, false);

        helper.assertValueEqual(
                patternBufferStorage.amount(iron),
                8L,
                "Pattern buffer view should aggregate only main structure pattern buffers");
        helper.assertValueEqual(
                patternBufferStorage.entries().getLong(iron),
                8L,
                "Pattern buffer entries should merge duplicate keys across main pattern buffers");
        helper.assertValueEqual(
                patternBufferStorage.extract(iron, 6L, true),
                6L,
                "Simulated pattern buffer extract should report available main contents");
        helper.assertValueEqual(
                firstPatternBuffer.patternBufferStorage(0).amount(iron),
                3L,
                "Simulated pattern buffer extract should not touch the first buffer");
        helper.assertValueEqual(
                secondPatternBuffer.patternBufferStorage(0).amount(iron),
                5L,
                "Simulated pattern buffer extract should not touch the second buffer");

        helper.assertValueEqual(
                patternBufferStorage.extract(iron, 6L, false),
                6L,
                "Pattern buffer view should extract across main pattern buffers in backing order");
        helper.assertValueEqual(
                firstPatternBuffer.patternBufferStorage(0).amount(iron),
                0L,
                "Pattern buffer extract should drain the first main buffer first");
        helper.assertValueEqual(
                secondPatternBuffer.patternBufferStorage(0).amount(iron),
                2L,
                "Pattern buffer extract should continue into the next main buffer");
        helper.assertValueEqual(
                alternatePatternBuffer.patternBufferStorage(0).amount(iron),
                11L,
                "Pattern buffer view should not modify alternate structure buffers");
        helper.assertValueEqual(
                patternBufferStorage.insert(gold, 4L, false),
                4L,
                "Pattern buffer view should write through a main pattern buffer");
        helper.assertValueEqual(
                firstPatternBuffer.patternBufferStorage(0).amount(gold),
                4L,
                "Pattern buffer write should reach the first main pattern buffer backing storage");
        helper.assertValueEqual(
                alternatePatternBuffer.patternBufferStorage(0).amount(gold),
                0L,
                "Pattern buffer write should not reach alternate structure buffers");

        firstPatternBuffer.compartment$unbindFromHost("main", host);
        secondPatternBuffer.compartment$unbindFromHost("main", host);

        helper.assertValueEqual(
                patternBufferStorage.amount(iron),
                0L,
                "Cached pattern buffer view should stop reading main buffers after unbinding");
        helper.assertTrue(
                patternBufferStorage.entries().isEmpty(),
                "Cached pattern buffer entries should be empty after main buffers unbind");
        helper.assertValueEqual(
                patternBufferStorage.extract(iron, 1L, false),
                0L,
                "Cached pattern buffer view should not extract after main buffers unbind");
        helper.assertValueEqual(
                patternBufferStorage.insert(gold, 3L, false),
                0L,
                "Cached pattern buffer view should not insert after main buffers unbind");
        helper.assertValueEqual(
                firstPatternBuffer.storage().amount(gold),
                4L,
                "Unbound cached pattern buffer write should not modify first backing storage");
        helper.assertValueEqual(
                secondPatternBuffer.storage().amount(iron),
                2L,
                "Unbound cached pattern buffer extract should not modify second backing storage");
        helper.assertValueEqual(
                secondPatternBuffer.storage().amount(gold),
                0L,
                "Unbound cached pattern buffer write should not modify second backing storage");
        helper.succeed();
    }

    @TestHolder("compartment_host_cached_storage_view_tracks_binding")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compartmentHostCachedStorageViewTracksBinding(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        CompositeWarehouseBlockEntity input = compositeWarehouse(
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompositeWarehouseBlockEntity output = compositeWarehouse(
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        CompartmentStorage inputView = host.compartmentHost$inputStorage("main");
        CompartmentStorage outputView = host.compartmentHost$outputStorage("main");

        input.storage().insert(iron, 5L, false);
        helper.assertValueEqual(inputView.amount(iron), 0L, "Cached input view should be empty before binding");
        helper.assertValueEqual(outputView.insert(gold, 1L, false), 0L, "Cached output view should reject writes before binding");

        input.compartment$bindToHost("main", host);
        output.compartment$bindToHost("main", host);

        helper.assertValueEqual(inputView.extract(iron, 2L, false), 2L, "Cached input view should read after binding");
        helper.assertValueEqual(input.storage().amount(iron), 3L, "Bound input extract should update backing storage");
        helper.assertValueEqual(outputView.insert(gold, 4L, false), 4L, "Cached output view should write after binding");
        helper.assertValueEqual(output.storage().amount(gold), 4L, "Bound output write should update backing storage");

        input.compartment$unbindFromHost("main", host);
        output.compartment$unbindFromHost("main", host);

        helper.assertValueEqual(inputView.extract(iron, 1L, false), 0L, "Cached input view should stop after unbinding");
        helper.assertValueEqual(outputView.insert(gold, 1L, false), 0L, "Cached output view should stop after unbinding");
        helper.assertValueEqual(input.storage().amount(iron), 3L, "Unbound input view should not modify backing storage");
        helper.assertValueEqual(output.storage().amount(gold), 4L, "Unbound output view should not modify backing storage");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_hides_compartment_views_until_formed")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void digitalConstructFlowerHidesCompartmentViewsUntilFormed(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower();
        CompositeWarehouseBlockEntity input = compositeWarehouse(
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompositeWarehouseBlockEntity output = compositeWarehouse(
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        MePatternBufferBlockEntity patternBuffer = patternBuffer();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);

        input.storage().insert(iron, 4L, false);
        input.compartment$bindToHost("main", flower);
        output.compartment$bindToHost("main", flower);
        patternBuffer.compartment$bindToHost("main", flower);
        patternBuffer.patternBufferStorage(0).insert(diamond, 5L, false);

        helper.assertFalse(flower.isStructureFormed(), "Flower should start unformed for this stale binding test");
        helper.assertValueEqual(
                flower.compartmentInputStorage().amount(iron),
                0L,
                "Unformed flower input accessor should hide stale input bindings");
        helper.assertTrue(
                flower.compartmentInputStorage().entries().isEmpty(),
                "Unformed flower input entries should be empty");
        helper.assertValueEqual(
                flower.compartmentInputStorage().extract(iron, 1L, false),
                0L,
                "Unformed flower input accessor should not extract from stale bindings");
        helper.assertValueEqual(
                input.storage().amount(iron),
                4L,
                "Unformed flower input accessor should not modify stale input backing storage");
        helper.assertValueEqual(
                flower.compartmentOutputStorage().insert(gold, 2L, false),
                0L,
                "Unformed flower output accessor should reject writes through stale output bindings");
        helper.assertValueEqual(
                output.storage().amount(gold),
                0L,
                "Unformed flower output accessor should not modify stale output backing storage");
        helper.assertValueEqual(
                flower.patternBuffers(),
                List.of(),
                "Unformed flower pattern buffer accessor should hide stale pattern buffer bindings");
        helper.assertValueEqual(
                flower.patternBufferStorage().amount(diamond),
                0L,
                "Unformed flower pattern buffer storage should hide stale pattern buffer contents");
        helper.assertTrue(
                flower.patternBufferStorage().entries().isEmpty(),
                "Unformed flower pattern buffer entries should be empty");
        helper.assertValueEqual(
                flower.patternBufferStorage().extract(diamond, 1L, false),
                0L,
                "Unformed flower pattern buffer storage should not extract from stale pattern buffers");
        helper.assertValueEqual(
                flower.patternBufferStorage().insert(gold, 3L, false),
                0L,
                "Unformed flower pattern buffer storage should reject writes through stale pattern buffers");
        helper.assertValueEqual(
                patternBuffer.patternBufferStorage(0).amount(diamond),
                5L,
                "Unformed flower pattern buffer storage should not drain stale backing storage");
        helper.assertValueEqual(
                patternBuffer.patternBufferStorage(0).amount(gold),
                0L,
                "Unformed flower pattern buffer storage should not write stale backing storage");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_exposes_main_compartment_views")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void digitalConstructFlowerExposesMainCompartmentViews(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = formedDigitalConstructFlower();
        CompositeWarehouseBlockEntity input = compositeWarehouse(
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        CompositeWarehouseBlockEntity output = compositeWarehouse(
                ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
        CompositeWarehouseBlockEntity alternateInput = compositeWarehouse(
                ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
        MePatternBufferBlockEntity patternBuffer = patternBuffer();
        MePatternBufferBlockEntity secondPatternBuffer = patternBuffer();
        MePatternBufferBlockEntity alternatePatternBuffer = patternBuffer();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);

        input.storage().insert(iron, 4L, false);
        alternateInput.storage().insert(iron, 10L, false);
        input.compartment$bindToHost("main", flower);
        output.compartment$bindToHost("main", flower);
        patternBuffer.compartment$bindToHost("main", flower);
        secondPatternBuffer.compartment$bindToHost("main", flower);
        alternateInput.compartment$bindToHost("alternate", flower);
        alternatePatternBuffer.compartment$bindToHost("alternate", flower);
        patternBuffer.patternBufferStorage(0).insert(diamond, 4L, false);
        secondPatternBuffer.patternBufferStorage(0).insert(diamond, 5L, false);
        alternatePatternBuffer.patternBufferStorage(0).insert(diamond, 13L, false);

        helper.assertValueEqual(
                flower.compartmentInputStorage().amount(iron),
                4L,
                "Flower input accessor should use the main structure compartments");
        helper.assertValueEqual(
                flower.compartmentOutputStorage().insert(gold, 2L, false),
                2L,
                "Flower output accessor should write through the main output view");
        helper.assertValueEqual(output.storage().amount(gold), 2L, "Flower output write should reach output backing storage");
        helper.assertValueEqual(
                flower.patternBuffers(),
                List.of(patternBuffer, secondPatternBuffer),
                "Flower pattern buffer accessor should expose main structure pattern buffers");
        helper.assertValueEqual(
                flower.patternBufferStorage().amount(diamond),
                9L,
                "Flower pattern buffer storage should aggregate main pattern buffers only");
        helper.assertValueEqual(
                flower.patternBufferStorage().extract(diamond, 6L, false),
                6L,
                "Flower pattern buffer storage should extract across main pattern buffers");
        helper.assertValueEqual(
                patternBuffer.patternBufferStorage(0).amount(diamond),
                0L,
                "Flower pattern buffer extract should drain the first main pattern buffer first");
        helper.assertValueEqual(
                secondPatternBuffer.patternBufferStorage(0).amount(diamond),
                3L,
                "Flower pattern buffer extract should continue into the second main pattern buffer");
        helper.assertValueEqual(
                alternatePatternBuffer.patternBufferStorage(0).amount(diamond),
                13L,
                "Flower pattern buffer storage should not include alternate structure buffers");
        helper.assertValueEqual(
                flower.patternBufferStorage().insert(gold, 3L, false),
                3L,
                "Flower pattern buffer storage should write to a main pattern buffer");
        helper.assertValueEqual(
                patternBuffer.patternBufferStorage(0).amount(gold),
                3L,
                "Flower pattern buffer write should reach a main pattern buffer backing storage");
        helper.assertValueEqual(
                alternatePatternBuffer.patternBufferStorage(0).amount(gold),
                0L,
                "Flower pattern buffer write should not reach alternate structure buffers");
        helper.succeed();
    }

    @TestHolder("pattern_buffer_storage_is_isolated_per_pattern_slot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void patternBufferStorageIsIsolatedPerPatternSlot(GameTestHelper helper) {
        MePatternBufferBlockEntity compartment = patternBuffer();
        PatternBufferCompartmentPart patternPart = compartment;
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);

        helper.assertTrue(
                patternPart.patternStorage() == compartment.patternStorage(),
                "Pattern buffer interface should expose the block entity pattern inventory");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(0).insert(iron, 2L, false),
                0L,
                "Unbound pattern buffer should reject interface structure-side writes");
        helper.assertValueEqual(
                patternPart.patternAggregateStorage().amount(iron),
                0L,
                "Unbound pattern buffer interface aggregate should be unavailable");

        TestCompartmentHost host = new TestCompartmentHost();
        compartment.compartment$bindToHost("main", host);
        CompartmentStorage slotZeroStorage = patternPart.patternBufferStorage(0);
        CompartmentStorage aggregateStorage = patternPart.patternAggregateStorage();

        helper.assertValueEqual(
                aggregateStorage.insert(iron, 2L, false),
                2L,
                "Bound pattern buffer aggregate should write to a real pattern slot storage");
        helper.assertValueEqual(
                slotZeroStorage.amount(iron),
                2L,
                "Aggregate insert should update slot 0 backing storage");
        helper.assertValueEqual(
                aggregateStorage.insert(iron, 5L, true),
                5L,
                "Simulated aggregate insert should report writable backing storage");
        helper.assertValueEqual(
                slotZeroStorage.amount(iron),
                2L,
                "Simulated aggregate insert should not modify slot 0 backing storage");
        helper.assertValueEqual(
                aggregateStorage.extract(iron, 1L, true),
                1L,
                "Simulated aggregate extract should report available backing storage");
        helper.assertValueEqual(
                slotZeroStorage.amount(iron),
                2L,
                "Simulated aggregate extract should not modify slot 0 backing storage");
        helper.assertValueEqual(
                aggregateStorage.extract(iron, 1L, false),
                1L,
                "Aggregate extract should remove contents from real slot storage");
        helper.assertValueEqual(
                slotZeroStorage.amount(iron),
                1L,
                "Aggregate extract should update slot 0 backing storage");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(1).insert(gold, 3L, false),
                3L,
                "Bound pattern buffer interface storage should accept writes for another pattern slot");

        helper.assertValueEqual(
                host.compartmentHost$getCompartments("main"),
                List.of(compartment),
                "Bound pattern buffer should register with its host");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(0).amount(gold),
                0L,
                "Pattern slot 0 should not see slot 1 buffer contents");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(1).amount(iron),
                0L,
                "Pattern slot 1 should not see slot 0 buffer contents");
        helper.assertValueEqual(
                patternPart.patternAggregateStorage().amount(iron),
                1L,
                "Aggregate buffer should include slot 0 contents");
        helper.assertValueEqual(
                patternPart.patternAggregateStorage().amount(gold),
                3L,
                "Aggregate buffer should include slot 1 contents");

        compartment.compartment$unbindFromHost("main", host);
        helper.assertValueEqual(
                aggregateStorage.amount(iron),
                0L,
                "Cached pattern buffer aggregate should stop exposing previous contents after invalidation");
        helper.assertValueEqual(
                slotZeroStorage.insert(iron, 1L, false),
                0L,
                "Cached pattern buffer storage should reject writes after invalidation");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(0).insert(iron, 1L, false),
                0L,
                "Fresh unbound pattern buffer storage should reject writes after invalidation");
        helper.succeed();
    }

    @TestHolder("pattern_buffer_aggregate_ignores_locked_pattern_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void patternBufferAggregateIgnoresLockedPatternSlots(GameTestHelper helper) {
        TestCompartmentHost host = new TestCompartmentHost();
        MePatternBufferBlockEntity compartment = patternBuffer(1);
        PatternBufferCompartmentPart patternPart = compartment;
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);

        compartment.compartment$bindToHost("main", host);
        CompartmentStorage aggregateStorage = patternPart.patternAggregateStorage();

        helper.assertValueEqual(
                patternPart.patternBufferStorage(0).insert(iron, 2L, false),
                2L,
                "Unlocked pattern slot should accept direct backing writes");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(1).insert(gold, 7L, false),
                7L,
                "Locked pattern slot backing should remain directly writable for existing per-slot views");
        helper.assertValueEqual(
                aggregateStorage.amount(iron),
                2L,
                "Aggregate storage should include unlocked slot contents");
        helper.assertValueEqual(
                aggregateStorage.amount(gold),
                0L,
                "Aggregate storage should ignore locked slot contents");
        helper.assertValueEqual(
                aggregateStorage.entries().getLong(gold),
                0L,
                "Aggregate entries should not expose locked slot contents");
        helper.assertValueEqual(
                aggregateStorage.extract(gold, 1L, false),
                0L,
                "Aggregate extract should not drain locked slot contents");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(1).amount(gold),
                7L,
                "Aggregate extract should leave locked slot backing unchanged");
        helper.assertValueEqual(
                aggregateStorage.insert(diamond, 4L, false),
                4L,
                "Aggregate insert should write through the unlocked backing range");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(0).amount(diamond),
                4L,
                "Aggregate insert should reach the unlocked pattern slot");
        helper.assertValueEqual(
                patternPart.patternBufferStorage(1).amount(gold),
                7L,
                "Aggregate insert should not touch locked slot backing storage");
        helper.succeed();
    }

    private static CompartmentBlockEntity compartment(BlockState state) {
        if (!(state.getBlock() instanceof CompartmentBlock compartmentBlock)) {
            throw new IllegalArgumentException("Test state is not a compartment block: " + state);
        }
        return switch (compartmentBlock.compartmentType()) {
            case INPUT, OUTPUT -> compositeWarehouse(state);
            case ME_INPUT -> new MeCompositeInputWarehouseBlockEntity(BlockPos.ZERO, state);
            case ME_OUTPUT -> new MeCompositeOutputWarehouseBlockEntity(BlockPos.ZERO, state);
            case PATTERN_BUFFER -> new MePatternBufferBlockEntity(BlockPos.ZERO, state);
        };
    }

    private static CompositeWarehouseBlockEntity compositeWarehouse(BlockState state) {
        return new CompositeWarehouseBlockEntity(BlockPos.ZERO, state);
    }

    private static MeCompositeInputWarehouseBlockEntity meInputWarehouse() {
        return new MeCompositeInputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static MeCompositeOutputWarehouseBlockEntity meOutputWarehouse() {
        return new MeCompositeOutputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static MePatternBufferBlockEntity patternBuffer() {
        return new MePatternBufferBlockEntity(BlockPos.ZERO, ModBlocks.ME_PATTERN_BUFFER.get().defaultBlockState());
    }

    private static MePatternBufferBlockEntity patternBuffer(int unlockedSlotCount) {
        return new LimitedPatternBufferBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_PATTERN_BUFFER.get().defaultBlockState(),
                unlockedSlotCount);
    }

    private static ItemStack encodedProcessingPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)));
    }

    private static DigitalConstructFlowerBlockEntity digitalConstructFlower() {
        return new DigitalConstructFlowerBlockEntity(
                BlockPos.ZERO,
                ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get().defaultBlockState());
    }

    private static DigitalConstructFlowerBlockEntity formedDigitalConstructFlower() {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        flower.loadTag(tag, HolderLookup.Provider.create(Stream.empty()));
        return flower;
    }

    private static void installCapacityCards(CompositeWarehouseBlockEntity compartment) {
        installCapacityCards(compartment, compartment.getUpgrades().size());
    }

    private static void installCapacityCards(CompositeWarehouseBlockEntity compartment, int count) {
        for (int slot = 0; slot < compartment.getUpgrades().size(); slot++) {
            if (slot < count) {
                compartment.getUpgrades().setItemDirect(slot, AEItems.CAPACITY_CARD.stack());
            }
        }
    }

    private static void assertPlayerInventorySlots(GameTestHelper helper, CompartmentMenu menu, String name) {
        helper.assertValueEqual(
                menu.getSlots(SlotSemantics.PLAYER_INVENTORY).size(),
                27,
                name + " should create the 27-slot player inventory");
        helper.assertValueEqual(
                menu.getSlots(SlotSemantics.PLAYER_HOTBAR).size(),
                9,
                name + " should create the 9-slot player hotbar");
    }

    private static void assertPlainWarehouseMainSlots(GameTestHelper helper, CompartmentMenu menu) {
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_STORAGE_ROW_1).size(),
                7,
                "Plain warehouse row 1 should expose seven main slots before the F/K columns");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_STORAGE_ROW_2).size(),
                7,
                "Plain warehouse row 2 should expose seven main slots before the F/K columns");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_STORAGE_ROW_3).size(),
                7,
                "Plain warehouse row 3 should expose seven main slots before the F/K columns");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_STORAGE_ROW_4).size(),
                7,
                "Plain warehouse row 4 should expose seven main slots before the F/K columns");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_STORAGE_ROW_5).size(),
                7,
                "Plain warehouse row 5 should expose seven main slots before the F/K columns");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_STORAGE_ROW_6).size(),
                7,
                "Plain warehouse row 6 should expose seven main slots before the F/K columns");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_STORAGE_ROW_7).size(),
                7,
                "Plain warehouse row 7 should expose seven main slots before the F/K columns");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_FLUID).size(),
                CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ROWS,
                "Plain warehouse should expose a full fluid column");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_KEY).size(),
                CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ROWS,
                "Plain warehouse should expose a full wrapped-key column");
        for (int row = CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS; row < CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ROWS; row++) {
            assertSlotTextureColumn(helper, menu, CompartmentMenu.COMPARTMENT_FLUID, row, 0);
            assertSlotTextureColumn(helper, menu, CompartmentMenu.COMPARTMENT_KEY, row, 1);
        }
    }

    private static void assertMeInputWarehouseMainSlots(GameTestHelper helper, CompartmentMenu menu) {
        assertMeInputWarehouseMarkerRow(helper, menu, CompartmentMenu.COMPARTMENT_CONFIG_ROW_1);
        assertMeInputWarehouseMarkerRow(helper, menu, CompartmentMenu.COMPARTMENT_CONFIG_ROW_2);
        assertMeInputWarehouseMarkerRow(helper, menu, CompartmentMenu.COMPARTMENT_CONFIG_ROW_3);
        assertMeInputWarehouseMarkerRow(helper, menu, CompartmentMenu.COMPARTMENT_CONFIG_ROW_4);
        assertMeInputWarehouseMarkerRow(helper, menu, CompartmentMenu.COMPARTMENT_CONFIG_ROW_5);
        assertMeInputWarehouseBufferRow(helper, menu, CompartmentMenu.COMPARTMENT_BUFFER_ROW_1);
        assertMeInputWarehouseBufferRow(helper, menu, CompartmentMenu.COMPARTMENT_BUFFER_ROW_2);
        assertMeInputWarehouseBufferRow(helper, menu, CompartmentMenu.COMPARTMENT_BUFFER_ROW_3);
        assertMeInputWarehouseBufferRow(helper, menu, CompartmentMenu.COMPARTMENT_BUFFER_ROW_4);
        assertMeInputWarehouseBufferRow(helper, menu, CompartmentMenu.COMPARTMENT_BUFFER_ROW_5);
    }

    private static void assertMeInputWarehouseMarkerRow(GameTestHelper helper,
                                                        CompartmentMenu menu,
                                                        SlotSemantic semantic) {
        var slots = menu.getSlots(semantic);
        helper.assertValueEqual(
                slots.size(),
                CompartmentMenu.ME_COMPOSITE_INPUT_ROW_SLOT_COUNT,
                semantic.id() + " should expose one five-slot marker row");
        for (var slot : slots) {
            if (!(slot instanceof AppEngSlot appEngSlot)) {
                helper.fail(semantic.id() + " should use AppEng slots");
                return;
            }
            helper.assertTrue(appEngSlot.isHideAmount(), semantic.id() + " marker slots should hide stack amounts");
        }
    }

    private static void assertMeInputWarehouseBufferRow(GameTestHelper helper,
                                                        CompartmentMenu menu,
                                                        SlotSemantic semantic) {
        var slots = menu.getSlots(semantic);
        helper.assertValueEqual(
                slots.size(),
                CompartmentMenu.ME_COMPOSITE_INPUT_ROW_SLOT_COUNT,
                semantic.id() + " should expose one five-slot buffer row");
        for (var slot : slots) {
            if (!(slot instanceof AppEngSlot appEngSlot)) {
                helper.fail(semantic.id() + " should use AppEng slots");
                return;
            }
            helper.assertFalse(appEngSlot.isHideAmount(), semantic.id() + " buffer slots should keep pulled amounts visible");
        }
    }

    private static void assertPatternBufferCompositeSlots(GameTestHelper helper, CompartmentMenu menu) {
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_PATTERN).size(),
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT,
                "Pattern buffer menu should expose every texture-backed pattern slot");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_PATTERN_BUFFER).size(),
                CompartmentMenu.PATTERN_BUFFER_DISPLAY_SLOT_COUNT,
                "Pattern buffer menu should expose the expected output display window");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_CATALYST).size(),
                CompartmentMenu.SHARED_CATALYST_SLOT_COUNT,
                "Pattern buffer menu should expose the shared catalyst slots");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_FLUID).size(),
                1,
                "Pattern buffer should keep the first fluid composite slot");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_KEY).size(),
                1,
                "Pattern buffer should keep one wrapped-key composite slot");
        helper.assertValueEqual(
                menu.getSlots(CompartmentMenu.COMPARTMENT_EXTRA_FLUID).size(),
                1,
                "Pattern buffer should keep the second fluid composite slot from the original texture");
    }

    private static void assertSlotTextureColumn(GameTestHelper helper,
                                                CompartmentMenu menu,
                                                SlotSemantic semantic,
                                                int slotIndex,
                                                int expectedTextureColumn) {
        var slots = menu.getSlots(semantic);
        helper.assertTrue(slotIndex < slots.size(), semantic.id() + " should expose slot " + slotIndex);
        if (!(slots.get(slotIndex) instanceof CompartmentSlotLabel labeledSlot)) {
            helper.fail(semantic.id() + " slot " + slotIndex + " should render as a textured F/K slot");
            return;
        }
        helper.assertValueEqual(
                labeledSlot.slotTextureColumn(),
                expectedTextureColumn,
                semantic.id() + " slot " + slotIndex + " should use the expected F/K texture column");
    }

    private static void assertOptionalBackgroundSlot(GameTestHelper helper,
                                                     CompartmentMenu menu,
                                                     SlotSemantic semantic,
                                                     int slotIndex,
                                                     int expectedTextureColumn) {
        var slots = menu.getSlots(semantic);
        helper.assertTrue(slotIndex < slots.size(), semantic.id() + " should expose slot " + slotIndex);
        if (!(slots.get(slotIndex) instanceof IOptionalSlot optionalSlot)) {
            helper.fail(semantic.id() + " slot " + slotIndex + " should follow AE2 optional slot rendering");
            return;
        }
        helper.assertTrue(
                optionalSlot.isRenderDisabled(),
                semantic.id() + " slot " + slotIndex + " should let AE2 draw the storage-bus style optional slot background");
        assertSlotTextureColumn(helper, menu, semantic, slotIndex, expectedTextureColumn);
    }

    private static final class TestCompartmentHost implements CompartmentHost {

        private final CompartmentHostState compartments = new CompartmentHostState();

        @Override
        public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {
            this.compartments.addCompartment(structureName, part);
        }

        @Override
        public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {
            this.compartments.removeCompartment(structureName, part);
        }

        @Override
        public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
            return this.compartments.compartments(structureName);
        }
    }

    private static class TestCompartmentPart implements CompartmentPart {

        private final CompartmentType type;
        private final CompartmentStorage storage = new CompartmentStorageImpl(() -> {});
        private boolean bound;

        private TestCompartmentPart(CompartmentType type) {
            this.type = type;
        }

        @Override
        public CompartmentType compartmentType() {
            return this.type;
        }

        @Override
        public VerticalMultiBlockPos compartmentPos() {
            return new VerticalMultiBlockPos(0, 0, 0);
        }

        @Override
        public CompartmentHost compartmentHost() {
            return null;
        }

        @Override
        public CompartmentStorage compartmentStorage() {
            return this.storage;
        }

        @Override
        public boolean isCompartmentBound() {
            return this.bound;
        }
    }

    private static final class TestPatternBufferPart extends TestCompartmentPart implements PatternBufferCompartmentPart {

        private final CompartmentInventory patternStorage = CompartmentInventory.itemStorage(1, () -> {}, () -> 1);
        private final CompartmentStorage patternBufferStorage = new CompartmentStorageImpl(() -> {});
        private final CompartmentStorage patternAggregateStorage = new CompartmentStorageGroup(
                () -> List.of(this.patternBufferStorage));

        private TestPatternBufferPart(CompartmentType type) {
            super(type);
        }

        @Override
        public CompartmentInventory patternStorage() {
            return this.patternStorage;
        }

        @Override
        public CompartmentStorage patternBufferStorage(int slot) {
            if (slot != 0) {
                throw new IllegalArgumentException("Test pattern buffer slot out of range: " + slot);
            }
            return this.patternBufferStorage;
        }

        @Override
        public CompartmentStorage patternAggregateStorage() {
            return this.patternAggregateStorage;
        }
    }

    private static final class LimitedPatternBufferBlockEntity extends MePatternBufferBlockEntity {

        private final int unlockedSlotCount;

        private LimitedPatternBufferBlockEntity(BlockPos pos, BlockState state, int unlockedSlotCount) {
            super(pos, state);
            this.unlockedSlotCount = unlockedSlotCount;
        }

        @Override
        public int unlockedSlotCount() {
            return this.unlockedSlotCount;
        }
    }
}
