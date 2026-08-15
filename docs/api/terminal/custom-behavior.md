# Universal Terminal 自定义行为

当标准 builder 无法表达安装校验、存储副本或 menu host 投影时，实现 `UniversalTerminalBehavior` 并注册该行为。

```java
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalBehavior;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalContext;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

public final class ExampleTerminalBehavior implements UniversalTerminalBehavior {

    @Override
    public String name() {
        return "example_terminal";
    }

    @Override
    public boolean matches(ItemStack stack) {
        return ExampleItems.TERMINAL.is(stack);
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(ExampleItems.TERMINAL);
    }

    @Override
    public MenuType<?> menuType() {
        return ExampleMenus.TERMINAL.get();
    }

    @Override
    public <T> @Nullable T resolveMenuHost(UniversalTerminalContext context, Class<T> hostInterface) {
        return context.resolveDefaultMenuHost(hostInterface);
    }
}
```

注册：

```java
registry.universalTerminals().register(new ExampleTerminalBehavior());
```

## 可覆盖行为

| 方法 | 默认行为或要求 |
| --- | --- |
| `name()` | 必须返回稳定、非空白的持久化名称 |
| `matches(stack)` | 必须实现；只负责候选识别 |
| `canInstall(stack)` | 默认再次使用 `matches`；可添加最终安装约束 |
| `createStoredTerminal(stack)` | 默认 `copyWithCount(1)`；返回值必须非空，运行时会再次规范为 count 1 |
| `createIcon()` | 必须返回非空 icon；运行时保存独立 copy |
| `menuType()` | 必须返回已注册 menu type |
| `requiresCustomMenuLocator()` | 默认 `false` |
| `createConfigManager(saveAction)` | 默认 `null`，表示无 host-local 配置 |
| `resolveMenuHost(context, type)` | 默认委托 `context.resolveDefaultMenuHost(type)` |
| `configurationProfile()` | 默认 `STANDARD` |

## Context 边界

`UniversalTerminalContext` 只公开：

- 当前 `Player`；
- 按接口类型请求标准 menu host projection。

不要强制转换 context，也不要依赖内部 `UniversalTerminalPart`、context bridge 或 menu locator 实现。若默认 host 无法满足你的 menu，应由 behavior 返回一个实现目标 host interface 的稳定投影；不要把内部 part FQCN 变成跨模组契约。

## 所有权和错误

- `matches`、`canInstall` 不得修改候选 stack。
- `createStoredTerminal` 必须返回独立拥有的非空 stack；最安全的做法是 copy。
- `createIcon` 每次返回可独立使用的非空 stack。
- `createConfigManager` 返回 `null` 是合法的“无需配置”，其他声明为非空的回调不得返回 `null`。
- 外部回调异常会在匹配或安装等边界记录并隔离，但注册名称冲突和多 matcher 命中属于真实歧义，不会静默兜底。

旧 `UniversalTerminalAdapter` 的迁移方法见[旧 API 迁移](legacy-migration.md)。
