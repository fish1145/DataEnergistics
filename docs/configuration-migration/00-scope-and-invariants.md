# 00：范围与不可破坏约束

## 目标

将六份 NeoForge COMMON TOML 的历史配置迁移到 Configuration 3.1.1 管理的单一 YAML，同时保持旧文件可恢复、运行期读取原子一致，并明确各领域的生效时机。

迁移后的主文件固定为：

`config/data_energistics/data_energistics.yaml`

计数必须始终区分：

- 旧 TOML 输入清单共 64 项；
- 目标 YAML 共 63 个业务叶字段；
- 目标领域计数为通用 8、Data Extractor 15、Flattening TNT / Data Nuke 14、Solar 4、Trinity Crafting 8、Trinity Dispatch 14；
- `tntConfigurable.displayName` 与历史 `mipTimeoutMs` 只作为旧输入识别，不进入目标 YAML；
- `safeRetryBackoffTicks` 是目标新增字段，旧文件缺失时取默认值 8。

独立的 Data Extractor 规则文件继续位于 `config/data_energistics-data_extractor_rules.json`，使用版本化 v1 格式，不计入 63 个 YAML 字段。

## 明确不做

- 不把 Data Extractor 规则 JSON 塞入注解式 YAML。
- 不删除、覆盖或重命名用户旧 TOML。
- 不引入项目自建 `WatchService`；文件变化由 Configuration 注册 Holder 时自动加入的 `FileWatchManager` 负责。
- 不通过 Mixin 修补 Configuration 内置 watcher 的生命周期。
- 不复制参考工程的单体全局 Holder。
- 不让业务代码直接读取可变 schema。
- 不使用反射或源码文本 contains 测试。
- 不使用统一实现后缀作为类命名约定；内部类型按职责命名，例如 `SnapshotBackedDataRipperConfiguration`、`LegacyConfigBridge`。

## 配置与迁移不变量

- 目标 schema 必须恰好 63 项，旧 TOML 导入器必须识别恰好 64 项历史输入。
- `maxBindingVariants` 的目标默认值为 32768；旧 TOML 中值为 512 时迁移为 32768，目标 YAML 中显式的 512 保持不变。
- `mipTimeoutMs` 作为历史废弃键识别并忽略。
- `tntConfigurable.displayName` 作为历史键识别、丢弃并记录一次警告；物品显示名改用既有 `block.data_energistics.tnt_configurable` 语言键。
- 缺少 `safeRetryBackoffTicks` 的旧输入写入默认值 8。
- 已存在旧文件中的未知键、重复键、类型错误、越界值或跨字段错误都使迁移失败；不得警告后继续。
- 默认值、范围、单位、列表顺序和 enum 序列化值除上述锁定兼容规则外保持不变。
- `plannerThreads` 的动态默认继续按 `clamp(CPU / 2, 1, 8)` 计算。
- double 到 float 的三个 Data Extractor 值必须在发布前验证为有限且可表示。

## 文件与编码不变量

- 所有新增或生成的文本均为 UTF-8 无 BOM；仓库文本保持 LF。
- YAML 路径使用稳定的 ASCII lowerCamelCase，不随客户端语言变化。
- 英文、中文注释按英文在前、中文在后的顺序成对写入。
- 目标 YAML 一旦存在且有效，就永远优先于旧 TOML。
- 写入使用同目录临时文件、flush/fsync、重新解析验证和原子移动；不支持原子移动时明确失败。

## 架构与发布不变量

- schema 只描述配置数据；解析、迁移、验证和快照发布使用职责明确的独立类型。
- 业务层只依赖领域读取接口或不可变领域快照。
- 根快照以单个原子引用发布，禁止逐字段暴露部分更新。
- 启动时先严格预校验已有 YAML，再注册 Configuration，防止框架初次处理覆盖损坏文件。
- 只在外部配置输入和类型明确声明为 nullable 的边界做空值验证；内部非空不变量不做防御式重复判空，违反时立即 fail fast。
- 主 YAML 的首份有效快照必须先发布，随后才迁移并加载 Data Extractor 规则。
- 批次 01 至 04 仅是同一迁移分支上的过渡提交，不得发布；批次 05 才移除旧 NeoForge ConfigSpec。

## watcher 与重载不变量

- 不添加 `@Config.NoAutoSync`；注册 Holder 后使用 Configuration 内置 `FileWatchManager`。
- 内置 watcher 在后台线程并持有 Holder 锁完成重载；服务器主线程每 tick 只比较 Holder 指纹。
- 指纹变化时，主线程在 Holder 锁内复制完整候选值，严格解析 YAML 的缺失、重复、未知字段及跨字段约束，再确认 Holder 未变化且 YAML 与 Holder 的 saved/pending 值一致，最后发布一个完整根快照。
- 解析失败、框架自动纠正值或 YAML 与 Holder 不一致时不发布，业务继续使用上一份有效快照；管理员修复文件后由下一次内置监视事件重试。
- 保留框架原始生命周期：同一客户端进程退出第一个单人世界后 watcher 永久关闭；第二个世界不再热加载手工文件修改，重启客户端后恢复。该行为只记录、测试和写入文档。

## 混合生效策略

- `plannerThreads`、`plannerQueueCapacity` 标记为 `GAME_RESTART`，规划线程池仅在服务器启动时创建。
- Data Extractor 规则 JSON 只在启动时加载。
- Data Ripper 以快照修订号使目标缓存失效。
- Data Extractor 已持久化到载体的 `requiredAmount` 不追溯修改。
- Flattening TNT 每次爆炸固定一个快照；活动 Data Nuke 每 tick 获取最新完整快照。
- Solar 发电值即时更新；容量加成只在机器加载或卡片变化时重算。
- Trinity Crafting 除线程池参数外在下一次规划时读取新快照。
- Trinity Dispatch 在下一次 grid tick 重建 Governor，并重置 warmup、EWMA、metrics window 和 cooldown 历史。

## 完成定义

1. 63 个 YAML 字段和 64 个旧 TOML 输入均由类型安全案例逐项覆盖。
2. 有效、损坏、部分和重复启动等文件状态全部验证，旧 TOML 哈希保持不变。
3. en_us 与 zh_cn 的 63 个字段键集合完全相等，GUI 使用 `LocalizationKey.FULL`。
4. 内置 watcher 的正常热重载、无效候选保持旧快照及已知单人世界生命周期均有测试。
5. JSON v0 到 v1 的迁移、备份、冲突和严格验证全部通过。
6. 所有消费者只通过新配置边界读取，旧配置链已在最终批次移除。
7. `test`、`spotlessCheck`、`build`、GameTest、客户端与专用服务器启动及打包依赖检查通过。
