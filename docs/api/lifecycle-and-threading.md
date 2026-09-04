# 生命周期与线程

Data Energistics 把“声明扩展”和“运行扩展”分成两个阶段。集成不应保存注册阶段的可变句柄，也不应把短生命周期的运行上下文放进缓存。

## 注册阶段

`DataEnergisticsPlugin.register(DataEnergisticsRegistry)` 在 common setup 的插件装配阶段调用。每个插件得到一个独立的 staging transaction：

1. 扫描并按 owning mod ID、入口类名进行确定性排序；
2. 创建插件专属的 `DataEnergisticsRegistry`；
3. 调用一次 `register`；
4. 校验该插件的全部声明；
5. 原子提交，或在异常时丢弃该插件的全部声明；
6. 所有插件结束后冻结成不可变运行时快照。

因此：

- 不要在字段中保留 `DataEnergisticsRegistry` 或任何 facet；回调返回后句柄已关闭。
- 不要在 `register` 中访问世界、服务器或运行中的 AE grid。
- 一项声明冲突可能导致当前插件的所有 staged registrations 一起被丢弃；不要假设已调用的前几项会部分生效。
- 一个插件失败会记录日志并与其他插件隔离；不能依赖失败插件留下的半成品状态。

## 运行阶段

冻结后，运行时代码只读取不可变声明快照。各扩展点有不同约束：

| 扩展点 | 明确的调用边界 |
| --- | --- |
| `CountedCraftingProviderAdapter` | server thread；prepare/capacity 阶段只读，commit 仍在同一 server thread |
| `PatternProviderFactory` | live provider 绑定时调用；`PatternProviderFactoryContext` 只在该回调期间有效，不得保留 |
| `PatternProviderMenuOpenAdapter` | 使用 `ServerPlayer` 的服务端菜单请求 |
| `PatternProviderPostCommitHook` | 服务端已确认真实 inventory delta 之后；只能观察，不能 veto 或改写提交 |
| `PatternProviderWorkstationSource` | server thread；为 exact provider leaf 返回当前真实工作站路线；不得返回 UI 分组或缓存的 display 目标 |
| `CraftingMachineCapacityAdapter` | server thread；capacity capture 与提交前重验只读，不得预留或消耗资源 |
| `PatternUploadWorkstationAdapter` | server thread；prepare 只构造可逆 change，runtime 在 provider 写入前 apply，并按真实 delta complete/rollback；mutation 后 delta 不可证明时走 completeIndeterminate |
| `VirtualCraftingOutputAdapter` | 必须无状态；不得检查 CPU、provider、grid、world 或 crafting job |
| `AdaptivePatternProviderDefinition` | 接收候选 `ItemStack`；不得保留或修改它；文档未承诺固定线程或调用次数 |
| `TrinityPatternRecipeIdResolver` | 冻结 resolver 快照中的运行时查询；应无副作用且结果确定 |
| `UniversalTerminalBehavior` | 匹配、安装校验、图标和菜单解析可能被多次调用；行为应确定且避免依赖调用顺序 |

对于未明确承诺线程的回调，不要自行假设 client thread、server thread 或只调用一次。若需要世界操作，应把能力放在明确提供服务端上下文的回调中。

## 可变对象与所有权

- `ItemStack` 是可变对象。只有契约明确转移所有权时才可保存；否则复制或只读访问。
- `PatternProviderFactoryContext`、菜单、post-commit、workstation source 和 machine upload context 都是一次调用的事实快照，不是长期 host 句柄。
- Counted dispatch 的 prototype 在 prepare/capacity 阶段必须只读；所有权规则详见[counted dispatch 契约](crafting/counted-dispatch-contract.md)。
- 对外部回调抛出的运行时异常，Data Energistics 会按各扩展点的约定记录并隔离；集成仍应 fail fast，并提供包含自身注册 ID 的可诊断日志。
