# 02：目标架构、Schema 与重载桥接

## 框架接入

- Minecraft 1.21.1 使用 `dev.toma.configuration:configuration-1.21.1:3.1.1-neoforge`；4.x 不兼容本项目版本。
- Gradle 以 `jarJar(api(...))` 嵌入依赖。
- NeoForge 元数据声明必需依赖 `configuration`，版本范围 `[3.1.1,4)`，排序 `AFTER`，side 为 `BOTH`。
- 主 YAML 与规则 YAML 均不添加 `@Config.NoAutoSync`，让 Configuration 注册 Holder 时自动接入内置 `FileWatchManager`。

## 目标文件与 63 项树

主文件固定为：

`config/data_energistics/data_energistics.yaml`

| YAML 分组 | 叶字段数 | 说明 |
| --- | ---: | --- |
| dataRipper、dataDistributionTower、dataSanctumInterface | 9 | 倍率正则与数值使用两个原生数组 |
| dataExtractor | 14 | 作物映射移入独立规则配置 |
| flatteningTnt.tntConfigurable | 11 | 不包含历史 displayName |
| flatteningTnt.dataNuke | 3 | Data Nuke |
| solarPanel | 4 | Solar |
| trinityCrafting | 8 | 不包含历史 mipTimeoutMs |
| trinityDispatch | 14 | 包含 safeRetryBackoffTicks |
| 合计 | 63 | 独立规则 YAML 的 10 个数组不计入 |

所有路径使用 ASCII lowerCamelCase。

## Java 包与职责命名

目标根包为 `com.fish_dan_.data_energistics.configuration`，类型按职责进入子包，不在根包平铺。

| 子包或类型 | 职责 |
| --- | --- |
| `configuration.schema.DataEnergisticsConfiguration` | Configuration 主 YAML 根 schema，并按 GTPM 风格直接持有 `INSTANCE` 与 `INTERNAL_INSTANCE` |
| `configuration.api.DataEnergisticsSettings` | 业务只读领域接口集合 |
| `configuration.snapshot` | 63 项不可变根快照、Trinity 领域值与组装逻辑 |
| `configuration.io` | 严格 YAML 读取与原子文件生成 |
| `configuration.migration` | 64 项旧 TOML 导入 |
| `configuration.runtime.ConfigurationBootstrap` | 按预校验、迁移、注册、首份快照、规则注册的固定顺序启动 |
| `configuration.runtime.HolderFingerprintBridge` | 在服务器 tick 边界检测主 Holder 指纹并触发快照候选流程 |
| `configuration.rules.schema.DataExtractorRulesConfiguration` | 按 GTPM 风格直接持有 `INSTANCE`、`INTERNAL_INSTANCE` 与规则重载入口；业务从实例读取完整 `LoadedRules` |
| `configuration.rules.codec` | 把规则 YAML 的 10 个原生列数组组装为不可变规则，并执行完整语义校验 |
| `configuration.rules.io` | 严格读写规则 YAML，并在首次启动时导入旧 JSON |

禁止以统一实现后缀命名内部类型。名称必须表达数据来源或职责，也不得创建 `ConfigurationService`、`ConfigurationManager`、`ConfigurationController` 一类容易膨胀的超级对象。

## schema 与业务边界

- 框架写入的 schema 实例不进入业务调用点；业务从 `DataEnergisticsConfiguration.INSTANCE` 读取已严格验证的不可变领域实例。
- `DataEnergisticsConfiguration.INTERNAL_INSTANCE` 只供启动、重载入口和客户端 Configuration 主题注册使用，不作为玩法配置入口。
- 简单标量通过领域读取接口暴露。
- 正则和注册表 ID 数组在快照组装时转换为不可变已验证值；倍率与规则使用独立列数组，生产路径不解析组合字符串。
- Trinity Crafting、Trinity Dispatch、TNT 操作使用完整领域快照。
- 根快照只通过一个原子引用发布；消费者拿到的领域视图来自同一修订号。
- 领域接口、字段和方法首次出现时说明动机与作用；大型快照构造使用 Builder 或清晰的分层构造。
- 外部 YAML、TOML、JSON 和明确 nullable API 边界做完整空值验证；内部构造后的非空不变量不重复判空，违反即 fail fast。

## 启动顺序

