package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.UpdateRestrictions;

/**
 * Defines the single localized YAML schema used by Data Energistics.
 *
 * <p>
 * The schema is deliberately data-only. Runtime parsing, cross-field validation and atomic publication live in
 * dedicated configuration components.
 * </p>
 */
@Config(
        id = Data_Energistics.MODID,
        filename = Data_Energistics.MODID + "/" + Data_Energistics.MODID,
        group = Data_Energistics.MODID)
public final class DataEnergisticsConfiguration {

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public DataRipperSchema dataRipper = new DataRipperSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public DataDistributionTowerSchema dataDistributionTower = new DataDistributionTowerSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public DataSanctumInterfaceSchema dataSanctumInterface = new DataSanctumInterfaceSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public DataExtractorSchema dataExtractor = new DataExtractorSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public FlatteningTntSchema flatteningTnt = new FlatteningTntSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public SolarPanelSchema solarPanel = new SolarPanelSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public TrinityCraftingSchema trinityCrafting = new TrinityCraftingSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    public TrinityDispatchSchema trinityDispatch = new TrinityDispatchSchema();

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
                "Regex power multipliers in pattern=value form.",
                "格式为 pattern=value 的正则表达式功耗倍率。"
        })
        public String[] multipliers = { "minecraft:hopper=1.5", "appeng:.*=2.0" };
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

        public static final String DEFAULT_CROP_INPUT_MAPPINGS = "minecraft:wheat_seeds=minecraft:wheat@0.5,minecraft:beetroot_seeds=minecraft:beetroot@0.5," + "minecraft:melon=minecraft:melon@1.0,minecraft:melon_seeds=minecraft:melon@0.5,minecraft:melon_slice=minecraft:melon@0.5," + "minecraft:pumpkin=minecraft:pumpkin@1.0,minecraft:pumpkin_seeds=minecraft:pumpkin@0.5," + "minecraft:sweet_berries=minecraft:sweet_berries@1.0,minecraft:brown_mushroom=minecraft:brown_mushroom@1.0," + "minecraft:red_mushroom=minecraft:red_mushroom@1.0,minecraft:crimson_fungus=minecraft:crimson_fungus@1.0," + "minecraft:warped_fungus=minecraft:warped_fungus@1.0,minecraft:cactus=minecraft:cactus@1.0," + "minecraft:sugar_cane=minecraft:sugar_cane@1.0,minecraft:bamboo=minecraft:bamboo@1.0," + "minecraft:dandelion=minecraft:dandelion@1.0,minecraft:poppy=minecraft:poppy@1.0," + "minecraft:blue_orchid=minecraft:blue_orchid@1.0,minecraft:allium=minecraft:allium@1.0," + "minecraft:azure_bluet=minecraft:azure_bluet@1.0,minecraft:red_tulip=minecraft:red_tulip@1.0," + "minecraft:orange_tulip=minecraft:orange_tulip@1.0,minecraft:white_tulip=minecraft:white_tulip@1.0," + "minecraft:pink_tulip=minecraft:pink_tulip@1.0,minecraft:oxeye_daisy=minecraft:oxeye_daisy@1.0," + "minecraft:cornflower=minecraft:cornflower@1.0,minecraft:lily_of_the_valley=minecraft:lily_of_the_valley@1.0," + "minecraft:wither_rose=minecraft:wither_rose@1.0,minecraft:sunflower=minecraft:sunflower@1.0," + "minecraft:lilac=minecraft:lilac@1.0,minecraft:rose_bush=minecraft:rose_bush@1.0," + "minecraft:peony=minecraft:peony@1.0,minecraft:pink_petals=minecraft:pink_petals@1.0," + "minecraft:torchflower=minecraft:torchflower@1.0,minecraft:open_eyeblossom=minecraft:open_eyeblossom@1.0," + "minecraft:closed_eyeblossom=minecraft:closed_eyeblossom@1.0";

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
        @Configurable.Comment({ "Comma-separated blocked entity ids.", "逗号分隔的禁用实体 ID。" })
        public String mobDataBlacklist = "";

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Required total amount for an ore data carrier.", "完成矿石数据载体所需的总数量。" })
        @Configurable.DecimalRange(min = 1.0, max = Double.MAX_VALUE)
        public double oreRequiredAmount = 4096.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Comma-separated blocked item ids for ore data.", "逗号分隔的矿物数据禁用物品 ID。" })
        public String oreDataBlacklist = "";

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Required total amount for a crop data carrier.", "完成农作数据载体所需的总数量。" })
        @Configurable.DecimalRange(min = 1.0, max = Double.MAX_VALUE)
        public double cropRequiredAmount = 4096.0D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Comma-separated blocked item ids for crop data.", "逗号分隔的农作数据禁用物品 ID。" })
        public String cropDataBlacklist = "";

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Comma-separated additional allowed crop item ids.", "逗号分隔的额外允许农作物品 ID。" })
        public String cropDataWhitelist = "";

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({
                "Comma-separated mappings in input_item=recorded_crop@progress form.",
                "逗号分隔的映射，格式为 input_item=recorded_crop@progress。"
        })
        public String cropInputMappings = DEFAULT_CROP_INPUT_MAPPINGS;
    }

    public static final class FlatteningTntSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        public ConfigurableTntSchema tntConfigurable = new ConfigurableTntSchema();

        @Configurable(key = Configurable.LocalizationKey.FULL)
        public DataNukeSchema dataNuke = new DataNukeSchema();
    }

    public static final class ConfigurableTntSchema {

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Chunk radius for the cleared area. 1 = 3x3 chunks.", "清除区域的区块半径。1 = 3x3 区块。" })
        @Configurable.Range(min = 0, max = 64)
        public int clearChunkRadius = 1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Vertical offset where clearing starts.", "清除起始位置的垂直偏移。" })
        @Configurable.Range(min = -384, max = 384)
        public int clearStartYOffset = 0;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Number of vertical blocks to clear.", "要清除的垂直方块数量。" })
        @Configurable.Range(min = 1, max = 512)
        public int clearHeight = 25;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Chunk radius for the filled floor. 1 = 3x3 chunks.", "填充地板的区块半径。1 = 3x3 区块。" })
        @Configurable.Range(min = 0, max = 64)
        public int fillChunkRadius = 1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Vertical offset for the filled floor layer.", "填充地板层的垂直偏移。" })
        @Configurable.Range(min = -384, max = 384)
        public int fillYOffset = -1;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Block id used for the filled floor.", "用于填充地板的方块 ID。" })
        public String fillBlock = "minecraft:dirt";

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "X offset applied to the TNT center.", "应用到 TNT 中心的 X 偏移。" })
        @Configurable.Range(min = -512, max = 512)
        public int centerOffsetX = 0;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Y offset applied to the TNT center.", "应用到 TNT 中心的 Y 偏移。" })
        @Configurable.Range(min = -512, max = 512)
        public int centerOffsetY = 0;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Z offset applied to the TNT center.", "应用到 TNT 中心的 Z 偏移。" })
        @Configurable.Range(min = -512, max = 512)
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
        public double speedCardBonusRatio = 0.75D;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Additional AE storage per energy card.", "每张能量卡提供的额外 AE 存储容量。" })
        @Configurable.DecimalRange(min = 0.0, max = Double.MAX_VALUE)
        public double energyCardCapacityBonusAE = 80000.0D;
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
        @Configurable.Comment({ "Bounded Trinity planning worker count.", "Trinity 规划器的有界工作线程数。" })
        @Configurable.Range(min = 1, max = 8)
        @Configurable.UpdateRestriction(UpdateRestrictions.GAME_RESTART)
        public int plannerThreads = recommendedPlannerThreads(Runtime.getRuntime().availableProcessors());

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum queued planning requests.", "允许排队的最大规划请求数。" })
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
        public int hardGridAttempts = 256;

        @Configurable(key = Configurable.LocalizationKey.FULL)
        @Configurable.Comment({ "Maximum attempts per provider and grid tick.", "单个 provider 每个网格 tick 的最大调用次数。" })
        @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
        public int hardProviderAttempts = 16;

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
