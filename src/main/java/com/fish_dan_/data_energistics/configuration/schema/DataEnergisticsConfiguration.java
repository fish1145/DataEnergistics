package com.fish_dan_.data_energistics.configuration.schema;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry;

import net.minecraft.resources.ResourceLocation;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.UpdateRestrictions;
import dev.toma.configuration.config.format.ConfigFormats;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines the single localized YAML schema used by Data Energistics.
 *
 */
@Config(
        id = Data_Energistics.MODID,
        filename = Data_Energistics.MODID + "/" + Data_Energistics.MODID,
        group = Data_Energistics.MODID)
public final class DataEnergisticsConfiguration {

    /** The holder registered with Configuration and automatically synchronized by its file watcher. */
    public static final ConfigHolder<DataEnergisticsConfiguration> HOLDER = Configuration.registerConfig(DataEnergisticsConfiguration.class, ConfigFormats.YAML);

    /** The framework-owned schema instance; its fields are updated by Configuration's Auto-Sync thread. */
    public static final DataEnergisticsConfiguration INSTANCE = HOLDER.getConfigInstance();

    @Configurable
    @Configurable.Comment({ "Machine and network component settings.", "机器与网络组件设置。" })
    public MachineConfigs machines = new MachineConfigs();

    @Configurable
    @Configurable.Comment({ "Explosive and terrain transformation settings.", "爆炸物与地形变换设置。" })
    public ExplosiveConfigs explosives = new ExplosiveConfigs();

    @Configurable
    @Configurable.Comment({ "Astronomy production and dimension multipliers.", "天文观测产出与维度倍率设置。" })
    public AstronomySchema astronomy = new AstronomySchema();

    @Configurable
    @Configurable.Comment({ "Orbital weapon reserves, deployment and endpoint limits.", "轨道武器储备、部署与端点限制。" })
    public OrbitalWeaponSchema orbitalWeapon = new OrbitalWeaponSchema();

    @Configurable
    @Configurable.Comment({ "Trinity planning and dispatch settings.", "三位一体规划与派发设置。" })
    public TrinityConfigs trinity = new TrinityConfigs();

    @Configurable
    @Configurable.Comment({ "Developer and diagnostic settings.", "开发者与诊断设置。" })
    public DeveloperConfigs developer = new DeveloperConfigs();

    /**
     * Returns a deterministic fingerprint of configuration values that affect orbital previews and payloads.
     *
     * <p>
     * The direct Configuration instance is mutable and does not expose the old immutable snapshot revision. A
     * value fingerprint preserves preview invalidation when a hot-reloaded value changes without reintroducing a
     * second configuration object graph.
     * </p>
     */
    public long revision() {
        AstronomySchema astronomySettings = this.astronomy;
        OrbitalWeaponSchema weaponSettings = this.orbitalWeapon;
        DataNukeSchema nukeSettings = this.explosives.dataNuke;
        return Integer.toUnsignedLong(Objects.hash(
                astronomySettings.lowTierCelestialEnergyPerTick,
                astronomySettings.lowTierAeEnergyPerTick,
                astronomySettings.highTierMirrorCelestialEnergyPerTick1To4,
                astronomySettings.highTierMirrorCelestialEnergyPerTick5To8,
                astronomySettings.highTierMirrorCelestialEnergyPerTick9To12,
                astronomySettings.highTierMirrorCelestialEnergyPerTick13To16,
                astronomySettings.highTierCoreAeEnergyPerTick,
                astronomySettings.highTierMirrorAeEnergyPerTick,
                astronomySettings.highTierMinimumMirrors,
                astronomySettings.highTierMaximumMirrors,
                astronomySettings.highTierMirrorHorizontalRange,
                astronomySettings.highTierMirrorVerticalRange,
                astronomySettings.highTierWaveguidePathLength,
                astronomySettings.rainOutputMultiplier,
                astronomySettings.observationWindowStartTick,
                astronomySettings.observationWindowEndTick,
                astronomySettings.defaultDimensionMultiplier,
                Arrays.hashCode(astronomySettings.dimensionIds),
                Arrays.hashCode(astronomySettings.dimensionMultiplierValues),
                weaponSettings.celestialEnergyCapacity,
                weaponSettings.aeEnergyCapacity,
                weaponSettings.celestialEnergyUpkeepPerTick,
                weaponSettings.aeEnergyUpkeepPerTick,
                weaponSettings.celestialEnergyChargePerTick,
                weaponSettings.aeEnergyChargePerTick,
                weaponSettings.reserveGraceTicks,
                weaponSettings.deploymentThreshold,
                weaponSettings.redeploymentTicks,
                weaponSettings.maxEndpointsPerWeapon,
                weaponSettings.maxEndpointsPerDimension,
                weaponSettings.endpointChunkLoadingEnabled,
                weaponSettings.maxAttackChunkTicketsPerTask,
                weaponSettings.maxAttackChunkTicketsGlobal,
                weaponSettings.maxAttackChunkGenerationPerDimension,
                weaponSettings.maxAttackChunkGenerationGlobal,
                weaponSettings.maxAttackBlockMutationsPerTaskTick,
                weaponSettings.maxAttackBlockMutationsGlobalTick,
                weaponSettings.maxCommittedAttackTasks,
                weaponSettings.kineticAttackEnabled,
                weaponSettings.directedEnergyAttackEnabled,
                weaponSettings.digitalAnnihilationAttackEnabled,
                weaponSettings.kineticColumnRadius,
                weaponSettings.kineticColumnDepth,
                weaponSettings.kineticCraterRadius,
                weaponSettings.kineticCraterDepth,
                weaponSettings.kineticShockwaveRadius,
                weaponSettings.kineticEntityDamage,
                weaponSettings.kineticKnockbackStrength,
                weaponSettings.kineticCelestialEnergyCost,
                weaponSettings.kineticAeEnergyCost,
                weaponSettings.attackWarningTicks,
                weaponSettings.kineticCooldownTicks,
                weaponSettings.directedEnergyMinimumRadius,
                weaponSettings.directedEnergyMaximumRadius,
                weaponSettings.directedEnergyRadiusStep,
                weaponSettings.directedEnergyShallowDepth,
                weaponSettings.directedEnergyMediumDepth,
                weaponSettings.directedEnergyDeepDepth,
                weaponSettings.directedEnergyBaseCelestialEnergyCost,
                weaponSettings.directedEnergyBaseAeEnergyCost,
                weaponSettings.directedEnergyCelestialEnergyPerCoordinate,
                weaponSettings.directedEnergyAeEnergyPerCoordinate,
                weaponSettings.directedEnergyCooldownTicks,
                weaponSettings.directedEnergyEntityDamage,
                weaponSettings.digitalAnnihilationCelestialEnergyCost,
                weaponSettings.digitalAnnihilationAeEnergyCost,
                weaponSettings.digitalAnnihilationCooldownTicks,
                nukeSettings.workIntervalTicks,
                nukeSettings.maxRadius,
                nukeSettings.centerEntityConsumeRadius));
    }

