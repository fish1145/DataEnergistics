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
     * Creates a storage type core from the shared K/M tier table.
     */
    public static TrinityCoreBlock storageCore(Properties properties, TrinityCoreTier tier) {
        return new TrinityCoreBlock(properties, TrinityCoreMetadata.storageCore(tier));
    }

    /**
     * Creates a merged CPU storage core from the shared K/M tier table.
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

    /**
     * Creates the lowest-tier universal unit accepted by all three Trinity core capability domains.
     */
    public static TrinityPatternCoreBlock emptyTrinityUnit(Properties properties) {
        return new TrinityPatternCoreBlock(properties, TrinityCoreMetadata.emptyTrinityUnit());
    }

    @Override
    public TrinityCoreKind kind() {
        return this.metadata.kind();
    }

    @Override
    public boolean supportsKind(TrinityCoreKind requestedKind) {
        return this.metadata.supportsKind(requestedKind);
    }

    @Override
    public int capacityValue() {
        return this.metadata.capacityValue();
    }

    @Override
    public long byteCapacity() {
        return this.metadata.byteCapacity();
    }

    @Override
    public int patternCapacity() {
        return this.metadata.patternCapacity();
    }
}
