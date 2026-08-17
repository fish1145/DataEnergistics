package com.fish_dan_.data_energistics.configuration.api;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.dataripper.DataRipperConfigParsingUtils.MultiplierEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read-only root boundary that prevents gameplay code from observing mutable Configuration schema objects.
 */
public interface DataEnergisticsSettings {

    long revision();

    /**
     * Whether potentially high-frequency runtime calculation and dispatch diagnostics may be logged.
     */
    boolean verboseRuntimeLogging();

    DataRipper dataRipper();

    DataDistributionTower dataDistributionTower();

    DataSanctumInterface dataSanctumInterface();

    DataExtractor dataExtractor();

    FlatteningTnt flatteningTnt();

    DataNuke dataNuke();

    SolarPanel solarPanel();

    /**
     * Returns the astronomy production settings captured by this immutable configuration revision.
     */
    Astronomy astronomy();

    /**
     * Returns the orbital weapon settings captured by this immutable configuration revision.
     */
    OrbitalWeapon orbitalWeapon();

    TrinityCrafting trinityCrafting();

    TrinityDispatch trinityDispatch();

    /**
     * Read-only Data Ripper power and target-selection configuration.
     */
    interface DataRipper {

        int baseCost();

        List<Pattern> blacklist();

        List<MultiplierEntry> multipliers();
    }

    /**
     * Read-only Data Distribution Tower chunk coverage configuration.
     */
    interface DataDistributionTower {

        int range();
    }

    /**
     * Read-only Data Sanctum interface capacity configuration.
     */
    interface DataSanctumInterface {

        int itemLimit();

        int fluidBuckets();

        int returnItemLimit();

        int returnFluidBuckets();
    }

    /**
     * Read-only Data Extractor work, carrier and input-rule configuration.
     */
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

    /**
     * Read-only settings captured once for one configurable TNT explosion.
     */
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

    /**
     * Read-only settings refreshed by an active Data Nuke each server tick.
     */
    interface DataNuke {

        int workIntervalTicks();

        int maxRadius();

        double centerEntityConsumeRadius();
    }

    /**
     * Read-only Solar generation and capacity-upgrade configuration.
     */
    interface SolarPanel {

        double dayGenerationAEPerTick();

        double nightGenerationAEPerTick();

        double speedCardBonusRatio();

        double energyCardCapacityBonusAE();
    }

    /**
     * Read-only astronomy production, observation-window and dimension-multiplier settings.
     *
     * <p>
     * Implementations belong to one immutable configuration snapshot. Gameplay code may retain and read them from
     * any thread; all values and dimension identifiers have already passed validation, collections are immutable,
     * no member is nullable and reading has no side effects.
     * </p>
     */
    interface Astronomy {

        /** Returns the clear-weather output of one low-tier observatory per server tick. */
        long lowTierCelestialEnergyPerTick();

        /** Returns the AE energy consumed by one successful low-tier observation tick. */
        long lowTierAeEnergyPerTick();

        /** Returns the per-mirror output for valid mirrors 1 through 4 in a high-tier array. */
        long highTierMirrorCelestialEnergyPerTick1To4();

        /** Returns the per-mirror output for valid mirrors 5 through 8 in a high-tier array. */
        long highTierMirrorCelestialEnergyPerTick5To8();

        /** Returns the per-mirror output for valid mirrors 9 through 12 in a high-tier array. */
        long highTierMirrorCelestialEnergyPerTick9To12();

        /** Returns the per-mirror output for valid mirrors 13 through 16 in a high-tier array. */
        long highTierMirrorCelestialEnergyPerTick13To16();

        /** Returns the fixed AE energy cost of an operating high-tier array core per server tick. */
        long highTierCoreAeEnergyPerTick();

        /** Returns the additional AE energy cost of each valid high-tier mirror per server tick. */
        long highTierMirrorAeEnergyPerTick();

        /** Returns the minimum valid mirror count required for a high-tier array to operate. */
        int highTierMinimumMirrors();

        /** Returns the maximum mirror count that one high-tier array may claim and use. */
        int highTierMaximumMirrors();

        /** Returns the maximum horizontal Euclidean distance from the core to a mirror center. */
        int highTierMirrorHorizontalRange();

        /** Returns the maximum absolute height difference from the core to a mirror center. */
        int highTierMirrorVerticalRange();

        /** Returns the maximum connected waveguide path length from a core port to a mirror. */
        int highTierWaveguidePathLength();

        /** Returns the output multiplier applied while it is raining. */
        double rainOutputMultiplier();

        /** Returns the inclusive normal-dimension observation-window start within a 24,000-tick day. */
        int observationWindowStartTick();

        /** Returns the exclusive normal-dimension observation-window end within a 24,000-tick day. */
        int observationWindowEndTick();

        /** Returns the multiplier used for tagged observable dimensions without an explicit override. */
        double defaultDimensionMultiplier();

