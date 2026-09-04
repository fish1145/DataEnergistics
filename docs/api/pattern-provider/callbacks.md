# Pattern Provider 菜单和提交回调

`PatternProviderRegistration` 可独立声明 menu-open adapter 和 post-commit hook。custom workstation source 使用独立的 `PatternProviderWorkstationSourceRegistration`；这些声明仍在同一个插件 staging transaction 中原子提交。

## Menu-open adapter

`PatternProviderMenuOpenAdapter.open` 接收 `PatternProviderMenuOpenContext`：

- `player()` 是发起请求的 `ServerPlayer`；
- `providers()` 是 terminal 选中的完整 provider group 的不可修改 copy。

返回值：

- `OPENED`：adapter 已成功打开菜单，停止后续处理；
- `DENIED`：adapter 识别该 group，但明确拒绝打开；
- `PASS`：交回 Data Energistics，继续默认 AE2/`MenuProvider` resolver。

```java
PatternProviderMenuOpenAdapter menuAdapter = context -> {
    if (!canOpen(context.player(), context.providers())) {
        return PatternProviderMenuOpenResult.DENIED;
    }
    openExampleMenu(context.player(), context.providers());
    return PatternProviderMenuOpenResult.OPENED;
};
```

只有 group 中所有 providers 都解析到同一个 plugin registration 时，才调用该 registration 的 adapter。混合 registrations、identity 解析异常或 adapter 异常会记录错误并拒绝该次打开；不会任选 group 中的第一个 provider。若 group 没有解析到插件 registration，则继续尝试默认 AE2/`MenuProvider` resolver。

返回 `OPENED` 前必须真的完成服务端菜单打开。普通“不属于我”应返回 `PASS`，不要抛异常。

## Post-commit hook

`PatternProviderPostCommitHook.afterCommit` 在服务端已确认真实 provider inventory delta 后调用。context 包含：

- `provider()`：发生改变的 `PatternContainer`；
- `identity()`：stable public provider identity；
- `encodedPattern()`：防御性复制的 encoded-pattern stack；
- `committedCount()`：严格大于零的真实提交数量。

```java
PatternProviderPostCommitHook hook = context -> {
    auditCommittedPatterns(
            context.identity().digest(),
            context.encodedPattern(),
            context.committedCount());
};
```

hook 是 completed commit observer：

- 不能 veto、回滚或改写提交；
- 不得假设 `encodedPattern()` 返回可修改内部状态；每次访问得到防御性 copy；
- 异常会被记录并隔离，不会让已发生的 inventory delta 消失；
- 不要在 hook 中再次提交同一 provider，避免递归和重复副作用。

需要只提供 callback 而不提供 counted factory 时，把其他可选字段设为 `null`，但至少保留一个实际行为。完整 registration 示例见[元数据与注册](metadata-and-registration.md)。

## Workstation source

`PatternProviderWorkstationSource` 只解决“这个 exact provider leaf 的 crafting inputs 实际会发到哪里”。标准 AE2 provider 的 active sides 由 Data Energistics 直接读取；只有 remote route、自定义路由或无法表达为普通邻接的 provider 才需要 source。

source context 包含：

- 发起上传的 `ServerPlayer`；
- exact `PatternContainer` leaf 与 stable public identity；
- server-decoded `IPatternDetails`；panel 尚无 encoded pattern 时可以为空；
- pattern 非空时，`patternDetails().getDefinition()` 返回 immutable `AEItemKey`，包含编码物品及其完整 components；
- 最终 encoded pattern 中可恢复或推导出的 recipe-type/category hint；
- viewer transfer 捕获的 stable processing recipe ID；
- 本次仍等待该 leaf 的 pattern count；pattern-less panel grouping 时为零。

返回 `ObjectList<PatternProviderWorkstationTarget>`，每一项包含实际 live `BlockEntity` 和该机器收到输入的 face。返回顺序必须稳定；runtime 在 callback 返回时复制一次目标引用并立即校验，不会长期保留 source 自有集合。不要返回 terminal group 的第一个 provider、viewer catalyst 或仅用于显示的 workstation；模式变更会绑定到真实接收样板的 leaf 和它的真实 dispatch routes。

显式 source registration 优先于 provider 自身实现的 source；两者都没有时才使用标准 AE2 active-side resolver。source 返回的目标会按机器 registration 的 `CraftingMachineScope` 去重。
