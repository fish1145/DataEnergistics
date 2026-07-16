package com.fish_dan_.data_energistics.mixin.neoecoae;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NeoEcoAdditionalRendererGuardTest {

    @Test
    void forwardsToOriginalRenderer() {
        var calls = new AtomicInteger();
        var renderer = NeoEcoAdditionalRendererGuard.guard(context -> calls.incrementAndGet(), BlockPos.ZERO);

        renderer.render(null);

        assertEquals(1, calls.get());
    }

    @Test
    void containsOriginalRendererRuntimeFailure() {
        var renderer = NeoEcoAdditionalRendererGuard.guard(context -> {
            throw new IllegalStateException("renderer failed");
        }, BlockPos.ZERO);

        assertDoesNotThrow(() -> renderer.render(null));
    }
}
