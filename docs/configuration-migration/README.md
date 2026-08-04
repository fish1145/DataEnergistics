# Configuration 迁移实施索引

## 基线与锁定结论

本目录是从 `1.21@ae910ee2` 实施 Configuration 迁移的约束与验收基线。

- 依赖固定为 `configuration-1.21.1:3.1.1-neoforge`，以 `jarJar(api(...))` 嵌入。
- 主文件固定为 `config/data_energistics/data_energistics.yaml`。
- 旧 TOML 导入器识别 64 个历史输入，目标 YAML 恰好 63 个叶字段。
- 领域计数为通用 8、Data Extractor 15、Flattening TNT / Data Nuke 14、Solar 4、Trinity Crafting 8、Trinity Dispatch 14。
- Data Extractor 规则继续使用独立 JSON，目标格式为 `schema_version: 1`。
- 所有实现类型按职责命名，禁止使用统一实现后缀命名约定。

## 核心原则

1. 已存在且有效的 YAML 永远优先；旧 TOML 不覆盖、不删除。
2. `displayName` 与 `mipTimeoutMs` 只作为历史键识别，分别丢弃/忽略；`safeRetryBackoffTicks` 缺失时补 8。
3. TOML `maxBindingVariants=512` 升级为 32768；YAML 显式 512 保留。
4. 启动注册 Configuration 前严格预校验 YAML，防止框架自动纠正坏文件。
5. 使用 Configuration 内置 `FileWatchManager`，不建立项目 watcher；服务器 tick 桥接为单个不可变根快照。
6. 保留内置 watcher 在退出首个单人世界后永久关闭的进程级生命周期，不使用 Mixin 修正。
7. `plannerThreads` 与 `plannerQueueCapacity` 为 `GAME_RESTART`；其他字段按领域采用混合生效策略。
8. GUI 全部使用 `LocalizationKey.FULL`；en_us 与 zh_cn 键集合相等，YAML 注释英文在前、中文在后。
9. `CraftingQuantityMode` 磁盘值保持 `NET_NEW`、`FINAL_TOTAL`，客户端适配本地化显示。
10. 六个批次均通过门禁前不得发布；未经授权不推送。

## 文档导航

- [00-scope-and-invariants.md](00-scope-and-invariants.md)：范围、计数与不可破坏约束。
- [01-current-state-and-inventory.md](01-current-state-and-inventory.md)：64 个旧输入与 63 个目标字段清单。
- [02-target-architecture-and-schema.md](02-target-architecture-and-schema.md)：schema、职责命名、内置 watcher 与快照发布。
- [03-localization-and-annotations.md](03-localization-and-annotations.md)：FULL key、双语注释、注解和 enum 显示。
- [04-legacy-migration.md](04-legacy-migration.md)：TOML 导入、特殊兼容和原子写入。
- [05-verification-matrix.md](05-verification-matrix.md)：字段、文件、生命周期与集成验收。
- [06-data-extractor-rules-v1.md](06-data-extractor-rules-v1.md)：规则 JSON v1 schema、v0 迁移和严格验证。
- [batches/00-compatibility-spike.md](batches/00-compatibility-spike.md)：已锁定的 Configuration 3.1.1 能力及探针门禁。
- [batches/01-foundation-and-importer.md](batches/01-foundation-and-importer.md)：依赖、schema、迁移器和 legacy-backed 边界。
- [batches/02-general-domains.md](batches/02-general-domains.md)：通用、Extractor、TNT/Data Nuke、Solar 消费者。
- [batches/03-trinity-domains.md](batches/03-trinity-domains.md)：Trinity Crafting 与 Dispatch 生命周期。
- [batches/04-localization-and-hardening.md](batches/04-localization-and-hardening.md)：本地化与 reload 加固。
- [batches/05-cutover-and-cleanup.md](batches/05-cutover-and-cleanup.md)：原子切换、旧链清理与最终验收。

## 批次依赖

`00 → 01 → 02 → 03 → 04 → 05`

批次 02 与 03 可独立审查，但共享根快照与 Bootstrap，必须在同一迁移分支完成。批次 05 前旧注册只作为过渡兼容来源存在，不构成可发布的双真值。
