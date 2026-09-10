package com.fish_dan_.data_energistics.item.powered.cannon.rail;

import com.fish_dan_.data_energistics.entity.projectile.RailRoundEntity;
import com.fish_dan_.data_energistics.entity.projectile.cannon.CannonShot;
import com.fish_dan_.data_energistics.entity.projectile.cannon.RailShot;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonBallistics;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-only charge/release transaction. Holding and cancellation never reserve or consume ammunition. */
public final class RailLauncher {

    public static final double ENERGY_PER_SHOT = 200;

    private RailLauncher() {}

    public static void cancel(ItemStack weapon) {
        weapon.remove(DEDataComponents.CANNON_CHARGE.get());
        weapon.remove(DEDataComponents.RAIL_CHARGE_AMMO.get());
    }

    public static boolean cooling(ItemStack weapon, long time) {
        return weapon.getOrDefault(DEDataComponents.RAIL_COOLDOWN_END.get(), 0L) > time;
    }

    public static void begin(Player player, InteractionHand hand, ItemStack weapon) {
        if (!(player.level() instanceof ServerLevel level) || !(weapon.getItem() instanceof MatterConvergingCrossbowItem item) || player.getItemInHand(hand) != weapon || MatterConvergingCrossbowItem.mode(weapon) != MatterConvergingCrossbowMode.RAIL || !player.isAlive() || player.isSpectator() || player.isUsingItem() || player.hasEffect(DEMobEffects.RADIX_LOSS) || player.containerMenu != player.inventoryMenu || MatterConvergingCrossbowItem.isCharged(weapon) || weapon.has(DEDataComponents.CANNON_CHARGE.get()) || cooling(weapon, level.getGameTime()) || player.getCooldowns().isOnCooldown(item)) return;
        var key = MountedAmmoCells.selectedKey(weapon, MatterConvergingCrossbowMode.RAIL);
        var ammo = key == null ? null : RailAmmunition.fromKey(key);
        if (ammo == null || item.getAECurrentPower(weapon) < ENERGY_PER_SHOT || MountedAmmoCells.transfer(weapon, key, ammo.cost(), false, Actionable.SIMULATE) != ammo.cost()) return;
        weapon.set(DEDataComponents.RAIL_CHARGE_AMMO.get(), key);
        weapon.set(DEDataComponents.CANNON_CHARGE.get(), new CannonCharge(level.getGameTime(),
                MatterConvergingCrossbowItem.getChargeDuration(weapon, player), MatterConvergingCrossbowMode.RAIL,
                hand, player.getUUID(), level.dimension().location()));
    }

    public static void release(Player player, InteractionHand hand, ItemStack weapon, Vec3 offset, Vec3 direction) {
        if (!(player.level() instanceof ServerLevel level) || !(weapon.getItem() instanceof MatterConvergingCrossbowItem item)) return;
        var charge = weapon.get(DEDataComponents.CANNON_CHARGE.get());
        var key = weapon.get(DEDataComponents.RAIL_CHARGE_AMMO.get());
        cancel(weapon);
        if (charge == null || key == null || !charge.belongsTo(player, hand, MatterConvergingCrossbowItem.mode(weapon)) || player.getItemInHand(hand) != weapon || player.isSpectator() || player.isUsingItem() || player.hasEffect(DEMobEffects.RADIX_LOSS) || player.containerMenu != player.inventoryMenu || cooling(weapon, level.getGameTime()) || player.getCooldowns().isOnCooldown(item) || !CannonBallistics.validAim(offset, direction, player.getViewVector(1))) return;
        var ammo = RailAmmunition.fromKey(key);
        float fraction = charge.progress(level.getGameTime());
        Vec3 muzzle = player.getEyePosition().add(offset);
        if (ammo == null || fraction <= 0 || !key.equals(MountedAmmoCells.selectedKey(weapon, MatterConvergingCrossbowMode.RAIL)) || level.clip(new ClipContext(player.getEyePosition(), muzzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS || item.getAECurrentPower(weapon) < ENERGY_PER_SHOT || MountedAmmoCells.transfer(weapon, key, ammo.cost(), false, Actionable.SIMULATE) != ammo.cost()) return;
        ItemStack display = key instanceof AEItemKey itemKey ? itemKey.toStack(1) : GenericStack.wrapInItemStack(key, 1);
        int cards = Math.clamp(item.getUpgrades(weapon).getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()), 0, 2);
        RailRoundEntity projectile = new RailRoundEntity(level, player, display);
        projectile.configureRailShot(new RailShot(ammo, cards, fraction));
        projectile.configureCannonShot(new CannonShot(MatterConvergingCrossbowMode.RAIL, fraction, item.cannonAmmoSpeed(weapon)));
        Vec3 velocity = CannonBallistics.launchVelocity(MatterConvergingCrossbowMode.RAIL, fraction, direction, item.cannonAmmoSpeed(weapon));
        projectile.setPos(muzzle);
        projectile.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0);
        long removed = MountedAmmoCells.transfer(weapon, key, ammo.cost(), false, Actionable.MODULATE);
        if (removed != ammo.cost()) {
            if (removed > 0 && MountedAmmoCells.transfer(weapon, key, removed, true, Actionable.MODULATE) != removed)
                throw new IllegalStateException("Rail ammunition rollback failed");
            return;
        }
        item.extractAEPower(weapon, ENERGY_PER_SHOT, Actionable.MODULATE);
        if (!level.addFreshEntity(projectile)) {
            if (MountedAmmoCells.transfer(weapon, key, removed, true, Actionable.MODULATE) != removed)
                throw new IllegalStateException("Rejected rail projectile refund failed");
            item.injectAEPower(weapon, ENERGY_PER_SHOT, Actionable.MODULATE);
            return;
        }
        weapon.set(DEDataComponents.RAIL_COOLDOWN_END.get(), level.getGameTime() + ammo.cooldownTicks());
        weapon.set(DEDataComponents.RAIL_COOLDOWN_DURATION.get(), ammo.cooldownTicks());
        weapon.set(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), weapon.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0) + 1);
        player.getCooldowns().addCooldown(item, ammo.cooldownTicks());
        player.awardStat(Stats.ITEM_USED.get(item));
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1, 0.75F);
    }
}
