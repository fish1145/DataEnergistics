# 批次 01：依赖、Schema 与迁移基础

## 目标

建立完整 Configuration 基础、主 YAML 迁移器、Data Extractor 规则 YAML 和旧 JSON 导入器，但暂不切换生产消费者。过渡期由职责型 legacy-backed 入口提供唯一旧值视图；本批次不可单独发布。

## 构建接入

- 固定 `configuration-1.21.1:3.1.1-neoforge`。
- 使用 `jarJar(api(...))`。
- Mod 元数据声明必需依赖、`[3.1.1,4)`、`AFTER`、`BOTH`。
- 不无依据移除 Cloth Config 或重复添加仓库。

## Schema 与边界

- 新建 `com.fish_dan_.data_energistics.configuration`。
- 根 schema 加领域 schema，精确声明 63 个叶字段。
- 所有分组和字段使用 FULL key、精确范围和首版中英双语注释。
- 构建不可变根快照、领域读取接口、严格 YAML 解析和跨字段验证。
- 内部类型使用 `SnapshotAssembler`、`LegacyConfigBridge`、`SnapshotBacked...` 等职责名，不使用统一实现后缀。
- legacy-backed 边界必须完整覆盖所有生产读取，使过渡期仍只有一份真值。

## 主配置迁移

- Configuration 注册前预校验已有 YAML。
- YAML 不存在时严格导入六份 TOML 的 64 个历史输入，生成 63 项目标。
- 应用 `displayName`、`mipTimeoutMs`、`maxBindingVariants` 和 `safeRetryBackoffTicks` 特殊规则。
- 未知、重复或非法旧键 fail fast。
- 同目录临时文件、UTF-8 无 BOM、flush/fsync、重读验证和原子移动。
- 目标 YAML 已存在时不再读取旧 TOML。

## 规则配置迁移

- 规则 YAML 使用两个 Configuration 分组、合计 10 个原生数组；实现旧 JSON v1 严格读取和无版本 v0 迁移。
- 原始字节备份为 `data_energistics-data_extractor_rules.v0.json`，已有备份必须一致。
- 保留 `_...` 元数据层级，`_mob_rule_examples` 不执行。
- 以不可变 `LoadedRules` 为发布单位；本批次只完成启动加载边界。

## 注册顺序

`预校验/迁移主 YAML → 注册主 Configuration → 核对 Holder → 发布根快照 → 预校验规则 YAML 或导入旧 JSON → 注册规则 Configuration Holder → 发布 LoadedRules`

旧 `ConfigHolder.init` 在过渡期继续注册旧 specs，消费者仍经 `LegacyConfigBridge` 使用旧值。新 schema 不得成为第二份生产真值。

## 实现与验收

- 不新增 Configuration 专项测试，不逐字段或按非法输入排列复制框架行为。
- 通过 schema 审查、一次真实旧文件迁移、生成 YAML 重读和二次启动核对 63/64 映射。
- 通过客户端日志确认主配置与规则配置注册、旧 JSON 导入和内置 watcher。
- 打包 jar 核对嵌入依赖和元数据。
- 禁止反射、源码 contains、空壳 adapter 或占位实现。

门禁：63/64 两套计数均精确、迁移原子幂等、旧文件哈希不变、新配置注册不改变旧消费者行为。
