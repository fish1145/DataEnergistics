package com.fish_dan_.data_energistics.util;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import com.fish_dan_.data_energistics.Config;

public final class DataRipperPowerUtils {
    private static final int DEFAULT_BASE_COST = 512;
    private static final double DATA_FLOW_COST_RATIO = 0.0625D;

    private DataRipperPowerUtils() {
    }

    public static int computeProductWithCap(IUpgradeInventory upgrades) {
        int speedCardCount = Math.min(upgrades.getInstalledUpgrades(AEItems.SPEED_CARD), 4);
        if (speedCardCount <= 0) {
            return 0;
        }

        return switch (speedCardCount) {
            case 1 -> 16;
            case 2 -> 64;
            case 3 -> 256;
            default -> 1024;
        };
    }

    public static double computeFinalPowerForProduct(int speed, int energyCardCount) {
        long basePower = basePowerForSpeed(speed);
        if (basePower <= 0L) {
            return 0.0D;
        }

        return basePower
                * getRemainingRatio(energyCardCount)
                * ((double) Config.dataRipperBaseCost / DEFAULT_BASE_COST)
                * DATA_FLOW_COST_RATIO;
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

    public static String formatDataFlowCost(double value) {
        return Long.toString(toDataFlowCost(value));
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