    public static final class MachineConfigs {

        @Configurable
        @Configurable.Comment({ "Data Ripper power and target selection.", "数据撕裂器的功耗与目标选择设置。" })
        public DataRipperSchema dataRipper = new DataRipperSchema();

        @Configurable
        @Configurable.Comment({ "Data Distribution Tower chunk coverage.", "数据分配塔的区块覆盖设置。" })
        public DataDistributionTowerSchema dataDistributionTower = new DataDistributionTowerSchema();

        @Configurable
        @Configurable.Comment({ "Data Sanctum Interface stocking capacities.", "数据圣所接口的储备容量设置。" })
        public DataSanctumInterfaceSchema dataSanctumInterface = new DataSanctumInterfaceSchema();

        @Configurable
        @Configurable.Comment({ "Data Extractor work and carrier requirements.", "数据提取器的工作与载体需求设置。" })
        public DataExtractorSchema dataExtractor = new DataExtractorSchema();

        @Configurable
        @Configurable.Comment({ "Solar generation and upgrade bonuses.", "太阳能发电与升级加成设置。" })
        public SolarPanelSchema solarPanel = new SolarPanelSchema();
    }

    public static final class ExplosiveConfigs {

        @Configurable
        @Configurable.Comment({ "Configurable TNT terrain replacement.", "可配置 TNT 的地形替换设置。" })
        public ConfigurableTntSchema flatteningTnt = new ConfigurableTntSchema();

        @Configurable
        @Configurable.Comment({ "Incremental Data Nuke consumption.", "数据核弹的渐进吞噬设置。" })
        public DataNukeSchema dataNuke = new DataNukeSchema();
    }

    public static final class TrinityConfigs {

        @Configurable
        @Configurable.Comment({ "Trinity planning limits and quantity semantics.", "三位一体规划限制与数量语义设置。" })
        public TrinityCraftingSchema crafting = new TrinityCraftingSchema();

        @Configurable
        @Configurable.Comment({ "Trinity dispatch governor tuning.", "三位一体派发调节器设置。" })
        public TrinityDispatchSchema dispatch = new TrinityDispatchSchema();
    }

    public static final class DeveloperConfigs {

        @Configurable
        @Configurable.Comment({
                "Logs high-frequency runtime calculations and dispatch decisions. Warnings and errors are unaffected.",
                "记录高频运行时计算与发配决策。警告和错误不受影响。"
        })
        public boolean verboseRuntimeLogging = false;
    }

