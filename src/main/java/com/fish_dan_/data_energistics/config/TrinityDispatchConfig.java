package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchGovernorSettings;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.concurrent.TimeUnit;

/**
 * Owns the independent Phase 5 dispatch-governor policy.
 *
 * <p>
 * One immutable {@link Settings} snapshot prevents config reloads from exposing mixed hard and safe budgets to a
 * grid. Planning limits intentionally remain in {@link TrinityCraftingConfig}.
 * </p>
 */
@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class TrinityDispatchConfig {

    public static final int DEFAULT_HARD_GRID_ATTEMPTS = 256;
    public static final int DEFAULT_HARD_PROVIDER_ATTEMPTS = 16;
    public static final int DEFAULT_HARD_COMMIT_BUDGET_MS = 30;
    public static final int DEFAULT_SAFE_GRID_ATTEMPTS = 16;
    public static final int DEFAULT_SAFE_PROVIDER_ATTEMPTS = 2;
    public static final int DEFAULT_SAFE_COMMIT_BUDGET_MS = 2;
    public static final int DEFAULT_SAFE_ACTOR_PERMITS = 1;
    public static final int DEFAULT_SAFE_RETRY_BACKOFF_TICKS = 8;
    public static final int DEFAULT_WARMUP_TICKS = 200;
    public static final int DEFAULT_METRICS_WINDOW_TICKS = 20;
    public static final double DEFAULT_EWMA_ALPHA = 0.25D;
    public static final int DEFAULT_TRANSITION_WINDOWS = 3;
    public static final int DEFAULT_COOLDOWN_TICKS = 60;
    public static final int DEFAULT_SAFE_HOLD_TICKS = 200;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue HARD_GRID_ATTEMPTS = BUILDER
            .comment("Maximum physical provider attempts per grid tick.",
                    "每个网格每 tick 允许的最大 provider 物理调用次数。")
            .defineInRange("hardGridAttempts", DEFAULT_HARD_GRID_ATTEMPTS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue HARD_PROVIDER_ATTEMPTS = BUILDER
            .comment("Maximum physical attempts assigned to one provider per grid tick.",
                    "每个网格 tick 内单个 provider 允许的最大物理调用次数。")
            .defineInRange("hardProviderAttempts", DEFAULT_HARD_PROVIDER_ATTEMPTS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue HARD_COMMIT_BUDGET_MS = BUILDER
            .comment("Maximum server-thread dispatch commit time per grid tick, in milliseconds.",
                    "每个网格 tick 的服务器线程发配提交时间上限，单位毫秒。")
            .defineInRange("hardCommitBudgetMs", DEFAULT_HARD_COMMIT_BUDGET_MS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SAFE_GRID_ATTEMPTS = BUILDER
            .comment("Synchronous SAFE-mode physical provider attempts per grid tick.",
                    "SAFE 同步模式下每个网格每 tick 的 provider 物理调用次数。")
            .defineInRange("safeGridAttempts", DEFAULT_SAFE_GRID_ATTEMPTS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SAFE_PROVIDER_ATTEMPTS = BUILDER
            .comment("Synchronous SAFE-mode physical attempts per provider and grid tick.",
                    "SAFE 同步模式下每个 provider 每个网格 tick 的物理调用次数。")
            .defineInRange("safeProviderAttempts", DEFAULT_SAFE_PROVIDER_ATTEMPTS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SAFE_COMMIT_BUDGET_MS = BUILDER
            .comment("Synchronous SAFE-mode server-thread commit budget, in milliseconds.",
                    "SAFE 同步模式下服务器线程提交预算，单位毫秒。")
            .defineInRange("safeCommitBudgetMs", DEFAULT_SAFE_COMMIT_BUDGET_MS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SAFE_ACTOR_PERMITS = BUILDER
            .comment("Per-grid outstanding proposal permits retained by SAFE policy.",
                    "SAFE 策略保留的单网格 outstanding proposal permit 数量。")
            .defineInRange("safeActorPermits", DEFAULT_SAFE_ACTOR_PERMITS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SAFE_RETRY_BACKOFF_TICKS = BUILDER
            .comment("Maximum adaptive retry backoff and the synchronous SAFE-mode retry delay, in ticks.",
                    "自适应重试退避上限及 SAFE 同步模式重试延迟，单位 tick。")
            .defineInRange("safeRetryBackoffTicks", DEFAULT_SAFE_RETRY_BACKOFF_TICKS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue WARMUP_TICKS = BUILDER
            .comment("Observation ticks collected before adaptive decisions are allowed.",
                    "允许自适应决策前收集指标的观察 tick 数量。")
            .defineInRange("warmupTicks", DEFAULT_WARMUP_TICKS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue METRICS_WINDOW_TICKS = BUILDER
            .comment("Ticks aggregated into one dispatch-governor metrics window.",
                    "每个派发 Governor 指标窗口聚合的 tick 数量。")
            .defineInRange("metricsWindowTicks", DEFAULT_METRICS_WINDOW_TICKS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue EWMA_ALPHA = BUILDER
            .comment("EWMA alpha used for complete server tick duration.",
                    "完整服务器 tick 耗时 EWMA 使用的 alpha。")
            .defineInRange("ewmaAlpha", DEFAULT_EWMA_ALPHA, Double.MIN_NORMAL, 1.0D);

    private static final ModConfigSpec.IntValue TRANSITION_WINDOWS = BUILDER
            .comment("Consecutive windows required before a governor state or budget transition.",
                    "Governor 状态或预算切换前要求连续满足条件的窗口数。")
            .defineInRange("transitionWindows", DEFAULT_TRANSITION_WINDOWS, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue COOLDOWN_TICKS = BUILDER
            .comment("Ticks retained after a budget adjustment before another adjustment.",
                    "预算调整后再次调整前保留的 cooldown tick 数量。")
            .defineInRange("cooldownTicks", DEFAULT_COOLDOWN_TICKS, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SAFE_HOLD_TICKS = BUILDER
            .comment("Ticks retained in SAFE mode before returning to observation.",
                    "SAFE 模式返回观察状态前保持的 tick 数量。")
            .defineInRange("safeHoldTicks", DEFAULT_SAFE_HOLD_TICKS, 1, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Settings current = Settings.defaults();

    private TrinityDispatchConfig() {}

    /**
     * @return one internally consistent dispatch-governor config snapshot
     */
    public static Settings settings() {
        return current;
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        current = new Settings(
                HARD_GRID_ATTEMPTS.get(),
                HARD_PROVIDER_ATTEMPTS.get(),
                HARD_COMMIT_BUDGET_MS.get(),
                SAFE_GRID_ATTEMPTS.get(),
                SAFE_PROVIDER_ATTEMPTS.get(),
                SAFE_COMMIT_BUDGET_MS.get(),
                SAFE_ACTOR_PERMITS.get(),
                SAFE_RETRY_BACKOFF_TICKS.get(),
                WARMUP_TICKS.get(),
                METRICS_WINDOW_TICKS.get(),
                EWMA_ALPHA.get(),
                TRANSITION_WINDOWS.get(),
                COOLDOWN_TICKS.get(),
                SAFE_HOLD_TICKS.get());
    }

    /**
     * Immutable config consumed when a per-grid Governor is created or reloaded.
     */
    public record Settings(
                           int hardGridAttempts,
                           int hardProviderAttempts,
                           int hardCommitBudgetMs,
                           int safeGridAttempts,
                           int safeProviderAttempts,
                           int safeCommitBudgetMs,
                           int safeActorPermits,
                           int safeRetryBackoffTicks,
                           int warmupTicks,
                           int metricsWindowTicks,
                           double ewmaAlpha,
                           int transitionWindows,
                           int cooldownTicks,
                           int safeHoldTicks) {

        public Settings {
            if (hardGridAttempts <= 0 || hardProviderAttempts <= 0 || hardCommitBudgetMs <= 0 ||
                    safeGridAttempts <= 0 || safeProviderAttempts <= 0 || safeCommitBudgetMs <= 0 ||
                    safeActorPermits <= 0 || safeRetryBackoffTicks <= 0 || warmupTicks <= 0 || metricsWindowTicks <= 0 ||
                    transitionWindows <= 0 || cooldownTicks < 0 || safeHoldTicks <= 0) {
                throw new IllegalArgumentException("Trinity dispatch governor integer settings are out of range");
            }
            if (!Double.isFinite(ewmaAlpha) || ewmaAlpha <= 0.0D || ewmaAlpha > 1.0D) {
                throw new IllegalArgumentException("Trinity dispatch EWMA alpha must be in (0, 1]");
            }
            if (safeGridAttempts > hardGridAttempts ||
                    safeProviderAttempts > hardProviderAttempts ||
                    safeCommitBudgetMs > hardCommitBudgetMs) {
                throw new IllegalArgumentException("Trinity dispatch SAFE budgets must not exceed hard budgets");
            }
        }

        /**
         * @return validated pure-logic settings used by one per-grid Governor
         */
        public CraftingDispatchGovernorSettings governorSettings() {
            CraftingDispatchBudget hardBudget = new CraftingDispatchBudget(
                    new CraftingDispatchLimits(
                            this.hardGridAttempts,
                            this.hardProviderAttempts,
                            TimeUnit.MILLISECONDS.toNanos(this.hardCommitBudgetMs)),
                    DispatchProposalLimits.DEFAULT_PER_GRID_OUTSTANDING,
                    this.hardProviderAttempts,
                    DispatchProposalLimits.DEFAULT_QUEUE_CAPACITY,
                    1,
                    true);
            CraftingDispatchBudget safeBudget = new CraftingDispatchBudget(
                    new CraftingDispatchLimits(
                            this.safeGridAttempts,
                            this.safeProviderAttempts,
                            TimeUnit.MILLISECONDS.toNanos(this.safeCommitBudgetMs)),
                    this.safeActorPermits,
                    this.safeProviderAttempts,
                    this.safeActorPermits,
                    this.safeRetryBackoffTicks,
                    false);
            return CraftingDispatchGovernorSettings.defaults(
                    hardBudget,
                    safeBudget,
                    this.warmupTicks,
                    this.metricsWindowTicks,
                    this.ewmaAlpha,
                    this.transitionWindows,
                    this.cooldownTicks,
                    this.safeHoldTicks);
        }

        public static Settings defaults() {
            return new Settings(
                    DEFAULT_HARD_GRID_ATTEMPTS,
                    DEFAULT_HARD_PROVIDER_ATTEMPTS,
                    DEFAULT_HARD_COMMIT_BUDGET_MS,
                    DEFAULT_SAFE_GRID_ATTEMPTS,
                    DEFAULT_SAFE_PROVIDER_ATTEMPTS,
                    DEFAULT_SAFE_COMMIT_BUDGET_MS,
                    DEFAULT_SAFE_ACTOR_PERMITS,
                    DEFAULT_SAFE_RETRY_BACKOFF_TICKS,
                    DEFAULT_WARMUP_TICKS,
                    DEFAULT_METRICS_WINDOW_TICKS,
                    DEFAULT_EWMA_ALPHA,
                    DEFAULT_TRANSITION_WINDOWS,
                    DEFAULT_COOLDOWN_TICKS,
                    DEFAULT_SAFE_HOLD_TICKS);
        }
    }
}
