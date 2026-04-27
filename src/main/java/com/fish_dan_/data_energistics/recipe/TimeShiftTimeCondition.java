package com.fish_dan_.data_energistics.recipe;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

public enum TimeShiftTimeCondition {
    ALL,
    DAY,
    NIGHT;

    public static final Codec<TimeShiftTimeCondition> CODEC = Codec.STRING.xmap(TimeShiftTimeCondition::byName, TimeShiftTimeCondition::getName);
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeShiftTimeCondition> STREAM_CODEC = StreamCodec.of(
            (buffer, condition) -> buffer.writeUtf(condition.getName()),
            buffer -> byName(buffer.readUtf()));

    public boolean matches(Level level) {
        return switch (this) {
            case ALL -> true;
            case DAY -> level.isDay();
            case NIGHT -> !level.isDay();
        };
    }

    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    private static TimeShiftTimeCondition byName(String name) {
        for (TimeShiftTimeCondition condition : values()) {
            if (condition.getName().equals(name)) {
                return condition;
            }
        }

        return ALL;
    }
}
