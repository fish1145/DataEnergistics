# Data Energistics 3.0.x API

本目录记录 Data Energistics 3.0.x 对外扩展 API 的用法、生命周期和兼容性边界。开始集成前，请先阅读[版本与稳定性](versioning-and-stability.md)和[生命周期与线程](lifecycle-and-threading.md)。

## 稳定 API 边界

3.0.x 的稳定外部 API 仅包括：

- `com.fish_dan_.data_energistics.api.**` 下的类型；
- [Universal Terminal 迁移文档](terminal/legacy-migration.md)明确列出的三个过渡类型。

源码中其他 `public` 类型仍属于内部实现。`public` 只代表 Java 可见性，不代表兼容承诺。

外部模组不得依赖内部完全限定类名（FQCN），包括直接 `import`、反射查找、Mixin 目标和写入存档或网络数据。内部包会随着职责拆分而直接迁移或改名。扩展应从 `DataEnergisticsRegistry` 提供的 registry facet 注册，而不是访问内部静态表或运行时实现。

## 文档索引

### 基础约定

- [版本与稳定性](versioning-and-stability.md)
- [生命周期与线程](lifecycle-and-threading.md)
- [插件注册](entrypoint/plugin-registration.md)
- [可选模组加载](entrypoint/optional-mod-loading.md)

### Universal Terminal

- [注册终端](terminal/registration.md)
- [自定义行为](terminal/custom-behavior.md)
- [旧 API 迁移](terminal/legacy-migration.md)

### Pattern Provider

- [元数据与注册](pattern-provider/metadata-and-registration.md)
- [运行时绑定与 counted dispatch](pattern-provider/runtime-and-counted-dispatch.md)
- [菜单和提交回调](pattern-provider/callbacks.md)

### Adaptive Pattern Provider

- [注册 definition](adaptive-pattern-provider/registration.md)
- [profile 与 capability](adaptive-pattern-provider/profiles-and-capabilities.md)

### Crafting

- [Counted dispatch 契约](crafting/counted-dispatch-contract.md)
- [Virtual output](crafting/virtual-output.md)

### Trinity

- [配方 ID resolver](trinity/recipe-id-resolver.md)

仓库内部的命名和目录维护规则另见[命名与包结构约定](../architecture/naming-and-package-conventions.md)。
