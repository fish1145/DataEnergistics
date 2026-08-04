package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.util.DataRipperConfigParsingUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Complete immutable configuration observed by all business consumers. */
public record ConfigurationSnapshot(
                                    long revision,
                                    DataRipperSettings dataRipper,
                                    DataDistributionTowerSettings dataDistributionTower,
                                    DataSanctumInterfaceSettings dataSanctumInterface,
                                    DataExtractorSettings dataExtractor,
                                    FlatteningTntSettings flatteningTnt,
                                    DataNukeSettings dataNuke,
                                    SolarPanelSettings solarPanel,
                                    TrinityCraftingSettings trinityCrafting,
                                    TrinityDispatchSettings trinityDispatch)
        implements DataEnergisticsSettings {

    public record DataRipperSettings(
                                     int baseCost,
                                     List<String> blacklistText,
                                     List<String> multiplierText,
                                     List<Pattern> blacklist,
                                     List<DataRipperConfigParsingUtils.MultiplierEntry> multipliers)
            implements DataEnergisticsSettings.DataRipper {

        public DataRipperSettings {
            blacklistText = List.copyOf(blacklistText);
            multiplierText = List.copyOf(multiplierText);
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
                                        Set<ResourceLocation> cropDataWhitelist,
                                        Map<ResourceLocation, CropInputMapping> cropInputMappings)
            implements DataEnergisticsSettings.DataExtractor {

        public DataExtractorSettings {
            mobDataBlacklist = Set.copyOf(mobDataBlacklist);
            oreDataBlacklist = Set.copyOf(oreDataBlacklist);
            cropDataBlacklist = Set.copyOf(cropDataBlacklist);
            cropDataWhitelist = Set.copyOf(cropDataWhitelist);
            cropInputMappings = Map.copyOf(cropInputMappings);
        }
    }

    public record CropInputMapping(ResourceLocation recordedItem, float progressPerItem) {}

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
