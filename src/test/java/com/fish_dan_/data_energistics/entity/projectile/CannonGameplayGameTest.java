package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.entity.projectile.cannon.CannonShot;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonBallistics;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEEntities;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.ids.AEComponents;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CannonGameplayGameTest {

    private CannonGameplayGameTest() {}

    @TestHolder("cannon_projectiles_match_preview_tick_rules")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void projectileFlightMatchesPreview(GameTestHelper helper) {
        for (MatterConvergingCrossbowMode mode : new MatterConvergingCrossbowMode[] { MatterConvergingCrossbowMode.GRENADE, MatterConvergingCrossbowMode.RAIL }) {
            for (float fraction : new float[] { 0.05F, 0.25F, 0.5F, 1.0F }) {
                for (boolean saber : new boolean[] { false, true }) {
                    for (float ammoSpeed : new float[] { 3.15F, 7.15F }) {
                        CannonShot shot = new CannonShot(mode, fraction, ammoSpeed);
                        Projectile projectile;
                        if (saber) {
                            ThrownLightSaberEntity arrow = new ThrownLightSaberEntity(DEEntities.THROWN_LIGHT_SABER.get(), helper.getLevel());
                            arrow.configureCannonShot(shot);
                            projectile = arrow;
                        } else {
                            MatterConvergingBoltEntity bolt = new MatterConvergingBoltEntity(DEEntities.MATTER_CONVERGING_BOLT.get(), helper.getLevel());
                            bolt.setItem(AEItems.MATTER_BALL.stack());
                            bolt.configureCannonShot(shot);
                            projectile = bolt;
                        }
                        Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 90, 2)));
                        Vec3 velocity = CannonBallistics.launchVelocity(mode, fraction, new Vec3(0, 0.25, 1), ammoSpeed);
                        projectile.setPos(position);
                        projectile.setDeltaMovement(velocity);
                        for (int tick = 0; tick < 12; tick++) {
                            position = position.add(velocity);
                            velocity = CannonBallistics.nextVelocity(velocity, mode, false, saber);
                            projectile.tick();
                            helper.assertTrue(projectile.position().distanceTo(position) < 1E-5D, "Flight position differs from preview: " + mode + " saber=" + saber + " tick=" + tick);
                            helper.assertTrue(projectile.getDeltaMovement().distanceTo(velocity) < 1E-6D, "Flight velocity differs from preview: " + mode + " saber=" + saber + " tick=" + tick);
                        }
                        projectile.discard();
                    }
                }
            }
        }
        helper.succeed();
    }

    @TestHolder("cannon_rail_damage_scales_with_charge_not_flight_speed")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void railDamageScalesWithCharge(GameTestHelper helper) {
        for (float fraction : new float[] { 0.25F, 0.5F, 1.0F }) {
            var target = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 1, 2));
            float before = target.getHealth();
            MatterConvergingBoltEntity bolt = new MatterConvergingBoltEntity(DEEntities.MATTER_CONVERGING_BOLT.get(), helper.getLevel());
            bolt.setItem(AEItems.MATTER_BALL.stack());
            bolt.configureCannonShot(new CannonShot(MatterConvergingCrossbowMode.RAIL, fraction, 3.15F));
            bolt.setDeltaMovement(0, 0, 0.8D);
            bolt.onHitEntity(new EntityHitResult(target));
            helper.assertTrue(Math.abs(before - target.getHealth() - 31.5F * fraction) < 1E-4F, "Rail damage is not proportional to server charge");
            target.discard();
        }
        helper.succeed();
    }

    @TestHolder("cannon_left_release_fires_once_without_crossbow_loading")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 60)
    public static void releaseFiresWithoutSecondClick(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        stack.set(AEComponents.STORED_ENERGY, 1000.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getInventory().add(AEItems.MATTER_BALL.stack(2));
        MatterConvergingCrossbowItem item = (MatterConvergingCrossbowItem) stack.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(stack.has(DEDataComponents.CANNON_CHARGE.get()), "Left press did not start independent cannon charge");
        helper.assertFalse(player.isUsingItem(), "Cannon entered vanilla right-button use state");
        helper.assertFalse(MatterConvergingCrossbowItem.isCharged(stack), "Cannon preloaded a crossbow projectile");
        helper.assertTrue(item.getAECurrentPower(stack) == 1000.0D, "Charge press spent firing energy");
        helper.runAfterDelay(5, () -> {
            item.releaseCannonCharge(player, InteractionHand.MAIN_HAND, stack, new Vec3(0, -.1, .5), new Vec3(0, 0, 1));
            helper.assertValueEqual(stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0), 1, "Release did not fire");
            helper.assertFalse(stack.has(DEDataComponents.CANNON_CHARGE.get()), "Charge survived release");
            helper.assertFalse(MatterConvergingCrossbowItem.isCharged(stack), "Cannon requires a second click");
            helper.assertTrue(item.getAECurrentPower(stack) == 800.0D, "Shot spent incorrect energy");
            item.releaseCannonCharge(player, InteractionHand.MAIN_HAND, stack, new Vec3(0, -.1, .5), new Vec3(0, 0, 1));
            helper.assertTrue(item.getAECurrentPower(stack) == 800.0D, "Duplicate release spent more energy");
            helper.assertValueEqual(stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0), 1, "Duplicate release fired again");
            item.beginCannonCharge(player, InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(stack.has(DEDataComponents.CANNON_CHARGE.get()), "The next charge must begin during the outgoing shot cooldown");
            helper.succeed();
        });
    }

    @TestHolder("cannon_switch_and_invalid_aim_cancel_without_spending")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cancellationDoesNotFire(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        stack.set(AEComponents.STORED_ENERGY, 1000.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getInventory().add(AEItems.MATTER_BALL.stack(2));
        MatterConvergingCrossbowItem item = (MatterConvergingCrossbowItem) stack.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, stack);
        stack.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.RAIL.id());
        item.inventoryTick(stack, helper.getLevel(), player, 0, true);
        helper.assertFalse(stack.has(DEDataComponents.CANNON_CHARGE.get()), "Mode switch did not cancel charge");
        item.releaseCannonCharge(player, InteractionHand.MAIN_HAND, stack, new Vec3(0, -.1, .5), new Vec3(0, 0, 1));
        helper.assertTrue(item.getAECurrentPower(stack) == 1000, "Cancelled charge spent energy");
        helper.assertFalse(CannonBallistics.validAim(new Vec3(100, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, 1)), "Remote muzzle was accepted");
        helper.assertFalse(CannonBallistics.validAim(Vec3.ZERO, new Vec3(Double.NaN, 0, 0), new Vec3(0, 0, 1)), "NaN direction was accepted");
        helper.assertFalse(CannonBallistics.validAim(Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 0, 1)), "Backwards direction was accepted");
        helper.succeed();
    }

    @TestHolder("cannon_charge_is_transient_and_shot_survives_save")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void chargeAndShotSerialization(GameTestHelper helper) {
        Player player = player(helper);
        CannonCharge charge = new CannonCharge(helper.getLevel().getGameTime(), 20, MatterConvergingCrossbowMode.RAIL, InteractionHand.MAIN_HAND, player.getUUID(), helper.getLevel().dimension().location());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CannonCharge.STREAM_CODEC.encode(buffer, charge);
            helper.assertValueEqual(CannonCharge.STREAM_CODEC.decode(buffer), charge, "Charge synchronization lost fields");
        } finally {
            buffer.release();
        }
        ItemStack stack = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        stack.set(DEDataComponents.CANNON_CHARGE.get(), charge);
        ItemStack restored = ItemStack.parseOptional(helper.getLevel().registryAccess(), (CompoundTag) stack.save(helper.getLevel().registryAccess()));
        helper.assertFalse(restored.has(DEDataComponents.CANNON_CHARGE.get()), "Charge persisted across world reload");
        CannonShot shot = new CannonShot(MatterConvergingCrossbowMode.RAIL, .25F, 3.15F);
        CompoundTag tag = new CompoundTag();
        shot.save(tag);
        helper.assertValueEqual(CannonShot.load(tag), shot, "Shot mode or damage changed after reload");
        helper.assertValueEqual(CannonShot.load(new CompoundTag()), CannonShot.CROSSBOW, "Legacy projectile did not preserve crossbow behavior");
        helper.succeed();
    }

    @TestHolder("cannon_full_speed_charge_holds_until_release")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 80)
    public static void fullChargeNeverAutoFires(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        stack.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.RAIL.id());
        stack.set(AEComponents.STORED_ENERGY, 1000.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getInventory().add(AEItems.MATTER_BALL.stack(2));
        MatterConvergingCrossbowItem item = (MatterConvergingCrossbowItem) stack.getItem();
        item.getUpgrades(stack).setItemDirect(0, AEItems.SPEED_CARD.stack(4));
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, stack);
        helper.runAfterDelay(30, () -> {
            CannonCharge charge = stack.get(DEDataComponents.CANNON_CHARGE.get());
            helper.assertTrue(charge != null && charge.progress(helper.getLevel().getGameTime()) == 1, "Full charge was not held");
            helper.assertValueEqual(stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0), 0, "Cannon auto-fired before release");
            item.releaseCannonCharge(player, InteractionHand.MAIN_HAND, stack, new Vec3(0, -.1, .5), new Vec3(0, 0, 1));
            helper.assertValueEqual(stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0), 1, "Full charged release did not fire");
            helper.succeed();
        });
    }

    @TestHolder("cannon_uses_preloaded_crossbow_ammo_once_without_double_charge")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 60)
    public static void previouslyLoadedAmmoIsNotChargedTwice(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        stack.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.RAIL.id());
        stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(AEItems.MATTER_BALL.stack()));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        MatterConvergingCrossbowItem item = (MatterConvergingCrossbowItem) stack.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, stack);
        helper.runAfterDelay(5, () -> {
            item.releaseCannonCharge(player, InteractionHand.MAIN_HAND, stack, new Vec3(0, -.1, .5), new Vec3(0, 0, 1));
            helper.assertValueEqual(stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0), 1, "Prepaid shot could not fire with empty battery");
            helper.assertFalse(MatterConvergingCrossbowItem.isCharged(stack), "Prepaid projectile was duplicated");
            helper.assertTrue(item.getAECurrentPower(stack) == 0, "Prepaid shot was charged twice");
            helper.succeed();
        });
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 1))));
        player.setYRot(0);
        player.setXRot(0);
        return player;
    }
}
