# Crafting Machine 容量与样板上传事务

`registry.craftingMachines()` 为一个 block-entity machine 暴露彼此独立的两类能力：

- remaining capacity：补充普通 inventory insertion 无法表达的 queue、parallel slot 或内部容量；
- pattern upload workstation：只读描述 live machine variant，并在 exact provider leaf 真正接受样板时验证或事务性调整配方槽、端口等机器自有状态。

注册其中一项不会强制注册另一项，也不会要求机器本身实现 pattern provider。

## 共享 scope

新 machine capability 使用 `CraftingMachineScope`：

- `BLOCK_ENTITY`：同一 block entity 的所有输入面共享一份容量或状态；
- `INPUT_SIDE`：每个输入面独立。

runtime 按 scope 去重。一个 provider 通过多个 route 到达同一个 `BLOCK_ENTITY` 工作站时，同一次 leaf upload 只 prepare 一次；`INPUT_SIDE` 则按 face 分别 prepare。

3.2 已发布的 `CraftingMachineCapacityRegistration.scope()` 仍返回 `CraftingMachineCapacityScope`，以保持到 3.3.0 前的二进制兼容；新代码可调用 `machineScope()`。Data Energistics 提供的旧 `craftingMachineCapacities()` facet 与新 `craftingMachines()` 共享同一个 staging transaction，不维护第二份注册表。

## 容量注册

```java
registry.craftingMachines().registerCapacity(
        CraftingMachineCapacityRegistration.blockEntity(
                ResourceLocation.fromNamespaceAndPath("example_mod", "alloy_furnace_capacity"),
                ResourceLocation.fromNamespaceAndPath("example_mod", "alloy_furnace"),
                context -> Optional.of(new CraftingMachineCapacity(
                        readRemainingJobs(context.machine(), context.patternDetails())))));
```

capacity callback 是只读观察。空 `Optional` 表示该已注册机器类型不处理当前 pattern；present zero 是权威的无容量，不能退回普通 insertion simulation。

## 样板上传注册

```java
registry.craftingMachines().registerPatternUploadWorkstation(
        PatternUploadWorkstationRegistration.blockEntity(
                ResourceLocation.fromNamespaceAndPath("example_mod", "alloy_furnace_pattern_upload"),
                ResourceLocation.fromNamespaceAndPath("example_mod", "alloy_furnace"),
                context -> prepareAlloyMode(context)));
```

`PatternUploadWorkstationAdapter` 仍是 functional interface；简单 adapter 只实现 `prepare(...)`。需要让 live machine mode 参与上传面板分组时，使用普通实现类同时覆盖只读的 `inspect(...)` default method。inspection 不得修改机器或创建事务。

`PatternUploadWorkstationContext` 给 adapter 完整但短生命周期的服务端事实：

- `player()`：发起上传的 `ServerPlayer`；
- `provider()` 与 `providerIdentity()`：本次可能接收样板的 exact leaf，而不是 UI group；
- `level()`、`workstationPosition()`、`inputSide()`、`workstation()`：实际 machine route；
- `patternDetails()`：由服务端当前世界解码的 `IPatternDetails`；
- `patternDetails().getDefinition()`：immutable `AEItemKey`，保留编码物品与全部 data components，避免为每个工作站复制 `ItemStack`；
- `recipeTypeId()`：可空的、从最终 encoded pattern 恢复或推导出的 recipe-type/category hint；crafting、smithing、stonecutting 和 processing 都可能提供，只能辅助定位候选，必须用 `patternDetails()` 和当前服务端配方再次验证；
- `recipeId()`：可空的 stable processing recipe ID；它独立于按 recipe type 统计的 provider history，用于锁定具体 server recipe，但仍必须与 `patternDetails()` 完整内容一致；
- `requestedPatternCount()`：仍等待当前 leaf 的正数数量。

这些对象只能在同步 callback 内检查。不要保存 player、level、provider、block entity 或 decoded pattern。需要跨越 inventory mutation 的状态，只能压缩进返回的 `PreparedPatternUploadChange`。

## 四种 prepare 结果

`PatternUploadWorkstationPreparation` 明确区分：

- `pass()`：该已注册 machine type 不处理当前 pattern，普通上传继续；
- `accepted()`：机器识别该 pattern，当前 live variant 已经兼容，不需要修改机器；
- `rejected(Component)`：机器识别该 pattern，但不能安全配置；当前 leaf 被跳过，全部 leaf 都拒绝时把原因发送给玩家；
- `prepared(change)`：返回一个可逆、一次性的 machine change。

不要把“无法识别”当成 rejection，否则一个 provider 同时连接多种机器时，无关机器会阻止合法上传。不要根据客户端传来的模式、viewer display name 或机器当前模式猜测；应使用当前 server recipes 和 decoded pattern contents 验证。

## Live variant 与上传面板分组

