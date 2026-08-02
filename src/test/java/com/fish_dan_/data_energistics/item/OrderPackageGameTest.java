package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.EchoKey;
import com.fish_dan_.data_energistics.client.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.menu.OrderPackageMenu;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.FakeSlot;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrderPackageGameTest {

    private OrderPackageGameTest() {}

    @TestHolder("order_package_target_contract_and_serialization")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void targetContractAndSerialization(GameTestHelper helper) {
        AEItemKey itemKey = AEItemKey.of(Items.DIAMOND);
        AEFluidKey fluidKey = AEFluidKey.of(Fluids.WATER);
        if (itemKey == null || fluidKey == null) {
            throw new GameTestAssertException("The test item and fluid must produce AE keys");
        }

        List<AEKey> targets = List.of(itemKey, fluidKey, EchoKey.of());
        OrderPackageTarget contract = OrderPackageTarget.get();
        ItemStack packageStack = contract.createMarkedPackage(targets.getFirst());
        helper.assertTrue(contract.isOrderPackage(packageStack), "The factory must create the registered order package");
        helper.assertValueEqual(
                contract.getTarget(packageStack).orElse(null),
                targets.getFirst(),
                "The factory must preserve the complete target key");

        for (AEKey target : targets) {
            contract.setTarget(packageStack, target);
            helper.assertValueEqual(
                    contract.getTarget(packageStack).orElse(null),
                    target,
                    "Setting a target must replace the complete AE key identity");
            assertPersistentRoundTrip(helper, packageStack, target);
            assertNetworkRoundTrip(helper, packageStack, target);
        }

        AEKey removed = contract.clearTarget(packageStack).orElse(null);
        helper.assertValueEqual(removed, targets.getLast(), "Clearing must return the target that was removed");
        helper.assertTrue(contract.getTarget(packageStack).isEmpty(), "A cleared package must be unmarked");
        helper.assertTrue(contract.clearTarget(packageStack).isEmpty(), "Clearing an unmarked package must be a no-op");
        helper.succeed();
    }

    @TestHolder("order_package_menu_uses_generic_non_consuming_ghost_slot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void menuUsesGenericNonConsumingGhostSlot(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "order-package-menu"),
                ClientInformation.createDefault());
        ItemStack packageStack = ModItems.ORDER_PACKAGE.toStack();
        player.getInventory().setItem(0, packageStack);

        OrderPackageMenuHost host = new OrderPackageMenuHost(
                ModItems.ORDER_PACKAGE.get(),
                player,
                MenuLocators.forInventorySlot(0));
        OrderPackageMenu menu = new OrderPackageMenu(1, player.getInventory(), host);
        var targetSlots = menu.getSlots(OrderPackageMenu.TARGET);
        if (targetSlots.size() != 1 || !(targetSlots.getFirst() instanceof FakeSlot targetSlot)) {
            throw new GameTestAssertException("The order package menu must expose exactly one AE2 fake slot");
        }

        ItemStack carriedItems = new ItemStack(Items.GOLD_INGOT, 37);
        targetSlot.increase(carriedItems);
        helper.assertValueEqual(carriedItems.getCount(), 37, "Setting an item target must not consume the carried stack");
        helper.assertValueEqual(
                OrderPackageTarget.get().getTarget(packageStack).orElse(null),
                AEItemKey.of(carriedItems),
                "The fake slot must persist the carried item's complete key identity");

        ItemStack wrappedCustomKey = OrderPackageGhostIngredient.wrapFilter(EchoKey.of());
        int wrappedCount = wrappedCustomKey.getCount();
        targetSlot.increase(wrappedCustomKey);
        helper.assertValueEqual(
                wrappedCustomKey.getCount(),
                wrappedCount,
                "Setting a generic target must not consume its ghost wrapper");
        helper.assertTrue(
                OrderPackageTarget.get().getTarget(packageStack).orElse(null) == EchoKey.of(),
                "The fake slot must preserve a registered custom key without converting it to an item");

        menu.clearTarget();
        helper.assertTrue(OrderPackageTarget.get().getTarget(packageStack).isEmpty(),
                "The menu clear action must remove the target component");
        helper.assertTrue(host.getTargetInventory().getKey(0) == null,
                "The menu clear action must also clear the displayed ghost slot");

        assertSharedGhostConversion(helper);
        helper.succeed();
    }

    private static void assertPersistentRoundTrip(GameTestHelper helper, ItemStack stack, AEKey expectedTarget) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        helper.assertValueEqual(
                OrderPackageTarget.get().getTarget(decoded).orElse(null),
                expectedTarget,
                "Persistent item-stack encoding must retain the target component");
    }

    private static void assertNetworkRoundTrip(GameTestHelper helper, ItemStack stack, AEKey expectedTarget) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.OTHER);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, stack);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(
                    OrderPackageTarget.get().getTarget(decoded).orElse(null),
                    expectedTarget,
                    "Network item-stack encoding must retain the target component");
            helper.assertValueEqual(buffer.readableBytes(), 0, "The network codec must consume the complete package payload");
        } finally {
            buffer.release();
        }
    }

    private static void assertSharedGhostConversion(GameTestHelper helper) {
        GenericStack item = OrderPackageGhostIngredient.toGenericStack(new ItemStack(Items.IRON_INGOT));
        GenericStack fluid = OrderPackageGhostIngredient.toGenericStack(new FluidStack(Fluids.LAVA, 1_000));
        GenericStack custom = OrderPackageGhostIngredient.toGenericStack(EchoKey.of());
        helper.assertValueEqual(item == null ? null : item.what(), AEItemKey.of(Items.IRON_INGOT),
                "The shared ghost adapter must retain item identity");
        helper.assertValueEqual(fluid == null ? null : fluid.what(), AEFluidKey.of(Fluids.LAVA),
                "The shared ghost adapter must retain fluid identity");
        helper.assertTrue(custom != null && custom.what() == EchoKey.of(),
                "The shared ghost adapter must retain registered custom key identity");
    }
}