    public static final class DataRipperSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Base power cost for the data ripper power curve.",
                "数据撕裂器功耗曲线的基础数据流消耗。"
        })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int baseCost = 512;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Regex patterns for block ids that the data ripper should never accelerate.",
                "数据撕裂器永远不会加速的方块 ID 正则表达式。"
        })
        public String[] blacklist = {};

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Power multipliers paired by array index.",
                "按数组索引配对的功耗倍率。"
        })
        public DataRipperMultiplierSchema multipliers = new DataRipperMultiplierSchema();
    }

    public static final class DataRipperMultiplierSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Block id regex patterns. Each index pairs with the value at the same index.",
                "方块 ID 正则表达式；每项与 values 中相同索引的倍率配对。"
        })
        public String[] patterns = { "minecraft:hopper", "appeng:.*" };

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Positive power multipliers paired with patterns by index.",
                "与 patterns 按索引配对的正功耗倍率。"
        })
        @Configurable.DecimalRange(min = Double.MIN_NORMAL, max = Double.MAX_VALUE)
        public double[] values = { 1.5D, 2.0D };
    }

    public static final class DataDistributionTowerSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Base chunk coverage level. 1=1x1 chunk, 2=3x3 chunks, and so on.",
                "基础区块覆盖等级。1=1x1 区块，2=3x3 区块，依此类推。"
        })
        @Configurable.Range(min = 1, max = 128)
        public int range = 1;
    }

    public static final class DataSanctumInterfaceSchema {

        private static final int MAX_BASE_CAPACITY = Integer.MAX_VALUE / 8;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Base stocked item amount per config or stock slot. Each capacity card doubles it.",
                "每个配置或库存槽的基础物品储备数量。每张容量卡会使其翻倍。"
        })
        @Configurable.Range(min = 1, max = MAX_BASE_CAPACITY)
        public int itemLimit = 2048;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Base stocked fluid buckets per config or stock slot. Each capacity card doubles it.",
                "每个配置或库存槽的基础流体桶数。每张容量卡会使其翻倍。"
        })
        @Configurable.Range(min = 1, max = MAX_BASE_CAPACITY)
        public int fluidBuckets = 2048;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Base item amount per return slot. Each capacity card doubles it.",
                "每个返回槽的基础物品数量。每张容量卡会使其翻倍。"
        })
        @Configurable.Range(min = 1, max = MAX_BASE_CAPACITY)
        public int returnItemLimit = 2048;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Base fluid buckets per return slot. Each capacity card doubles it.",
                "每个返回槽的基础流体桶数。每张容量卡会使其翻倍。"
        })
        @Configurable.Range(min = 1, max = MAX_BASE_CAPACITY)
        public int returnFluidBuckets = 2048;
    }

    public static final class DataExtractorSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Base damage dealt each cycle.", "每次工作造成的基础伤害。" })
        @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
        public int baseDamage = 5;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Seconds between work cycles before speed cards.", "速度卡生效前每次工作之间的秒数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int workIntervalSeconds = 5;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Base data flow generated per cycle.", "每次工作生成的基础数据流。" })
        @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
        public int baseDataFlowPerCycle = 100;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Additional data flow per point of sword or base damage.", "每点剑或基础伤害额外生成的数据流。" })
        @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
        public int dataFlowPerSwordDamage = 20;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Base maximum number of affected targets.", "可影响目标数量的基础上限。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int baseTargetLimit = 20;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Additional target limit per capacity card.", "每张容量卡提供的额外目标上限。" })
        @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
        public int targetLimitPerCapacityCard = 5;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Additional data flow multiplier per extra target.", "每个额外目标提供的数据流倍率加成。" })
        @Configurable.DecimalRange(min = 0.0, max = Double.MAX_VALUE)
        public double extraTargetDataFlowMultiplier = 0.25D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Required total damage for a mob data carrier.", "完成生物数据载体所需的总伤害。" })
        @Configurable.DecimalRange(min = 1.0, max = Double.MAX_VALUE)
        public double mobRequiredDamage = 1024.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Blocked entity ids. Each array element is one registry id.", "禁用的实体 ID；每个数组元素对应一个注册表 ID。" })
        public String[] mobDataBlacklist = {};

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Required total amount for an ore data carrier.", "完成矿石数据载体所需的总数量。" })
        @Configurable.DecimalRange(min = 1.0, max = Double.MAX_VALUE)
        public double oreRequiredAmount = 4096.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Blocked item ids for ore data. Each array element is one registry id.", "矿物数据禁用的物品 ID；每个数组元素对应一个注册表 ID。" })
        public String[] oreDataBlacklist = {};

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Required total amount for a crop data carrier.", "完成农作数据载体所需的总数量。" })
        @Configurable.DecimalRange(min = 1.0, max = Double.MAX_VALUE)
        public double cropRequiredAmount = 4096.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Blocked item ids for crop data. Each array element is one registry id.", "农作数据禁用的物品 ID；每个数组元素对应一个注册表 ID。" })
        public String[] cropDataBlacklist = {};

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Additional allowed crop item ids. Each array element is one registry id.", "额外允许的农作物品 ID；每个数组元素对应一个注册表 ID。" })
        public String[] cropDataWhitelist = {};
    }

    public static final class ConfigurableTntSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Chunk radius for the cleared area. 1 = 3x3 chunks.", "清除区域的区块半径。1 = 3x3 区块。" })
        @Configurable.Range(min = 0, max = 64)
        @Configurable.Gui.Slider
        public int clearChunkRadius = 1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Vertical offset where clearing starts.", "清除起始位置的垂直偏移。" })
        @Configurable.Range(min = -384, max = 384)
        @Configurable.Gui.Slider
        public int clearStartYOffset = 0;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Number of vertical blocks to clear.", "要清除的垂直方块数量。" })
        @Configurable.Range(min = 1, max = 512)
        public int clearHeight = 25;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Chunk radius for the filled floor. 1 = 3x3 chunks.", "填充地板的区块半径。1 = 3x3 区块。" })
        @Configurable.Range(min = 0, max = 64)
        @Configurable.Gui.Slider
        public int fillChunkRadius = 1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Vertical offset for the filled floor layer.", "填充地板层的垂直偏移。" })
        @Configurable.Range(min = -384, max = 384)
        @Configurable.Gui.Slider
        public int fillYOffset = -1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Block id used for the filled floor.", "用于填充地板的方块 ID。" })
        @Configurable.StringPattern("^[a-z0-9_.-]+:[a-z0-9/._-]+$")
        public String fillBlock = "minecraft:dirt";

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "X offset applied to the TNT center.", "应用到 TNT 中心的 X 偏移。" })
        @Configurable.Range(min = -512, max = 512)
        @Configurable.Gui.Slider
        public int centerOffsetX = 0;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Y offset applied to the TNT center.", "应用到 TNT 中心的 Y 偏移。" })
        @Configurable.Range(min = -512, max = 512)
        @Configurable.Gui.Slider
        public int centerOffsetY = 0;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Z offset applied to the TNT center.", "应用到 TNT 中心的 Z 偏移。" })
        @Configurable.Range(min = -512, max = 512)
        @Configurable.Gui.Slider
        public int centerOffsetZ = 0;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Preserve water and lava while clearing.", "清除时保留水和岩浆。" })
        public boolean preserveFluids = false;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Allow unbreakable blocks to be removed or replaced.", "允许移除或替换不可破坏方块。" })
        public boolean replaceUnbreakableBlocks = false;
    }

    public static final class DataNukeSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Server ticks between block-devouring cycles.", "吞噬方块工作周期之间的服务器 tick。" })
        @Configurable.Range(min = 1, max = 1200)
        public int workIntervalTicks = 1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum spherical radius in blocks.", "最大球形半径，单位方块。" })
        @Configurable.Range(min = 1, max = 8192)
        public int maxRadius = 2048;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Entity-devouring radius checked every tick.", "每 tick 检查的实体吞噬半径。" })
        @Configurable.DecimalRange(min = 0.0, max = 128.0)
        public double centerEntityConsumeRadius = 4.0D;
    }

    public static final class SolarPanelSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Daytime AE generation per tick.", "白天每 tick 生成的 AE。" })
        @Configurable.DecimalRange(min = 0.0, max = Double.MAX_VALUE)
        public double dayGenerationAEPerTick = 3000.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Nighttime AE generation per tick.", "夜晚每 tick 生成的 AE。" })
        @Configurable.DecimalRange(min = 0.0, max = Double.MAX_VALUE)
        public double nightGenerationAEPerTick = 1000.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Additional generation ratio per speed card.", "每张速度卡提供的额外发电倍率。" })
        @Configurable.DecimalRange(min = 0.0, max = 1000.0)
        @Configurable.Gui.Slider
        public double speedCardBonusRatio = 0.75D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Additional AE storage per energy card.", "每张能量卡提供的额外 AE 存储容量。" })
        @Configurable.DecimalRange(min = 0.0, max = Double.MAX_VALUE)
        public double energyCardCapacityBonusAE = 80000.0D;
    }

    public static final class AstronomySchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Celestial Energy produced per clear-weather tick by one low-tier observatory.",
                "单个低阶天文观测台在晴朗天气下每 tick 产出的星体能量。"
        })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long lowTierCelestialEnergyPerTick = 8L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "AE energy consumed by one successful low-tier observation tick.",
                "低阶天文观测台每次成功观测 tick 消耗的 AE 能量。"
        })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long lowTierAeEnergyPerTick = 4_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Celestial Energy produced per tick by each valid high-tier mirror from mirror 1 through 4.",
                "高阶阵列第 1 至 4 个有效镜单元各自每 tick 产出的星体能量。"
        })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long highTierMirrorCelestialEnergyPerTick1To4 = 40L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Celestial Energy produced per tick by each valid high-tier mirror from mirror 5 through 8.",
                "高阶阵列第 5 至 8 个有效镜单元各自每 tick 产出的星体能量。"
        })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long highTierMirrorCelestialEnergyPerTick5To8 = 30L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Celestial Energy produced per tick by each valid high-tier mirror from mirror 9 through 12.",
                "高阶阵列第 9 至 12 个有效镜单元各自每 tick 产出的星体能量。"
        })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long highTierMirrorCelestialEnergyPerTick9To12 = 20L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Celestial Energy produced per tick by each valid high-tier mirror from mirror 13 through 16.",
                "高阶阵列第 13 至 16 个有效镜单元各自每 tick 产出的星体能量。"
        })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long highTierMirrorCelestialEnergyPerTick13To16 = 10L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Fixed AE energy consumed per successful high-tier array tick.",
                "高阶阵列每次成功工作 tick 固定消耗的 AE 能量。"
        })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long highTierCoreAeEnergyPerTick = 25_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Additional AE energy consumed per valid high-tier mirror and successful tick.",
                "高阶阵列每个有效镜单元在每次成功工作 tick 额外消耗的 AE 能量。"
        })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long highTierMirrorAeEnergyPerTick = 10_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Minimum valid mirrors required to operate.", "高阶阵列开始工作所需的最少有效镜单元数。" })
        @Configurable.Range(min = 1, max = 16)
        public int highTierMinimumMirrors = 4;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum mirrors claimed and used by one core.", "单个高阶阵列核心可绑定并使用的最大镜单元数。" })
        @Configurable.Range(min = 1, max = 16)
        public int highTierMaximumMirrors = 16;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Maximum horizontal distance from a high-tier core to a mirror center.",
                "高阶阵列核心到镜单元中心的最大水平距离。"
        })
        @Configurable.Range(min = 1, max = 64)
        public int highTierMirrorHorizontalRange = 32;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Maximum absolute height difference from a high-tier core to a mirror center.",
                "高阶阵列核心到镜单元中心允许的最大高度差。"
        })
        @Configurable.Range(min = 0, max = 32)
        public int highTierMirrorVerticalRange = 4;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Maximum waveguide path length from a high-tier core port to a mirror.",
                "高阶阵列核心端口到镜单元的最大星能波导路径长度。"
        })
        @Configurable.Range(min = 1, max = 64)
        public int highTierWaveguidePathLength = 32;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Output multiplier while raining.", "降雨时的产出倍率。" })
        @Configurable.DecimalRange(min = 0.0D, max = 1.0D)
        public double rainOutputMultiplier = 0.25D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Inclusive observation-window start within a 24,000-tick day.",
                "普通昼夜维度在 24000 tick 周期内的观测窗口起始值（包含）。"
        })
        @Configurable.Range(min = 0, max = 23_999)
        public int observationWindowStartTick = 13_000;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Exclusive observation-window end within a 24,000-tick day.",
                "普通昼夜维度在 24000 tick 周期内的观测窗口结束值（不包含）。"
        })
        @Configurable.Range(min = 1, max = 24_000)
        public int observationWindowEndTick = 23_000;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Output multiplier for tagged observable dimensions without an explicit override.",
                "未显式覆盖的可观测维度使用的产出倍率。"
        })
        @Configurable.DecimalRange(min = 0.0D, max = 1_000_000.0D)
        public double defaultDimensionMultiplier = 1.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Dimension ids paired by index with dimensionMultiplierValues.",
                "与 dimensionMultiplierValues 按索引配对的维度 ID。"
        })
        public String[] dimensionIds = { "minecraft:overworld", "minecraft:the_end", "minecraft:the_nether" };

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Per-dimension output multipliers paired by index with dimensionIds.",
                "与 dimensionIds 按索引配对的维度产出倍率。"
        })
        @Configurable.DecimalRange(min = 0.0D, max = 1_000_000.0D)
        public double[] dimensionMultiplierValues = { 1.0D, 2.0D, 0.0D };

        public Map<ResourceLocation, Double> dimensionMultipliers() {
            LinkedHashMap<ResourceLocation, Double> result = new LinkedHashMap<>();
            int count = Math.min(this.dimensionIds.length, this.dimensionMultiplierValues.length);
            for (int index = 0; index < count; index++) {
                ResourceLocation dimension = ResourceLocation.tryParse(this.dimensionIds[index]);
                if (dimension != null) {
                    result.put(dimension, this.dimensionMultiplierValues[index]);
                }
            }
            return Map.copyOf(result);
        }
    }

    public static final class OrbitalWeaponSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum orbital Celestial Energy reserve.", "轨道星体能量储备上限。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long celestialEnergyCapacity = 500_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum orbital AE energy reserve, separate from Celestial Energy.", "轨道 AE 能量储备上限，与星体能量相互独立。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long aeEnergyCapacity = 500_000_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Celestial Energy consumed per deployed tick.", "部署状态每 tick 消耗的星体能量。" })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long celestialEnergyUpkeepPerTick = 100L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "AE energy consumed per deployed tick.", "部署状态每 tick 消耗的 AE 能量。" })
        @Configurable.Range(min = 0L, max = Long.MAX_VALUE)
        public long aeEnergyUpkeepPerTick = 250_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum Celestial Energy transferred by one endpoint per tick.", "单个端点每 tick 可传输的星体能量上限。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long celestialEnergyChargePerTick = 20_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum AE energy transferred by one endpoint per tick.", "单个端点每 tick 可传输的 AE 能量上限。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long aeEnergyChargePerTick = 10_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Ticks at zero reserve before returning to dormancy.", "储备归零后返回休眠状态前的宽限 tick 数。" })
        @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
        public int reserveGraceTicks = 12_000;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Fraction of both reserve capacities required to deploy.", "开始部署所需的两种储备容量比例。" })
        @Configurable.DecimalRange(min = Double.MIN_NORMAL, max = 1.0D)
        public double deploymentThreshold = 0.10D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Ticks spent tearing down and rebuilding after changing the primary uplink beacon.", "切换主上行信标后拆解并重组投影所需的 tick 数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int redeploymentTicks = 1_200;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum control consoles and uplink beacons bound to one weapon.", "单件武器可绑定的控制终端与上行信标总上限。" })
        @Configurable.Range(min = 1, max = 1024)
        public int maxEndpointsPerWeapon = 32;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum endpoints for one weapon in a single dimension.", "单件武器在同一维度内的端点上限。" })
        @Configurable.Range(min = 1, max = 1024)
        public int maxEndpointsPerDimension = 8;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Keep each bound endpoint chunk loaded.", "让每个已绑定端点强制加载自身区块。" })
        public boolean endpointChunkLoadingEnabled = true;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum attack work chunk tickets held by one task.", "单个攻击任务同时持有的工作区块票据上限。" })
        @Configurable.Range(min = 1, max = 64)
        public int maxAttackChunkTicketsPerTask = 8;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum attack work chunk tickets held across the server.", "全服攻击工作区块票据总上限。" })
        @Configurable.Range(min = 1, max = 1024)
        public int maxAttackChunkTicketsGlobal = 64;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum concurrent attack chunk requests per dimension.", "每个维度同时执行的攻击区块请求上限。" })
        @Configurable.Range(min = 1, max = 64)
        public int maxAttackChunkGenerationPerDimension = 2;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum concurrent attack chunk requests across the server.", "全服同时执行的攻击区块请求上限。" })
        @Configurable.Range(min = 1, max = 256)
        public int maxAttackChunkGenerationGlobal = 8;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum terrain positions visited by one attack per tick.", "单个攻击任务每 tick 访问的地形位置上限。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int maxAttackBlockMutationsPerTaskTick = 8_192;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum terrain positions visited by all orbital attacks per tick.", "全服轨道攻击每 tick 访问的地形位置总上限。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int maxAttackBlockMutationsGlobalTick = 32_768;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum preview chunk cells checked by one player per tick.", "单名玩家的攻击预览每 tick 检查的区块单元上限。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int previewChunkChecksPerTaskTick = 4_096;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum preview chunk cells checked across the server per tick.", "全服攻击预览每 tick 检查的区块单元总上限。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int previewChunkChecksGlobalTick = 16_384;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum warning, committed and delivering orbital attack tasks.", "全服预警、已提交和投送中的轨道攻击任务上限。" })
        @Configurable.Range(min = 1, max = 1024)
        public int maxCommittedAttackTasks = 32;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Allow new kinetic attack confirmations. Existing attacks continue.", "允许确认新的动能攻击。已开始的攻击继续执行。" })
        public boolean kineticAttackEnabled = true;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Allow new directed-energy attack confirmations. Existing attacks continue.", "允许确认新的定向能攻击。已开始的攻击继续执行。" })
        public boolean directedEnergyAttackEnabled = true;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Allow new digital-annihilation attack confirmations. Existing attacks continue.", "允许确认新的数位湮灭攻击。已开始的攻击继续执行。" })
        public boolean digitalAnnihilationAttackEnabled = true;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Radius of the kinetic strike's vertical clearing column.", "动能攻击垂直清除柱的半径。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.Kinetic.MAX_TERRAIN_RADIUS)
        public int kineticColumnRadius = OrbitalAttackGeometry.Kinetic.DEFAULT_COLUMN_RADIUS;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Blocks cleared below the kinetic target by its vertical column.", "动能攻击垂直清除柱向目标下方延伸的深度。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.Kinetic.MAX_TERRAIN_DEPTH)
        public int kineticColumnDepth = OrbitalAttackGeometry.Kinetic.DEFAULT_COLUMN_DEPTH;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Radius of the shallow crater below a kinetic impact.", "动能攻击命中点下方浅陨坑的半径。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.Kinetic.MAX_TERRAIN_RADIUS)
        public int kineticCraterRadius = OrbitalAttackGeometry.Kinetic.DEFAULT_CRATER_RADIUS;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Depth of the shallow crater below a kinetic impact.", "动能攻击命中点下方浅陨坑的深度。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.Kinetic.MAX_TERRAIN_DEPTH)
        public int kineticCraterDepth = OrbitalAttackGeometry.Kinetic.DEFAULT_CRATER_DEPTH;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Radius of the instantaneous kinetic impact shockwave.", "动能攻击瞬时冲击波的半径。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.Kinetic.MAX_SHOCKWAVE_RADIUS)
        public int kineticShockwaveRadius = OrbitalAttackGeometry.Kinetic.DEFAULT_SHOCKWAVE_RADIUS;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Damage dealt to a non-exempt living entity inside the kinetic shockwave.", "动能冲击波对范围内非豁免生物造成的伤害。" })
        @Configurable.Range(min = 1L, max = Integer.MAX_VALUE)
        public long kineticEntityDamage = OrbitalAttackGeometry.Kinetic.DEFAULT_ENTITY_DAMAGE;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Horizontal knockback strength applied by the kinetic shockwave.", "动能冲击波施加的水平击退强度。" })
        @Configurable.DecimalRange(min = 0.0D, max = OrbitalAttackGeometry.Kinetic.MAX_KNOCKBACK_STRENGTH)
        public double kineticKnockbackStrength = OrbitalAttackGeometry.Kinetic.DEFAULT_KNOCKBACK_STRENGTH;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Celestial Energy reserved by one kinetic strike.", "一次动能攻击预留的星体能量。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long kineticCelestialEnergyCost = 5_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "AE energy reserved by one kinetic strike.", "一次动能攻击预留的 AE 能量。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long kineticAeEnergyCost = 5_000_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Public warning duration before an attack commits.", "攻击提交前的公开预警时长。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int attackWarningTicks = 300;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Kinetic strike cooldown after its effect completes.", "动能攻击效果完成后的冷却时长。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int kineticCooldownTicks = 6_000;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Minimum selectable directed-energy scan radius.", "定向能扫描可选的最小半径。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS)
        public int directedEnergyMinimumRadius = OrbitalAttackGeometry.DirectedEnergy.DEFAULT_MIN_RADIUS;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum selectable directed-energy scan radius.", "定向能扫描可选的最大半径。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS)
        public int directedEnergyMaximumRadius = OrbitalAttackGeometry.DirectedEnergy.DEFAULT_MAX_RADIUS;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Radius increment measured from the configured directed-energy minimum.", "从定向能最小半径开始计算的半径步进。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS)
        public int directedEnergyRadiusStep = OrbitalAttackGeometry.DirectedEnergy.DEFAULT_RADIUS_STEP;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Captured block depth of the shallow directed-energy profile.", "定向能浅层档冻结的方块深度。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_DEPTH)
        public int directedEnergyShallowDepth = OrbitalAttackGeometry.DirectedEnergy.DEFAULT_SHALLOW_DEPTH;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Captured block depth of the medium directed-energy profile.", "定向能中层档冻结的方块深度。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_DEPTH)
        public int directedEnergyMediumDepth = OrbitalAttackGeometry.DirectedEnergy.DEFAULT_MEDIUM_DEPTH;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Captured block depth of the deep directed-energy profile.", "定向能深层档冻结的方块深度。" })
        @Configurable.Range(min = 1, max = OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_DEPTH)
        public int directedEnergyDeepDepth = OrbitalAttackGeometry.DirectedEnergy.DEFAULT_DEEP_DEPTH;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Fixed Celestial Energy base escrow for one directed-energy scan.", "一次定向能扫描固定预留的星体能量基础费用。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long directedEnergyBaseCelestialEnergyCost = 2_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Fixed AE base escrow for one directed-energy scan.", "一次定向能扫描固定预留的 AE 基础费用。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long directedEnergyBaseAeEnergyCost = 2_000_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Celestial Energy escrow per directed-energy disk coordinate.", "定向能扫描每个圆盘调度坐标的星体能量费用。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long directedEnergyCelestialEnergyPerCoordinate = 4L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "AE escrow per directed-energy disk coordinate.", "定向能扫描每个圆盘调度坐标的 AE 能量费用。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long directedEnergyAeEnergyPerCoordinate = 2_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Directed-energy cooldown after its scan completes.", "定向能扫描完成后的冷却时长。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int directedEnergyCooldownTicks = 12_000;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Damage applied to a living entity each time a directed-energy beam column covers it.", "定向能光束每次覆盖实体时造成的伤害。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long directedEnergyEntityDamage = 500L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Celestial Energy reserved by one digital annihilation payload.", "一次数位湮灭体轨道载荷预留的星体能量。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long digitalAnnihilationCelestialEnergyCost = 80_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "AE energy reserved by one digital annihilation payload.", "一次数位湮灭体轨道载荷预留的 AE 能量。" })
        @Configurable.Range(min = 1L, max = Long.MAX_VALUE)
        public long digitalAnnihilationAeEnergyCost = 80_000_000_000L;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Digital annihilation cooldown after its payload completes.", "数位湮灭体载荷完成后的冷却时长。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int digitalAnnihilationCooldownTicks = 72_000;
    }

    public static final class TrinityCraftingSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum AEKey count in one strongly connected component.", "单个强连通分量允许的最大 AEKey 数量。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int maxSccKeys = 64;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum legal input-binding variants per request.", "单次请求允许的最大合法输入绑定变体数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int maxBindingVariants = 32768;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum compressed scheduling states.", "最大压缩排程状态数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int maxScheduleStates = 500000;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Per-tick graph rebuild budget in milliseconds.", "每 tick 合成图重建预算，单位毫秒。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int graphRebuildBudgetMs = 4;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Bounded workers for initial Trinity plan calculations. Default: clamp(CPU / 2, 1, 8). Requires a game restart.",
                "Trinity 初始计划计算的有界工作线程数。默认：clamp(CPU / 2, 1, 8)。需要重启游戏。"
        })
        @Configurable.Range(min = 1, max = 8)
        @Configurable.UpdateRestriction(UpdateRestrictions.GAME_RESTART)
        @Configurable.Gui.Slider
        public int plannerThreads = recommendedPlannerThreads(Runtime.getRuntime().availableProcessors());

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Bounded workers for Trinity CPU remaining-work replanning. Default: clamp(CPU / 2, 1, 8). Requires a game restart.",
                "Trinity CPU 剩余工作重规划的有界工作线程数。默认：clamp(CPU / 2, 1, 8)。需要重启游戏。"
        })
        @Configurable.Range(min = 1, max = 8)
        @Configurable.UpdateRestriction(UpdateRestrictions.GAME_RESTART)
        @Configurable.Gui.Slider
        public int cpuPlannerThreads = recommendedPlannerThreads(Runtime.getRuntime().availableProcessors());

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Maximum queued requests per planning lane. Requires a game restart.",
                "每条规划轨道允许排队的最大请求数。需要重启游戏。"
        })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        @Configurable.UpdateRestriction(UpdateRestrictions.GAME_RESTART)
        public int plannerQueueCapacity = 128;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum dynamic-input retry backoff in ticks.", "动态输入重试退避的最大 tick 间隔。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int dynamicRetryMaxTicks = 200;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Default quantity semantics for non-player requests.", "非玩家请求使用的默认数量语义。" })
        public CraftingQuantityMode defaultQuantityMode = CraftingQuantityMode.NET_NEW;

        public static int recommendedPlannerThreads(int availableProcessors) {
            if (availableProcessors <= 0) {
                throw new IllegalArgumentException("Available processor count must be positive");
            }
            return Math.clamp(availableProcessors / 2, 1, 8);
        }
    }

    public static final class TrinityDispatchSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum physical provider attempts per grid tick.", "每个网格每 tick 的最大 provider 物理调用次数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int hardGridAttempts = 32_768;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum attempts per provider and grid tick.", "单个 provider 每个网格 tick 的最大调用次数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int hardProviderAttempts = 32_768;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum dispatch commit time per grid tick in milliseconds.", "每个网格 tick 的派发提交时间上限，单位毫秒。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int hardCommitBudgetMs = 30;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "SAFE-mode grid attempts per tick.", "SAFE 模式每个网格每 tick 的调用次数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int safeGridAttempts = 16;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "SAFE-mode attempts per provider and grid tick.", "SAFE 模式单个 provider 每个网格 tick 的调用次数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int safeProviderAttempts = 2;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "SAFE-mode commit budget in milliseconds.", "SAFE 模式提交预算，单位毫秒。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int safeCommitBudgetMs = 2;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Outstanding proposal permits retained by SAFE mode.", "SAFE 模式保留的 outstanding proposal permit 数量。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int safeActorPermits = 1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum adaptive and SAFE retry backoff in ticks.", "自适应及 SAFE 重试退避上限，单位 tick。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int safeRetryBackoffTicks = 8;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Observation ticks before adaptive decisions.", "允许自适应决策前的观察 tick 数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int warmupTicks = 200;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Ticks aggregated into one metrics window.", "每个指标窗口聚合的 tick 数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int metricsWindowTicks = 20;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "EWMA alpha for complete server tick duration.", "完整服务器 tick 耗时使用的 EWMA alpha。" })
        @Configurable.DecimalRange(min = Double.MIN_NORMAL, max = 1.0)
        @Configurable.Gui.Slider
        public double ewmaAlpha = 0.25D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Consecutive windows required for a transition.", "状态切换前要求连续满足条件的窗口数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int transitionWindows = 3;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Cooldown ticks after a budget adjustment.", "预算调整后的 cooldown tick 数。" })
        @Configurable.Range(min = 0, max = Integer.MAX_VALUE)
        public int cooldownTicks = 60;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Ticks retained in SAFE mode before observation.", "SAFE 模式返回观察状态前保持的 tick 数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int safeHoldTicks = 200;
    }
}
