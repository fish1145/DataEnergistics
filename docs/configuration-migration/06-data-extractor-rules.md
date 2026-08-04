# 06：Data Extractor 独立规则配置

## 活动文件与 Configuration 注册

活动规则文件固定为：

`config/data_energistics/data_extractor_rules.yaml`

它以 `data_energistics_data_extractor_rules` 注册为第二份原生 Configuration 配置，不计入主 YAML 的 63 个业务字段。schema 按职责分为两个分组：

- `carrierRules`：6 个原生数组；
- `outputRules`：4 个原生数组。

所有字段使用 `LocalizationKey.FULL`，默认可见，不要求 Advanced 模式。文件注释按英文、中文顺序成对生成，编码为 UTF-8 无 BOM。

## 原生列数组

相同分组内，各数组相同索引的值组成一行。没有 `name=value`、分号、逗号列表或运行期字符串分割。

```yaml
carrierRules:
  slots:
    - CROP
  dataTypes:
    - CROP
  inputItems:
    - minecraft:wheat_seeds
  recordedItems:
    - minecraft:wheat
  progressPerItems:
    - 0.5
  requiredAmounts:
    - 4096.0

outputRules:
  dataTypes:
    - CROP
  recordedItems:
    - minecraft:oak_sapling
  items:
    - minecraft:oak_log
  counts:
    - 4
```

载体组字段：

| 数组 | 类型 | 含义 |
| --- | --- | --- |
| slots | `Slot[]` | `ORE` 或 `CROP` |
| dataTypes | `DataType[]` | `MOB`、`ORE` 或 `CROP` |
| inputItems | `String[]` | 每行一个输入物品注册表 ID |
| recordedItems | `String[]` | 每行一个记录物品注册表 ID |
| progressPerItems | `float[]` | 单个输入提供的有限正进度 |
| requiredAmounts | `float[]` | 完成载体所需的有限正进度 |

输出组字段：

| 数组 | 类型 | 含义 |
| --- | --- | --- |
| dataTypes | `DataType[]` | 输出所属数据类型 |
| recordedItems | `String[]` | 触发输出的记录物品 ID |
| items | `String[]` | 输出物品 ID |
| counts | `int[]` | 1..`Integer.MAX_VALUE` |

同一数据类型与记录物品需要多个输出时，重复前两列并增加多行。相同输出物品与相同数量去重；同一输出物品出现不同数量则拒绝。任一组内数组长度不同、非法 ID、空值、非有限/非正数或重复载体键都会拒绝整份候选。

## 启动优先级与旧文件导入

旧 JSON 只作为兼容输入：

`config/data_energistics-data_extractor_rules.json`

启动顺序固定为：

1. 主 YAML 首份有效快照发布。
2. 活动规则 YAML 已存在时严格读取，旧 JSON 和旧 TOML 均不得覆盖。
3. 活动规则 YAML 不存在但旧 JSON 存在时，严格读取旧 JSON v0/v1，再直接写入 10 个原生数组。
4. 两者都不存在时，旧 TOML 的 `cropInputMappings` 仅在迁移边界转换为载体列；没有旧 TOML 时使用 36 条原子内置行。
5. 补入 oak sapling 与 raw gold 默认行为，通过同目录临时文件、flush/fsync、严格重读和原子移动发布 YAML。
6. 注册规则 Holder，核对框架实例与预校验结果一致后发布首份 `LoadedRules`。

活动 YAML 一旦存在，后续启动不再读取旧 JSON 或旧 TOML。旧文件永不删除。

## 旧 JSON v0/v1 兼容

严格解析仍识别：

- v1 的 `schema_version: 1`、`carrier_rules`、`output_rules`；
- 无版本 v0 的 `carrier_rules`、`input_rules`、`rules`；
- `final_carrier`、`final_carrier_item`、`mimetic_outputs`、历史 `outputs`；
- `output_rules.recorded_id`；
- 任意既有层级的 `_...` 元数据，其中 `_mob_rule_examples` 只作示例。

v0 原始字节备份到 `config/data_energistics-data_extractor_rules.v0.json`。已有备份必须与当前 v0 源逐字节一致，否则停止；通过验证后旧 JSON 原文件原子规范化为 v1，再生成活动 YAML。重复键、future version、未知非下划线字段、缺失值、非法 ID、NaN、Infinity、非正数和非整数 count 全部失败。

## 实时发布

规则 Holder 不添加 `@Config.NoAutoSync`，由 Configuration 内置 `FileWatchManager` 监视。有效文件修改不需要重启：

1. watcher 在 Holder 锁内重读两组列数组；
2. 下一服务器 tick 复制完整候选；
3. 严格读取磁盘 YAML 并组装完整 `LoadedRules`；
4. 再次确认 Holder 未变化，且 Holder 与磁盘候选完全一致；
5. 一次性替换 `DataExtractorRulesConfiguration.INSTANCE` 持有的规则快照。

无效候选、Holder/磁盘不一致或读取竞态均保留上一份有效规则。修复文件后由下一次内置 watcher 事件重试。单人世界退出后 watcher 的进程级生命周期限制与主 YAML 相同，不另建项目 watcher 修正。

## 最小验收

- 一份旧 v1 JSON 可转换为原生列数组。
- 一份 v0 JSON 可迁移并保留备份与元数据。
- 默认 36 个作物行以及 oak/raw-gold 行为保持。
- 有效运行期修改下一服务器 tick 生效；无效候选保持上一份完整规则。
