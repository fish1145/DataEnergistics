package com.fish_dan_.data_energistics.item.powered.cannon.bow;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.entity.projectile.MatterConvergingBoltEntity;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class StarShardItemGameTest {

    private StarShardItemGameTest() {}

    @GameTest(template = "empty_5x5")
    public static void independentItemStillLoadsAndFiresBow(GameTestHelper h) {
        Player player = player(h);
        ItemStack weapon = weapon(h);
        Item base = weapon.getItem();
        h.assertFalse(base instanceof CrossbowItem || base instanceof ProjectileWeaponItem, "Weapon still inherits a vanilla projectile weapon");
        MatterConvergingCrossbowItem item = (MatterConvergingCrossbowItem) base;
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        h.assertTrue(weapon.getUseAnimation() == UseAnim.CROSSBOW && item.useOnRelease(weapon), "Bow mode lacks its explicit use contract");
        load(item, weapon, player, h);
        h.assertTrue(MatterConvergingCrossbowItem.isCharged(weapon), "Custom bow did not load");
        h.assertTrue(weapon.getOrDefault(AEComponents.STORED_ENERGY, 0.0) == 800, "Loading energy cost");
        h.assertTrue(amount(weapon) == 1, "Loading ammunition cost");
        item.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertFalse(MatterConvergingCrossbowItem.isCharged(weapon), "Custom bow failed to release its loaded projectile");
        h.assertTrue(h.getLevel().getEntitiesOfClass(MatterConvergingBoltEntity.class, player.getBoundingBox().inflate(8), e -> e.getOwner() == player).size() == 1, "Custom shot missing");
        h.assertTrue(amount(weapon) == 1 && weapon.getOrDefault(AEComponents.STORED_ENERGY, 0.0) == 800, "Firing charged twice");
        weapon.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.RAIL.id());
        h.assertTrue(weapon.getUseAnimation() == UseAnim.NONE && !item.useOnRelease(weapon), "Rail mode inherited bow use semantics");
        h.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void fourSpeedCardsKeepBowAutoFire(GameTestHelper h) {
        Player player = player(h);
        ItemStack weapon = weapon(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        for (int i = 0; i < 4; i++) item.getUpgrades(weapon).setItemDirect(i, AEItems.SPEED_CARD.stack());
        item.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        item.onUseTick(h.getLevel(), player, weapon, 3);
        h.assertFalse(player.isUsingItem() || MatterConvergingCrossbowItem.isCharged(weapon), "Auto-fire retained a loaded/using state");
        h.assertValueEqual(h.getLevel().getEntitiesOfClass(MatterConvergingBoltEntity.class, player.getBoundingBox().inflate(8), e -> e.getOwner() == player).size(), 1, "Four-card automatic shot missing");
        h.assertTrue(amount(weapon) == 1 && weapon.getOrDefault(AEComponents.STORED_ENERGY, 0.0) == 800, "Auto-fire resources");
        h.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void partialDrawCostsNothing(GameTestHelper h) {
        Player player = player(h);
        ItemStack weapon = weapon(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        item.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        item.releaseUsing(weapon, h.getLevel(), player, item.getUseDuration(weapon, player) - 5);
        player.stopUsingItem();
        h.assertFalse(MatterConvergingCrossbowItem.isCharged(weapon), "Partial draw loaded ammunition");
        h.assertTrue(amount(weapon) == 2 && weapon.getOrDefault(AEComponents.STORED_ENERGY, 0.0) == 1000, "Partial draw consumed resources");
        h.succeed();
    }

    private static void load(MatterConvergingCrossbowItem item, ItemStack weapon, Player player, GameTestHelper h) {
        item.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        item.releaseUsing(weapon, h.getLevel(), player, 3);
        player.stopUsingItem();
    }

    private static long amount(ItemStack weapon) {
        return MountedAmmoCells.amount(weapon, MatterConvergingCrossbowMode.CROSSBOW, AEItemKey.of(AEItems.MATTER_BALL.asItem()));
    }

    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 1))));
        player.setXRot(0);
        player.setYRot(0);
        return player;
    }

    private static ItemStack weapon(GameTestHelper h) {
        ItemStack weapon = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        ItemStack cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null && storage.insert(AEItemKey.of(AEItems.MATTER_BALL.asItem()), 2, Actionable.MODULATE, IActionSource.empty()) == 2, "Test disk setup");
        storage.persist();
        NonNullList<ItemStack> slots = NonNullList.withSize(3, ItemStack.EMPTY);
        slots.set(MatterConvergingCrossbowMode.CROSSBOW.id(), cell);
        MountedAmmoCells.setCells(weapon, ItemContainerContents.fromItems(slots));
        weapon.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.CROSSBOW.id());
        weapon.set(AEComponents.STORED_ENERGY, 1000.0);
        return weapon;
    }
}
