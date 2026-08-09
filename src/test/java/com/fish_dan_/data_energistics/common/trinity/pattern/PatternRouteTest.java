package com.fish_dan_.data_energistics.common.trinity.pattern;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class PatternRouteTest {

    @Test
    void roundTripsThroughNbt() {
        PatternRoute route = new PatternRoute(UUID.randomUUID(), UUID.randomUUID(), 511);

        assertEquals(route, PatternRoute.readFromTag(route.writeToTag()));
    }

    @Test
    void rejectsInvalidConstructionAndIncompleteNbt() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new PatternRoute(id, id, -1));
        assertThrows(IllegalArgumentException.class, () -> PatternRoute.readFromTag(new CompoundTag()));

        CompoundTag negativeSlot = new PatternRoute(id, id, 0).writeToTag();
        negativeSlot.putInt("slot", -1);
        assertThrows(IllegalArgumentException.class, () -> PatternRoute.readFromTag(negativeSlot));
    }
}
