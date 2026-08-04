# 05：验证矩阵

## 原则

测试直接调用 schema、严格解析器、导入器、快照组装、领域接口和消费者生命周期。禁止反射、源码文本 contains 测试，以及只证明某旧类已删除的永久测试。

所有文件测试使用独立临时 config 根目录，不读取、覆盖或清理开发者真实配置。

## 63 项目标 YAML 矩阵

| 领域 | 数量 | 默认值 | 自定义值 | 边界 | YAML 往返 | 领域消费 |
| --- | ---: | --- | --- | --- | --- | --- |
| 通用 | 8 | 必测 | 必测 | 必测 | 必测 | 必测 |
| Data Extractor | 15 | 必测 | 必测 | 必测 | 必测 | 必测 |
| Flattening TNT / Data Nuke | 14 | 必测 | 必测 | 必测 | 必测 | 必测 |
| Solar Panel | 4 | 必测 | 必测 | 必测 | 必测 | 必测 |
| Trinity Crafting | 8 | 必测 | 必测 | 必测 | 必测 | 必测 |
| Trinity Dispatch | 14 | 必测 | 必测 | 必测 | 必测 | 必测 |
| 合计 | 63 | 63 | 63 | 63 | 63 | 63 |

类型合计：

| 类型 | 数量 | 验证重点 |
| --- | ---: | --- |
| int | 42 | 上下界、越界拒绝、往返 |
| double | 10 | NaN、Infinity、范围、float 窄化 |
| 字符串数组 | 2 | 空、顺序、增删、非法条目索引 |
| 语法字符串 | 6 | CSV、mapping、ResourceLocation 与错误位置 |
| boolean | 2 | true/false 与危险说明 |
| enum | 1 | 稳定值、未知值拒绝、客户端本地化显示 |

使用类型安全案例表明确 getter、默认值、有效自定义值和无效边界；不反射遍历项目字段。

## 64 个旧 TOML 输入矩阵

- 逐项映射 64 个历史键，不遗漏、不额外接受未知键。
- 62 个保留输入按语义迁入目标字段。
- `displayName` 被识别、丢弃并警告语言键替代。
- `mipTimeoutMs` 被识别为废弃项并忽略。
- TOML `maxBindingVariants=512` 迁移为 32768；其他有效值保持。
- 缺少目标 `safeRetryBackoffTicks` 时写入 8。
- YAML 显式 `maxBindingVariants=512` 保持 512。

## 主文件状态矩阵

| 目标 YAML | 旧 TOML | 期望 |
| --- | --- | --- |
| 不存在 | 全部不存在 | 原子生成 63 项默认 YAML |
| 不存在 | 六份有效 | 从 64 个输入生成 63 项 YAML |
| 不存在 | 部分有效 | 已有值导入，缺失领域使用目标默认 |
| 不存在 | 任一存在文件含未知或非法键 | 停止迁移，不发布目标 |
| 有效 | 存在 | 只用 YAML，旧文件不覆盖 |
| 有效 | 不存在 | 预校验、注册并正常读取 |
| 无效 | 任意 | 注册 Configuration 前精确报错并停止 |
| 不存在、临时文件完整有效 | 任意 | 重读后原子恢复 |
| 不存在、临时文件无效 | 任意 | 停止并保留证据 |

每种状态执行二次启动；记录六份旧 TOML 的哈希并断言未变化。

## watcher 与原子发布

| 场景 | 断言 |
| --- | --- |
| Holder 指纹未变化 | 不解析、不分配新快照 |
| watcher 写入完整有效值 | 主线程下一 tick 发布单个新修订 |
| YAML 缺失、重复或未知字段 | 不发布，业务保留旧快照 |
| 框架自动纠正候选 | YAML 与 saved/pending 不一致，不发布 |
| 复制后 Holder 再变化 | 放弃候选，下 tick 重试 |
| 两个并发消费者 | 只观察到旧或新完整根快照 |
| 修复先前无效文件 | 下一次内置 watcher 事件成功发布 |

生命周期序列必须使用真实 `FileWatchManager`：

1. 第一个服务器生命周期内热重载有效；
2. 退出第一个单人世界后，在同一客户端进程进入第二个世界；
3. 第二个世界的手工文件修改不再自动加载；
4. 重启客户端后热重载恢复。

不为测试建立项目 `WatchService`，也不模拟不存在的回调。

## 消费者生效矩阵

| 消费者 | 变更后的验证 |
| --- | --- |
| Data Ripper | 快照修订号使目标缓存失效 |
| Data Extractor | 新工作使用新值，已持久化 requiredAmount 不变 |
| Flattening TNT | 单次爆炸不混用两个修订 |
| 活动 Data Nuke | 每 tick 获取最新完整快照 |
| Solar 发电 | 新修订即时影响计算 |
| Solar 容量 | 只在加载或卡片变化时重算 |
| Trinity Crafting 普通字段 | 下一次规划读取新修订 |
| plannerThreads、plannerQueueCapacity | 运行中线程池不重建，重启后生效 |
| Trinity Dispatch | 下一 grid tick 重建 Governor 并清空 warmup、EWMA、window、cooldown 历史 |

额外覆盖 Data Ripper regex、Data Extractor float 可表示性、fillBlock 注册表存在性和 Dispatch 四条跨字段约束。

## Data Extractor 规则矩阵

- v1 正常加载与不可变 `LoadedRules` 原子发布；
- 无版本 v0 的所有兼容键组合；
- 原始字节备份、已有相同备份、已有冲突备份；
- v0 临时写入、重读验证、原子移动和二次启动；
- future version、重复键、未知非下划线字段、缺失字段、非法 ID、非有限/非正数和无效 count；
- 相同输出去重与冲突输出失败；
- `_...` 元数据原层级保留，`_mob_rule_examples` 不执行；
- 默认 36 个作物映射及 oak/raw-gold 行为。

详细案例见 `06-data-extractor-rules-v1.md`。

## 本地化与编码

- schema 元数据产生 63 个字段及分组的 FULL key 集合；
- en_us 与 zh_cn 键集合精确相等，无重复、空值或错误 Mod ID；
- `CraftingQuantityMode` 客户端显示本地化，磁盘仍为稳定 enum 名；
- literal 注释按英文、中文顺序生成并可重新解析；
- 所有生成文本字节级确认 UTF-8 无 BOM；
- 专用服务器不加载客户端枚举适配器类。

## 集成与最终门禁

- `test`；
- `spotlessCheck`；
- `build`；
- GameTest；
- 客户端启动、首个/第二个单人世界生命周期；
- 专用服务器启动与停止；
- 打包 jar 中的嵌入依赖和 NeoForge 依赖元数据；
- 全新安装、上一版升级、有效二次启动和损坏配置。

实际任务名先由 Gradle 任务列表确认；Gradle 缓存只使用系统环境变量。任一批次失败必须在进入下一批前修复。
