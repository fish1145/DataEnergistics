package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.item.MobDataCarrierItemData;
import com.fish_dan_.data_energistics.item.OreDataCarrierItemData;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Verifies high-volume mimetic output and container backpressure through real block capabilities. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataMimeticFieldOutputGameTest {

    private static final BlockPos FIELD_POS = new BlockPos(2, 2, 2);
    private static final BlockPos CHEST_POS = new BlockPos(3, 2, 2);
    private static final long INITIAL_DATA_FLOW = 20_000L;
    private static final long FIRST_CYCLE_DATA_FLOW = 3_200L;
    private static final ResourceLocation BHC_YELLOW_HEART_ID = ResourceLocation.fromNamespaceAndPath("bhc", "yellow_heart");

    private DataMimeticFieldOutputGameTest() {}

    /**
     * Reproduces a work cycle larger than the former 64-slot hidden inventory, then proves blocked output pauses work.
     *
     * @param helper game-test world access and assertions
     */
    @TestHolder("data_mimetic_field_streams_large_nonstackable_output_with_backpressure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 700)
    public static void streamsLargeNonstackableOutputWithBackpressure(GameTestHelper helper) {
        helper.setBlock(FIELD_POS, ModBlocks.DATA_MIMETIC_FIELD.get().defaultBlockState());
        helper.setBlock(CHEST_POS, Blocks.CHEST.defaultBlockState());
        DataMimeticFieldBlockEntity field = requireField(helper);
        Container chest = requireChest(helper);

        field.setDropRoutingMode(DataExtractorDropRoutingMode.CONTAINER);
        field.getInternalInventory().setItemDirect(0, completedNetheriteSwordCarrier());
        long inserted = field.getExternalKeyInventory().insert(
                0,
                DataFlowKey.of(),
                INITIAL_DATA_FLOW,
                Actionable.MODULATE);
        helper.assertValueEqual(inserted, INITIAL_DATA_FLOW, "The test must fully charge the mimetic key input");

        int expectedOutput = field.getOreOutputRollsPerCycle();
        helper.assertTrue(expectedOutput > 64, "The regression requires output larger than the legacy hidden buffer");
        AtomicBoolean drainChest = new AtomicBoolean();
        AtomicLong received = new AtomicLong();
        helper.onEachTick(() -> {
            if (drainChest.get()) {
                received.addAndGet(removeNetheriteSwords(helper, chest));
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(
                            keyAmount(field),
                            INITIAL_DATA_FLOW - FIRST_CYCLE_DATA_FLOW,
                            "Exactly one work cycle must consume data flow before the chest blocks output");
                    helper.assertValueEqual(
                            countNetheriteSwords(chest),
                            chest.getContainerSize(),
                            "The first pending-output flush must fill the real chest");
                })
                .thenIdle(field.getWorkMaxProgress() + 20)
                .thenExecute(() -> helper.assertValueEqual(
                        keyAmount(field),
                        INITIAL_DATA_FLOW - FIRST_CYCLE_DATA_FLOW,
                        "Pending output must prevent a second work cycle from consuming data flow"))
                .thenExecute(() -> drainChest.set(true))
                .thenWaitUntil(() -> helper.assertTrue(
                        received.get() >= expectedOutput,
                        "The chest must eventually receive every pending nonstackable output"))
                .thenExecute(() -> {
                    drainChest.set(false);
                    received.addAndGet(removeNetheriteSwords(helper, chest));
                    helper.assertValueEqual(
                            received.get(),
                            (long) expectedOutput,
                            "Container routing must conserve the complete generated batch without duplication");
                    helper.assertValueEqual(
                            keyAmount(field),
                            INITIAL_DATA_FLOW - FIRST_CYCLE_DATA_FLOW,
                            "Draining one pending batch must not consume another work cycle early");
                })
                .thenSucceed();
    }

    /**
     * Drives the real BHC Wither listener through the field, persistence, backpressure, and a real chest.
     *
     * @param helper game-test world access and assertions
     */
    @TestHolder("data_mimetic_field_persists_and_outputs_real_bhc_heart")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 800)
    public static void persistsAndOutputsRealBhcHeart(GameTestHelper helper) {
        helper.setBlock(FIELD_POS, ModBlocks.DATA_MIMETIC_FIELD.get().defaultBlockState());
        helper.setBlock(CHEST_POS, Blocks.CHEST.defaultBlockState());
        DataMimeticFieldBlockEntity field = requireField(helper);
        Container chest = requireChest(helper);
        Item yellowHeart = BuiltInRegistries.ITEM.getOptional(BHC_YELLOW_HEART_ID)
                .orElseThrow(() -> new IllegalStateException("Baubley Heart Canisters test dependency is not loaded"));

        field.setDropRoutingMode(DataExtractorDropRoutingMode.CONTAINER);
        field.getInternalInventory().setItemDirect(0, completedWitherCarrier());
        long inserted = field.getExternalKeyInventory().insert(
                0,
                DataFlowKey.of(),
                INITIAL_DATA_FLOW,
                Actionable.MODULATE);
        helper.assertValueEqual(inserted, INITIAL_DATA_FLOW, "The test must fully charge the mimetic key input");
        fillChest(chest);

        long expectedPerDrop = field.getBiologyLootRollsPerCycle();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(
                        keyAmount(field),
                        INITIAL_DATA_FLOW - FIRST_CYCLE_DATA_FLOW,
                        "The blocked BHC cycle must consume data flow exactly once"))
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            countItem(chest, Items.COBBLESTONE),
                            (long) chest.getContainerSize() * Items.COBBLESTONE.getDefaultMaxStackSize(),
                            "The full chest must reject the complete BHC output batch");
                    CompoundTag persisted = new CompoundTag();
                    field.saveAdditional(persisted, helper.getLevel().registryAccess());
                    helper.assertValueEqual(
                            pendingAmount(persisted, helper.getLevel().registryAccess(), yellowHeart),
                            expectedPerDrop,
                            "The first blocked cycle must cache every BHC yellow heart exactly once");
                    helper.assertValueEqual(
                            pendingAmount(persisted, helper.getLevel().registryAccess(), Items.NETHER_STAR),
                            expectedPerDrop,
                            "The first blocked cycle must cache every ordinary Nether Star exactly once");
                    field.loadTag(persisted, helper.getLevel().registryAccess());
                    helper.assertValueEqual(
                            keyAmount(field),
                            INITIAL_DATA_FLOW - FIRST_CYCLE_DATA_FLOW,
                            "Reloading pending BHC output must retain the consumed-cycle state");
                    CompoundTag reloaded = new CompoundTag();
                    field.saveAdditional(reloaded, helper.getLevel().registryAccess());
                    helper.assertValueEqual(
                            pendingAmount(reloaded, helper.getLevel().registryAccess(), yellowHeart),
                            expectedPerDrop,
                            "Reloading must not duplicate or discard cached BHC yellow hearts");
                    helper.assertValueEqual(
                            pendingAmount(reloaded, helper.getLevel().registryAccess(), Items.NETHER_STAR),
                            expectedPerDrop,
                            "Reloading must not duplicate or discard cached ordinary drops");
                    clearCarriers(field);
                    clearChest(chest);
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(
                            countItem(chest, yellowHeart),
                            expectedPerDrop,
                            "Every real BHC yellow heart must survive pending-output persistence");
                    helper.assertValueEqual(
                            countItem(chest, Items.NETHER_STAR),
                            expectedPerDrop,
                            "BHC hearts must not replace the Wither's ordinary Nether Star drops");
                })
                .thenExecute(() -> {
                    assertOnlyItems(helper, chest, yellowHeart, Items.NETHER_STAR);
                    helper.assertValueEqual(
                            keyAmount(field),
                            INITIAL_DATA_FLOW - FIRST_CYCLE_DATA_FLOW,
                            "Draining persisted BHC output must not consume a second cycle");
                    Vec3 center = Vec3.atCenterOf(helper.absolutePos(FIELD_POS));
                    helper.assertTrue(
                            helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.ofSize(center, 8.0D, 8.0D, 8.0D)).isEmpty(),
                            "Captured BHC hearts and ordinary drops must not leak into the world");
                })
                .thenSucceed();
    }

    private static DataMimeticFieldBlockEntity requireField(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(FIELD_POS);
        if (blockEntity instanceof DataMimeticFieldBlockEntity field) {
            return field;
        }
        throw new GameTestAssertException("Placed data mimetic field has no matching block entity");
    }

    private static Container requireChest(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(CHEST_POS);
        if (blockEntity instanceof Container container) {
            return container;
        }
        throw new GameTestAssertException("Placed chest has no container block entity");
    }

    private static ItemStack completedNetheriteSwordCarrier() {
        ItemStack carrier = new ItemStack(ModItems.ORE_DATA_CARRIER.get());
        carrier.set(
                ModDataComponents.ORE_DATA_CARRIER.get(),
                new OreDataCarrierItemData(BuiltInRegistries.ITEM.getKey(Items.NETHERITE_SWORD), 1.0F, 1.0F));
        return carrier;
    }

    private static ItemStack completedWitherCarrier() {
        ItemStack carrier = new ItemStack(ModItems.MOB_DATA_CARRIER.get());
        carrier.set(
                ModDataComponents.MOB_DATA_CARRIER.get(),
                new MobDataCarrierItemData(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.WITHER), 1.0F, 1.0F));
        return carrier;
    }

    private static long keyAmount(DataMimeticFieldBlockEntity field) {
        return field.getKeyInputStack() == null ? 0L : field.getKeyInputStack().amount();
    }

    private static int countNetheriteSwords(Container chest) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(Items.NETHERITE_SWORD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static long countItem(Container container, Item item) {
        long count = 0L;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static long pendingAmount(CompoundTag data, HolderLookup.Provider registries, Item item) {
        AEItemKey expectedKey = AEItemKey.of(item);
        long amount = 0L;
        for (Tag entry : data.getList("pending_outputs", Tag.TAG_COMPOUND)) {
            GenericStack stack = GenericStack.readTag(registries, (CompoundTag) entry);
            if (stack != null && expectedKey.equals(stack.what())) {
                amount = Math.addExact(amount, stack.amount());
            }
        }
        return amount;
    }

    private static void clearCarriers(DataMimeticFieldBlockEntity field) {
        for (int slot = 0; slot < DataMimeticFieldBlockEntity.SLOT_COUNT; slot++) {
            field.getInternalInventory().setItemDirect(slot, ItemStack.EMPTY);
        }
    }

    private static void fillChest(Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.COBBLESTONE, Items.COBBLESTONE.getDefaultMaxStackSize()));
        }
        chest.setChanged();
    }

    private static void clearChest(Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, ItemStack.EMPTY);
        }
        chest.setChanged();
    }

    private static void assertOnlyItems(GameTestHelper helper, Container container, Item first, Item second) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                helper.assertTrue(stack.is(first) || stack.is(second), "The output chest contains an unexpected item");
            }
        }
    }

    private static int removeNetheriteSwords(GameTestHelper helper, Container chest) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            helper.assertTrue(stack.is(Items.NETHERITE_SWORD), "The output chest must contain only the recorded ore item");
            count += stack.getCount();
            chest.setItem(slot, ItemStack.EMPTY);
        }
        chest.setChanged();
        return count;
    }
}
