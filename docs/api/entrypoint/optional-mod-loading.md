# 可选模组加载

当入口类会直接引用可选模组的类时，把对应 mod ID 写入 `@DataEnergisticsEntrypoint.requiredMods`。扫描器先从 bytecode scan data 读取该成员并检查模组是否加载，满足条件后才解析入口类。

```java
package com.example.integration;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

@DataEnergisticsEntrypoint(requiredMods = "example_machine_mod")
public final class ExampleMachinePlugin implements DataEnergisticsPlugin {

    public ExampleMachinePlugin() {}

    @Override
    public void register(DataEnergisticsRegistry registry) {
        // 只有 example_machine_mod 已加载时，这个类才会被解析和实例化。
    }
}
```

多个前置模组使用数组：

```java
@DataEnergisticsEntrypoint(requiredMods = {"first_mod", "second_mod"})
public final class CombinedIntegrationPlugin implements DataEnergisticsPlugin {
    // ...
}
```

## 两种可选集成方式

直接链接方式适合确实需要第三方类型的集成：

- 把实现放入独立 integration package；
- 在入口注解中列出全部直接类加载前置；
- 不要在无保护的公共 bootstrap 类中引用该 integration 类。

Registry-ID 方式适合只需要识别物品或方块的声明：使用 `ResourceLocation`/registry key 比较，不引用可选模组实现类。这样入口本身可以安全加载，并且无需为每个模组写一条类分支。

## 重要限制

- `requiredMods` 必须是非空白 mod ID；重复值会被去重并排序。
- 缺少任一 required mod 时，入口会被跳过，而不是部分注册。
- 注解只保护被标记的入口类。若其他提前加载的类、静态字段或公共注册类直接引用可选类型，仍可能触发 `LinkageError`。
- 入口所在的 scanned mod file 必须能解析出唯一 owning mod。一个文件声明多个无法区分的 owners 时，该文件中的入口会被忽略并记录错误。
- 不要通过反射探测第三方内部类来代替 `requiredMods` 或 registry-ID 匹配。

入口的原子提交和 facet 说明见[插件注册](plugin-registration.md)。
