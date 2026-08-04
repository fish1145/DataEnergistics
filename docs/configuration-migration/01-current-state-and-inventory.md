# 01：当前状态与字段清单

## 基线与加载链

实现基线固定为 `1.21@ae910ee2`。旧链路为：

`Data_Energistics` 构造 → `CommonBootstrap.init` → 旧 `ConfigHolder.init` → 注册六份 COMMON `ModConfigSpec` → `ModConfigEvent` → 静态值或 `Settings`。

旧文件：

| 领域 | 旧文件 |
| --- | --- |
| 通用 | `data_energistics-common.toml` |
| Flattening TNT | `data_energistics-tnt.toml` |
| Data Extractor | `data_energistics-data_extractor.toml` |
| Solar Panel | `data_energistics-solar_panel.toml` |
| Trinity Crafting | `data_energistics-trinity_crafting.toml` |
| Trinity Dispatch | `data_energistics-trinity_dispatch.toml` |

导入器按下面的历史兼容清单识别 64 个 TOML 输入；目标 YAML 清单为 63 个叶字段。两者不得再混称为同一计数。

## 计数对照

| 领域 | 旧 TOML 输入 | 目标 YAML | 差异 |
| --- | ---: | ---: | --- |
| 通用 | 8 | 8 | 全部迁移 |
| Data Extractor | 15 | 15 | 全部迁移 |
| Flattening TNT / Data Nuke | 15 | 14 | 丢弃历史 `displayName` |
| Solar Panel | 4 | 4 | 全部迁移 |
| Trinity Crafting | 9 | 8 | 忽略历史 `mipTimeoutMs` |
| Trinity Dispatch | 13 | 14 | 新增 `safeRetryBackoffTicks=8` |
| 合计 | 64 | 63 | 删除两个历史字段，增加一个目标字段 |

## 通用：8 → 8

| 旧字段 | 目标路径 | 类型 | 默认值 | 范围或格式 |
| --- | --- | --- | --- | --- |
| dataRipperBaseCost | dataRipper.baseCost | int | 512 | 1..MAX |
| dataRipperBlacklist | dataRipper.blacklist | 字符串列表 | 空 | ResourceLocation 或规则字符串 |
| dataRipperMultipliers | dataRipper.multipliers | 字符串列表 | minecraft:hopper=1.5、appeng:.*=2.0 | pattern=multiplier |
| dataDistributionTowerRange | dataDistributionTower.range | int | 1 | 1..128 |
| dataSanctumInterfaceItemLimit | dataSanctumInterface.itemLimit | int | 2048 | 1..268435455 |
| dataSanctumInterfaceFluidBuckets | dataSanctumInterface.fluidBuckets | int | 2048 | 1..268435455 |
| dataSanctumInterfaceReturnItemLimit | dataSanctumInterface.returnItemLimit | int | 2048 | 1..268435455 |
| dataSanctumInterfaceReturnFluidBuckets | dataSanctumInterface.returnFluidBuckets | int | 2048 | 1..268435455 |

`dataRipperMultipliers` 在快照构造时解析并预编译正则；格式、有限数值和正则错误都必须带条目索引失败。

## Data Extractor：15 → 15

目标前缀统一为 `dataExtractor`，叶字段名保持不变。

| 字段 | 类型 | 默认值 | 范围或格式 |
| --- | --- | --- | --- |
| baseDamage | int | 5 | 0..MAX |
| workIntervalSeconds | int | 5 | 1..MAX，秒 |
| baseDataFlowPerCycle | int | 100 | 0..MAX |
| dataFlowPerSwordDamage | int | 20 | 0..MAX |
| baseTargetLimit | int | 20 | 1..MAX |
| targetLimitPerCapacityCard | int | 5 | 0..MAX |
| extraTargetDataFlowMultiplier | double | 0.25 | 0..Double.MAX_VALUE，有限 |
| mobRequiredDamage | double schema、float 消费 | 1024 | 1..Double.MAX_VALUE，须可窄化 |
| mobDataBlacklist | CSV 字符串 | 空 | 实体 ID |
| oreRequiredAmount | double schema、float 消费 | 4096 | 1..Double.MAX_VALUE，须可窄化 |
| oreDataBlacklist | CSV 字符串 | 空 | 方块或标签 |
| cropRequiredAmount | double schema、float 消费 | 4096 | 1..Double.MAX_VALUE，须可窄化 |
| cropDataBlacklist | CSV 字符串 | 空 | 作物 ID |
| cropDataWhitelist | CSV 字符串 | 空 | 作物 ID |
| cropInputMappings | 映射字符串 | 36 条内置映射 | input_item=recorded_crop@progress |

规则 JSON 中的作物映射与本表的配置字符串含义不同；两者均保留，但规则 JSON 使用独立的 v1 迁移流程。

## Flattening TNT / Data Nuke：15 → 14

### 目标保留的 14 项

