package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class FlatteningTntConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final String DEFAULT_CONFIGURABLE_TNT_DISPLAY_NAME = "自定义平地TNT";

    private static final Entry CONFIGURABLE_ENTRY;
    private static final ModConfigSpec.ConfigValue<String> CONFIGURABLE_TNT_DISPLAY_NAME;
    private static final DataNukeEntry DATA_NUKE_ENTRY;

    public static final ModConfigSpec SPEC;

    public static Definition configurableTnt;
    public static String configurableTntDisplayName;
    public static DataNukeDefinition dataNuke;

    static {
        BUILDER.comment("Chunk-flattening TNT settings.",
                "可配置平地TNT设置。",
                "Chunk radius uses the center chunk as 0. Example: 1 = 3x3 chunks, 2 = 5x5 chunks.",
                "区块半径以中心区块为 0。例如：1 = 3x3 区块，2 = 5x5 区块。")
                .push("flatteningTnt");

        CONFIGURABLE_ENTRY = new Entry(BUILDER, "tntConfigurable",
                "Settings for the reserved configurable TNT block.",
                "预留的可配置平地TNT方块设置。",
                1, 0, 25, 1, -1, "minecraft:dirt", 0, 0, 0, false, false);
        CONFIGURABLE_TNT_DISPLAY_NAME = BUILDER
                .comment("Display name shown for the configurable TNT item.",
                        "可配置平地TNT物品显示的名称。")
                .define("tntConfigurable.displayName", DEFAULT_CONFIGURABLE_TNT_DISPLAY_NAME);
        DATA_NUKE_ENTRY = new DataNukeEntry(BUILDER, "dataNuke",
                "Settings for the data nuke TNT block.",
                "数据核弹 TNT 方块设置。",
                1, 2048, 4.0D);
        BUILDER.pop();
        SPEC = BUILDER.build();

        configurableTnt = CONFIGURABLE_ENTRY.resolveDefaults();
        configurableTntDisplayName = DEFAULT_CONFIGURABLE_TNT_DISPLAY_NAME;
        dataNuke = DATA_NUKE_ENTRY.resolveDefaults();
    }

    private FlatteningTntConfig() {}

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        configurableTnt = CONFIGURABLE_ENTRY.resolve("tnt_configurable");
        configurableTntDisplayName = CONFIGURABLE_TNT_DISPLAY_NAME.get();
        dataNuke = DATA_NUKE_ENTRY.resolve();
    }

    public record Definition(
                             int clearChunkRadius,
                             int clearStartYOffset,
                             int clearHeight,
                             int fillChunkRadius,
                             int fillYOffset,
                             BlockState fillBlockState,
                             BlockPos explosionCenterOffset,
                             boolean preserveFluids,
                             boolean replaceUnbreakableBlocks) {}

    public record DataNukeDefinition(
                                     int workIntervalTicks,
                                     int maxRadius,
                                     double centerEntityConsumeRadius) {}

    private static final class Entry {

        private final ModConfigSpec.IntValue clearChunkRadius;
        private final ModConfigSpec.IntValue clearStartYOffset;
        private final ModConfigSpec.IntValue clearHeight;
        private final ModConfigSpec.IntValue fillChunkRadius;
        private final ModConfigSpec.IntValue fillYOffset;
        private final ModConfigSpec.ConfigValue<String> fillBlockId;
        private final ModConfigSpec.IntValue centerOffsetX;
        private final ModConfigSpec.IntValue centerOffsetY;
        private final ModConfigSpec.IntValue centerOffsetZ;
        private final ModConfigSpec.BooleanValue preserveFluids;
        private final ModConfigSpec.BooleanValue replaceUnbreakableBlocks;
        private final int defaultClearChunkRadius;
        private final int defaultClearStartYOffset;
        private final int defaultClearHeight;
        private final int defaultFillChunkRadius;
        private final int defaultFillYOffset;
        private final String defaultFillBlockId;
        private final int defaultCenterOffsetX;
        private final int defaultCenterOffsetY;
        private final int defaultCenterOffsetZ;
        private final boolean defaultPreserveFluids;
        private final boolean defaultReplaceUnbreakableBlocks;

        private Entry(ModConfigSpec.Builder builder, String key, String comment, String chineseComment, int clearChunkRadius,
                      int clearStartYOffset, int clearHeight, int fillChunkRadius, int fillYOffset, String fillBlockId,
                      int centerOffsetX, int centerOffsetY, int centerOffsetZ, boolean preserveFluids,
                      boolean replaceUnbreakableBlocks) {
            this.defaultClearChunkRadius = clearChunkRadius;
            this.defaultClearStartYOffset = clearStartYOffset;
            this.defaultClearHeight = clearHeight;
            this.defaultFillChunkRadius = fillChunkRadius;
            this.defaultFillYOffset = fillYOffset;
            this.defaultFillBlockId = fillBlockId;
            this.defaultCenterOffsetX = centerOffsetX;
            this.defaultCenterOffsetY = centerOffsetY;
            this.defaultCenterOffsetZ = centerOffsetZ;
            this.defaultPreserveFluids = preserveFluids;
            this.defaultReplaceUnbreakableBlocks = replaceUnbreakableBlocks;
            builder.comment(comment, chineseComment).push(key);
            this.clearChunkRadius = builder
                    .comment("Chunk radius for the cleared area. 1 = 3x3 chunks.",
                            "清除区域的区块半径。1 = 3x3 区块。")
                    .defineInRange("clearChunkRadius", clearChunkRadius, 0, 64);
            this.clearStartYOffset = builder
                    .comment("Vertical offset from the TNT position where clearing starts.",
                            "清除起始位置相对 TNT 位置的垂直偏移。")
                    .defineInRange("clearStartYOffset", clearStartYOffset, -384, 384);
            this.clearHeight = builder
                    .comment("Number of vertical blocks to clear.",
                            "要清除的垂直方块数量。")
                    .defineInRange("clearHeight", clearHeight, 1, 512);
            this.fillChunkRadius = builder
                    .comment("Chunk radius for the filled floor area. 1 = 3x3 chunks.",
                            "填充地板区域的区块半径。1 = 3x3 区块。")
                    .defineInRange("fillChunkRadius", fillChunkRadius, 0, 64);
            this.fillYOffset = builder
                    .comment("Vertical offset from the TNT position for the filled floor layer.",
                            "填充地板层相对 TNT 位置的垂直偏移。")
                    .defineInRange("fillYOffset", fillYOffset, -384, 384);
            this.fillBlockId = builder
                    .comment("Block id used for the filled floor layer.",
                            "用于填充地板层的方块 ID。")
                    .define("fillBlock", fillBlockId);
            this.centerOffsetX = builder
                    .comment("X offset applied to the TNT position before calculating clear/fill areas.",
                            "计算清除/填充区域前，对 TNT 位置应用的 X 偏移。")
                    .defineInRange("centerOffsetX", centerOffsetX, -512, 512);
            this.centerOffsetY = builder
                    .comment("Y offset applied to the TNT position before calculating clear/fill areas.",
                            "计算清除/填充区域前，对 TNT 位置应用的 Y 偏移。")
                    .defineInRange("centerOffsetY", centerOffsetY, -512, 512);
            this.centerOffsetZ = builder
                    .comment("Z offset applied to the TNT position before calculating clear/fill areas.",
                            "计算清除/填充区域前，对 TNT 位置应用的 Z 偏移。")
                    .defineInRange("centerOffsetZ", centerOffsetZ, -512, 512);
            this.preserveFluids = builder
                    .comment("If true, water/lava blocks are not removed by the clearing step.",
                            "为 true 时，清除步骤不会移除水/岩浆方块。")
                    .define("preserveFluids", preserveFluids);
            this.replaceUnbreakableBlocks = builder
                    .comment("If true, blocks with negative destroy time can still be removed/replaced.",
                            "为 true 时，破坏时间为负数的方块也可以被移除/替换。")
                    .define("replaceUnbreakableBlocks", replaceUnbreakableBlocks);
            builder.pop();
        }

        private Definition resolve(String id) {
            return new Definition(
                    this.clearChunkRadius.get(),
                    this.clearStartYOffset.get(),
                    this.clearHeight.get(),
                    this.fillChunkRadius.get(),
                    this.fillYOffset.get(),
                    resolveBlockState(id, this.fillBlockId.get(), this.defaultFillBlockId),
                    new BlockPos(this.centerOffsetX.get(), this.centerOffsetY.get(), this.centerOffsetZ.get()),
                    this.preserveFluids.get(),
                    this.replaceUnbreakableBlocks.get());
        }

        private Definition resolveDefaults() {
            return new Definition(
                    this.defaultClearChunkRadius,
                    this.defaultClearStartYOffset,
                    this.defaultClearHeight,
                    this.defaultFillChunkRadius,
                    this.defaultFillYOffset,
                    resolveBlockState("default", this.defaultFillBlockId, this.defaultFillBlockId),
                    new BlockPos(this.defaultCenterOffsetX, this.defaultCenterOffsetY, this.defaultCenterOffsetZ),
                    this.defaultPreserveFluids,
                    this.defaultReplaceUnbreakableBlocks);
        }

        private static BlockState resolveBlockState(String tntId, String configuredId, String fallbackId) {
            BlockState fallback = getBlockStateOrFallback(fallbackId, Blocks.DIRT.defaultBlockState());
            ResourceLocation location = ResourceLocation.tryParse(configuredId);
            if (location == null) {
                LOGGER.warn("Invalid fill block '{}' for {}. Falling back to '{}'.", configuredId, tntId, fallbackId);
                return fallback;
            }

            var block = BuiltInRegistries.BLOCK.get(location);
            if (block == Blocks.AIR) {
                LOGGER.warn("Unknown fill block '{}' for {}. Falling back to '{}'.", configuredId, tntId, fallbackId);
                return fallback;
            }

            return block.defaultBlockState();
        }

        private static BlockState getBlockStateOrFallback(String blockId, BlockState fallback) {
            ResourceLocation location = ResourceLocation.tryParse(blockId);
            if (location == null) {
                return fallback;
            }

            var block = BuiltInRegistries.BLOCK.get(location);
            return block == Blocks.AIR ? fallback : block.defaultBlockState();
        }
    }

    private static final class DataNukeEntry {

        private final ModConfigSpec.IntValue workIntervalTicks;
        private final ModConfigSpec.IntValue maxRadius;
        private final ModConfigSpec.DoubleValue centerEntityConsumeRadius;
        private final int defaultWorkIntervalTicks;
        private final int defaultMaxRadius;
        private final double defaultCenterEntityConsumeRadius;

        private DataNukeEntry(ModConfigSpec.Builder builder, String key, String comment, String chineseComment,
                              int workIntervalTicks, int maxRadius, double centerEntityConsumeRadius) {
            this.defaultWorkIntervalTicks = workIntervalTicks;
            this.defaultMaxRadius = maxRadius;
            this.defaultCenterEntityConsumeRadius = centerEntityConsumeRadius;
            builder.comment(comment, chineseComment).push(key);
            this.workIntervalTicks = builder
                    .comment("Server ticks between each block-devouring work cycle.",
                            "每次吞噬方块工作周期之间的服务器 tick 间隔。")
                    .defineInRange("workIntervalTicks", workIntervalTicks, 1, 1200);
            this.maxRadius = builder
                    .comment("Maximum spherical radius in blocks that the data nuke can devour. Default 2048 = 128 chunks.",
                            "数据核弹可吞噬的最大球形半径（方块）。默认 2048 = 128 区块。")
                    .defineInRange("maxRadius", maxRadius, 1, 8192);
            this.centerEntityConsumeRadius = builder
                    .comment("Entity-devouring radius around the center checked every tick.",
                            "每 tick 检查一次的中心实体吞噬半径。")
                    .defineInRange("centerEntityConsumeRadius", centerEntityConsumeRadius, 0.0D, 128.0D);
            builder.pop();
        }

        private DataNukeDefinition resolve() {
            return new DataNukeDefinition(
                    this.workIntervalTicks.get(),
                    this.maxRadius.get(),
                    this.centerEntityConsumeRadius.get());
        }

        private DataNukeDefinition resolveDefaults() {
            return new DataNukeDefinition(
                    this.defaultWorkIntervalTicks,
                    this.defaultMaxRadius,
                    this.defaultCenterEntityConsumeRadius);
        }
    }
}
