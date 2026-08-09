# 插件注册

所有 Data Energistics 扩展都从一个公共入口注册。入口类实现 `DataEnergisticsPlugin`，使用 `@DataEnergisticsEntrypoint` 标记，并通过 `DataEnergisticsRegistry` 选择需要的 facet。

## 最小入口

```java
package com.example.integration;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

@DataEnergisticsEntrypoint
public final class ExampleDataEnergisticsPlugin implements DataEnergisticsPlugin {

    public ExampleDataEnergisticsPlugin() {}

    @Override
    public void register(DataEnergisticsRegistry registry) {
        // 从 registry 获取所需 facet 并在此声明扩展。
    }
}
```

入口必须满足以下条件：

- 是 `public` concrete class；
- 实现 `DataEnergisticsPlugin`；
- 有 `public` 无参构造器；
- 类上存在 `@DataEnergisticsEntrypoint`；
- 构造和 `register` 不依赖世界或运行中的服务器状态。

扫描器从 NeoForge bytecode scan data 发现入口，不要求手工注册入口类名。候选按 owning mod ID 和类名排序后加载。

## Registry facets

`DataEnergisticsRegistry` 当前提供：

| Facet | 用途 |
| --- | --- |
| `universalTerminals()` | Universal Terminal 定义 |
| `patternProviders()` | provider 元数据、factory 和 lifecycle callback |
| `adaptivePatternProviders()` | adaptive provider definition |
| `trinityPatternRecipes()` | Trinity pattern recipe-ID resolver |
| `virtualCrafting()` | virtual crafting output adapter |

优先通过这些 facet 声明扩展。不要访问 `registry`、`common` 或 `util` 包中的内部静态集合；这些集合不是 API，也不提供晚注册保证。

## 原子事务与冲突

一个入口的一次 `register` 是原子 staging transaction。回调正常结束且所有唯一性校验通过后才提交；如果构造、注册或提交校验失败，该入口已暂存的所有 terminal、provider、resolver 和 adapter 都会一起丢弃并记录日志。

常见冲突包括：

- 重复 Universal Terminal 持久化名称；
- 重复 pattern-provider registration ID；
- 两项 pattern-provider registration 使用相同 identity descriptor；
- 重复 adaptive-provider registration ID；
- 重复 Trinity resolver ID；
- 同一 virtual-output adapter 实例重复注册。

不要捕获这些冲突后继续提交一个不完整入口。应使用自己模组的命名空间和稳定 ID，在开发阶段直接修正声明。

## 生命周期警告

`DataEnergisticsRegistry` 和各 facet 仅在 `register` 回调中有效。不要把它们保存到静态字段、实例字段、lambda 延迟任务或服务器启动后的缓存中。注册结束后，Data Energistics 发布的是复制并冻结后的运行时值，而不是一个可继续修改的 registrar。

可选依赖的入口写法见[可选模组加载](optional-mod-loading.md)。
