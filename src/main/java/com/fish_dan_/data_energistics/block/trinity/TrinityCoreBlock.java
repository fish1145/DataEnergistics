package com.fish_dan_.data_energistics.block.trinity;

import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreMetadata;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreTier;

import net.minecraft.world.level.block.Block;

/**
 * Plain trinity structure component block that only carries static capability metadata.
 */
public class TrinityCoreBlock extends Block implements TrinityCoreComponent {

    /** Static capability metadata exposed when a future trinity structure scan reads this block. */
    private final TrinityCoreMetadata metadata;

    public TrinityCoreBlock(Properties properties, TrinityCoreMetadata metadata) {
        super(properties);
        this.metadata = metadata;
    }

    /**
     * Creates a storage type core from the shared M/G tier table.
     */
    public static TrinityCoreBlock storageCore(Properties properties, TrinityCoreTier tier) {
        return new TrinityCoreBlock(properties, TrinityCoreMetadata.storageCore(tier));
    }

    /**
     * Creates a parallel CPU core from the shared M/G tier table.
     */
    public static TrinityCoreBlock parallelCpuCore(Properties properties, TrinityCoreTier tier) {
        return new TrinityCoreBlock(properties, TrinityCoreMetadata.parallelCpuCore(tier));
    }

    /**
     * Creates a pattern processing core with a fixed recognizable pattern capacity.
     */
    public static TrinityPatternCoreBlock patternProcessingCore(Properties properties, int patternCapacity) {
        return new TrinityPatternCoreBlock(properties, TrinityCoreMetadata.patternProcessingCore(patternCapacity));
    }

    @Override
    public TrinityCoreKind kind() {
        return this.metadata.kind();
    }

    @Override
    public int capacityValue() {
        return this.metadata.capacityValue();
    }

    @Override
    public int patternCapacity() {
        return this.metadata.patternCapacity();
    }
}
