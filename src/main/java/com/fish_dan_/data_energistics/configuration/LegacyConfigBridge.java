package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.config.Config;
import com.fish_dan_.data_energistics.config.DataExtractorConfig;
import com.fish_dan_.data_energistics.config.FlatteningTntConfig;
import com.fish_dan_.data_energistics.config.SolarPanelConfig;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;
import com.fish_dan_.data_energistics.config.TrinityDispatchConfig;

import net.minecraft.core.registries.BuiltInRegistries;

import java.nio.file.Path;

/** Supplies one immutable root snapshot from the old ConfigSpec chain during non-releasable transition batches. */
public final class LegacyConfigBridge {

    private static volatile ConfigurationSnapshot current = assemble(0L);

    private LegacyConfigBridge() {}

    public static ConfigurationSnapshot current() {
        return current;
    }

    public static synchronized void refresh() {
        current = assemble(current.revision() + 1L);
    }

    private static ConfigurationSnapshot assemble(long revision) {
        DataEnergisticsConfiguration schema = new DataEnergisticsConfiguration();
        schema.dataRipper.baseCost = Config.dataRipperBaseCost;
        schema.dataRipper.blacklist = Config.dataRipperBlacklist.toArray(String[]::new);
        schema.dataRipper.multipliers = Config.dataRipperMultipliers.toArray(String[]::new);
        schema.dataDistributionTower.range = Config.dataDistributionTowerRange;
        schema.dataSanctumInterface.itemLimit = Config.dataSanctumInterfaceItemLimit;
        schema.dataSanctumInterface.fluidBuckets = Config.dataSanctumInterfaceFluidBuckets;
        schema.dataSanctumInterface.returnItemLimit = Config.dataSanctumInterfaceReturnItemLimit;
        schema.dataSanctumInterface.returnFluidBuckets = Config.dataSanctumInterfaceReturnFluidBuckets;

        schema.dataExtractor.baseDamage = DataExtractorConfig.baseDamage;
        schema.dataExtractor.workIntervalSeconds = DataExtractorConfig.workIntervalSeconds;
        schema.dataExtractor.baseDataFlowPerCycle = DataExtractorConfig.baseDataFlowPerCycle;
        schema.dataExtractor.dataFlowPerSwordDamage = DataExtractorConfig.dataFlowPerSwordDamage;
        schema.dataExtractor.baseTargetLimit = DataExtractorConfig.baseTargetLimit;
        schema.dataExtractor.targetLimitPerCapacityCard = DataExtractorConfig.targetLimitPerCapacityCard;
        schema.dataExtractor.extraTargetDataFlowMultiplier = DataExtractorConfig.extraTargetDataFlowMultiplier;
        schema.dataExtractor.mobRequiredDamage = DataExtractorConfig.mobRequiredDamage;
        schema.dataExtractor.mobDataBlacklist = DataExtractorConfig.mobDataBlacklist;
        schema.dataExtractor.oreRequiredAmount = DataExtractorConfig.oreRequiredAmount;
        schema.dataExtractor.oreDataBlacklist = DataExtractorConfig.oreDataBlacklist;
        schema.dataExtractor.cropRequiredAmount = DataExtractorConfig.cropRequiredAmount;
        schema.dataExtractor.cropDataBlacklist = DataExtractorConfig.cropDataBlacklist;
        schema.dataExtractor.cropDataWhitelist = DataExtractorConfig.cropDataWhitelist;
        schema.dataExtractor.cropInputMappings = DataExtractorConfig.cropInputMappings;

        FlatteningTntConfig.Definition tnt = FlatteningTntConfig.configurableTnt;
        schema.flatteningTnt.tntConfigurable.clearChunkRadius = tnt.clearChunkRadius();
        schema.flatteningTnt.tntConfigurable.clearStartYOffset = tnt.clearStartYOffset();
        schema.flatteningTnt.tntConfigurable.clearHeight = tnt.clearHeight();
        schema.flatteningTnt.tntConfigurable.fillChunkRadius = tnt.fillChunkRadius();
        schema.flatteningTnt.tntConfigurable.fillYOffset = tnt.fillYOffset();
        schema.flatteningTnt.tntConfigurable.fillBlock = BuiltInRegistries.BLOCK
                .getKey(tnt.fillBlockState().getBlock())
                .toString();
        schema.flatteningTnt.tntConfigurable.centerOffsetX = tnt.explosionCenterOffset().getX();
        schema.flatteningTnt.tntConfigurable.centerOffsetY = tnt.explosionCenterOffset().getY();
        schema.flatteningTnt.tntConfigurable.centerOffsetZ = tnt.explosionCenterOffset().getZ();
        schema.flatteningTnt.tntConfigurable.preserveFluids = tnt.preserveFluids();
        schema.flatteningTnt.tntConfigurable.replaceUnbreakableBlocks = tnt.replaceUnbreakableBlocks();
        FlatteningTntConfig.DataNukeDefinition dataNuke = FlatteningTntConfig.dataNuke;
        schema.flatteningTnt.dataNuke.workIntervalTicks = dataNuke.workIntervalTicks();
        schema.flatteningTnt.dataNuke.maxRadius = dataNuke.maxRadius();
        schema.flatteningTnt.dataNuke.centerEntityConsumeRadius = dataNuke.centerEntityConsumeRadius();

        schema.solarPanel.dayGenerationAEPerTick = SolarPanelConfig.dayGenerationAEPerTick;
        schema.solarPanel.nightGenerationAEPerTick = SolarPanelConfig.nightGenerationAEPerTick;
        schema.solarPanel.speedCardBonusRatio = SolarPanelConfig.speedCardBonusRatio;
        schema.solarPanel.energyCardCapacityBonusAE = SolarPanelConfig.energyCardCapacityBonusAE;

        TrinityCraftingConfig.Settings crafting = TrinityCraftingConfig.settings();
        schema.trinityCrafting.maxSccKeys = crafting.maxSccKeys();
        schema.trinityCrafting.maxBindingVariants = crafting.maxBindingVariants();
        schema.trinityCrafting.maxScheduleStates = crafting.maxScheduleStates();
        schema.trinityCrafting.graphRebuildBudgetMs = crafting.graphRebuildBudgetMs();
        schema.trinityCrafting.plannerThreads = crafting.plannerThreads();
        schema.trinityCrafting.plannerQueueCapacity = crafting.plannerQueueCapacity();
        schema.trinityCrafting.dynamicRetryMaxTicks = crafting.dynamicRetryMaxTicks();
        CraftingQuantityMode quantityMode = crafting.defaultQuantityMode();
        schema.trinityCrafting.defaultQuantityMode = quantityMode;

        TrinityDispatchConfig.Settings dispatch = TrinityDispatchConfig.settings();
        schema.trinityDispatch.hardGridAttempts = dispatch.hardGridAttempts();
        schema.trinityDispatch.hardProviderAttempts = dispatch.hardProviderAttempts();
        schema.trinityDispatch.hardCommitBudgetMs = dispatch.hardCommitBudgetMs();
        schema.trinityDispatch.safeGridAttempts = dispatch.safeGridAttempts();
        schema.trinityDispatch.safeProviderAttempts = dispatch.safeProviderAttempts();
        schema.trinityDispatch.safeCommitBudgetMs = dispatch.safeCommitBudgetMs();
        schema.trinityDispatch.safeActorPermits = dispatch.safeActorPermits();
        schema.trinityDispatch.safeRetryBackoffTicks = dispatch.safeRetryBackoffTicks();
        schema.trinityDispatch.warmupTicks = dispatch.warmupTicks();
        schema.trinityDispatch.metricsWindowTicks = dispatch.metricsWindowTicks();
        schema.trinityDispatch.ewmaAlpha = dispatch.ewmaAlpha();
        schema.trinityDispatch.transitionWindows = dispatch.transitionWindows();
        schema.trinityDispatch.cooldownTicks = dispatch.cooldownTicks();
        schema.trinityDispatch.safeHoldTicks = dispatch.safeHoldTicks();

        try {
            return SnapshotAssembler.assemble(schema, Path.of("legacy-config-bridge"), revision);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalStateException("Legacy ConfigSpec values cannot form a valid configuration snapshot", exception);
        }
    }
}
