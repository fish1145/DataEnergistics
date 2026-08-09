# Adaptive Provider Profile 与 Capability

`AdaptivePatternProviderProfile` 是一个安装后 provider stack 的完整、不可变视图：

| 字段 | 约束 |
| --- | --- |
| `slotsPerProvider` | 必须大于零 |
| `mainMenuIcon` | 非空 `ItemStack`；构造和读取时都会复制 |
| `terminalIcon` | 非空 `AEItemKey` |
| `displayName` | 非空 `Component`；构造和读取时都会复制 |
| `capabilities` | 非空 set；构造时冻结 |

不要在 definition 外继续修改用于构造 profile 的 icon、component 或 capability set，也不要依赖对象 identity。消费行为应通过 profile 的值和 `supports(capability)` 判断。

## 内置 capability IDs

`AdaptivePatternProviderCapabilities` 当前提供：

- `METEORITE`：AE2 Crystal Science meteorite provider 处理；
- `ADVANCED_PATTERN`：AdvancedAE-specific pattern handling；
- `FILTERED_IMPORT`：filtered-import option；
- `MECHANICAL_CRAFTING`：Applied Create mechanical-crafting dispatch；
- `RESONATING`：resonating-pattern handling。

Capability 是可组合的 `ResourceLocation`，不是封闭 provider-kind enum。只声明实际实现的能力：

```java
Set.of(
        AdaptivePatternProviderCapabilities.ADVANCED_PATTERN,
        AdaptivePatternProviderCapabilities.FILTERED_IMPORT)
```

不要因为某 item 来自特定 namespace 就自动附加 capability；namespace 本身不证明行为。如果第三方集成需要 Data Energistics 尚未理解的新行为，仅创建自己的 ID 不会自动添加运行逻辑，应先在公共 API 中形成明确契约。

## Profile 一致性

同一个稳定 item state 应解析出语义一致的 profile。slot count、capabilities 或 identity 不应依赖帧时间、客户端本地配置或不稳定迭代顺序。显示名可以来自 stack 的 hover name，但不能反过来用显示名决定是否匹配。

完整注册示例见[注册 Adaptive Pattern Provider](registration.md)。
