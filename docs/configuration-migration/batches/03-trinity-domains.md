# 批次 03：Trinity Crafting 与 Dispatch

## 范围

- Trinity Crafting 8 项；
- Trinity Dispatch 14 项；
- 合计 22 个目标 YAML 字段。

两者从同一不可变根快照派生完整 `Settings`，schema 可变字段不得进入规划器、线程池、Governor 或异步任务。

## Crafting

`maxSccKeys`、`maxBindingVariants`、`maxScheduleStates`、`graphRebuildBudgetMs`、`dynamicRetryMaxTicks` 和 `defaultQuantityMode` 在下一次规划读取新快照。

`plannerThreads` 与 `plannerQueueCapacity`：

- 使用 `GAME_RESTART`；
- 线程池只在服务器启动时按当时完整快照创建；
- 运行中 YAML 变化不重建、不并存第二个池，也不宣称已应用；
- 下一次游戏启动使用新值。

`maxBindingVariants` 目标默认 32768；TOML 512 的升级只发生在导入器，YAML 512 仍是有效显式值。

## Dispatch

14 项完整快照继续强制：

- `safeGridAttempts <= hardGridAttempts`；
- `safeProviderAttempts <= hardProviderAttempts`；
- `safeCommitBudgetMs <= hardCommitBudgetMs`；
- `ewmaAlpha` 有限且 `0 < value <= 1`。

根快照修订变化后，在下一次 grid tick 创建新 Governor 并原子替换。重建固定重置：

- warmup 进度；
- EWMA；
- metrics window 聚合；
- transition 连续窗口；
- cooldown；
- safe-hold 历史。

不得逐字段更新 Governor，也不得继承旧统计状态。

## enum

`defaultQuantityMode` 磁盘值保持 `NET_NEW`、`FINAL_TOTAL`。领域快照只携带 enum；客户端本地化显示适配器在批次 04 注册，专用服务器不加载该类。

## 验收

### Crafting 行为审查

- 8 项默认与非默认往返；
- 不同 CPU 数的动态 `plannerThreads`；
- TOML 512 → 32768 与 YAML 512 保留；
- 普通字段下一规划生效；
- 两个线程池字段运行中不重建、重启后生效；
- reload 与规划并发时只使用单一完整 Settings。

### Dispatch 行为审查

- 14 项默认与非默认往返，包括缺失旧值补 `safeRetryBackoffTicks=8`；
- 四条跨字段约束；
- 下一 grid tick 的 Governor 原子替换；
- warmup、EWMA、window、transition、cooldown、safe hold 全部重置；
- 配置变化与 grid tick 并发时不出现混合预算。

### 端到端门禁

两份旧 Trinity TOML → 目标 YAML → Settings 映射一致；仓库原有 crafting 与 Governor 业务测试继续通过，不增加 Configuration 专项测试，也不使用反射。
