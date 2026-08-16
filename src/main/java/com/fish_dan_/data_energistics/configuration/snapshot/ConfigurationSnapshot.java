package com.fish_dan_.data_energistics.configuration.snapshot;

import com.fish_dan_.data_energistics.common.dataripper.DataRipperConfigParsingUtils;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Complete immutable configuration observed by all business consumers.
 */
public record ConfigurationSnapshot(
                                    long revision,
                                    boolean verboseRuntimeLogging,
                                    DataRipperSettings dataRipper,
                                    DataDistributionTowerSettings dataDistributionTower,
                                    DataSanctumInterfaceSettings dataSanctumInterface,
                                    DataExtractorSettings dataExtractor,
                                    FlatteningTntSettings flatteningTnt,
                                    DataNukeSettings dataNuke,
                                    SolarPanelSettings solarPanel,
                                    OrbitalWeaponSettings orbitalWeapon,
                                    TrinityCraftingSettings trinityCrafting,
                                    TrinityDispatchSettings trinityDispatch)
        implements DataEnergisticsSettings {

    public record DataRipperSettings(
                                     int baseCost,
                                     List<Pattern> blacklist,
                                     List<DataRipperConfigParsingUtils.MultiplierEntry> multipliers)
            implements DataEnergisticsSettings.DataRipper {

        public DataRipperSettings {
            blacklist = List.copyOf(blacklist);
            multipliers = List.copyOf(multipliers);
        }
    }

    public record DataDistributionTowerSettings(int range) implements DataEnergisticsSettings.DataDistributionTower {}

    public record DataSanctumInterfaceSettings(
                                               int itemLimit,
                                               int fluidBuckets,
                                               int returnItemLimit,
                                               int returnFluidBuckets)
            implements DataEnergisticsSettings.DataSanctumInterface {}

    public record DataExtractorSettings(
                                        int baseDamage,
                                        int workIntervalSeconds,
                                        int baseDataFlowPerCycle,
                                        int dataFlowPerSwordDamage,
                                        int baseTargetLimit,
                                        int targetLimitPerCapacityCard,
                                        double extraTargetDataFlowMultiplier,
                                        float mobRequiredDamage,
                                        Set<ResourceLocation> mobDataBlacklist,
                                        float oreRequiredAmount,
                                        Set<ResourceLocation> oreDataBlacklist,
                                        float cropRequiredAmount,
                                        Set<ResourceLocation> cropDataBlacklist,
                                        Set<ResourceLocation> cropDataWhitelist)
            implements DataEnergisticsSettings.DataExtractor {

        public DataExtractorSettings {
            mobDataBlacklist = Set.copyOf(mobDataBlacklist);
            oreDataBlacklist = Set.copyOf(oreDataBlacklist);
            cropDataBlacklist = Set.copyOf(cropDataBlacklist);
            cropDataWhitelist = Set.copyOf(cropDataWhitelist);
        }
    }

    public record FlatteningTntSettings(
                                        int clearChunkRadius,
                                        int clearStartYOffset,
                                        int clearHeight,
                                        int fillChunkRadius,
                                        int fillYOffset,
                                        BlockState fillBlockState,
                                        BlockPos explosionCenterOffset,
                                        boolean preserveFluids,
                                        boolean replaceUnbreakableBlocks)
            implements DataEnergisticsSettings.FlatteningTnt {}

    public record DataNukeSettings(int workIntervalTicks, int maxRadius, double centerEntityConsumeRadius)
            implements DataEnergisticsSettings.DataNuke {}

    public record SolarPanelSettings(
                                     double dayGenerationAEPerTick,
                                     double nightGenerationAEPerTick,
                                     double speedCardBonusRatio,
                                     double energyCardCapacityBonusAE)
            implements DataEnergisticsSettings.SolarPanel {}
}