1. 检查目标 YAML 及迁移临时文件。
2. 已有 YAML 先由严格解析器完整预校验，禁止框架抢先纠正或覆盖坏文件。
3. YAML 不存在时，按 64 项历史清单导入六份 TOML，或生成 63 项默认配置。
4. 注册 Configuration 根 schema；注册动作自动将 Holder 加入内置 watcher。
5. 在 Holder 锁内复制首份候选，与严格读取的 YAML 核对。
6. 发布首份不可变根快照。
7. 预校验规则 YAML；不存在时从旧 JSON v0/v1 或默认规则原子生成，再注册独立 Configuration Holder 并发布单个 `LoadedRules`。
8. 启动需要配置的服务器资源；规划线程池只在服务器启动时创建。

任一步失败都不得向业务层发布半初始化状态。

## 内置 watcher 到主线程快照

Configuration 的 watcher 在后台线程、Holder 锁内更新框架状态。项目不另建磁盘 watcher，而在服务器主线程每 tick 执行以下桥接：

1. 比较 Holder 指纹；未变化立即返回。
2. 在 Holder 锁内复制完整 63 项候选值及当前指纹。
3. 严格解析当前 YAML，拒绝缺失、重复、未知字段、非法值和跨字段错误。
4. 再次在 Holder 锁内确认指纹未变化。
5. 确认严格 YAML 候选与 Holder 的 saved/pending 值逐项一致，防止发布框架自动纠正后的伪有效值。
6. 构造带新修订号的单个不可变根快照并原子发布。

任一阶段失败时记录文件、字段和值，不发布候选，业务继续使用上一份有效快照。文件修复后由下一次 `FileWatchManager` 事件重新进入流程。

## 已知 watcher 生命周期

保留 Configuration 3.1.1 的原始行为：

- 第一个服务器生命周期内手工修改主 YAML 或规则 YAML 可触发热重载；
- 同一客户端进程退出第一个单人世界后，内置 watcher 被永久关闭；
- 第二次进入单人世界时两份配置的手工文件修改都不再自动加载；
- 重启客户端后 watcher 恢复。

实现不得用 Mixin 或自建 `WatchService` 修正此行为。GUI 与发布文档必须说明限制，不为该框架生命周期建立专项测试。

## 字段生效分类

| 领域 | 生效策略 |
| --- | --- |
| Data Ripper | 新快照发布后即时使用；修订号使目标缓存失效 |
| Tower、Sanctum | 新快照发布后的下一次读取生效 |
| Data Extractor | 新工作读取新值；载体已持久化的 requiredAmount 不追溯 |
| Flattening TNT | 每次爆炸开始时固定一次完整快照 |
| 活动 Data Nuke | 每个服务器 tick 获取最新完整快照 |
| Solar 发电 | 新快照发布后即时读取 |
| Solar 容量加成 | 机器加载或卡片变化时重算 |
| Trinity Crafting 普通字段 | 下一次规划读取新快照 |
| plannerThreads、plannerQueueCapacity | `GAME_RESTART`；只在服务器启动时创建线程池 |
| Trinity Dispatch | 下一次 grid tick 重建 Governor，并重置所有统计历史 |
| Data Extractor 规则 YAML | 内置 watcher 重读后，在下一服务器 tick 严格核对并原子发布完整规则快照 |

## Data Extractor 规则边界

规则文件继续独立存在，因为它是变长规则集合，不属于主 YAML 的 63 个固定业务字段。规则 schema 分成 `carrierRules` 与 `outputRules` 两组：载体组使用 6 个原生列数组，输出组使用 4 个原生列数组；相同索引组成一行，不编码或分割结构化字符串，也不引入自定义 Configuration Format、隐藏 document 字段和额外 Holder 包装。活动格式与旧 JSON 迁移协议见 `06-data-extractor-rules.md`。

主配置与规则的启动发布顺序是单向的：先有有效根快照，再预校验/迁移规则文件，最后注册规则 Holder；框架加载结果必须与预校验结果一致后才发布。规则失败不得留下空或部分 `LoadedRules`。

运行期由同一个服务器 tick 入口观察规则 Holder：在 Holder 锁内复制两组完整列数组，严格读取当前磁盘 YAML，再次确认 Holder 未变化且磁盘内容与候选完全一致，最后一次性更新 `DataExtractorRulesConfiguration.INSTANCE` 中的完整规则。无效 YAML、框架候选与文件不一致或读取期间再次变化时保留上一份规则，并在修复后的下一次内置 watcher 事件重试。

## 不采用的结构

- 旧配置类各自提供静态 getter 的多入口结构。
- 业务层直接访问 `INTERNAL_INSTANCE` 或框架可变 schema。
- 每个字段一个原子变量。
- 项目自建 watcher 或磁盘轮询。
- SHORT 本地化键。
- 依赖框架自动纠正无效 YAML。
- 以实现后缀代替职责语义的类名。
