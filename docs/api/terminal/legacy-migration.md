# Universal Terminal 旧 API 迁移

Data Energistics 3.0.x 曾暂时保留三个 `util` 包过渡类型，以便已有集成迁移。这些类型已从 3.1.0 起移除，仍引用旧 API 的集成必须按下表迁移：

| 3.0.x 旧类型 | 新 API |
| --- | --- |
| `util.UniversalTerminalAdapter` | `api.registry.terminal.UniversalTerminalBehavior` |
| `util.UniversalTerminalDefinition` | `api.registry.terminal.UniversalTerminalRegistration` 或 `UniversalTerminalRegistry.registerTerminal` |
| `util.UniversalTerminalConfigProfile` | `api.registry.terminal.UniversalTerminalConfigurationProfile` |

这三个类型只在 3.0.x 作为过渡 API 保留，均已标记 `@Deprecated(forRemoval = true)`，并已按计划在 3.1.0 移除。3.1.x 不再提供任何 `util` 兼容表面；`registry`、`part` 和 `menu` 等内部包也不属于外部 API。

## Definition 迁移

旧代码：

```java
UniversalTerminalDefinition definition = new UniversalTerminalDefinition(
        "example_terminal",
        ExampleItems.TERMINAL::is,
        () -> new ItemStack(ExampleItems.TERMINAL),
        ExampleMenus.TERMINAL::get,
        UniversalTerminalConfigProfile.STANDARD);
```

新代码：

```java
UniversalTerminalRegistration registration = UniversalTerminalRegistration
        .builder(
                "example_terminal",
                ExampleItems.TERMINAL::is,
                () -> new ItemStack(ExampleItems.TERMINAL),
                ExampleMenus.TERMINAL::get)
        .configurationProfile(UniversalTerminalConfigurationProfile.STANDARD)
        .build();

registry.universalTerminals().register(registration);
```

也可以直接使用 `registry.universalTerminals().registerTerminal(...)`。

## Adapter 方法映射

| 旧方法 | 新方法 |
| --- | --- |
| `getMenuType()` | `menuType()` |
| `configProfile()` | `configurationProfile()` |
| `resolveMenuHost(UniversalTerminalPart, Player, Class<T>)` | `resolveMenuHost(UniversalTerminalContext, Class<T>)` |
| `name()`、`matches()`、`canInstall()`、`createStoredTerminal()`、`createIcon()` | 方法名不变 |
| `requiresCustomMenuLocator()`、`createConfigManager()` | 方法名不变 |

新 context 有意隐藏 concrete `UniversalTerminalPart`。使用 `context.player()` 获取 player，使用 `context.resolveDefaultMenuHost(hostInterface)` 请求标准 host。不要通过反射重新取回 internal part。

## 迁移步骤

1. 把 imports 从 `util` 三个旧类型切换到 `api.registry.terminal`。
2. 把旧 definition 构造改为 builder 或 `registerTerminal`。
3. 把 bean-style `getMenuType` 改为 `menuType`。
4. 用 `UniversalTerminalContext` 重写自定义 host 解析。
5. 通过 `DataEnergisticsPlugin.register` 的 `universalTerminals()` facet 注册。
6. 删除对内部 `UniversalTerminalPart`、`UniversalTerminalAdapters`、`UniversalTerminalData` 和 menu locator 的直接依赖。
7. 在升级依赖前完成源码替换，再使用 3.1.x 重新编译，确认所有旧类型引用均已清除。

新注册示例见[注册终端](registration.md)，自定义 host 见[自定义行为](custom-behavior.md)。
