package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowRailRecoil;
import com.fish_dan_.data_energistics.effect.ChromaticGlow;
import com.fish_dan_.data_energistics.effect.RadixLossEffectLogic;
import com.fish_dan_.data_energistics.effect.WeaponBurn;
import com.fish_dan_.data_energistics.entity.projectile.cannon.ElementalGrenade;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.AmmunitionRules;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

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
        h.assertTrue(MountedAmmoCells.accepts(DEItems.DIGITAL_STORAGE_CELL_64K.toStack()), "Data disk rejected");
        h.assertTrue(MountedAmmoCells.accepts(AEItems.FLUID_CELL_1K.stack()), "Fluid disk rejected");
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

    @TestHolder("weapon_fire_grenade_ignites_round_ground_footprint")
    @GameTest(template = "empty_50x32x50")
    public static void roundGroundFire(GameTestHelper h) {
        BlockPos center = new BlockPos(5, 3, 5);
        for (int cards = 0; cards <= 2; cards++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    h.setBlock(center.offset(x, -1, z), Blocks.STONE);
                    h.setBlock(center.offset(x, 0, z), Blocks.AIR);
                }
            }
            ElementalGrenade.detonate(h.getLevel(), new ItemStack(Items.FIRE_CHARGE),
                    Vec3.atBottomCenterOf(h.absolutePos(center)), null, cards);
            String[] footprint = cards == 0 ? new String[] { "..#..", ".###.", "#####", ".###.", "..#.." } : new String[] { "...#...", ".#####.", ".#####.", "#######", ".#####.", ".#####.", "...#..." };
            int radius = footprint.length / 2;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    boolean expected = Math.abs(x) <= radius && Math.abs(z) <= radius && footprint[z + radius].charAt(x + radius) == '#';
                    h.assertBlockPresent(expected ? Blocks.FIRE : Blocks.AIR, center.offset(x, 0, z));
                    h.assertBlockPresent(Blocks.STONE, center.offset(x, -1, z));
                }
            }
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

    @TestHolder("weapon_recoil_short_monotonic_return")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void recoilTimeline(GameTestHelper h) {
        float previous = 0.5F;
        for (int tick = 2; tick <= 14; tick++) {
            float current = CrossbowRailRecoil.retraction(tick);
            h.assertTrue(current <= previous && current >= 0 && current <= 0.5, "Rail rebound or excess stroke");
            previous = current;
        }
        h.assertTrue(previous == 0 && CrossbowRailRecoil.retraction(13) > 0, "Rail recoil must finish at 14 ticks");
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
}
