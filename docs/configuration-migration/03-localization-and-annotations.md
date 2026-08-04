# 03：本地化、双语注释与注解策略

## 本地化目标

en_us 与 zh_cn 必须同时覆盖：

- 配置界面标题；
- 八个一级领域分组及两个 TNT 子分组；
- 63 个目标 YAML 字段标签；
- 独立规则 YAML 的两个分组与 10 个列数组标签；
- GUI 中实际显示的格式、范围、危险和重启提示；
- `CraftingQuantityMode` 的两个显示值。

根标题键：

`config.screen.data_energistics`

字段与分组键统一使用完整路径：

`config.data_energistics.option.<完整字段路径>`

所有分组和叶字段显式采用 `Configurable.LocalizationKey.FULL`。en_us 与 zh_cn 的配置键集合必须完全相同。Configuration 的数组虚拟条目不会继承父路径，因此另按框架实际约定提供 `<字段 id>.entry` 键。

## 序列化与显示分离

- YAML 键固定为 ASCII lowerCamelCase。
- `CraftingQuantityMode` 的磁盘值始终为 `NET_NEW`、`FINAL_TOTAL`。
- 客户端注册专用的枚举显示适配器，将两个稳定值显示为当前语言；该适配器不得被专用服务器加载。
- 语言切换只改变 GUI 文本，不改 YAML 路径或 enum 序列化值。
- 目标 schema 不包含 `tntConfigurable.displayName`。可配置 TNT 使用既有 `block.data_energistics.tnt_configurable`，不再从配置读取物品名。

## 双语文件注释

每个分组、主配置 63 个叶字段及独立规则 10 个数组都写入 literal 注释，顺序固定为英文在前、中文在后。注释至少说明行为，并按字段补充：

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
| 小范围数值 | `Configurable.Gui.Slider` | 同时保留精确文本输入 |
| 线程池参数 | `Configurable.UpdateRestriction(GAME_RESTART)` | 仅服务器启动创建资源 |
| 跨字段约束 | 程序化验证与不可变快照 | 通过后才能发布 |

不使用 `FixedSize`、`CharacterLimit`、会改变精度的 `NumberFormat` 或 4.x 才提供的 Validator 注解。主 YAML 与独立规则 YAML 均不添加 `@Config.NoAutoSync`；两者都由内置 watcher 驱动，并在服务器 tick 边界发布完整不可变实例。

## 字符串、列表和注册表字段

- `fillBlock` 先用 `StringPattern` 验证 ResourceLocation 词法，再在注册表可用阶段确认方块存在。
- Data Ripper 倍率使用 `patterns[]` 与 `values[]` 两个原生数组按索引配对，运行期不解析 `pattern=value`。
- Data Extractor 黑白名单使用一项一个注册表 ID 的原生数组。
- 旧 `cropInputMappings` 移入独立规则配置的载体列数组；规则全部按原生字段列保存，不编码条目语法。
- 所有数组同时提供框架约定的 `<字段 key>.entry` 本地化键，列表中的每项显示“索引 + 本地化条目名”，不得暴露原始 key。
- 框架无法逐元素表达的数组约束全部放在领域转换层，不以宽松注解代替。

## GUI 可见性

主配置 63 个字段和独立规则配置 10 个字段默认全部可见，不要求玩家切换 Advanced 模式。分组只负责按领域导航，不承担隐藏字段的职责。

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
- 规则 YAML：内置 watcher 重读并通过主线程严格核对后的下一服务器 tick。

## enum 显示适配

客户端枚举适配器必须：

- 以 enum 常量为输入，返回 en_us/zh_cn 语言键文本；
- 保存时仍输出 `Enum.name()`；
- 对未知值由严格 YAML 解析直接拒绝，不提供兜底标签；
- 只在客户端初始化路径注册；
- 通过一次真实 GUI 启动检查显示，不使用反射或专项控件测试。

## 验收

- 从 schema 元数据得到主配置 63 个字段、独立规则 10 个字段及所有分组的 FULL key。
- 解析 en_us.json、zh_cn.json 并比较精确键集合；禁止重复、空值和错误 Mod ID。
- 生成 YAML 后重新解析双语 literal 注释，字节级确认 UTF-8 无 BOM。
- 英文和简体中文 GUI 均检查标题、分组、全部默认可见字段、Slider、enum 和重启提示。
- 专用服务器启动证明不会加载枚举显示适配器的客户端类。
