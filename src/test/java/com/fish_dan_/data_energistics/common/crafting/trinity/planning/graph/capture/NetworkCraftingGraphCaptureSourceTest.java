package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.core.RegistryAccess;

import appeng.me.service.helpers.NetworkCraftingProviders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class NetworkCraftingGraphCaptureSourceTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void explicitRevisionSupplierIsValidatedAsMonotonic() {
        AtomicLong revision = new AtomicLong(4L);
        NetworkCraftingGraphCaptureSource source = new NetworkCraftingGraphCaptureSource(
                new NetworkCraftingProviders(),
                RegistryAccess.EMPTY,
                revision::get);

        assertEquals(4L, source.revision());
        assertEquals(4L, source.revision());
        revision.set(5L);
        assertEquals(5L, source.revision());
        revision.set(-1L);
        assertThrows(IllegalStateException.class, source::revision);
        revision.set(3L);
        assertThrows(IllegalStateException.class, source::revision);
    }

    @Test
    void defaultAdapterPrefersTheTrueRevisionBridgeOverAe2sTickValue() {
        RevisionedProviders providers = new RevisionedProviders();
        providers.revision.set(23L);
        NetworkCraftingGraphCaptureSource source = new NetworkCraftingGraphCaptureSource(providers, RegistryAccess.EMPTY);

        assertEquals(23L, source.revision());
        providers.revision.incrementAndGet();
        assertEquals(24L, source.revision());
    }

    @Test
    void defaultAdapterRejectsAe2sNonMonotonicLastModifiedTickFallback() {
        assertThrows(
                IllegalStateException.class,
                () -> new NetworkCraftingGraphCaptureSource(
                        new NetworkCraftingProviders(),
                        RegistryAccess.EMPTY));
    }

    private static final class RevisionedProviders
                                                   extends NetworkCraftingProviders implements TrinityCraftingProviderRevision {

        private final AtomicLong revision = new AtomicLong();

        @Override
        public long data_energistics$trinityCraftingProviderRevision() {
            return this.revision.get();
        }
    }
}
