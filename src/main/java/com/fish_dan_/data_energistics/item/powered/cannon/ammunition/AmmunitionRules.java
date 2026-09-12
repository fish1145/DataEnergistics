package com.fish_dan_.data_energistics.item.powered.cannon.ammunition;

/** Immutable shot parameters. Upgrade counts are normalized once at the weapon boundary. */
public final class AmmunitionRules {

    private AmmunitionRules() {}

    public record Wind(int width, double height, float damage) {}

    public record Flame(int width, float damage, int burnTicks, float burnDamage) {}

    public record Cube(float damage, int fragments) {

        public float fragmentDamage() {
            return damage / 4.0F;
        }
    }

    public static Wind wind(int cards) {
        return new Wind(cards == 0 ? 5 : 7, cards == 2 ? 28 : 9, switch (cards) {
            case 0 -> 7;
            case 1 -> 13;
            case 2 -> 26;
            default -> throw new IllegalArgumentException("Invalid focusing upgrade count");
        });
    }

    public static Flame flame(int cards) {
        checkCards(cards);
        return new Flame(cards == 0 ? 5 : 7, cards == 0 ? 4 : 6, cards == 2 ? 280 : 100, cards == 2 ? 3 : 1.5F);
    }

    public static Cube cube(int cards) {
        checkCards(cards);
        return new Cube(cards == 0 ? 36 : 40, 4 + 2 * cards);
    }

    public static void checkCards(int cards) {
        if (cards < 0 || cards > 2) throw new IllegalArgumentException("Invalid focusing upgrade count");
    }

    /** Discrete living-entity motion: move, then apply gravity and air drag. */
    public static double launchSpeed(double height, double gravity) {
        if (!Double.isFinite(height) || height <= 0 || !Double.isFinite(gravity) || gravity <= 0) {
            throw new IllegalArgumentException("Invalid launch height or gravity");
        }
        double low = 0, high = height;
        for (int step = 0; step < 60; step++) {
            double speed = (low + high) / 2;
            double rise = 0;
            for (double velocity = speed; velocity > 0; velocity = (velocity - gravity) * 0.98D) {
                rise += velocity;
            }
            if (rise < height) low = speed;
            else high = speed;
        }
        return (low + high) / 2;
    }
}
