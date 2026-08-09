package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class PlayerInventoryRefundDeliveryTest {

    private PlayerInventoryRefundDeliveryTest() {}

    @TestHolder("trinity_refund_delivery_prefers_available_ae_storage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void deliveryPrefersAeStorageBeforePlayerInventory(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        fillInventory(player.getInventory(), new ItemStack(Items.STONE, 64));
        RecordingStorage storage = new RecordingStorage(true);
        PlayerInventoryRefundDelivery delivery = new PlayerInventoryRefundDelivery(player, storage, IActionSource.empty());
        List<TrinityItemAmount> items = List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2)));

        assertTrue(delivery.prepare(items));
        delivery.deliver(items);

        assertEquals(2L, storage.insertedAmount);
        for (ItemStack stack : player.getInventory().items) {
            assertTrue(stack.is(Items.STONE));
            assertEquals(64, stack.getCount());
        }
        helper.succeed();
    }

    @TestHolder("trinity_refund_delivery_ae_remainder_falls_back_to_player")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void deliveryFallsBackToPlayerWhenAeStorageRejects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        clearInventory(player.getInventory());
        RecordingStorage storage = new RecordingStorage(false);
        PlayerInventoryRefundDelivery delivery = new PlayerInventoryRefundDelivery(player, storage, IActionSource.empty());
        List<TrinityItemAmount> items = List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2)));

        assertTrue(delivery.prepare(items));
        delivery.deliver(items);

        assertEquals(0L, storage.insertedAmount);
        assertHasStack(player.getInventory().items, Items.DIAMOND, 2);
        helper.succeed();
    }

    @TestHolder("trinity_refund_delivery_full_inventory_drops_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void deliveryDropsRemainderWhenAeAndPlayerInventoryReject(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        fillInventory(player.getInventory(), new ItemStack(Items.STONE, 64));
        RecordingStorage storage = new RecordingStorage(false);
        PlayerInventoryRefundDelivery delivery = new PlayerInventoryRefundDelivery(player, storage, IActionSource.empty());
        List<TrinityItemAmount> items = List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2)));

        assertTrue(delivery.prepare(items));
        delivery.deliver(items);

        assertEquals(0L, storage.insertedAmount);
        assertDroppedItem(helper, player, Items.DIAMOND, 2);
        helper.succeed();
    }

    private static void fillInventory(Inventory inventory, ItemStack stack) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.items.set(slot, stack.copy());
        }
    }

    private static void clearInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.items.set(slot, ItemStack.EMPTY);
        }
    }

    private static void assertHasStack(List<ItemStack> stacks, ItemLike item, int count) {
        for (ItemStack stack : stacks) {
            if (stack.is(item.asItem()) && stack.getCount() == count) {
                return;
            }
        }
        throw new GameTestAssertException("Expected " + count + " of " + item);
    }

    private static void assertDroppedItem(GameTestHelper helper, Player player, ItemLike item, int count) {
        AABB searchArea = AABB.ofSize(player.position(), 32.0D, 32.0D, 32.0D);
        boolean found = helper.getLevel().getEntitiesOfClass(ItemEntity.class, searchArea).stream()
                .map(ItemEntity::getItem)
                .anyMatch(stack -> stack.is(item.asItem()) && stack.getCount() == count);
        assertTrue(found);
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    /** Test storage whose explicit acceptance policy makes delivery order observable. */
    private static final class RecordingStorage implements MEStorage {

        private final boolean accepts;
        private long insertedAmount;

        private RecordingStorage(boolean accepts) {
            this.accepts = accepts;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode != Actionable.MODULATE || !this.accepts) {
                return 0L;
            }
            this.insertedAmount += amount;
            return amount;
        }

        @Override
        public Component getDescription() {
            return Component.literal("Trinity refund test storage");
        }
    }
}
