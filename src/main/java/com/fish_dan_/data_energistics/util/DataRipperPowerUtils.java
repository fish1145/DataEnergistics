package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.Config;
import com.fish_dan_.data_energistics.item.EntityAccelerationCardItem;
import net.minecraft.world.item.ItemStack;
import appeng.api.upgrades.IUpgradeInventory;

public final class DataRipperPowerUtils {
    private static final int DEFAULT_BASE_COST = 512;

    private DataRipperPowerUtils() {
    }

    public static int computeProductWithCap(IUpgradeInventory upgrades) {
        long product = 1L;
        long cap = 1L;
        int speedCardCount = 0;

        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!(stack.getItem() instanceof EntityAccelerationCardItem)) {
                continue;
            }
            if (speedCardCount >= 4) {
                continue;
            }

            speedCardCount++;
            byte multiplier = EntityAccelerationCardItem.readMultiplier(stack);
            product *= multiplier;
            cap = Math.max(cap, EntityAccelerationCardItem.getCap(multiplier));
        }

        if (product <= 1L) {
            return 1;
        }

        return (int) Math.min(Math.min(product, cap), 1024L);
    }

    public static double computeFinalPowerForProduct(int speed, int energyCardCount) {
        long basePower = basePowerForSpeed(speed);
        if (basePower <= 0L) {
            return 0.0D;
        }

        return basePower * getRemainingRatio(energyCardCount) * ((double) Config.dataRipperBaseCost / DEFAULT_BASE_COST);
    }

    public static double getRemainingRatio(int energyCardCount) {
        return switch (energyCardCount) {
            case 0 -> 1.0D;
            case 1 -> 0.9D;
            case 2 -> 0.855D;
            case 3 -> 0.8285D;
            case 4 -> 0.81D;
            case 5 -> 0.7979D;
            case 6 -> 0.7885D;
            case 7 -> 0.781D;
            default -> 0.5D;
        };
    }

    public static String formatPercentage(double value) {
        return String.format("%.2f%%", value * 100.0D);
    }

    private static long basePowerForSpeed(int speed) {
        return switch (speed) {
            case 2 -> 256L;
            case 4 -> 1_024L;
            case 8 -> 2_048L;
            case 16 -> 8_192L;
            case 32 -> 16_384L;
            case 64 -> 65_536L;
            case 128 -> 131_072L;
            case 256 -> 524_288L;
            case 512 -> 268_435_456L;
            case 1024 -> 2_147_483_648L;
            default -> 0L;
        };
    }
}
