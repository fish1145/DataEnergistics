# 03：本地化、双语注释与注解策略

## 本地化目标

en_us 与 zh_cn 必须同时覆盖：

- 配置界面标题；
- 八个一级领域分组及两个 TNT 子分组；
- 63 个目标 YAML 字段标签；
- GUI 中实际显示的格式、范围、危险和重启提示；
- `CraftingQuantityMode` 的两个显示值。

根标题键：

`config.screen.data_energistics`

字段与分组键统一使用完整路径：

`config.data_energistics.option.<完整字段路径>`

所有分组和叶字段显式采用 `Configurable.LocalizationKey.FULL`。en_us 与 zh_cn 的配置键集合必须完全相同，不使用 SHORT key 去重。

## 序列化与显示分离

- YAML 键固定为 ASCII lowerCamelCase。
- `CraftingQuantityMode` 的磁盘值始终为 `NET_NEW`、`FINAL_TOTAL`。
- 客户端注册专用的枚举显示适配器，将两个稳定值显示为当前语言；该适配器不得被专用服务器加载。
- 语言切换只改变 GUI 文本，不改 YAML 路径或 enum 序列化值。
- 目标 schema 不包含 `tntConfigurable.displayName`。可配置 TNT 使用既有 `block.data_energistics.tnt_configurable`，不再从配置读取物品名。

## 双语文件注释

每个分组和 63 个叶字段都写入 literal 注释，顺序固定为英文在前、中文在后。注释至少说明行为，并按字段补充：

1. 单位：tick、秒、毫秒、AE/t 或桶；
2. 范围或字符串语法；
3. 默认值或动态默认算法；
4. 生效时机；
5. 性能、世界修改或并发风险。

示例：

> Maximum number of chunks cleared outward from the center.
>
> 从中心向外清除的最大区块半径。
>
> Range: 0–64. Applies when a new explosion starts.
>
> 范围：0–64。新爆炸开始时生效。

`plannerThreads` 注释描述 `clamp(CPU / 2, 1, 8)`，不写死开发机结果；`plannerThreads` 和 `plannerQueueCapacity` 均明确标注需游戏重启。

## 注解矩阵

| 需求 | 注解或机制 | 固定规则 |
| --- | --- | --- |
| 字段与分组 | `Configurable` | 一律 FULL key |
| 文件说明 | `Configurable.Comment` | literal 英文、中文成对 |
| 整数范围 | `Configurable.Range` | 与 01 清单一致 |
| 浮点范围 | `Configurable.DecimalRange` | 领域层额外拒绝 NaN、Infinity 和窄化溢出 |
| 固定格式字符串 | `Configurable.StringPattern` | 只承担可证明的词法检查 |
| 高级字段 | `Configurable.Gui.Visibility(ADVANCED)` | 仍需完整风险注释 |
| 小范围数值 | `Configurable.Gui.Slider` | 同时保留精确文本输入 |
| 线程池参数 | `Configurable.UpdateRestriction(GAME_RESTART)` | 仅服务器启动创建资源 |
| 跨字段约束 | 程序化验证与不可变快照 | 通过后才能发布 |

不使用 `FixedSize`、`CharacterLimit`、会改变精度的 `NumberFormat` 或 4.x 才提供的 Validator 注解。不添加 `@Config.NoAutoSync`。

## 字符串、列表和注册表字段

- `fillBlock` 先用 `StringPattern` 验证 ResourceLocation 词法，再在注册表可用阶段确认方块存在。
- `dataRipperMultipliers` 逐项验证等号分隔、有限倍率和可编译 regex。
- Data Extractor CSV 与 `cropInputMappings` 由集中解析器验证并报告条目索引。
- 框架无法逐元素表达的数组约束全部放在领域转换层，不以宽松注解代替。

## GUI 分层

以下字段使用 Advanced 展示：

- Data Ripper regex、倍率表；
- Data Extractor CSV 与大型 mapping；
- `replaceUnbreakableBlocks`、TNT 大范围和偏移参数；
- Trinity 的尝试次数、预算、窗口、队列、退避和 EWMA 调参项。

`ewmaAlpha`、小范围 TNT 半径与偏移、`speedCardBonusRatio` 使用可精确输入的 Slider。上限为 `Integer.MAX_VALUE` 或 `Double.MAX_VALUE` 的字段不使用 Slider。

## 生效提示

GUI 与注释使用与实现一致的混合策略：

- `plannerThreads`、`plannerQueueCapacity`：GAME_RESTART；
- Flattening TNT：下一次爆炸；
- Data Nuke：下一服务器 tick；
- Solar 容量：机器加载或卡片变化；
- Trinity Dispatch：下一 grid tick，Governor 历史重置；
- Data Extractor 已持久化 requiredAmount：不追溯；
- 其他主 YAML 字段：有效快照发布后的下一次对应读取；
- 规则 JSON：下一次启动。

## enum 显示适配

客户端枚举适配器必须：

- 以 enum 常量为输入，返回 en_us/zh_cn 语言键文本；
- 保存时仍输出 `Enum.name()`；
- 对未知值由严格 YAML 解析直接拒绝，不提供兜底标签；
- 只在客户端初始化路径注册；
- 通过真实 GUI 或框架公开编辑器入口测试，不使用反射。

## 验收

- 从 schema 元数据得到 63 个字段及所有分组的 FULL key。
- 解析 en_us.json、zh_cn.json 并比较精确键集合；禁止重复、空值和错误 Mod ID。
- 生成 YAML 后重新解析双语 literal 注释，字节级确认 UTF-8 无 BOM。
- 英文和简体中文 GUI 均检查标题、分组、字段、Advanced、Slider、enum 和重启提示。
- 专用服务器启动证明不会加载枚举显示适配器的客户端类。
