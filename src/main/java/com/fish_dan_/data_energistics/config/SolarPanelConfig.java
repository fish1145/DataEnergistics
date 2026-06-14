package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class SolarPanelConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue DAY_GENERATION_AE_PER_TICK = BUILDER
            .comment("Daytime AE generation per tick for the ME Solar Panel.",
                    "ME太阳能板白天每 tick 生成的 AE。")
            .defineInRange("dayGenerationAEPerTick", 3_000.0D, 0.0D, Double.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue NIGHT_GENERATION_AE_PER_TICK = BUILDER
            .comment("Nighttime AE generation per tick for the ME Solar Panel.",
                    "ME太阳能板夜晚每 tick 生成的 AE。")
            .defineInRange("nightGenerationAEPerTick", 1_000.0D, 0.0D, Double.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue SPEED_CARD_BONUS_RATIO = BUILDER
            .comment("Additional generation ratio provided by each installed speed card. 0.75 = +75%.",
                    "每张已安装速度卡提供的额外发电倍率。0.75 表示 +75%。")
            .defineInRange("speedCardBonusRatio", 0.75D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue ENERGY_CARD_CAPACITY_BONUS_AE = BUILDER
            .comment("Additional internal AE storage provided by each installed energy card.",
                    "每张已安装能量卡提供的额外内部 AE 存储容量。")
            .defineInRange("energyCardCapacityBonusAE", 80_000.0D, 0.0D, Double.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static double dayGenerationAEPerTick = 3_000.0D;
    public static double nightGenerationAEPerTick = 1_000.0D;
    public static double speedCardBonusRatio = 0.75D;
    public static double energyCardCapacityBonusAE = 80_000.0D;

    private SolarPanelConfig() {}

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        dayGenerationAEPerTick = DAY_GENERATION_AE_PER_TICK.get();
        nightGenerationAEPerTick = NIGHT_GENERATION_AE_PER_TICK.get();
        speedCardBonusRatio = SPEED_CARD_BONUS_RATIO.get();
        energyCardCapacityBonusAE = ENERGY_CARD_CAPACITY_BONUS_AE.get();
    }
}