`inspect(PatternUploadWorkstationInspectionContext)` 可以返回稳定的 `PatternUploadWorkstationVariant` 和当前 pattern compatibility：

inspection context 同样提供 `player()`、exact `provider()`、`providerIdentity()` 和实际 machine route；只有尚未编码时 `patternDetails()` 允许为空，且 inspection 始终只读。这样第三方 adapter 可以按权限、供应器类型或稳定身份描述 live variant，而不必等到 `prepare(...)` 才发现上下文不足。

- `UNKNOWN`：没有 encoded pattern 或信息不足；仍按 variant 分组，但不标 exact；
- `COMPATIBLE`：当前 live variant 可以执行服务端验证后的 pattern；该组优先显示；
- `INCOMPATIBLE`：机器识别 pattern，但当前 variant 不匹配；单独分组且不标 exact。

aggregation key 同时包含原 provider group、排序后的 variant IDs 和 compatibility。多个 provider 分别连接不同模式机器时会形成不同上传行；一个 provider leaf 同时连接多个模式时只形成一个 mixed row，不能把同一个 pattern inventory 伪装成两个可独立路由的 leaf。

点击上传时不会信任 panel snapshot，而是重新解析该 leaf 的全部 actual routes。只要一个实际可接收工作站返回 mismatch/rejection，整个 leaf 就不可用；同一 leaf 的所有工作站都兼容后才允许写 pattern inventory。

Data Integrated Charger 使用该规则只验证当前模式，不会因上传样板而自动切换任意机器模式。

## Stable processing recipe ID

viewer transfer 会把具体 processing recipe ID 与 recipe type 分开保存：

- recipe type 继续作为 provider ranking 和点击历史的聚合键；
- recipe ID 追加到最终 encoded pattern，用于精确配方和模式解析；
- Data Integrated Charger 的统一类别 ID 包含 recipe family 与 source recipe ID，JEI/EMI 生成相同值；
- 服务端先以 stable ID 查找当前 recipe，再验证完整 inputs/outputs；过期或伪造 ID不能直接决定模式；
- 旧样板没有 recipe ID 时继续使用 content-only fallback，唯一模式仍可上传，跨模式歧义仍拒绝。

## 事务顺序

每个 provider leaf 独立执行：

1. 全 group duplicate 检查完成；duplicate 不 prepare、不 apply；
2. 解析该 exact leaf 的当前工作站 routes；
3. 对适用的机器调用 adapter prepare；
4. 按稳定 route 顺序调用所有 `change.apply()`；
5. 尝试写 provider pattern inventory；
6. 读取真实 inventory delta，不信任第三方 inventory 的返回 remainder；
7. delta 大于零：调用 `complete(committedPatternCount)`；
8. delta 为零或写入前失败：按反向顺序调用 `rollback()`。
9. provider 已开始 mutation、但第三方 inventory 阻止 runtime 证明 delta：不允许 rollback，调用 `completeIndeterminate()`，保守刷新并保存 provider，停止继续尝试 leaf，保留 encoder pattern并提示玩家检查 provider；由于缺少准确 count，不调用 provider post-commit observer。

`apply()` 只能执行 rollback 能完整恢复的修改；即使 `apply()` 自己在部分修改后抛异常，随后调用 `rollback()` 也必须安全。持久化、客户端同步、不可逆队列操作等应放在 `complete` 或 `completeIndeterminate`。`rollback` 必须恢复 apply 前的精确状态，而不是重置为默认值。两个 complete 回调发生时 provider inventory 已经提交或可能已经提交，不能再 veto；因此实现必须保持短小、确定、无外部 I/O。

group upload 可以跨多个 leaf 分摊一叠 pattern。每个 leaf 都重新 prepare，不能复用另一个 leaf 的事务或旧 machine snapshot。一个 leaf 容量不足并 rollback 后，runtime 可继续尝试后续 leaf。

## 自定义 provider 路由

普通 AE2 provider 自动使用 `PatternProviderLogic` 的 active sides。remote/custom provider 应通过 `PatternProviderRegistry.registerWorkstationSource(...)` 注册独立的 `PatternProviderWorkstationSourceRegistration`，或让其 `PatternContainer` 实现 `PatternProviderWorkstationSource`，返回 actual live `PatternProviderWorkstationTarget`。

source 只声明拓扑；machine-specific 配方判断与状态事务仍属于 workstation adapter。这样 provider integration 和 machine integration 可以由不同 Mod 分别注册，而不需要相互硬编码类名。

## 上传入口边界

上述事务由 Data Energistics 自带的 terminal provider uploader 调度；它不是对任意 Mod 上传函数的全局拦截。外部 uploader 若绕过该入口，必须有单独的、能覆盖其真实 inventory mutation 的兼容接入，不能在事后 observer 中伪造 preflight/rollback。当前 ExtendedAE-Plus 的直接 provider upload 只接入成功历史记录，不执行本事务；matrix upload 也不属于 `PatternContainer` workstation route。
