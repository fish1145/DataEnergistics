package com.fish_dan_.data_energistics;

import com.fish_dan_.data_energistics.util.DataRipperConfigParsingUtils;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = Data_Energistics.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue DATA_RIPPER_BASE_COST = BUILDER.comment("Base power cost for the data ripper power curve.")
            .defineInRange("dataRipperBaseCost", 512, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DATA_RIPPER_BLACKLIST = BUILDER
            .comment("Regex patterns for block ids that the data ripper should never accelerate.")
            .defineListAllowEmpty("dataRipperBlacklist", List.of(), value -> value instanceof String);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DATA_RIPPER_MULTIPLIERS = BUILDER
            .comment("Regex-based power multipliers for the data ripper, formatted as pattern=value.")
            .defineListAllowEmpty("dataRipperMultipliers", List.of(), value -> value instanceof String);

    private static final ModConfigSpec.IntValue DATA_DISTRIBUTION_TOWER_RANGE = BUILDER
            .comment("Base chunk coverage level for the Data Distribution Tower. 1=1x1 chunk, 2=3x3 chunks, etc.")
            .defineInRange("dataDistributionTowerRange", 1, 1, 128);

    private static final ModConfigSpec.IntValue DATA_DISTRIBUTION_TOWER_TRANSFER_PER_TICK = BUILDER
            .comment("Maximum FE transferred per tick by a Data Distribution Tower network.")
            .defineInRange("dataDistributionTowerTransferPerTick", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int dataRipperBaseCost;
    public static int dataDistributionTowerRange;
    public static int dataDistributionTowerTransferPerTick;
    public static List<String> dataRipperBlacklist;
    public static List<String> dataRipperMultipliers;
    public static List<Pattern> dataRipperBlacklistCompiled = List.of();
    public static List<DataRipperConfigParsingUtils.MultiplierEntry> dataRipperMultipliersCompiled = List.of();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        dataRipperBaseCost = DATA_RIPPER_BASE_COST.get();
        dataDistributionTowerRange = DATA_DISTRIBUTION_TOWER_RANGE.get();
        dataDistributionTowerTransferPerTick = DATA_DISTRIBUTION_TOWER_TRANSFER_PER_TICK.get();
        dataRipperBlacklist = List.copyOf(DATA_RIPPER_BLACKLIST.get().stream().map(String::valueOf).toList());
        dataRipperMultipliers = List.copyOf(DATA_RIPPER_MULTIPLIERS.get().stream().map(String::valueOf).toList());
        dataRipperBlacklistCompiled = DataRipperConfigParsingUtils.precompilePatterns(dataRipperBlacklist);
        dataRipperMultipliersCompiled = DataRipperConfigParsingUtils.precompileMultipliers(dataRipperMultipliers);
    }
}
