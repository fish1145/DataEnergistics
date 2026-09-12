package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowAnimation;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowRailRecoil;
import com.fish_dan_.data_energistics.client.render.item.crossbow.plasma.PlasmaPalette;
import com.fish_dan_.data_energistics.entity.projectile.cannon.CannonShot;
import com.fish_dan_.data_energistics.entity.projectile.cannon.RailShot;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailLauncher;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailRecovery;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEEntities;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class RailChargeGameTest {

    private RailChargeGameTest() {}

    @TestHolder("rail_charge_holds_without_firing_and_release_spends_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void chargeRelease(GameTestHelper h) {
        Player player = player(h);
        ItemStack weapon = weapon(h, DataFlowKey.of(), 3000);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        var initial = weapon.get(DEDataComponents.CANNON_CHARGE.get());
        h.assertTrue(initial != null, "Press must start a charge");
        h.runAfterDelay(45, () -> {
            h.assertTrue(amount(weapon, DataFlowKey.of()) == 3000 && energy(weapon) == 1000, "Holding consumed resources");
            h.assertTrue(rounds(h, player).isEmpty(), "Holding fired automatically");
            item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
            h.assertValueEqual(weapon.get(DEDataComponents.CANNON_CHARGE.get()), initial, "Repeated START reset charge");
            release(item, player, weapon);
            h.assertTrue(amount(weapon, DataFlowKey.of()) == 1500 && energy(weapon) == 800, "Single shot cost must be 1500 data + 200 AE");
            var rounds = rounds(h, player);
            h.assertValueEqual(rounds.size(), 1, "Release must create exactly one physical projectile");
            h.assertTrue(GenericStack.unwrapItemStack(rounds.getFirst().getItem()).what().equals(DataFlowKey.of()), "Projectile did not contain selected data ammo");
            h.assertTrue(rounds.getFirst().isNoGravity() && rounds.getFirst().getDeltaMovement().z >= 7.9, "Rail round is not straight/fast");
            release(item, player, weapon);
            item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
            h.assertFalse(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Cooldown bypass");
            h.assertTrue(amount(weapon, DataFlowKey.of()) == 1500 && energy(weapon) == 800, "Duplicate release double charged");
            rounds.forEach(MatterConvergingBoltEntity::discard);
            h.succeed();
        });
    }

    @TestHolder("rail_partial_charge_scales_damage_but_keeps_whole_cost")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void partialCharge(GameTestHelper h) {
        Player player = player(h);
        var key = AEItemKey.of(Items.HEAVY_CORE);
        ItemStack weapon = weapon(h, key, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.runAfterDelay(5, () -> {
            float fraction = weapon.get(DEDataComponents.CANNON_CHARGE.get()).progress(h.getLevel().getGameTime());
            release(item, player, weapon);
            h.assertTrue(amount(weapon, key) == 1 && energy(weapon) == 800, "Partial shot must consume a whole core and full AE");
            Mob target = target(h, 2, 1, 2);
            var shot = rounds(h, player).getFirst();
            h.assertTrue(shot.getItem().is(Items.HEAVY_CORE), "Core projectile model identity");
            shot.onHitEntity(new EntityHitResult(target));
            h.assertTrue(Math.abs(target.getHealth() - (1000 - 34 * fraction)) < 0.01, "Partial damage does not follow original charge fraction");
            h.assertTrue(weapon.get(DEDataComponents.RAIL_COOLDOWN_END.get()) - h.getLevel().getGameTime() == 42, "Heavy cooldown must be three times 14 ticks");
            h.succeed();
        });
    }

    @TestHolder("rail_normal_can_fire_again_during_return")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 80)
    public static void normalRepeatDuringReturn(GameTestHelper h) {
        repeatDuringReturn(h, Items.BLAZE_ROD, RailAmmunition.BLAZE);
    }

    @TestHolder("rail_heavy_requires_full_return_before_charging_again")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 80)
    public static void heavyWaitsForFullReturn(GameTestHelper h) {
        Player player = player(h);
        var key = AEItemKey.of(Items.HEAVY_CORE);
        ItemStack weapon = weapon(h, key, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        // A heavy round cannot opt into an ordinary round's early-charge window either.
        weapon.set(DEDataComponents.RAIL_COOLDOWN_DURATION.get(), 14);
        weapon.set(DEDataComponents.RAIL_COOLDOWN_END.get(), h.getLevel().getGameTime() + 12);
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.assertFalse(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Heavy charge began during an ordinary round's return");
        h.runAfterDelay(12, () -> {
            item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
            h.assertTrue(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Fully recovered heavy charge was rejected");
            h.runAfterDelay(1, () -> {
                release(item, player, weapon);
                h.assertValueEqual(rounds(h, player).size(), 1, "Heavy shot was not fired");
                rounds(h, player).forEach(MatterConvergingBoltEntity::discard);
                h.runAfterDelay(6, () -> {
                    for (int tick = 0; tick < 6; tick++) player.getCooldowns().tick();
                    item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
                    h.assertTrue(RailLauncher.cooling(weapon, h.getLevel().getGameTime()) && !weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Heavy braking completion incorrectly allowed another charge");
                });
                h.runAfterDelay(41, () -> {
                    for (int tick = 0; tick < 35; tick++) player.getCooldowns().tick();
                    item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
                    h.assertFalse(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Heavy charge began before all 42 ticks elapsed");
                });
                h.runAfterDelay(42, () -> {
                    player.getCooldowns().tick();
                    item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
                    h.assertTrue(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Heavy charge was still blocked after full return");
                    h.assertTrue(amount(weapon, key) == 2 && energy(weapon) == 800, "Blocked or restarted heavy charging spent resources");
                    h.succeed();
                });
            });
        });
    }

    private static void repeatDuringReturn(GameTestHelper h, Item ammunition, RailAmmunition kind) {
        Player player = player(h);
        var key = AEItemKey.of(ammunition);
        ItemStack weapon = weapon(h, key, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        int brake = RailRecovery.brakeTicks(kind.cooldownTicks());
        int halfway = brake + (kind.cooldownTicks() - brake) / 2;
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.runAfterDelay(1, () -> {
            release(item, player, weapon);
            h.assertValueEqual(rounds(h, player).size(), 1, "First shot was not fired");
            rounds(h, player).forEach(MatterConvergingBoltEntity::discard);
            h.runAfterDelay(brake - 1, () -> {
                // Mock players are not in the world's tick list; advance their actual cooldown tracker explicitly.
                for (int tick = 0; tick < brake - 1; tick++) player.getCooldowns().tick();
                item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
                h.assertFalse(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Charge began before braking finished");
            });
            h.runAfterDelay(brake, () -> {
                player.getCooldowns().tick();
                item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
                var charge = weapon.get(DEDataComponents.CANNON_CHARGE.get());
                h.assertTrue(charge != null, "Brake completion must allow the next charge before full return");
                h.assertTrue(RailRecovery.chargeProgress(weapon, charge, h.getLevel().getGameTime()) == 0, "Unreturned rails allowed charge");
            });
            h.runAfterDelay(brake + 1, () -> {
                var charge = weapon.get(DEDataComponents.CANNON_CHARGE.get());
                long time = h.getLevel().getGameTime();
                float fraction = RailRecovery.chargeProgress(weapon, charge, time);
                float recovered = 1 - 2 * RailRecovery.retraction(weapon, time);
                h.assertTrue(fraction > 0 && fraction < charge.progress(time) && Math.abs(fraction - recovered) < 0.0001F, "Early charge was not capped by recovered distance");
            });
            h.runAfterDelay(halfway, () -> {
                var charge = weapon.get(DEDataComponents.CANNON_CHARGE.get());
                long time = h.getLevel().getGameTime();
                float fraction = RailRecovery.chargeProgress(weapon, charge, time);
                float expected = Math.min(0.5F, charge.progress(time));
                h.assertTrue(fraction > 0 && Math.abs(fraction - expected) < 0.0001F, "Charge cap did not rise with half-return distance");
                h.assertTrue(RailRecovery.chargeProgress(weapon, charge, time + kind.cooldownTicks()) == 1, "Continuing to hold did not reach full charge after return");
                release(item, player, weapon);
                h.assertTrue(amount(weapon, key) == 1 && energy(weapon) == 600, "Repeat shot did not consume exactly one whole round and 200 AE");
                var shot = rounds(h, player).getFirst();
                Mob target = target(h, 2, 1, 2);
                shot.onHitEntity(new EntityHitResult(target));
                h.assertTrue(Math.abs(target.getHealth() - (1000 - kind.damage(0) * fraction)) < 0.01F, "Repeat impact exceeded recovered charge");
                h.assertTrue(Math.abs(weapon.get(DEDataComponents.RAIL_RECOIL_START.get()) - 0.25F) < 0.0001F, "Repeat recoil jumped away from half-return position");
                ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) weapon.save(h.getLevel().registryAccess()));
                h.assertTrue(Math.abs(RailRecovery.retraction(loaded, time) - 0.25F) < 0.0001F, "Reload lost repeat recoil position");
                release(item, player, weapon);
                h.assertTrue(amount(weapon, key) == 1 && energy(weapon) == 600, "Duplicate repeat release spent resources");
                h.succeed();
            });
        });
    }

    @TestHolder("rail_cancel_change_cell_and_reload_cost_nothing")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cancelAndReload(GameTestHelper h) {
        Player player = player(h);
        ItemStack weapon = weapon(h, DataFlowKey.of(), 3000);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        RailLauncher.cancel(weapon);
        release(item, player, weapon);
        h.assertTrue(amount(weapon, DataFlowKey.of()) == 3000 && energy(weapon) == 1000, "Cancel spent resources");
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) weapon.save(h.getLevel().registryAccess()));
        h.assertFalse(loaded.has(DEDataComponents.CANNON_CHARGE.get()), "Charge persisted");
        h.assertFalse(loaded.has(DEDataComponents.RAIL_CHARGE_AMMO.get()), "Charged key persisted");
        h.assertTrue(amount(loaded, DataFlowKey.of()) == 3000, "Reload lost resources");
        MountedAmmoCells.setCells(weapon, ItemContainerContents.fromItems(NonNullList.withSize(3, ItemStack.EMPTY)));
        h.assertFalse(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Removing disk didn't cancel charge");
        release(item, player, weapon);
        h.assertTrue(rounds(h, player).isEmpty(), "Removed disk could still fire");
        h.succeed();
    }

    @TestHolder("rail_insufficient_resources_and_invalid_aim")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void invalidRelease(GameTestHelper h) {
        Player player = player(h);
        ItemStack weapon = weapon(h, DataFlowKey.of(), 1499);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.assertFalse(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Insufficient data started charge");
        MountedAmmoCells.transfer(weapon, DataFlowKey.of(), 1, true, Actionable.MODULATE);
        weapon.set(AEComponents.STORED_ENERGY, 199.0);
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.assertFalse(weapon.has(DEDataComponents.CANNON_CHARGE.get()), "Insufficient AE started charge");
        weapon.set(AEComponents.STORED_ENERGY, 1000.0);
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.runAfterDelay(5, () -> {
            item.releaseCannonCharge(player, InteractionHand.MAIN_HAND, weapon, new Vec3(50, 0, 0), new Vec3(0, 0, 1));
            h.assertTrue(amount(weapon, DataFlowKey.of()) == 1500 && energy(weapon) == 1000, "Invalid aim spent resources");
            h.assertTrue(rounds(h, player).isEmpty(), "Invalid aim spawned a round");
            h.succeed();
        });
    }

    @TestHolder("rail_single_impact_damage_layers_and_snapshot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void impacts(GameTestHelper h) {
        Player player = player(h);
        float[][] damages = { { 8, 10, 14 }, { 34, 40, 40 }, { 13, 13, 20 } };
        var kinds = List.of(RailAmmunition.BLAZE, RailAmmunition.HEAVY, RailAmmunition.DATA);
        for (int k = 0; k < kinds.size(); k++) for (int cards = 0; cards <= 2; cards++) {
            var kind = kinds.get(k);
            Mob target = target(h, 2, 1, 2);
            target.setHealth(800);
            var shot = round(h, player, new RailShot(kind, cards, 1));
            CompoundTag saved = new CompoundTag();
            shot.addAdditionalSaveData(saved);
            var restored = new MatterConvergingBoltEntity(DEEntities.MATTER_CONVERGING_BOLT.get(), h.getLevel());
            restored.readAdditionalSaveData(saved);
            float expected = damages[k][cards];
            if (kind == RailAmmunition.HEAVY && cards == 2) expected += 50;
            if (kind == RailAmmunition.DATA && cards == 1) expected += 13 * 0.05F;
            if (kind == RailAmmunition.DATA && cards == 2) expected += 150;
            restored.onHitEntity(new EntityHitResult(target));
            h.assertTrue(Math.abs(target.getHealth() - (800 - expected)) < 0.01, "Wrong full-charge hit " + kind + " cards=" + cards + " health=" + target.getHealth());
            float after = target.getHealth();
            restored.onHitEntity(new EntityHitResult(target));
            h.assertTrue(target.getHealth() == after, "Projectile settled a second collision");
            if (kind == RailAmmunition.HEAVY) h.assertValueEqual(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier(), cards == 2 ? 9 : 3, "Slow level");
            if (kind == RailAmmunition.DATA) {
                var effect = target.getEffect(DEMobEffects.RADIX_LOSS);
                h.assertValueEqual(effect.getAmplifier(), cards == 0 ? 0 : 1, "Data stack count");
                h.assertValueEqual(effect.getDuration(), cards == 0 ? 80 : 180, "Data effect duration");
            }
            if (kind == RailAmmunition.BLAZE) h.assertTrue(target.getRemainingFireTicks() >= (cards == 0 ? 100 : 200), "Blaze ignition duration");
            shot.discard();
            target.discard();
        }
        h.succeed();
    }

    @TestHolder("all_modes_accept_item_data_and_fluid_disks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unrestrictedDisks(GameTestHelper h) {
        for (var disk : List.of(AEItems.ITEM_CELL_1K.stack(), AEItems.FLUID_CELL_1K.stack(), DEItems.DIGITAL_STORAGE_CELL_64K.toStack())) {
            for (var mode : MatterConvergingCrossbowMode.values()) {
                var weapon = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
                var slots = NonNullList.withSize(3, ItemStack.EMPTY);
                slots.set(mode.id(), disk);
                MountedAmmoCells.setCells(weapon, ItemContainerContents.fromItems(slots));
                h.assertTrue(ItemStack.isSameItemSameComponents(MountedAmmoCells.cell(weapon, mode), disk), "Disk rejected in " + mode);
            }
        }
        h.succeed();
    }

    @TestHolder("rail_recoil_preserves_exhaust_without_long_hold")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void recoil(GameTestHelper h) {
        CrossbowAnimation animation = new CrossbowAnimation();
        for (int tick = 0; tick < 36; tick++) animation.tick(true, true, false, 1, MatterConvergingCrossbowMode.RAIL, false);
        h.assertTrue(animation.exhaustStrength() == 0, "Charging sprayed exhaust");
        animation.tick(true, false, false, 0, MatterConvergingCrossbowMode.RAIL, true);
        float max = 0;
        for (int tick = 1; tick <= 14; tick++) {
            animation.tick(true, false, false, 0, MatterConvergingCrossbowMode.RAIL, false);
            max = Math.max(max, animation.exhaustStrength());
        }
        h.assertTrue(max > 0 && animation.exhaustStrength() == 0 && animation.pose(1).recoil() == 0, "Short recoil/exhaust timeline");
        h.succeed();
    }

    private static MatterConvergingBoltEntity round(GameTestHelper h, Player owner, RailShot shot) {
        var entity = new MatterConvergingBoltEntity(h.getLevel(), owner, new ItemStack(Items.BLAZE_ROD));
        entity.configureCannonShot(new CannonShot(MatterConvergingCrossbowMode.RAIL, shot.charge(), 3.15F));
        entity.configureRailShot(shot);
        return entity;
    }

    @TestHolder("rail_cooldown_two_tick_brake_twelve_tick_return")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void newCooldown(GameTestHelper h) {
        h.assertValueEqual(RailAmmunition.BLAZE.cooldownTicks(), 14, "Normal cooldown");
        h.assertValueEqual(RailAmmunition.HEAVY.cooldownTicks(), 42, "Heavy cooldown");
        h.assertTrue(CrossbowRailRecoil.retraction(2) == 0.5F, "Brake must reach half stroke at 0.1s");
        h.assertTrue(CrossbowRailRecoil.retraction(13) > 0 && CrossbowRailRecoil.retraction(14) == 0, "Return must take another 0.6s");
        h.assertTrue(CrossbowRailRecoil.retraction(6, 42) == 0.5F, "Heavy brake must take three times as long");
        float previous = 0.5F;
        for (int tick = 7; tick <= 42; tick++) {
            float current = CrossbowRailRecoil.retraction(tick, 42);
            h.assertTrue(current >= 0 && current <= previous, "Heavy return rebounds");
            previous = current;
        }
        h.assertTrue(previous == 0, "Heavy return not finished");
        float returnedPosition = RailRecovery.retraction(8, 14, 0);
        CrossbowAnimation animation = new CrossbowAnimation();
        animation.tick(true, true, false, 0.5F, MatterConvergingCrossbowMode.RAIL, false, 14, 8);
        animation.tick(true, false, false, 0, MatterConvergingCrossbowMode.RAIL, true, 14, 0, returnedPosition);
        h.assertTrue(animation.pose(0).recoil() == returnedPosition && animation.pose(1).recoil() == returnedPosition, "Repeat shot visually jumped to fully extended rails");
        h.assertTrue(RailRecovery.retraction(2, 14, returnedPosition) == 0.5F, "Repeat brake did not reach the authored stop");
        h.succeed();
    }

    @TestHolder("plasma_color_uses_visible_texture_hue")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void plasmaColors(GameTestHelper h) {
        int[] dataPixels = { 0x000000FF, 0xFF000000, 0xFFFFFF00 };
        PlasmaPalette cyan = PlasmaPalette.sample(3, 1, (x, y) -> dataPixels[x]);
        h.assertValueEqual(cyan.rgb(), 0x00FFFF, "Transparent/background pixels polluted cyan");
        PlasmaPalette green = PlasmaPalette.sample(1, 1, (x, y) -> 0xFF009900);
        h.assertValueEqual(green.rgb(), 0x00FF00, "FE texture green was not preserved");
        h.assertTrue(green.red(0.8F) > green.red(0) && green.green(0.8F) == 1, "Core must whiten without changing the color identity");
        h.succeed();
    }

    @TestHolder("rail_round_real_flight_hits_once")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 60)
    public static void physicalFlight(GameTestHelper h) {
        Player player = player(h);
        Mob target = target(h, 2, 2, 6);
        ItemStack weapon = weapon(h, AEItemKey.of(Items.BLAZE_ROD), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.runAfterDelay(21, () -> {
            h.assertTrue(target.getHealth() == 1000, "Charge caused damage before release");
            release(item, player, weapon);
            h.assertTrue(target.getHealth() == 1000, "Release used instant ray damage");
        });
        h.runAfterDelay(24, () -> {
            h.assertTrue(target.getHealth() == 992, "Real swept projectile did not apply one hit");
            h.assertTrue(rounds(h, player).isEmpty(), "Spent projectile remained");
            h.succeed();
        });
    }

    @TestHolder("rail_round_stops_at_wall_before_target")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 60)
    public static void wallCollision(GameTestHelper h) {
        Player player = player(h);
        Mob target = target(h, 2, 2, 6);
        for (int y = 1; y <= 6; y++) for (int x = 1; x <= 3; x++) h.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
        ItemStack weapon = weapon(h, AEItemKey.of(Items.BLAZE_ROD), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var item = (MatterConvergingCrossbowItem) weapon.getItem();
        item.beginCannonCharge(player, InteractionHand.MAIN_HAND, weapon);
        h.runAfterDelay(21, () -> release(item, player, weapon));
        h.runAfterDelay(24, () -> {
            h.assertTrue(target.getHealth() == 1000, "Rail crossed a blocking wall");
            h.assertTrue(amount(weapon, AEItemKey.of(Items.BLAZE_ROD)) == 1, "Wall shot must cost a whole round");
            h.assertTrue(rounds(h, player).isEmpty(), "Round remained after wall impact");
            h.succeed();
        });
    }

    @TestHolder("rail_spawn_preserves_selected_item_and_unclamped_speed")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void spawnData(GameTestHelper h) {
        var source = new RailRoundEntity(h.getLevel(), player(h), new ItemStack(Items.HEAVY_CORE));
        source.setDeltaMovement(0, 0, 14.3);
        var receiver = new RailRoundEntity(DEEntities.RAIL_ROUND.get(), h.getLevel());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            source.writeSpawnData(buffer);
            receiver.readSpawnData(buffer);
            h.assertTrue(receiver.getItem().is(Items.HEAVY_CORE), "Spawn lost selected model");
            h.assertTrue(Math.abs(receiver.getDeltaMovement().z - 14.3) < 0.00001, "Spawn velocity was clamped");
            h.assertTrue(receiver.firingMode() == MatterConvergingCrossbowMode.RAIL && receiver.isNoGravity(), "Spawn did not initialize rail flight");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    private static List<MatterConvergingBoltEntity> rounds(GameTestHelper h, Player owner) {
        return h.getLevel().getEntitiesOfClass(MatterConvergingBoltEntity.class, owner.getBoundingBox().inflate(64), e -> e.getOwner() == owner && !e.isRemoved());
    }

    private static void release(MatterConvergingCrossbowItem item, Player player, ItemStack weapon) {
        item.releaseCannonCharge(player, InteractionHand.MAIN_HAND, weapon, new Vec3(0, -.1, .5), new Vec3(0, 0, 1));
    }

    private static double energy(ItemStack weapon) {
        return weapon.getOrDefault(AEComponents.STORED_ENERGY, 0.0);
    }

    private static long amount(ItemStack weapon, AEKey key) {
        return MountedAmmoCells.amount(weapon, MatterConvergingCrossbowMode.RAIL, key);
    }

    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 1))));
        player.setYRot(0);
        player.setXRot(0);
        return player;
    }

    private static Mob target(GameTestHelper h, int x, int y, int z) {
        Mob target = h.spawn(EntityType.IRON_GOLEM, new BlockPos(x, y, z));
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        return target;
    }

    private static ItemStack weapon(GameTestHelper h, AEKey key, long amount) {
        var weapon = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        weapon.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.RAIL.id());
        ItemStack cell = key instanceof AEItemKey ? AEItems.ITEM_CELL_1K.stack() : key.equals(DataFlowKey.of()) ? DEItems.DIGITAL_STORAGE_CELL_64K.toStack() : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("appflux", "fe_1k_cell")));
        var inventory = StorageCells.getCellInventory(cell, null);
        h.assertTrue(inventory != null && inventory.insert(key, amount, Actionable.MODULATE, IActionSource.empty()) == amount, "Cannot fill disk");
        inventory.persist();
        var slots = NonNullList.withSize(3, ItemStack.EMPTY);
        slots.set(1, cell);
        MountedAmmoCells.setCells(weapon, ItemContainerContents.fromItems(slots));
        weapon.set(AEComponents.STORED_ENERGY, 1000.0);
        return weapon;
    }
}
