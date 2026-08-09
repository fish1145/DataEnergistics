# Trinity Pattern Recipe-ID Resolver

Trinity 需要把支持的 encoded pattern 解析成稳定 recipe ID。AE2 内置 crafting、stonecutting 和 smithing-table components 已有 resolver；第三方 pattern implementation 只有在 recipe identity 不属于这些内置 component 时才需要注册额外 resolver。

## 实现与注册

```java
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolver;

import net.minecraft.resources.ResourceLocation;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

public final class ExampleRecipeIdResolver implements TrinityPatternRecipeIdResolver {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("example_mod", "pattern_recipe");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supports(IMolecularAssemblerSupportedPattern pattern) {
        return pattern instanceof ExampleSupportedPattern;
    }

    @Override
    public ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern) {
        return ((ExampleSupportedPattern) pattern).recipeId();
    }
}
```

在插件入口中注册：

```java
registry.trinityPatternRecipes().register(new ExampleRecipeIdResolver());
```

## 契约

- `id()` 是 resolver 的稳定 registration identity，必须非空且在冻结快照中唯一。
- `supports(pattern)` 只判断该 resolver 是否拥有该 decoded pattern，应无副作用且结果确定。
- 只有 `supports` 返回 `true` 后才会调用 `recipeId(pattern)`。
- `recipeId` 必须返回非空、稳定 recipe `ResourceLocation`。
- recipe ID 会和 resolver ID 一起形成 `TrinityPatternRecipeIdResolution`，并随 Trinity pattern definition 保留，避免 reload 把已排队工作重新解释成另一配方。

使用 pattern 提供的公开、持久化 recipe identity。不要从显示输出、class simple name、运行时对象 identity 或 recipe iteration order 推导 ID。

## 唯一匹配

冻结 lookup 会检查所有 resolvers：

- 没有 resolver 支持时返回 empty；
- `supports` 抛异常时记录日志并隔离该 resolver；
- 准确一个 resolver 支持时调用它的 `recipeId`；
- 多个 resolvers 同时支持时抛出 `IllegalStateException`，不会按注册顺序选第一个；
- `recipeId` 抛异常或返回 `null` 时记录错误并拒绝该 pattern。

因此 `supports` 条件必须和 AE2 built-ins 及其他集成互斥。不要注册一个“所有 `IMolecularAssemblerSupportedPattern` 都支持”的兜底 resolver。

`TrinityPatternRecipeIdLookup` 是冻结后的只读解析表面；插件应通过 registry facet 声明 resolver，而不是寻找或修改内部 resolver collection。