        /** Returns immutable per-dimension output multiplier overrides keyed by dimension id. */
        Map<ResourceLocation, Double> dimensionMultipliers();
    }

    /**
     * Read-only orbital reserve, deployment and endpoint settings.
     *
     * <p>
     * Implementations belong to one immutable configuration snapshot and may be retained for the lifetime of that
     * snapshot. Gameplay code may read them from any thread but must never treat them as mutable schema objects.
     * Every value has already passed finite, range and cross-field validation; no member is nullable and reading has no
     * side effects.
     * </p>
     */
    interface OrbitalWeapon {

        /** Returns the maximum stored Celestial Energy, independent from the AE energy reserve. */
        long celestialEnergyCapacity();

        /** Returns the independent maximum stored AE energy measured in AE units. */
        long aeEnergyCapacity();

        /** Returns Celestial Energy consumed per deployed server tick. */
        long celestialEnergyUpkeepPerTick();

        /** Returns AE energy consumed per deployed server tick. */
        long aeEnergyUpkeepPerTick();

        /** Returns the maximum Celestial Energy one selected endpoint may transfer per server tick. */
        long celestialEnergyChargePerTick();

        /** Returns the maximum AE energy one selected endpoint may transfer per server tick. */
        long aeEnergyChargePerTick();

        /** Returns the zero-reserve grace period in server ticks before the projection returns to dormancy. */
        int reserveGraceTicks();

        /** Returns the fraction of both capacities required to begin or resume deployment. */
        double deploymentThreshold();

        /** Returns the teardown/rebuild window in server ticks after changing the primary anchor. */
        int redeploymentTicks();

        /** Returns the combined control-console and uplink-beacon limit for one weapon. */
        int maxEndpointsPerWeapon();

        /** Returns the combined endpoint limit for one weapon within a single dimension. */
        int maxEndpointsPerDimension();

        /** Returns whether bound endpoints should keep their own chunks loaded. */
        boolean endpointChunkLoadingEnabled();

        /** Returns the maximum attack work tickets retained by one terrain task. */
        int maxAttackChunkTicketsPerTask();

        /** Returns the maximum attack work tickets retained across the server. */
        int maxAttackChunkTicketsGlobal();

        /** Returns the maximum concurrent attack chunk requests in one dimension. */
        int maxAttackChunkGenerationPerDimension();

        /** Returns the maximum concurrent attack chunk requests across the server. */
        int maxAttackChunkGenerationGlobal();

        /** Returns the maximum terrain positions visited by one attack in one server tick. */
        int maxAttackBlockMutationsPerTaskTick();

        /** Returns the maximum terrain positions visited by all orbital attacks in one server tick. */
        int maxAttackBlockMutationsGlobalTick();

        /** Returns the maximum number of warning, committed and delivering orbital attack tasks. */
        int maxCommittedAttackTasks();

        /** Returns the Celestial Energy escrow required to confirm a kinetic strike. */
        long kineticCelestialEnergyCost();

        /** Returns the AE energy escrow required to confirm a kinetic strike. */
        long kineticAeEnergyCost();

        /** Returns the public warning duration applied before a confirmed attack commits. */
        int attackWarningTicks();

        /** Returns the kinetic strike cooldown applied after its world effect completes. */
        int kineticCooldownTicks();

        /** Returns the fixed Celestial Energy base escrow for one directed-energy scan. */
        long directedEnergyBaseCelestialEnergyCost();

        /** Returns the fixed AE base escrow for one directed-energy scan. */
        long directedEnergyBaseAeEnergyCost();

        /** Returns the Celestial Energy escrow charged for every scheduled disk coordinate. */
        long directedEnergyCelestialEnergyPerCoordinate();

        /** Returns the AE escrow charged for every scheduled disk coordinate. */
        long directedEnergyAeEnergyPerCoordinate();

        /** Returns the directed-energy cooldown applied after its scan completes. */
        int directedEnergyCooldownTicks();

        /** Returns the damage applied to a living entity each time a beam column covers it. */
        long directedEnergyEntityDamage();

        /** Returns the Celestial Energy escrow required to confirm a digital annihilation payload. */
        long digitalAnnihilationCelestialEnergyCost();

        /** Returns the AE energy escrow required to confirm a digital annihilation payload. */
        long digitalAnnihilationAeEnergyCost();

        /** Returns the cooldown applied after a digital annihilation payload completes. */
        int digitalAnnihilationCooldownTicks();
    }

    /**
     * Read-only Trinity planning bounds, including restart-only worker-pool settings.
     */
    interface TrinityCrafting {

        int maxSccKeys();

        int maxBindingVariants();

        int maxScheduleStates();

        int graphRebuildBudgetMs();

        int plannerThreads();

        int cpuPlannerThreads();

        int plannerQueueCapacity();

        int dynamicRetryMaxTicks();

        CraftingQuantityMode defaultQuantityMode();
    }

    /**
     * Read-only Trinity dispatch-governor configuration.
     */
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
