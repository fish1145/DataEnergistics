package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.config.Config;
import com.fish_dan_.data_energistics.registry.ModItems;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;

public final class DataRipperPowerUtils {

    private static final int DEFAULT_BASE_COST = 512;
    private static final double DATA_FLOW_COST_RATIO = 0.00048828125D;
    private static final int MAX_SPEED_CARD_TIERS = 5;
    private static final int MAX_SABER_CARD_TIERS = 5;
    private static final long POWER_SCALE = 1000L;

    private DataRipperPowerUtils() {}

    public static int computeProductWithCap(IUpgradeInventory upgrades) {
        int speedCardCount = Math.min(upgrades.getInstalledUpgrades(AEItems.SPEED_CARD), MAX_SPEED_CARD_TIERS);
        int saberCardCount = Math.min(upgrades.getInstalledUpgrades(ModItems.CARD_SABER_ENERGY.get()), MAX_SABER_CARD_TIERS);
        int totalTiers = speedCardCount + saberCardCount;
        if (totalTiers <= 0) {
            return 0;
        }
        return 1 << totalTiers;
    }

    public static double computeFinalPowerForProduct(int speed, int energyCardCount) {
        long basePower = basePowerForSpeed(speed);
        if (basePower <= 0L) {
            return 0.0D;
        }

        return (basePower / 4.0D) * getRemainingRatio(energyCardCount) * ((double) Config.dataRipperBaseCost / DEFAULT_BASE_COST) * DATA_FLOW_COST_RATIO;
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

    public static double getAdjustedExtraMultiplier(double baseMultiplier, int inverterCardCount) {
        int cappedCardCount = Math.min(Math.max(inverterCardCount, 0), 5);
        return Math.max(0.0D, baseMultiplier - cappedCardCount * 0.05D);
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
        if (speed < 2 || speed > 1024 || Integer.bitCount(speed) != 1) {
            return 0L;
        }

        int speedExponent = Integer.numberOfTrailingZeros(speed);
        int powerExponent = 7 + (3 * speedExponent) / 2;
        return (1L << powerExponent) * POWER_SCALE;
    }
}
