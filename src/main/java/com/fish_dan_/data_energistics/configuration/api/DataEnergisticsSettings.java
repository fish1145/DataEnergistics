package com.fish_dan_.data_energistics.configuration.api;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.util.DataRipperConfigParsingUtils.MultiplierEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Read-only root boundary that prevents gameplay code from observing mutable Configuration schema objects. */
public interface DataEnergisticsSettings {

    long revision();

    DataRipper dataRipper();

    DataDistributionTower dataDistributionTower();

    DataSanctumInterface dataSanctumInterface();

    DataExtractor dataExtractor();

    FlatteningTnt flatteningTnt();

    DataNuke dataNuke();

    SolarPanel solarPanel();

    TrinityCrafting trinityCrafting();

    TrinityDispatch trinityDispatch();

    /** Read-only Data Ripper power and target-selection configuration. */
    interface DataRipper {

        int baseCost();

        List<Pattern> blacklist();

        List<MultiplierEntry> multipliers();
    }

    /** Read-only Data Distribution Tower chunk coverage configuration. */
    interface DataDistributionTower {

        int range();
    }

    /** Read-only Data Sanctum interface capacity configuration. */
    interface DataSanctumInterface {

        int itemLimit();

        int fluidBuckets();

        int returnItemLimit();

        int returnFluidBuckets();
    }

    /** Read-only Data Extractor work, carrier and input-rule configuration. */
    interface DataExtractor {

        int baseDamage();

        int workIntervalSeconds();

        int baseDataFlowPerCycle();

        int dataFlowPerSwordDamage();

        int baseTargetLimit();

        int targetLimitPerCapacityCard();

        double extraTargetDataFlowMultiplier();

        float mobRequiredDamage();

        Set<ResourceLocation> mobDataBlacklist();

        float oreRequiredAmount();

        Set<ResourceLocation> oreDataBlacklist();

        float cropRequiredAmount();

        Set<ResourceLocation> cropDataBlacklist();

        Set<ResourceLocation> cropDataWhitelist();
    }

    /** Read-only settings captured once for one configurable TNT explosion. */
    interface FlatteningTnt {

        int clearChunkRadius();

        int clearStartYOffset();

        int clearHeight();

        int fillChunkRadius();

        int fillYOffset();

        BlockState fillBlockState();

        BlockPos explosionCenterOffset();

        boolean preserveFluids();

        boolean replaceUnbreakableBlocks();
    }

    /** Read-only settings refreshed by an active Data Nuke each server tick. */
    interface DataNuke {

        int workIntervalTicks();

        int maxRadius();

        double centerEntityConsumeRadius();
    }

    /** Read-only Solar generation and capacity-upgrade configuration. */
    interface SolarPanel {

        double dayGenerationAEPerTick();

        double nightGenerationAEPerTick();

        double speedCardBonusRatio();

        double energyCardCapacityBonusAE();
    }

    /** Read-only Trinity planning bounds, including restart-only worker-pool settings. */
    interface TrinityCrafting {

        int maxSccKeys();

        int maxBindingVariants();

        int maxScheduleStates();

        int graphRebuildBudgetMs();

        int plannerThreads();

        int plannerQueueCapacity();

        int dynamicRetryMaxTicks();

        CraftingQuantityMode defaultQuantityMode();
    }

    /** Read-only Trinity dispatch-governor configuration. */
    interface TrinityDispatch {

        int hardGridAttempts();

        int hardProviderAttempts();

        int hardCommitBudgetMs();

        int safeGridAttempts();

        int safeProviderAttempts();

        int safeCommitBudgetMs();

        int safeActorPermits();

        int safeRetryBackoffTicks();

        int warmupTicks();

        int metricsWindowTicks();

        double ewmaAlpha();

        int transitionWindows();

        int cooldownTicks();

        int safeHoldTicks();
    }
}
