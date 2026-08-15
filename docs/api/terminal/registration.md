# 注册 Universal Terminal

从 `DataEnergisticsRegistry.universalTerminals()` 获取 `UniversalTerminalRegistry`，在插件注册阶段声明终端。新集成和从 3.0.x 升级的集成都必须使用 `api.registry.terminal`；旧 `util.UniversalTerminalDefinition` 已从 3.1.0 起移除。

## 标准注册

不需要自定义 host 行为时，使用 `registerTerminal`：

```java
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;

import net.minecraft.world.item.ItemStack;

public void register(DataEnergisticsRegistry registry) {
    UniversalTerminalRegistry terminals = registry.universalTerminals();
    terminals.registerTerminal(
            "example_terminal",
            ExampleItems.TERMINAL::is,
            () -> new ItemStack(ExampleItems.TERMINAL),
            ExampleMenus.TERMINAL::get);
}
```

参数含义：

- `name`：稳定且非空白的持久化名称；同一运行时快照中必须唯一；
- `matcher`：判断候选 `ItemStack` 是否由该声明识别；
- `iconSupplier`：每次创建非空 menu icon；
- `menuTypeSupplier`：返回已注册的 `MenuType<?>`。

名称会进入 Universal Terminal 的持久化数据。发布后不得为了显示效果改名；显示名来自 icon 的 hover name，不需要把本地化文本塞进稳定名称。

## 带配置的注册

```java
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalConfigurationProfile;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistration;

UniversalTerminalRegistration registration = UniversalTerminalRegistration
        .builder(
                "example_pattern_access",
                ExampleItems.PATTERN_ACCESS_TERMINAL::is,
                () -> new ItemStack(ExampleItems.PATTERN_ACCESS_TERMINAL),
                ExampleMenus.PATTERN_ACCESS_TERMINAL::get)
        .configurationProfile(UniversalTerminalConfigurationProfile.PATTERN_ACCESS)
        .configManagerFactory(saveAction -> createConfigManager(saveAction))
        .build();

registry.universalTerminals().register(registration);
```

内置 configuration profile 只有：

- `STANDARD`：标准 AE2 terminal settings；
- `PATTERN_ACCESS`：包含 provider visibility 的 pattern-access settings。

`configManagerFactory` 可省略；省略表示该终端不需要 host-local `IConfigManager`。需要 terminal-name-aware menu locator 时才设置 `.requiresCustomMenuLocator(true)`。

## 匹配与安装

运行时会检查所有 terminal registrations：

- matcher 抛出异常时会记录日志并隔离该 registration；
- 没有 matcher 接受时，该物品不是受支持终端；
- 两个 registrations 同时接受同一 stack 时属于歧义，运行时抛出 `IllegalStateException`，不会按注册顺序任选一个；
- matcher 接受后还会调用 `canInstall`；标准 builder 的行为等同于 matcher。

因此 matcher 应使用精确 registry identity 或完整组件条件，并与其他集成保持互斥。不要以显示名、翻译文本、Java 类名或模组命名空间猜测终端。

需要自定义安装副本、host 或配置行为时，见[自定义行为](custom-behavior.md)。
