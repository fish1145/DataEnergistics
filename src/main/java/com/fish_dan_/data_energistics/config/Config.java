package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.LegacyConfigBridge;
import com.fish_dan_.data_energistics.util.DataRipperConfigParsingUtils;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final int DATA_SANCTUM_INTERFACE_MAX_CAPACITY_CARDS = 3;
    private static final int DATA_SANCTUM_INTERFACE_MAX_CAPACITY_MULTIPLIER = 1 << DATA_SANCTUM_INTERFACE_MAX_CAPACITY_CARDS;
    private static final int DATA_SANCTUM_INTERFACE_MAX_BASE_CAPACITY = Integer.MAX_VALUE / DATA_SANCTUM_INTERFACE_MAX_CAPACITY_MULTIPLIER;

    private static final ModConfigSpec.IntValue DATA_RIPPER_BASE_COST = BUILDER
            .comment("Base power cost for the data ripper power curve.",
                    "数据撕裂器功耗曲线的基础数据流消耗。")
            .defineInRange("dataRipperBaseCost", 512, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DATA_RIPPER_BLACKLIST = BUILDER
            .comment("Regex patterns for block ids that the data ripper should never accelerate.",
                    "数据撕裂器永远不会加速的方块 ID 正则表达式。")
            .defineList("dataRipperBlacklist", List.of(), () -> "", value -> value instanceof String);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DATA_RIPPER_MULTIPLIERS = BUILDER
            .comment("Regex-based power multipliers for the data ripper, formatted as pattern=value.",
                    "数据撕裂器基于正则表达式的额外消耗倍率，格式为 pattern=value。")
            .defineList("dataRipperMultipliers", List.of(
                    "minecraft:hopper=1.5",
                    "appeng:.*=2.0"), () -> "", value -> value instanceof String);

    private static final ModConfigSpec.IntValue DATA_DISTRIBUTION_TOWER_RANGE = BUILDER
            .comment("Base chunk coverage level for the Data Distribution Tower. 1=1x1 chunk, 2=3x3 chunks, etc.",
                    "数据均分塔的基础区块覆盖等级。1=1x1 区块，2=3x3 区块，依此类推。")
            .defineInRange("dataDistributionTowerRange", 1, 1, 128);

    private static final ModConfigSpec.IntValue DATA_SANCTUM_INTERFACE_ITEM_LIMIT = BUILDER
            .comment("Base stocked item amount per Data Sanctum interface config/stock slot. Each capacity card doubles this value.",
                    "极限承载接口每个配置/库存槽的基础物品储备数量。每张容量卡会使该值翻倍。")
            .defineInRange("dataSanctumInterfaceItemLimit", 2048, 1, DATA_SANCTUM_INTERFACE_MAX_BASE_CAPACITY);

    private static final ModConfigSpec.IntValue DATA_SANCTUM_INTERFACE_FLUID_BUCKETS = BUILDER
            .comment("Base stocked fluid buckets per Data Sanctum interface config/stock slot. Each capacity card doubles this value.",
                    "极限承载接口每个配置/库存槽的基础流体桶数。每张容量卡会使该值翻倍。")
            .defineInRange("dataSanctumInterfaceFluidBuckets", 2048, 1, DATA_SANCTUM_INTERFACE_MAX_BASE_CAPACITY);

    private static final ModConfigSpec.IntValue DATA_SANCTUM_INTERFACE_RETURN_ITEM_LIMIT = BUILDER
            .comment("Base item amount per Data Sanctum interface return slot. Each capacity card doubles this value.",
                    "极限承载接口每个返回槽的基础物品数量。每张容量卡会使该值翻倍。")
            .defineInRange("dataSanctumInterfaceReturnItemLimit", 2048, 1, DATA_SANCTUM_INTERFACE_MAX_BASE_CAPACITY);

    private static final ModConfigSpec.IntValue DATA_SANCTUM_INTERFACE_RETURN_FLUID_BUCKETS = BUILDER
            .comment("Base fluid buckets per Data Sanctum interface return slot. Each capacity card doubles this value.",
                    "极限承载接口每个返回槽的基础流体桶数。每张容量卡会使该值翻倍。")
            .defineInRange("dataSanctumInterfaceReturnFluidBuckets", 2048, 1, DATA_SANCTUM_INTERFACE_MAX_BASE_CAPACITY);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int dataRipperBaseCost = 512;
    public static int dataDistributionTowerRange = 1;
    public static int dataSanctumInterfaceItemLimit = 2048;
    public static int dataSanctumInterfaceFluidBuckets = 2048;
    public static int dataSanctumInterfaceReturnItemLimit = 2048;
    public static int dataSanctumInterfaceReturnFluidBuckets = 2048;
    public static List<String> dataRipperBlacklist = List.of();
    public static List<String> dataRipperMultipliers = List.of("minecraft:hopper=1.5", "appeng:.*=2.0");
    public static List<Pattern> dataRipperBlacklistCompiled = List.of();
    public static List<DataRipperConfigParsingUtils.MultiplierEntry> dataRipperMultipliersCompiled = List.of();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        dataRipperBaseCost = DATA_RIPPER_BASE_COST.get();
        dataDistributionTowerRange = DATA_DISTRIBUTION_TOWER_RANGE.get();
        dataSanctumInterfaceItemLimit = DATA_SANCTUM_INTERFACE_ITEM_LIMIT.get();
        dataSanctumInterfaceFluidBuckets = DATA_SANCTUM_INTERFACE_FLUID_BUCKETS.get();
        dataSanctumInterfaceReturnItemLimit = DATA_SANCTUM_INTERFACE_RETURN_ITEM_LIMIT.get();
        dataSanctumInterfaceReturnFluidBuckets = DATA_SANCTUM_INTERFACE_RETURN_FLUID_BUCKETS.get();
        dataRipperBlacklist = List.copyOf(DATA_RIPPER_BLACKLIST.get().stream().map(String::valueOf).toList());
        dataRipperMultipliers = List.copyOf(DATA_RIPPER_MULTIPLIERS.get().stream().map(String::valueOf).toList());
        dataRipperBlacklistCompiled = DataRipperConfigParsingUtils.precompilePatterns(dataRipperBlacklist);
        dataRipperMultipliersCompiled = DataRipperConfigParsingUtils.precompileMultipliers(dataRipperMultipliers);
        LegacyConfigBridge.refresh();
    }
}