| 目标路径 | 类型 | 默认值 | 范围或格式 |
| --- | --- | --- | --- |
| flatteningTnt.tntConfigurable.clearChunkRadius | int | 1 | 0..64 |
| flatteningTnt.tntConfigurable.clearStartYOffset | int | 0 | -384..384 |
| flatteningTnt.tntConfigurable.clearHeight | int | 25 | 1..512 |
| flatteningTnt.tntConfigurable.fillChunkRadius | int | 1 | 0..64 |
| flatteningTnt.tntConfigurable.fillYOffset | int | -1 | -384..384 |
| flatteningTnt.tntConfigurable.fillBlock | string | minecraft:dirt | 有效且已注册的方块 ID |
| flatteningTnt.tntConfigurable.centerOffsetX | int | 0 | -512..512 |
| flatteningTnt.tntConfigurable.centerOffsetY | int | 0 | -512..512 |
| flatteningTnt.tntConfigurable.centerOffsetZ | int | 0 | -512..512 |
| flatteningTnt.tntConfigurable.preserveFluids | boolean | false | 开关 |
| flatteningTnt.tntConfigurable.replaceUnbreakableBlocks | boolean | false | 高风险开关 |
| flatteningTnt.dataNuke.workIntervalTicks | int | 1 | 1..1200 |
| flatteningTnt.dataNuke.maxRadius | int | 2048 | 1..8192 |
| flatteningTnt.dataNuke.centerEntityConsumeRadius | double | 4.0 | 0..128，有限 |

### 历史输入

`flatteningTnt.tntConfigurable.displayName` 是第 15 个旧 TOML 输入。导入器必须识别并丢弃它，记录改用 `block.data_energistics.tnt_configurable` 的警告。目标 schema 不声明该字段；无消费者后删除 `ConfigurableTntBlockItem`。

## Solar Panel：4 → 4

目标前缀为 `solarPanel`。

| 字段 | 类型 | 默认值 | 范围 |
| --- | --- | --- | --- |
| dayGenerationAEPerTick | double | 3000 | 0..Double.MAX_VALUE，有限 |
| nightGenerationAEPerTick | double | 1000 | 0..Double.MAX_VALUE，有限 |
| speedCardBonusRatio | double | 0.75 | 0..1000，有限 |
| energyCardCapacityBonusAE | double | 80000 | 0..Double.MAX_VALUE，有限 |

## Trinity Crafting：目标 8 项

目标前缀为 `trinityCrafting`。

| 目标字段 | 类型 | 目标默认值 | 范围或值 |
| --- | --- | --- | --- |
| maxSccKeys | int | 64 | 1..MAX |
| maxBindingVariants | int | 32768 | 1..MAX |
| maxScheduleStates | int | 500000 | 1..MAX |
| graphRebuildBudgetMs | int | 4 | 1..MAX，毫秒 |
| plannerThreads | int | clamp(CPU / 2, 1, 8) | 1..8，GAME_RESTART |
| plannerQueueCapacity | int | 128 | 1..MAX，GAME_RESTART |
| dynamicRetryMaxTicks | int | 200 | 1..MAX，tick |
| defaultQuantityMode | enum | NET_NEW | NET_NEW、FINAL_TOTAL |

旧 TOML 的 `mipTimeoutMs` 是第 9 个历史输入，只识别并忽略。旧 TOML 的 `maxBindingVariants=512` 升级为 32768；其他显式正整数保持。该特殊规则只针对 TOML 来源，YAML 中显式写 512 必须保留。

## Trinity Dispatch：目标 14 项

目标前缀为 `trinityDispatch`。

| 字段 | 类型 | 默认值 | 范围 |
| --- | --- | --- | --- |
| hardGridAttempts | int | 256 | 1..MAX |
| hardProviderAttempts | int | 16 | 1..MAX |
| hardCommitBudgetMs | int | 30 | 1..MAX |
| safeGridAttempts | int | 16 | 1..MAX |
| safeProviderAttempts | int | 2 | 1..MAX |
| safeCommitBudgetMs | int | 2 | 1..MAX |
| safeActorPermits | int | 1 | 1..MAX |
| safeRetryBackoffTicks | int | 8 | 1..MAX |
| warmupTicks | int | 200 | 1..MAX |
| metricsWindowTicks | int | 20 | 1..MAX |
| ewmaAlpha | double | 0.25 | 0 < value <= 1，有限 |
| transitionWindows | int | 3 | 1..MAX |
| cooldownTicks | int | 60 | 0..MAX |
| safeHoldTicks | int | 200 | 1..MAX |

旧 Dispatch 输入中没有 `safeRetryBackoffTicks`；导入时补 8。完整快照必须满足：

- `safeGridAttempts <= hardGridAttempts`；
- `safeProviderAttempts <= hardProviderAttempts`；
- `safeCommitBudgetMs <= hardCommitBudgetMs`；
- `ewmaAlpha` 有限且 `0 < value <= 1`。

## 独立规则文件

`config/data_energistics-data_extractor_rules.json` 不属于目标 YAML。目标格式固定为 `schema_version: 1`，包含 `carrier_rules` 和 `output_rules`；无版本文件作为 v0 迁移。完整结构、兼容键、备份和严格验证见 `06-data-extractor-rules-v1.md`。

## 需要覆盖的现有缺口

- 63 个 YAML 字段的默认值、自定义值、边界、往返和消费行为；
- 64 个 TOML 输入的映射、废弃键和特殊升级规则；
- watcher 后台重载到主线程原子快照发布；
- 第一个与第二个单人世界的内置 watcher 生命周期；
- FULL 本地化键、双语注释和 enum 客户端显示适配；
- JSON v0/v1/future 版本、重复键、未知键和备份冲突；
- 客户端、专用服务器、GameTest 与打包依赖。
