package com.fish_dan_.data_energistics.item.powered.cannon.rail;

import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.world.item.ItemStack;

/** Shared server/client rail stroke; heavy rounds retain their full-cycle lockout. */
public final class RailRecovery {

    public static final int DURATION_TICKS = 14;
    private static final float MAX_RETRACTION = 0.5F;

    private RailRecovery() {}

    public static int brakeTicks(int duration) {
        return (duration + 6) / 7;
    }

    public static boolean cooling(ItemStack weapon, long time) {
        int duration = duration(weapon);
        // The persisted cycle belongs to the fired round, so changing ammo cannot bypass a heavy shot's lockout.
        int lockout = duration == RailAmmunition.HEAVY.cooldownTicks() ? duration : brakeTicks(duration);
        return elapsed(weapon, time, duration) < lockout;
    }

    public static boolean recovering(ItemStack weapon, long time) {
        return weapon.getOrDefault(DEDataComponents.RAIL_COOLDOWN_END.get(), 0L) > time;
    }

    /** Available charge increases with actual return distance, not elapsed time alone. */
    public static float chargeProgress(ItemStack weapon, CannonCharge charge, long time) {
        int duration = duration(weapon);
        int elapsed = elapsed(weapon, time, duration);
        if (elapsed < brakeTicks(duration)) return 0;
        return Math.min(charge.progress(time), 1 - retraction(elapsed, duration, start(weapon)) / MAX_RETRACTION);
    }

    public static float retraction(ItemStack weapon, long time) {
        int duration = duration(weapon);
        return retraction(elapsed(weapon, time, duration), duration, start(weapon));
    }

    /** A repeat shot starts its brake stroke at the previous shot's current rail position. */
    public static float retraction(int tick, int duration, float start) {
        if (tick >= duration) return 0;
        if (tick <= 0) return start;
        float phase = tick * (float) DURATION_TICKS / duration;
        if (phase <= 2) {
            float remaining = 1 - phase / 2;
            return start + (MAX_RETRACTION - start) * (1 - remaining * remaining);
        }
        float progress = (phase - 2) / 12;
        return MAX_RETRACTION * (1 - progress * progress * (3 - 2 * progress));
    }

    private static int duration(ItemStack weapon) {
        return weapon.getOrDefault(DEDataComponents.RAIL_COOLDOWN_DURATION.get(), DURATION_TICKS);
    }

    private static float start(ItemStack weapon) {
        return weapon.getOrDefault(DEDataComponents.RAIL_RECOIL_START.get(), 0.0F);
    }

    private static int elapsed(ItemStack weapon, long time, int duration) {
        long remaining = weapon.getOrDefault(DEDataComponents.RAIL_COOLDOWN_END.get(), 0L) - time;
        return remaining <= 0 ? duration : duration - (int) Math.min(duration, remaining);
    }
}
