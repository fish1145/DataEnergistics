# Pattern Provider 元数据与注册

Pattern Provider extension 把“如何识别 provider family”和可选运行行为放进同一个 `PatternProviderRegistration`。从 `registry.patternProviders()` 注册，不能只向内部运行时表塞 factory。

## 元数据

```java
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

PatternProviderMetadata metadata = new PatternProviderMetadata(
        ResourceLocation.fromNamespaceAndPath("example_mod", "alloy_furnace"),
        new ProviderIdentityDescriptor.Block(
                ResourceLocation.fromNamespaceAndPath("example_mod", "alloy_furnace")),
        List.of(ResourceLocation.fromNamespaceAndPath("example_mod", "alloying")),
        List.of(ResourceLocation.fromNamespaceAndPath("example_mod", "alloy_furnace")));
```

字段含义：

- `registrationId`：该插件声明的稳定唯一 ID；
- `providerIdentity`：所有 live instances 共享的 provider-family schema；
- `recipeCategoryIds`：provider 理解的完整 recipe-category ID 集合；
- `workstationItemIds`：provider 理解的完整 workstation item-ID 集合。

category 和 workstation 列表会复制、去重并按 `ResourceLocation.toString()` 排序。匹配比较完整 ID；显示名、Java 类名和 namespace heuristic 都没有隐含匹配语义。

## Identity descriptor

选择能稳定描述 provider family 的 descriptor：

- `ProviderIdentityDescriptor.Block(blockEntityTypeId)`：一个注册的 block-entity type；
- `ProviderIdentityDescriptor.Part(partItemId)`：一个可重建 part 的注册 item；
- `ProviderIdentityDescriptor.Trinity.INSTANCE`：Data Energistics Trinity family；
- `ProviderIdentityDescriptor.External(type, schemaVersion)`：外部自定义 identity schema。

`External` 的 `schemaVersion` 必须大于零。它只描述 family；live instance 的 canonical fields 由 `ExternalPatternProviderIdentity` 提供。不要把位置、显示名或临时 routing key 放入 declaration-time descriptor。

## 原子 registration

只有 counted factory：

```java
registry.patternProviders().registerFactory(metadata, context ->
        new ExampleCountedCraftingAdapter(context.provider()));
```

组合多个行为：

```java
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;

PatternProviderRegistration registration = new PatternProviderRegistration(
        metadata,
        context -> new ExampleCountedCraftingAdapter(context.provider()),
        context -> openProviderGroup(context),
        context -> onCommitted(context));

registry.patternProviders().register(registration);
```

`factory`、`menuOpenAdapter`、`postCommitHook` 可以分别为 `null`，但三者不能全为 `null`。metadata-only declaration 会立即抛出 `IllegalArgumentException`。

同一冻结快照中，`registrationId` 和 `providerIdentity` 都必须唯一。重复任一项会使当前插件的整个 staging transaction 失败，而不是覆盖已有声明。

运行时 identity 和 counted factory 说明见[运行时绑定与 counted dispatch](runtime-and-counted-dispatch.md)，回调结果语义见[菜单和提交回调](callbacks.md)。
