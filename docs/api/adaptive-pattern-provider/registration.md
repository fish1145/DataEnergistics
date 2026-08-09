# 注册 Adaptive Pattern Provider

Adaptive Pattern Provider definition 把第三方 provider item 映射成一个完整 `AdaptivePatternProviderProfile`。从 `registry.adaptivePatternProviders()` 注册，不需要向内部 resolver 增加模组分支。

```java
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderProfile;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;

import java.util.Set;

AdaptivePatternProviderRegistration registration = new AdaptivePatternProviderRegistration(
        ResourceLocation.fromNamespaceAndPath("example_mod", "adaptive_provider"),
        providerStack -> {
            if (!ExampleItems.PATTERN_PROVIDER.is(providerStack)) {
                return null;
            }

            var icon = providerStack.copyWithCount(1);
            AEItemKey terminalIcon = AEItemKey.of(icon);
            if (terminalIcon == null) {
                throw new IllegalStateException("Example provider has no AE item key");
            }
            return new AdaptivePatternProviderProfile(
                    9,
                    icon,
                    terminalIcon,
                    icon.getHoverName(),
                    Set.of());
        });

registry.adaptivePatternProviders().register(registration);
```

## Definition 契约

`AdaptivePatternProviderDefinition.resolve` 接收一个非空候选 stack：

- 返回 `null` 表示该 definition 不识别候选；
- 返回 profile 表示完整认领，profile 的每个字段都必须有效；
- 不得保留或修改传入 stack；
- 应使用精确 item/组件/registry identity 匹配，不使用显示名、Java 类名或 namespace heuristic；
- 应无副作用，不假设只调用一次或固定线程。

registration ID 必须稳定且全局唯一，建议使用集成模组自己的 namespace。重复 ID 会使当前插件的原子 transaction 失败。

运行时 definitions 会按 registration ID 排序后查询。排序只用于确定性，不用于优先级：两个 definitions 同时返回 profile 时会抛出 ambiguity error，不会让较早注册项获胜。

definition 抛出的运行时异常会被记录并隔离，resolver 会继续检查其他 definitions。普通未匹配必须返回 `null`，不要用异常表达。

profile 字段和 capability 约定见[Profile 与 Capability](profiles-and-capabilities.md)。
