# Virtual Crafting Output

Virtual output adapter 把 pattern 声明的 placeholder output 映射成 dispatch-time virtual target，或把它声明为只完成计数、不生成物品的 control token。

## 注册

实现无状态 `VirtualCraftingOutputAdapter`，再通过 `registry.virtualCrafting()` 注册：

```java
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.Optional;

public final class ExampleVirtualOutputAdapter implements VirtualCraftingOutputAdapter {

    @Override
    public Optional<AEKey> resolveTarget(GenericStack declaredOutput) {
        if (!ExampleKeys.isPlaceholder(declaredOutput.what())) {
            return Optional.empty();
        }
        return Optional.of(ExampleKeys.targetOf(declaredOutput.what()));
    }
}

registry.virtualCrafting().registerOutputAdapter(new ExampleVirtualOutputAdapter());
```

`resolveTarget` 查看完整 declared `AEKey` identity 和每 craft amount。返回空表示不认领，返回 target 表示：

- declared placeholder 不再按普通 physical output 完成；
- provider 接受相应 logical craft 后，dispatcher 才创建 target completion；
- adapter 返回的 target 不会递归传给其他 virtual adapters。

数量核算由 dispatcher 完成。adapter 必须无状态，并且不得检查 CPU、provider、grid、world 或 crafting job。

## Completion mode

默认 `completionMode` 是 `DELIVER_TARGET`：完成 adapter 解析出的 target，按普通 virtual output 交付。

对于只代表控制语义、不应生成或交付物品的 placeholder：

```java
@Override
public VirtualCraftingCompletionMode completionMode(GenericStack declaredOutput) {
    return VirtualCraftingCompletionMode.COMPLETE_WITHOUT_OUTPUT;
}
```

`COMPLETE_WITHOUT_OUTPUT` 会完成 declared output 的等待计数，但不会向 CPU、requester 或 network inventory 物化物品。此模式下 declared key 仍是 logical/accounting key。

## 歧义与错误

- `resolveTarget` 必须返回非空 `Optional`；返回 `null` 会记录错误并隔离该 adapter。
- `completionMode` 不得返回 `null`。
- adapter 回调抛出的运行时异常会记录并隔离，其他 adapters 仍会检查该 output。
- 两个 adapters 同时认领同一 declared output 是配置歧义，会抛出 `IllegalStateException`；不会按注册顺序选择。
- 同一个 adapter 实例不得重复注册。
- declared output amount 必须为正；sparse processing-output 列表中的 `null` slot 只代表空洞，不会传给 adapter。

使用完整组件/AEKey identity 匹配 placeholder。不要依赖显示名、物品类名或 internal crafting pattern wrapper。
