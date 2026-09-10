package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowRailRecoil;
import com.fish_dan_.data_energistics.effect.ChromaticGlow;
import com.fish_dan_.data_energistics.effect.RadixLossEffectLogic;
import com.fish_dan_.data_energistics.effect.WeaponBurn;
import com.fish_dan_.data_energistics.entity.projectile.cannon.ElementalGrenade;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.weapon.appflux.FluxAmmunition;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.AmmunitionRules;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailBeam;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailFiring;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailSession;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class WeaponAmmunitionGameTest {

    private WeaponAmmunitionGameTest() {}

    @TestHolder("weapon_mode_ammunition_and_cell_types")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void modeFilters(GameTestHelper h) {
        h.assertTrue(MatterConvergingCrossbowItem.supportsAmmo(MatterConvergingCrossbowMode.GRENADE, new ItemStack(Items.WIND_CHARGE)), "Wind grenade rejected");
        h.assertFalse(MatterConvergingCrossbowItem.supportsAmmo(MatterConvergingCrossbowMode.RAIL, AEItems.SINGULARITY.stack()), "Rail accepted crossbow ammo");
        h.assertTrue(MatterConvergingCrossbowItem.supportsAmmo(MatterConvergingCrossbowMode.CROSSBOW, DEItems.SINGULARITY_BLOCK.toStack()), "Cube rejected");
        h.assertTrue(MountedAmmoCells.accepts(DEItems.DIGITAL_STORAGE_CELL_64K.toStack(), MatterConvergingCrossbowMode.RAIL), "Data disk rejected");
        h.assertFalse(MountedAmmoCells.accepts(DEItems.DIGITAL_STORAGE_CELL_64K.toStack(), MatterConvergingCrossbowMode.CROSSBOW), "Data disk accepted by bow");
        for (int c = 0; c <= 2; c++) {
            h.assertValueEqual(AmmunitionRules.cube(c).fragments(), 4 + c * 2, "Fragment tiers");
            h.assertTrue(AmmunitionRules.cube(c).fragmentDamage() == (c == 0 ? 9 : 10), "Fragment quarter damage");
        }
        h.succeed();
    }

    @TestHolder("weapon_wind_actual_rise_and_damage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void windRise(GameTestHelper h) {
        for (int cards = 0; cards <= 2; cards++) {
            Mob target = target(h, new BlockPos(2, 90, 2));
            target.setNoAi(false);
            Vec3 center = target.position();
            var profile = AmmunitionRules.wind(cards);
            ElementalGrenade.detonate(h.getLevel(), new ItemStack(Items.WIND_CHARGE), center, null, cards);
            h.assertTrue(Math.abs(1000 - target.getHealth() - profile.damage()) < 0.001, "Wrong wind damage");
            double peak = target.getY();
            for (int tick = 0; tick < 100; tick++) {
                target.tick();
                peak = Math.max(peak, target.getY());
            }
            h.assertTrue(Math.abs(peak - center.y - profile.height()) < 0.15, "Wind rise was " + (peak - center.y) + " expected " + profile.height());
            target.discard();
        }
        h.succeed();
    }

    @TestHolder("weapon_burn_refresh_preserves_damage_cadence")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 80)
    public static void timedBurn(GameTestHelper h) {
        Mob target = target(h, new BlockPos(2, 1, 2));
        WeaponBurn.apply(target, null, 100, 1.5F);
        h.runAfterDelay(10, () -> WeaponBurn.apply(target, null, 100, 1.5F));
        h.runAfterDelay(41, () -> {
            h.assertTrue(Math.abs(target.getHealth() - 997) < 0.001, "Burn refreshed cadence or doubled vanilla damage: " + target.getHealth());
            target.clearFire();
        });
        h.runAfterDelay(62, () -> {
            h.assertTrue(Math.abs(target.getHealth() - 997) < 0.001, "Extinguished burn continued");
            h.succeed();
        });
    }

    @TestHolder("weapon_rail_fixed_hit_tiers_and_heavy_missing_health")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void railDamage(GameTestHelper h) {
        Player player = player(h);
        for (int cards = 0; cards <= 2; cards++) {
            for (RailAmmunition ammo : List.of(RailAmmunition.BLAZE, RailAmmunition.HEAVY, RailAmmunition.DATA)) {
                Mob target = target(h, new BlockPos(2, 1, 2));
                target.setHealth(800);
                AEKey key = ammo == RailAmmunition.DATA ? DataFlowKey.of() : AEItemKey.of(ammo == RailAmmunition.BLAZE ? Items.BLAZE_ROD : Items.HEAVY_CORE);
                RailSession session = new RailSession(UUID.randomUUID(), key, cards, 1);
                RailBeam.hit(player, session, new RailBeam.Contact(target.position(), target, List.of()), 1);
                float expected = ammo.damage(cards) + (ammo == RailAmmunition.HEAVY && cards == 2 ? 50 : 0);
                h.assertTrue(Math.abs(800 - target.getHealth() - expected) < 0.001, "Wrong rail damage " + ammo + " " + cards);
                if (ammo == RailAmmunition.HEAVY) h.assertValueEqual(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier(), cards == 2 ? 9 : 3, "Slowness tier");
                target.discard();
            }
        }
        h.succeed();
    }

    @TestHolder("weapon_rail_fe_fractional_cadence")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void feCadence(GameTestHelper h) {
        if (!ModFlags.isAppFluxLoaded()) {
            h.succeed();
            return;
        }
        Player player = player(h);
        for (int cards = 0; cards <= 2; cards++) {
            Mob target = target(h, new BlockPos(2, 1, 2));
            for (int tick = 1; tick <= 30; tick++) {
                RailBeam.hit(player, new RailSession(UUID.randomUUID(), FluxAmmunition.key(), cards, tick),
                        new RailBeam.Contact(target.position(), null, List.of(target)), 0);
            }
            float expected = cards == 0 ? 6 : cards == 1 ? 10 : 40;
            h.assertTrue(Math.abs(1000 - target.getHealth() - expected) < 0.001, "FE cadence " + cards);
            target.discard();
        }
        h.succeed();
    }

    @TestHolder("weapon_radix_rail_percent_is_per_application")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void radixPercent(GameTestHelper h) {
        Mob target = target(h, new BlockPos(2, 1, 2));
        RadixLossEffectLogic.applyOrBurst(target, 30, null, 6, true);
        h.assertTrue(target.getHealth() == 1000, "First stack dealt percentage damage");
        RadixLossEffectLogic.applyOrBurst(target, 30, null, 6, true);
        h.assertTrue(Math.abs(target.getHealth() - 850) < 0.001, "One old stack must deal 15%");
        RadixLossEffectLogic.applyOrBurst(target, 30, null, 6, true);
        h.assertTrue(Math.abs(target.getHealth() - 550) < 0.001, "Two old stacks must deal 30%");
        target.removeEffect(DEMobEffects.RADIX_LOSS);
        target.setHealth(1000);
        target.invulnerableTime = 0;
        RadixLossEffectLogic.applyOrBurst(target, 30, null, 20);
        RadixLossEffectLogic.applyOrBurst(target, 30, null, 20);
        h.assertTrue(Math.abs(target.getHealth() - 999) < 0.001, "Original weapon radix formula changed");
        h.succeed();
    }

    @TestHolder("weapon_rail_partial_settlement_and_duplicate_release")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void partialRail(GameTestHelper h) {
        Player player = player(h);
        ItemStack weapon = railWeapon(h, DataFlowKey.of(), 3000, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        RailFiring.begin(player, InteractionHand.MAIN_HAND, weapon);
        h.assertTrue(weapon.has(DEDataComponents.RAIL_SESSION.get()), "Rail did not start");
        RailFiring.begin(player, InteractionHand.MAIN_HAND, weapon);
        h.runAfterDelay(11, () -> {
            RailSession session = weapon.get(DEDataComponents.RAIL_SESSION.get());
            h.assertTrue(session != null && session.elapsed() > 0, "Shooting did not advance");
            int ticks = session.elapsed();
            RailFiring.stop(player, weapon);
            long expected = 3000 - RailAmmunition.DATA.consumed(ticks);
            h.assertTrue(MountedAmmoCells.amount(weapon, MatterConvergingCrossbowMode.RAIL, DataFlowKey.of()) == expected, "Wrong proportional data consumption");
            double energy = weapon.getOrDefault(AEComponents.STORED_ENERGY, 0.0);
            h.assertTrue(Math.abs(energy - (1000 - ticks)) < 0.001, "Wrong proportional AE consumption");
            RailFiring.stop(player, weapon);
            RailFiring.begin(player, InteractionHand.MAIN_HAND, weapon);
            h.assertFalse(weapon.has(DEDataComponents.RAIL_SESSION.get()), "Cooldown bypassed");
            h.assertTrue(MountedAmmoCells.amount(weapon, MatterConvergingCrossbowMode.RAIL, DataFlowKey.of()) == expected, "Duplicate release changed resources");
            h.assertTrue(weapon.get(DEDataComponents.RAIL_COOLDOWN_END.get()) - h.getLevel().getGameTime() == 80, "Cooldown is not 80 ticks");
            h.succeed();
        });
    }

    @TestHolder("weapon_rail_full_duration_and_heavy_extension")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 330)
    public static void fullRail(GameTestHelper h) {
        Player regular = player(h), heavy = player(h);
        ItemStack normal = railWeapon(h, AEItemKey.of(Items.BLAZE_ROD), 3, 1000);
        ItemStack extended = railWeapon(h, AEItemKey.of(Items.HEAVY_CORE), 3, 1000);
        regular.setItemInHand(InteractionHand.MAIN_HAND, normal);
        heavy.setItemInHand(InteractionHand.MAIN_HAND, extended);
        RailFiring.begin(regular, InteractionHand.MAIN_HAND, normal);
        RailFiring.begin(heavy, InteractionHand.MAIN_HAND, extended);
        h.runAfterDelay(201, () -> {
            h.assertFalse(normal.has(DEDataComponents.RAIL_SESSION.get()), "Normal rail exceeded 200 ticks");
            h.assertTrue(extended.has(DEDataComponents.RAIL_SESSION.get()), "Heavy rail did not extend to 270 ticks");
            h.assertTrue(normal.getOrDefault(AEComponents.STORED_ENERGY, 0.0) == 800, "Full energy cost");
        });
        h.runAfterDelay(271, () -> {
            h.assertFalse(extended.has(DEDataComponents.RAIL_SESSION.get()), "Heavy rail exceeded 270 ticks");
            h.assertTrue(MountedAmmoCells.amount(extended, MatterConvergingCrossbowMode.RAIL, AEItemKey.of(Items.HEAVY_CORE)) == 2, "Heavy item cost");
            h.assertTrue(extended.getOrDefault(AEComponents.STORED_ENERGY, 0.0) == 800, "Heavy energy cost");
            h.succeed();
        });
    }

    @TestHolder("weapon_rail_escrow_reload_reconciles_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reloadEscrow(GameTestHelper h) {
        ItemStack weapon = railWeapon(h, DataFlowKey.of(), 1500, 800);
        weapon.set(DEDataComponents.RAIL_SESSION.get(), new RailSession(UUID.randomUUID(), DataFlowKey.of(), 2, 100));
        ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) weapon.save(h.getLevel().registryAccess()));
        RailFiring.reconcile(loaded, h.getLevel());
        h.assertFalse(loaded.has(DEDataComponents.RAIL_SESSION.get()), "Reload resumed shooting");
        h.assertTrue(MountedAmmoCells.amount(loaded, MatterConvergingCrossbowMode.RAIL, DataFlowKey.of()) == 2250, "Reload refund wrong");
        h.assertTrue(loaded.getOrDefault(AEComponents.STORED_ENERGY, 0.0) == 900, "Reload AE refund");
        RailFiring.reconcile(loaded, h.getLevel());
        h.assertTrue(MountedAmmoCells.amount(loaded, MatterConvergingCrossbowMode.RAIL, DataFlowKey.of()) == 2250, "Reload refunded twice");
        h.succeed();
    }

    @TestHolder("weapon_cube_splits_once_and_snapshots_upgrades")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cubeFragments(GameTestHelper h) {
        Player player = player(h);
        for (int cards = 0; cards <= 2; cards++) {
            ItemStack weapon = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
            MatterConvergingCrossbowItem item = (MatterConvergingCrossbowItem) weapon.getItem();
            for (int slot = 0; slot < cards; slot++) item.getUpgrades(weapon).setItemDirect(slot, DEItems.CARD_SABER_ENERGY.toStack());
            Mob target = target(h, new BlockPos(2, 1, 2));
            MatterConvergingBoltEntity bolt = new MatterConvergingBoltEntity(h.getLevel(), player, DEItems.SINGULARITY_BLOCK.toStack());
            bolt.setWeaponStack(weapon);
            item.getUpgrades(weapon).setItemDirect(0, ItemStack.EMPTY);
            bolt.onHitEntity(new EntityHitResult(target));
            var fragments = h.getLevel().getEntitiesOfClass(MatterConvergingBoltEntity.class, target.getBoundingBox().inflate(2), entity -> entity.getOwner() == player);
            h.assertValueEqual(fragments.size(), 4 + cards * 2, "Fragment count changed after weapon upgrade removal");
            float before = target.getHealth();
            for (var fragment : fragments) fragment.onHitEntity(new EntityHitResult(target));
            h.assertTrue(Math.abs(before - target.getHealth() - fragments.size() * (cards == 0 ? 9 : 10)) < 0.001, "Fragment damage");
            h.assertTrue(h.getLevel().getEntitiesOfClass(MatterConvergingBoltEntity.class, target.getBoundingBox().inflate(2), entity -> entity.getOwner() == player).isEmpty(), "Recursive splitting");
            target.discard();
        }
        h.succeed();
    }

    @TestHolder("weapon_singularity_delayed_max_health_burst")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void singularityBurst(GameTestHelper h) {
        Mob target = target(h, new BlockPos(2, 1, 2));
        target.setNoGravity(true);
        Player player = player(h);
        ItemStack weapon = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        ((MatterConvergingCrossbowItem) weapon.getItem()).getUpgrades(weapon).setItemDirect(0, DEItems.CARD_SABER_ENERGY.toStack());
        ((MatterConvergingCrossbowItem) weapon.getItem()).getUpgrades(weapon).setItemDirect(1, DEItems.CARD_SABER_ENERGY.toStack());
        MatterConvergingBoltEntity bolt = new MatterConvergingBoltEntity(h.getLevel(), player, AEItems.SINGULARITY.stack());
        bolt.setWeaponStack(weapon);
        h.getLevel().addFreshEntity(bolt);
        bolt.onHitEntity(new EntityHitResult(target));
        h.runAfterDelay(12, () -> {
            h.assertTrue(Math.abs(target.getHealth() - 850) < 0.001, "Singularity did not deal delayed 15%");
            h.assertTrue(bolt.isRemoved(), "Singularity field remained");
            h.succeed();
        });
    }

    @TestHolder("weapon_crystal_caps_at_ten_percent_and_is_consumed")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void crystalConsumed(GameTestHelper h) {
        Mob target = target(h, new BlockPos(2, 1, 2));
        ThrownLightSaberEntity crystal = new ThrownLightSaberEntity(h.getLevel(), player(h), DEItems.DATA_LIGHT_SABER.toStack());
        crystal.setConsumableCrystal(true);
        crystal.setDataDustDamageRatio(1);
        crystal.setDeltaMovement(Vec3.ZERO);
        crystal.onHitEntity(new EntityHitResult(target));
        h.assertTrue(Math.abs(target.getHealth() - 900) < 0.001, "Crystal ratio did not cap at 10%");
        h.assertTrue(crystal.isRemoved(), "Crystal remained recoverable");
        h.succeed();
    }

    @TestHolder("weapon_outline_tint_expires_without_team_changes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 120)
    public static void glowExpires(GameTestHelper h) {
        Mob target = target(h, new BlockPos(2, 1, 2));
        var team = h.getLevel().getScoreboard().addPlayerTeam("ammo" + target.getId());
        h.getLevel().getScoreboard().addPlayerToTeam(target.getScoreboardName(), team);
        ChromaticGlow.apply(target, 0x32CC77);
        h.assertTrue(ChromaticGlow.color(target) == 0x32CC77 && target.getTeam() == team, "Tint changed team");
        h.runAfterDelay(101, () -> {
            h.assertTrue(ChromaticGlow.color(target) == -1 && target.getTeam() == team, "Tint did not expire independently");
            h.getLevel().getScoreboard().removePlayerTeam(team);
            h.succeed();
        });
    }

    @TestHolder("weapon_recoil_four_second_monotonic_return")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void recoilTimeline(GameTestHelper h) {
        float previous = 0.5F;
        for (int tick = 2; tick <= 80; tick++) {
            float current = CrossbowRailRecoil.retraction(tick);
            h.assertTrue(current <= previous && current >= 0 && current <= 0.5, "Rail rebound or excess stroke");
            previous = current;
        }
        h.assertTrue(previous == 0 && CrossbowRailRecoil.retraction(79) > 0, "Return did not occupy 80 ticks");
        h.assertTrue(CrossbowRailRecoil.bowRetraction(12) == 0, "Bow inherited rail cooldown");
        h.succeed();
    }

    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 3, 1))));
        player.setYRot(0);
        player.setXRot(-80);
        return player;
    }

    private static Mob target(GameTestHelper h, BlockPos pos) {
        Mob target = h.spawn(EntityType.IRON_GOLEM, pos);
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        return target;
    }

    private static ItemStack railWeapon(GameTestHelper h, AEKey key, long amount, double energy) {
        ItemStack weapon = DEItems.MATTER_CONVERGING_CROSSBOW.toStack();
        weapon.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.RAIL.id());
        ItemStack cell = key instanceof AEItemKey ? AEItems.ITEM_CELL_1K.stack() : DEItems.DIGITAL_STORAGE_CELL_64K.toStack();
        var inventory = StorageCells.getCellInventory(cell, null);
        h.assertTrue(inventory != null && inventory.insert(key, amount, Actionable.MODULATE, IActionSource.empty()) == amount, "Cannot fill test disk");
        inventory.persist();
        NonNullList<ItemStack> cells = NonNullList.withSize(3, ItemStack.EMPTY);
        cells.set(MatterConvergingCrossbowMode.RAIL.id(), cell);
        MountedAmmoCells.setCells(weapon, ItemContainerContents.fromItems(cells));
        weapon.set(AEComponents.STORED_ENERGY, energy);
        return weapon;
    }
}
