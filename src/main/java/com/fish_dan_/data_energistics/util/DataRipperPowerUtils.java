package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.config.Config;
import com.fish_dan_.data_energistics.registry.ModItems;

import appeng.api.upgrades.IUpgradeInventory;

public final class DataRipperPowerUtils {

    private static final int DEFAULT_BASE_COST = 512;
    private static final double DATA_FLOW_COST_RATIO = 0.00048828125D;

    private DataRipperPowerUtils() {}

    public static int computeProductWithCap(IUpgradeInventory upgrades) {
        int speedCardCount = Math.min(upgrades.getInstalledUpgrades(ModItems.CARD_SABER_ENERGY.get()), 5);
        if (speedCardCount <= 0) {
            return 0;
        }

        return switch (speedCardCount) {
            case 1 -> 16;
            case 2 -> 64;
            case 3 -> 256;
            case 4 -> 512;
            default -> 1024;
        };
    }

    public static double computeFinalPowerForProduct(int speed, int energyCardCount) {
        long basePower = basePowerForSpeed(speed);
        if (basePower <= 0L) {
            return 0.0D;
        }

        return basePower * getRemainingRatio(energyCardCount) * ((double) Config.dataRipperBaseCost / DEFAULT_BASE_COST) * DATA_FLOW_COST_RATIO;
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

    public static long toDataFlowCost(double value) {
        if (value <= 0.0D) {
            return 0L;
        }
        return (long) Math.ceil(value);
    }

    private static final long[] POWER_FOR_SPEED = {
            0, 0, 256, 0, 1024, 0, 2048, 0, 8192, 0,
            16384, 0, 65536, 0, 131072, 0, 524288, 0,
            268435456, 0, 2147483648L
    };

    public static String formatDataFlowCost(double value) {
        return Long.toString(toDataFlowCost(value));
    }

    private static long basePowerForSpeed(int speed) {
        int idx = Integer.numberOfTrailingZeros(speed) * 2;
        return idx < POWER_FOR_SPEED.length ? POWER_FOR_SPEED[idx] : 0;
    }
}
