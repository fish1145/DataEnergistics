package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Owns deterministic planning-complexity and dynamic-input budgets used by Trinity crafting.
 *
 * <p>
 * The values live in a dedicated COMMON file so planning policy can evolve without expanding the legacy global
 * configuration object. Callers consume one immutable {@link Settings} snapshot and therefore cannot observe a
 * partially reloaded configuration.
 * </p>
 */
@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class TrinityCraftingConfig {

    /** Default maximum number of keys solved inside one strongly connected component. */
    public static final int DEFAULT_MAX_SCC_KEYS = 64;
    /** Default maximum number of legal input-binding variants materialized per request. */
    public static final int DEFAULT_MAX_BINDING_VARIANTS = 32_768;
    /** Previous default migrated because it incorrectly rejected ordinary graphs at their 513th pattern. */
    private static final int LEGACY_MAX_BINDING_VARIANTS = 512;
    /** Default upper bound for compressed scheduling-search states. */
    public static final int DEFAULT_MAX_SCHEDULE_STATES = 500_000;
    /** Default server-thread graph rebuild budget per tick, in milliseconds. */
    public static final int DEFAULT_GRAPH_REBUILD_BUDGET_MS = 4;
    /** Default capacity of the shared bounded planning queue. */
    public static final int DEFAULT_PLANNER_QUEUE_CAPACITY = 128;
    /** Default maximum exponential backoff for dynamic input retries, in ticks. */
    public static final int DEFAULT_DYNAMIC_RETRY_MAX_TICKS = 200;
    /** Default quantity interpretation used by machine and external requests. */
    public static final CraftingQuantityMode DEFAULT_QUANTITY_MODE = CraftingQuantityMode.NET_NEW;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue MAX_SCC_KEYS = BUILDER
            .comment("Maximum AEKey count accepted in one strongly connected planning component.",
                    "单个强连通规划分量允许的最大 AEKey 数量。")
            .defineInRange("maxSccKeys", DEFAULT_MAX_SCC_KEYS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MAX_BINDING_VARIANTS = BUILDER
            .comment("Maximum legal input-binding variants materialized for one planning request.",
                    "单次规划请求允许展开的最大合法输入绑定变体数。")
            .defineInRange("maxBindingVariants", DEFAULT_MAX_BINDING_VARIANTS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MAX_SCHEDULE_STATES = BUILDER
            .comment("Maximum compressed scheduling states explored after a cyclic integer solution.",
                    "循环整数解完成后允许搜索的最大压缩排程状态数。")
            .defineInRange("maxScheduleStates", DEFAULT_MAX_SCHEDULE_STATES, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue GRAPH_REBUILD_BUDGET_MS = BUILDER
            .comment("Per-tick server-thread budget for rebuilding the immutable crafting graph.",
                    "服务器线程每 tick 重建不可变合成图的预算，单位毫秒。")
            .defineInRange("graphRebuildBudgetMs", DEFAULT_GRAPH_REBUILD_BUDGET_MS, 1, Integer.MAX_VALUE);

    private static final int DEFAULT_PLANNER_THREADS = recommendedPlannerThreads(
            Runtime.getRuntime().availableProcessors());

    private static final ModConfigSpec.IntValue PLANNER_THREADS = BUILDER
            .comment("Bounded Trinity planning worker count.",
                    "Trinity 规划器的有界工作线程数。")
            .defineInRange("plannerThreads", DEFAULT_PLANNER_THREADS, 1, 8);

    private static final ModConfigSpec.IntValue PLANNER_QUEUE_CAPACITY = BUILDER
            .comment("Maximum queued Trinity planning requests before fail-fast fallback to AE2.",
                    "触发快速回退 AE2 前允许排队的 Trinity 规划请求数。")
            .defineInRange("plannerQueueCapacity", DEFAULT_PLANNER_QUEUE_CAPACITY, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue DYNAMIC_RETRY_MAX_TICKS = BUILDER
            .comment("Maximum exponential backoff between dynamic-input retries, in ticks.",
                    "动态输入重试指数退避的最大 tick 间隔。")
            .defineInRange("dynamicRetryMaxTicks", DEFAULT_DYNAMIC_RETRY_MAX_TICKS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.EnumValue<CraftingQuantityMode> DEFAULT_QUANTITY = BUILDER
            .comment("Default quantity semantics for machine and external crafting requests.",
                    "机器和外部合成请求使用的默认数量语义。")
            .defineEnum("defaultQuantityMode", DEFAULT_QUANTITY_MODE);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Settings current = Settings.defaults(Runtime.getRuntime().availableProcessors());

    private TrinityCraftingConfig() {}

    /**
     * @return one internally consistent snapshot of every currently loaded Trinity planning setting
     */
    public static Settings settings() {
        return current;
    }

    /**
     * Computes the large-modpack default without overcommitting small hosts.
     *
     * @param availableProcessors positive runtime processor count
     * @return {@code max(1, min(8, availableProcessors / 2))}
     */
    static int recommendedPlannerThreads(int availableProcessors) {
        if (availableProcessors <= 0) {
            throw new IllegalArgumentException("Available processor count must be positive");
        }
        return Math.clamp(availableProcessors / 2, 1, 8);
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        int configuredBindingVariants = MAX_BINDING_VARIANTS.get();
        int maxBindingVariants = migrateBindingVariantLimit(configuredBindingVariants);
        if (maxBindingVariants != configuredBindingVariants) {
            MAX_BINDING_VARIANTS.set(maxBindingVariants);
            var loadedConfig = event.getConfig().getLoadedConfig();
            if (loadedConfig == null) {
                throw new IllegalStateException("Loaded Trinity crafting config is unavailable during migration");
            }
            loadedConfig.save();
            Data_Energistics.LOGGER.info(
                    "Migrated Trinity maxBindingVariants from {} to {}",
                    LEGACY_MAX_BINDING_VARIANTS,
                    maxBindingVariants);
        }
        current = new Settings(
                MAX_SCC_KEYS.get(),
                maxBindingVariants,
                MAX_SCHEDULE_STATES.get(),
                GRAPH_REBUILD_BUDGET_MS.get(),
                PLANNER_THREADS.get(),
                PLANNER_QUEUE_CAPACITY.get(),
                DYNAMIC_RETRY_MAX_TICKS.get(),
                DEFAULT_QUANTITY.get());
    }

    static int migrateBindingVariantLimit(int configuredLimit) {
        return configuredLimit == LEGACY_MAX_BINDING_VARIANTS ? DEFAULT_MAX_BINDING_VARIANTS : configuredLimit;
    }

    /**
     * Immutable configuration consumed by graph capture, solver and execution components.
     *
     * @param maxSccKeys           largest accepted SCC key count
     * @param maxBindingVariants   largest materialized input-binding set
     * @param maxScheduleStates    compressed scheduling search bound
     * @param graphRebuildBudgetMs per-tick graph capture budget
     * @param plannerThreads       bounded planning worker count
     * @param plannerQueueCapacity bounded pending-request capacity
     * @param dynamicRetryMaxTicks maximum missing-input retry backoff
     * @param defaultQuantityMode  quantity semantics for non-player requests
     */
    public record Settings(
                           int maxSccKeys,
                           int maxBindingVariants,
                           int maxScheduleStates,
                           int graphRebuildBudgetMs,
                           int plannerThreads,
                           int plannerQueueCapacity,
                           int dynamicRetryMaxTicks,
                           CraftingQuantityMode defaultQuantityMode) {

        /** Rejects invalid programmatic settings before they can disable a planner bound. */
        public Settings {
            if (maxSccKeys <= 0 || maxBindingVariants <= 0 || maxScheduleStates <= 0 ||
                    graphRebuildBudgetMs <= 0 || plannerThreads <= 0 || plannerThreads > 8 ||
                    plannerQueueCapacity <= 0 || dynamicRetryMaxTicks <= 0) {
                throw new IllegalArgumentException("Trinity crafting budgets must be positive and use at most 8 workers");
            }
            if (defaultQuantityMode == null) {
                throw new IllegalArgumentException("Default Trinity quantity mode is required");
            }
        }

        /**
         * Creates the documented large-modpack defaults for a concrete processor count.
         *
         * @param availableProcessors positive runtime processor count
         * @return validated default settings
         */
        public static Settings defaults(int availableProcessors) {
            return new Settings(
                    DEFAULT_MAX_SCC_KEYS,
                    DEFAULT_MAX_BINDING_VARIANTS,
                    DEFAULT_MAX_SCHEDULE_STATES,
                    DEFAULT_GRAPH_REBUILD_BUDGET_MS,
                    recommendedPlannerThreads(availableProcessors),
                    DEFAULT_PLANNER_QUEUE_CAPACITY,
                    DEFAULT_DYNAMIC_RETRY_MAX_TICKS,
                    DEFAULT_QUANTITY_MODE);
        }
    }
}
