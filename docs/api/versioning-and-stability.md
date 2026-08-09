# 版本与稳定性

本文定义 Data Energistics 3.0.x 文档所说的“外部 API”范围。它不是对仓库中所有 `public` 类的兼容保证。

## 受支持的外部表面

稳定表面包括：

1. `com.fish_dan_.data_energistics.api.**`；
2. [Universal Terminal 旧 API 迁移](terminal/legacy-migration.md)列出的三个 3.0.x 过渡类型。

新增集成必须使用 `api/**`。过渡类型只用于让已有 Universal Terminal 集成完成迁移，不应成为新代码的依赖。

## 不受支持的表面

除上面明确列出的类型外，下列内容均不构成兼容承诺：

- `common`、`ae2`、`blockentity`、`client`、`integration`、`menu`、`mixin`、`registry`、`util` 等内部包；
- 内部类的构造器、静态字段、单例和集合；
- Mixin 类名、Accessor、Bridge 和实现类；
- 资源加载顺序之外的内部注册顺序或内部异常文本。

即使这些类型当前是 `public`，也可能在 3.0.x 的内部整理中直接移动、改名或收窄。外部模组不得：

- `import` 内部类型；
- 通过反射或 Method Handle 定位内部 FQCN；
- 以内部 FQCN 为 Mixin 目标；
- 把内部 FQCN 持久化到存档、配置、配方、网络数据或跨模组协议。

## 3.0.x 与 3.1.0

`api/**` 是 3.0.x 的目标兼容表面。需要外部调用者迁移的 API 清理应通过文档和弃用信息明确说明，而不是依赖内部包恰好未变化。

以下 Universal Terminal 类型在整个 3.0.x 支持周期内作为过渡 API 保留，并已标记 `@Deprecated(forRemoval = true)`：

- `com.fish_dan_.data_energistics.util.UniversalTerminalAdapter`；
- `com.fish_dan_.data_energistics.util.UniversalTerminalDefinition`；
- `com.fish_dan_.data_energistics.util.UniversalTerminalConfigProfile`。

它们计划在 3.1.0 移除。迁移目标和方法映射见[旧 API 迁移](terminal/legacy-migration.md)。

## 兼容性实践

- 只从 `DataEnergisticsRegistry` 获取对应 registry facet。
- 使用自己的命名空间创建稳定 `ResourceLocation` 注册 ID。
- 把文档中标记为 stable、persisted、canonical 的值当作协议字段维护；不要从显示名或类名推导这些值。
- 对返回 `Optional` 或 `@Nullable` 的扩展点遵守其“未匹配”语义，不使用异常表达普通未匹配。
- 在升级 Data Energistics 或 AE2 时，重新编译并验证运行时回调；API 类型稳定不等于上游 Minecraft/NeoForge/AE2 类型永远不变。
