# 04：旧 TOML 到新 YAML 的迁移

## 迁移目标

导入器识别六份 COMMON TOML 的 64 个历史输入，生成恰好 63 个叶字段的：

`config/data_energistics/data_energistics.yaml`

Data Extractor 规则 JSON 使用独立的 v0 → v1 迁移，见 `06-data-extractor-rules-v1.md`。

## 文件映射

| 旧文件 | 历史输入数 | 目标前缀 | 目标字段数 |
| --- | ---: | --- | ---: |
| data_energistics-common.toml | 8 | dataRipper、dataDistributionTower、dataSanctumInterface | 8 |
| data_energistics-data_extractor.toml | 15 | dataExtractor | 15 |
| data_energistics-tnt.toml | 15 | flatteningTnt | 14 |
| data_energistics-solar_panel.toml | 4 | solarPanel | 4 |
| data_energistics-trinity_crafting.toml | 9 | trinityCrafting | 8 |
| data_energistics-trinity_dispatch.toml | 13 | trinityDispatch | 14 |
| 合计 | 64 |  | 63 |

## 特殊兼容规则

- `flatteningTnt.tntConfigurable.displayName`：识别、丢弃并警告使用 `block.data_energistics.tnt_configurable` 语言键。
- `trinityCrafting.mipTimeoutMs`：识别为历史废弃项并忽略。
- TOML 来源的 `trinityCrafting.maxBindingVariants=512`：升级为 32768。
- 其他 TOML `maxBindingVariants` 正整数：原值保留。
- YAML 中显式 `maxBindingVariants=512`：原值保留，不套用 TOML 兼容规则。
- 旧 Dispatch 缺少 `safeRetryBackoffTicks`：目标写入默认值 8。

## 固定启动顺序

1. 检查正式 YAML 与同目录迁移临时文件。
2. YAML 已存在时先严格预校验；有效 YAML 唯一优先，旧 TOML 不参与合并。
3. YAML 不存在时探测六份旧 TOML。
4. 对每份存在的旧文件进行严格语法、重复键、未知键、类型、范围和领域约束验证。
5. 缺失旧文件对应领域使用当前目标默认值。
6. 应用明确的 64 → 63 特殊兼容规则并在内存构造完整目标值。
7. 在目标同目录创建唯一临时文件，以 UTF-8 无 BOM 写入并 flush/fsync。
8. 重新严格解析临时 YAML，逐项核对 63 个目标字段。
9. 使用同文件系统原子移动发布正式 YAML。
10. 注册 Configuration，再在 Holder 锁内核对并发布首份根快照。

Configuration 注册必须晚于已有 YAML 的预校验或导入发布，防止框架初次 `processConfig` 用默认内容覆盖损坏输入。

## 输入状态

### 有效 YAML 已存在

- 只使用 YAML，不比较旧文件时间戳，也不重新导入。
- YAML 中的 512 等显式值保持原义。
- 旧 TOML 只作为用户备份保留。

### YAML 不存在、至少一份 TOML 存在

- 存在文件必须完整有效；任何未知或非法键使整个迁移失败。
- 缺失领域使用当前目标默认值。
- 全部 63 项通过验证后才原子发布。
- 任一失败不得留下正式目标或半份业务快照。

### 新旧正式文件均不存在

- 生成 63 项完整默认 YAML。
- `plannerThreads` 按本机处理器数计算一次并写入。
- 生成文件同样走临时文件、fsync、重读和原子移动。

### 仅迁移临时文件存在

- 严格解析临时文件并确认它是完整的 63 项候选。
- 完整有效时按原子移动协议恢复；无效时停止初始化并保留证据。
- 不以旧 TOML 或默认值静默覆盖无法解释的临时状态。

## 严格错误策略

| 情况 | 结果 |
| --- | --- |
| YAML/TOML 语法或重复键错误 | 报告文件、位置和键，停止 |
| 已存在旧文件含未知键 | 报告完整旧路径，停止 |
| 已知键类型或范围错误 | 报告实际值与约束，停止 |
| regex、mapping、ResourceLocation 非法 | 报告条目索引与原值，停止 |
| Trinity 跨字段约束失败 | 报告两个相关完整路径，停止 |
| YAML 与旧 TOML 同时存在 | 只使用有效 YAML |
| 原子移动不受支持 | 明确失败并保留临时文件 |
| 框架读值与严格 YAML 不一致 | 不发布快照，保留上一份有效值 |

不允许空 catch、未知键警告后继续、直接覆盖或异常后回退默认。

## 幂等与可恢复性

- 一份完整有效的正式 YAML 就是成功标志，不创建额外 marker。
- 正式 YAML 存在后，后续启动永不重新导入 TOML。
- 六份旧 TOML 不删除、不截断、不重命名、不写回。
- 写入前后记录旧文件哈希并在测试中断言不变。
- 临时文件与目标在同一目录，名称包含 migration 和进程唯一后缀。
- 每个输入状态至少运行两次，第二次结果与第一次逐项一致。

## 过渡批次

批次 01 至 04 可以保留旧 spec 供尚未切换的消费者读取，但生产真值必须唯一：

- 新领域接口在过渡期由 `LegacyConfigBridge` 完整提供；
- 一个领域切换到快照后不得回退读取旧静态字段；
- 中间提交不发布；
- 批次 05 启用最终 Bootstrap 并移除六份旧 spec 注册和事件复制链。
