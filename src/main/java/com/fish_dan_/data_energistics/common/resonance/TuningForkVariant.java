package com.fish_dan_.data_energistics.common.resonance;

import com.fish_dan_.data_energistics.registry.DEGameEvents;

import net.minecraft.core.Holder;
import net.minecraft.world.level.gameevent.GameEvent;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

/**
 * Central definition of the three tuning-fork tiers and their gameplay parameters.
 */
@ApiStatus.Internal
public enum TuningForkVariant {

    AMETHYST("amethyst", 500, 5, 3, 5, () -> DEGameEvents.AMETHYST_TUNING_FORK_WAVE),
    DATA("data", 1000, 10, 5, 10, () -> DEGameEvents.DATA_TUNING_FORK_WAVE),
    RESONANCE("resonance", 3000, 15, 8, 15, () -> DEGameEvents.RESONANCE_TUNING_FORK_WAVE);

    public static final float ECHO_SUCCESS_CHANCE = 0.75F;

    private final String serializedName;
    private final int durability;
    private final int wardenEchoYield;
    private final int ordinaryEchoYield;
    private final int frequency;
    private final Supplier<? extends Holder<GameEvent>> gameEvent;

    TuningForkVariant(String serializedName, int durability, int wardenEchoYield, int ordinaryEchoYield, int frequency,
                      Supplier<? extends Holder<GameEvent>> gameEvent) {
        this.serializedName = serializedName;
        this.durability = durability;
        this.wardenEchoYield = wardenEchoYield;
        this.ordinaryEchoYield = ordinaryEchoYield;
        this.frequency = frequency;
        this.gameEvent = gameEvent;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public int durability() {
        return this.durability;
    }

    public int wardenEchoYield() {
        return this.wardenEchoYield;
    }

    public int ordinaryEchoYield() {
        return this.ordinaryEchoYield;
    }

    public int frequency() {
        return this.frequency;
    }

    public Holder<GameEvent> gameEvent() {
        return this.gameEvent.get();
    }
}
