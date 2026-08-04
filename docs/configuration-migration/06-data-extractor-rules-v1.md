# 06：Data Extractor 规则 JSON v1

## 文件与生命周期

规则文件保持为：

`config/data_energistics-data_extractor_rules.json`

它不计入主 YAML 的 63 个字段，也不由 Configuration watcher 热重载。规则只在启动时迁移和加载；完整成功后以单个不可变 `LoadedRules` 快照发布。

启动顺序固定为：主 YAML 首份有效快照发布 → 规则文件迁移/生成 → 严格读取 v1 → 发布 `LoadedRules`。规则失败不得发布空列表或部分列表覆盖上一份有效状态。

## v1 顶层结构

```json
{
  "schema_version": 1,
  "carrier_rules": [
    {
      "slot": "crop",
      "data_type": "crop",
      "input_item": "minecraft:wheat",
      "recorded_item": "minecraft:wheat",
      "progress_per_item": 1.0,
      "required_amount": 4096.0
    }
  ],
  "output_rules": [
    {
      "data_type": "crop",
      "recorded_item": "minecraft:oak_sapling",
      "outputs": [
        { "item": "minecraft:oak_log", "count": 4 }
      ]
    }
  ]
}
```

必需顶层字段为 `schema_version`、`carrier_rules`、`output_rules`。名称以 `_` 开头的元数据可出现在任意既有层级，迁移时在原层级原样保留；其他未知字段失败。

## carrier_rules

每项固定包含：

| 字段 | 要求 |
| --- | --- |
| slot | 已支持的 extractor slot 稳定值 |
| data_type | mob、ore、crop 中与规则语义一致的稳定值 |
| input_item | 合法且已注册的物品 ID |
| recorded_item | 合法且已注册的记录对象 ID |
| progress_per_item | 有限且大于 0 |
| required_amount | 有限且大于 0 |

缺失、null、错误类型、未知 slot/data_type、非法注册表 ID 或非正数都使整个文件失败。数组元素不是对象也失败，不再静默跳过。

## output_rules

每项固定包含：

| 字段 | 要求 |
| --- | --- |
| data_type | mob、ore、crop |
| recorded_item | 合法的记录对象 ID |
| outputs | 非空输出数组 |

`outputs` 每项只包含 `item` 和 `count`：

- `item` 必须是合法且已注册的物品 ID；
- `count` 必须是 JSON 整数且大于 0，不接受小数、字符串、0 或负数；
- 同一 output rule 内完全相同的 `item/count` 重复项去重并保持首次出现顺序；
- 对同一 `data_type/recorded_item` 出现语义相同的输出规则时去重；
- 同一 `data_type/recorded_item` 对应不同输出集合时视为冲突并失败。

## v0 识别与规范化

没有 `schema_version` 的根对象按 v0 处理。兼容的历史入口：

- `carrier_rules`；
- `input_rules`；
- `rules`；
- `output_rules`；
- carrier 项内的 `final_carrier`、`final_carrier_item`；
- carrier 项内的 `mimetic_outputs` 或历史 `outputs`。

规范化规则：

1. `carrier_rules` 中从 `final_carrier`、`final_carrier_item` 或 `data_type` 推导唯一 `data_type`；多个来源冲突时失败。
2. `input_rules` 或 `rules` 直接映射到 v1 `carrier_rules` 的六个固定字段。
3. carrier 项内的 `mimetic_outputs` / `outputs` 拆成独立 v1 `output_rules`，键为该项的 `data_type/recorded_item`。
4. 历史 `output_rules.recorded_id` 规范化为 v1 `recorded_item`。
5. 全部规则进入同一去重和冲突检测，再生成确定顺序的 v1。
6. `_...` 元数据保留在原对象或数组所在层级；`_mob_rule_examples` 整体保留但从不参与执行规则。

v0 中同时出现多个历史入口并产生重复语义时按相同规则去重；任何冲突、未知非下划线字段或无法无歧义转换的值都失败。

## 备份与原子迁移

v0 原始字节备份到同一 config 目录：

`data_energistics-data_extractor_rules.v0.json`

迁移协议：

1. 以字节读取 v0 原文件并完成严格解析和规范化。
2. 备份不存在时，以同目录临时文件写入原始字节、flush/fsync 后原子移动为备份。
3. 备份已存在时必须与当前 v0 原文件逐字节相同；不同即停止，绝不覆盖。
4. 将规范化 v1 写入原文件同目录的唯一临时文件，UTF-8 无 BOM。
5. 严格重读临时 v1，并比较规范化的 `LoadedRules` 与元数据。
6. flush/fsync 后原子替换正式规则文件。
7. 二次启动看到 `schema_version: 1`，不再执行 v0 迁移。

原子移动不受支持或任一步失败时明确记录并保留证据，不降级为直接覆盖。

## 版本与严格错误

- `schema_version` 必须是 JSON 整数 1。
- 0、负数、字符串版本和未来版本都失败；未来版本不得按 v1 猜测读取。
- 使用能检测重复对象键的严格 JSON 读取器；任意层级重复键失败。
- 任意层级的未知非下划线字段失败。
- 错误包含文件、JSON path、数组索引、实际值和原因。
- 不允许 catch 后返回空规则、跳过坏条目或修正 count。

## 默认规则兼容

全新文件必须以 v1 生成，并保持：

- 现有 36 个作物映射的 input、recorded item、progress 和 required amount 语义；
- oak sapling 的 oak log、oak leaves、stick、apple 输出行为；
- raw gold 到 gold ore 的输出行为；
- `_mob_rule_examples` 继续只作为模板，不进入运行规则。

## 验收矩阵

- 全新安装生成 v1，UTF-8 无 BOM，二次启动不改写。
- 每一种 v0 入口及组合均能确定迁移。
- v0 原文件备份字节一致；相同已有备份通过，冲突备份失败。
- 有效临时文件恢复，无效临时文件保留并失败。
- 重复键、future version、未知字段、缺失字段、非法 ID、NaN、Infinity、非正数和非整数 count 均失败。
- 相同输出去重并保序，冲突输出失败。
- 36 个作物映射以及 oak/raw-gold 通过真实查询入口验证。
- `LoadedRules` 发布是原子的，消费者不可观察到半份规则。
