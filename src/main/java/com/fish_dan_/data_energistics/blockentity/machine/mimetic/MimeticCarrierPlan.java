package com.fish_dan_.data_energistics.blockentity.machine.mimetic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import org.jspecify.annotations.Nullable;

/**
 * Resolved production inputs for one installed data carrier.
 *
 * <p>
 * Plans are server-thread values. They remain valid only while the carrier slot and the published extractor-rule
 * snapshot are unchanged; the owning data mimetic field is responsible for invalidating them at those boundaries.
 * </p>
 */
public sealed interface MimeticCarrierPlan
                                           permits MimeticCarrierPlan.Empty, MimeticCarrierPlan.Biology, MimeticCarrierPlan.Ore, MimeticCarrierPlan.Crop {

    /** A slot without complete or resolvable recorded data. */
    enum Empty implements MimeticCarrierPlan {

        INSTANCE
    }

    /**
     * Resolved biology carrier data.
     *
     * @param entityId    recorded entity identity used by the sampled-loot cache
     * @param entityType  entity type used for experience/drop simulation, or {@code null} when unavailable
     * @param fixedOutput configured or built-in deterministic item output for one roll
     */
    record Biology(
                   ResourceLocation entityId,
                   @Nullable EntityType<?> entityType,
                   MimeticGeneratedOutput fixedOutput)
            implements MimeticCarrierPlan {}

    /**
     * Resolved deterministic ore output for one roll.
     *
     * @param output component-sensitive output counts
     */
    record Ore(MimeticGeneratedOutput output) implements MimeticCarrierPlan {}

    /**
     * Resolved crop fallback chain for one roll.
     *
     * @param fixedOutput configured or built-in output, checked first
     * @param lootTableId recorded loot table, or {@code null} when absent
     * @param sourceBlock recorded source block, or {@code null} when absent
     * @param fallback    recorded crop item fallback
     */
    record Crop(
                MimeticGeneratedOutput fixedOutput,
                @Nullable ResourceLocation lootTableId,
                @Nullable Block sourceBlock,
                MimeticGeneratedOutput fallback)
            implements MimeticCarrierPlan {}
}
