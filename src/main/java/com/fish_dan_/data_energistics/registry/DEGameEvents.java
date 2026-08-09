package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Game events emitted by tuning-fork waves.
 */
public final class DEGameEvents {

    private static final int NOTIFICATION_RADIUS = 16;

    public static final DeferredRegister<GameEvent> GAME_EVENTS = DeferredRegister.create(
            Registries.GAME_EVENT,
            Data_Energistics.MODID);

    public static final DeferredHolder<GameEvent, GameEvent> AMETHYST_TUNING_FORK_WAVE = register(
            "amethyst_tuning_fork_wave");
    public static final DeferredHolder<GameEvent, GameEvent> DATA_TUNING_FORK_WAVE = register(
            "data_tuning_fork_wave");
    public static final DeferredHolder<GameEvent, GameEvent> RESONANCE_TUNING_FORK_WAVE = register(
            "resonance_tuning_fork_wave");

    private DEGameEvents() {}

    private static DeferredHolder<GameEvent, GameEvent> register(String id) {
        return GAME_EVENTS.register(id, () -> new GameEvent(NOTIFICATION_RADIUS));
    }

    public static void register(IEventBus modEventBus) {
        GAME_EVENTS.register(modEventBus);
    }
}
