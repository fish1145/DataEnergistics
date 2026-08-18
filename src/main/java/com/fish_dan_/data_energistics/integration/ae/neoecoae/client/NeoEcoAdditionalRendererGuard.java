package com.fish_dan_.data_energistics.integration.ae.neoecoae.client;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Isolates NeoECOAE section renderer failures without replacing its renderer registration logic.
 */
public final class NeoEcoAdditionalRendererGuard {

    private static final int MAX_WARNINGS = 5;
    private static final AtomicInteger SAFE_RENDER_FAILURES = new AtomicInteger();

    private NeoEcoAdditionalRendererGuard() {}

    /**
     * Wraps the original renderer so a broken optional renderer cannot abort chunk meshing.
     *
     * @param renderer      NeoECOAE renderer registered for the section
     * @param sectionOrigin section origin used to identify failures in the log
     * @return guarded renderer that preserves the original invocation
     */
    public static AdditionalSectionRenderer guard(AdditionalSectionRenderer renderer, BlockPos sectionOrigin) {
        return context -> {
            try {
                renderer.render(context);
            } catch (RuntimeException exception) {
                int failureCount = SAFE_RENDER_FAILURES.incrementAndGet();
                if (failureCount <= MAX_WARNINGS) {
                    Data_Energistics.LOGGER.warn(
                            "Skipped NeoECOAE fixed block entity renderers at {} after renderer failure #{}",
                            sectionOrigin,
                            failureCount,
                            exception);
                }
            }
        };
    }
}
