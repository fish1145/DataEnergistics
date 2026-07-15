# 存储、CPU 与合成子 UI 增强

## 1. 范围与命名

本章是 LDLib2 迁移完成后的业务增强，不重新设计另一套 Screen。四个 hosted 子 UI 的内部 key 保持兼容，玩家可见职责如下：

| 内部 key | 玩家可见名称 | 增强职责 |
| --- | --- | --- |
| `main` | 存储 | 主结构 Scene、逐项 `AEKey` 存储量、结构状态 |
| `cpu` | CPU | CPU 结构 Scene、合成任务、跳转 AE 原生 CPU 状态界面 |
| `crafting` | 合成 | 合成结构 Scene、结构内样板槽统一入口、当前合成详情 |
| `auto_build` | 自动搭建 | 结构 Scene、单目标设置、检查、确认和结果 |

四窗继续独立拖动并拥有各自 Scene。业务增强只替换右侧 workbench 内容，不把 Scene、库存、任务和样板重新堆到根界面。

## 2. 共用业务外框

正常视口使用 `352x220`，左侧 `196px` Scene 列，右侧 `144px` workbench。存储/CPU 可回落到 `292x210`（`168px` Scene + `112px` workbench）；合成页为容纳稳定 `8x8` 槽池，compact 仍至少为 `320x210`（`168px` Scene + `144px` workbench）。背景、页签、行状态、进度和分隔全部使用 Modular UI 元素与 LSS/Style，不新增或引用 DataE GUI PNG、精灵图、九宫格和状态图集。

```text
┌────────────────────────────────┬─────────────────────────┐
│                                │ [窗口专属页签]           │
│                                ├─────────────────────────┤
│         独立 Scene              │                         │
│                                │ 当前业务页              │
│                                │                         │
├────────────────────────────────┤                         │
│ 参数 / 材料 / 选中方块 Dock     │ 固定 action/footer      │
└────────────────────────────────┴─────────────────────────┘
```

规则：

- 切业务页不销毁、不重建 Scene，也不重置相机、layer 或选中方块。
- workbench 一次只展示一个业务页；动作固定在 footer，不随列表滚动。
- 列表、槽页和详情各自定义 loading、empty、stale、error、pending 状态。
- compact 只减少文字宽度和可见行数，不隐藏 Scene，也不把关键命令降级成只能猜含义的图标；合成页不缩小 `18px` 槽或改变每页 64 槽。

## 3. 存储子 UI

### 3.1 页面布局

```text
144 x 194 workbench
┌────────────────────────┐
│ [存储内容][结构状态]    │ 18
├────────────────────────┤
│ [搜索........] [排序 v]│ 20
├────────────────────────┤
│ [key] 名称       12.4K │
│ [key] 名称          64 │
│ [key] 名称        1.2M │ ScrollerView
│ ...                    │
├────────────────────────┤
│ 128 类 / 总量与容量    │ 18
└────────────────────────┘
```

`存储内容` 是只读清单，`结构状态` 只显示 formed、matched、容量、故障和在线状态。它们不能同时常驻。

### 3.2 AEKey 行契约

每行严格对应一个条目：

```text
StorageEntry(AEKey key, BigInteger exactAmount)
```

- 以完整 `AEKey` 作为身份，不能按显示名合并，也不能先转成 `ItemStack`；物品、流体及其他注册的 key type 必须走同一通用渲染入口。
- 存储真值保持 `BigInteger` 精度；同步载荷可使用规范十进制字符串，但不能先饱和或截断成 `long`。AE2 `KeyCounter` 所需的饱和 `long` 只属于对外可用栈派生视图。
- 行内同时显示 key 图标、短名称和对应数量。图标、名称、数量属于同一稳定 row，滚动或刷新后不能错位。
- 数量列右对齐；行内可使用紧凑格式，tooltip 必须显示精确原始数量和 key 的完整名称。
- 数量为零的条目从权威 snapshot 删除，不保留幽灵行；相同 key 的数量更新只更新该 row。
- 默认按数量降序，名称排序作为单一 `Selector` 选项；搜索只过滤客户端已有 snapshot，不改变服务端内容。
- 列表使用稳定 row 池或虚拟化 `ScrollerView`，不能每 tick 清空并重建全部元素。

### 3.3 同步边界

存储内容使用服务端权威的 revisioned snapshot/delta：首次打开传完整 snapshot，此后只传新增、数量变化和删除。容量汇总与逐项内容使用同一个 revision，避免 footer 已更新但 row 仍属于旧状态。

所需数据源不得直接暴露可变 `MEStorage`；UI 只读取不可变的 `AEKey -> amount` 视图。无法访问 grid、host 已失效或 revision 不连续时进入明确 stale/error 状态并请求一次完整刷新。

当前 `TrinityDataCoreStorageSavedData` 只有 summary、单 key amount 和向 AE `KeyCounter` 填充可用栈的入口，没有精确条目 snapshot/revision 公共 API。因此本节需要新增 DataE 侧只读目录 API，不能把现有 summary 冒充成已可用的逐项同步。

## 4. CPU 子 UI

### 4.1 页面布局

```text
144 x 194 workbench
┌────────────────────────┐
│ [合成任务][结构状态]    │ 18
├────────────────────────┤
│ CPU #1  [输出] x4096   │
│ ███████░░░  71%        │
│ CPU #3  [输出] x64     │ ScrollerView
│ ██░░░░░░░░  等待中     │
│ ...                    │
├────────────────────────┤
│ [在 AE 中查看]          │ 18
└────────────────────────┘
```

- `合成任务` 一行对应本 Data Core runtime 的一个真实 worker partition；保留 CPU number，不使用 AE 私有列表 serial 作为持久身份。
- 行内显示 CPU number、最终输出 `GenericStack`、总量、进度和状态。没有任务的 partition 不伪造空任务行。
- 点击任务只改变当前选择；详情仍由 AE 原生 CPU 界面负责，Data Core 不复制一套不完整的 AE 状态表。
- footer 的“在 AE 中查看”只在选中任务仍存在且用户可访问同一 grid 时启用。
- 任务颜色、进度和 busy 状态使用 `ProgressBar`、Label 和 Style class，不再为 Trinity CPU 行制作 idle/task overlay 精灵图。
- 当前 `TrinityDataCoreCraftingStatus` 扫描整个 grid 且只保留一个代表性 target，不能直接充当任务列表。本增强需要从当前 runtime 的 published worker 与 `getJobStatus()` 生成新的 task snapshot。
- CPU number 会在任务结束后复用；任务选择身份必须是 `(hostId, cpuNumber, taskRevision)`，不能只缓存 number，也不能把 `getLastModifiedOnTick()` 当作跨重载的持久 revision。

### 4.2 跳转 AE CPU 界面

跳转是一次服务端权威的菜单切换：

1. 客户端提交 host identity、hosted generation、CPU number 和当前 task revision。
2. 服务端验证玩家仍可访问 Data Core、host 仍在原 grid、CPU number 仍对应当前 `TrinityDataCoreVirtualCpu`，且任务 revision 未过期。
3. 服务端通过正式的 terminal-host adapter/navigation bridge 打开 AE `CraftingStatusMenu`，再由非反射 integration hook 把任务 token 解析到当前 `ICraftingCPU` 并调用正常选择路径；当前 Data Core 菜单按正常容器生命周期关闭。
4. 任务结束、CPU 消失、grid 改变或无权限时不打开错误界面，原窗口显示可本地化的 rejected 结果。

禁止只在客户端 `setScreen`、伪造 AE 私有 serial、使用反射访问 AE 菜单内部字段，或让按钮在没有服务端确认时表现为成功。

现有 `TrinityDataCoreVirtualCpu` 和 `CraftingCPUMenuMixin` 已能让 AE CPU 状态表读取 Trinity CPU，但 Data Core 当前不是 `ITerminalHost`，AE `CraftingCPUMenu.TYPE` 也只接受真实 `CraftingBlockEntity`。本增强必须新增 adapter 与预选 hook，不能假定 AE 已提供从 Data Core 直接打开并定位虚拟 CPU 的公共入口。

## 5. 合成子 UI

合成 workbench 顶层严格只有两页：`样板槽位` 与 `合成详情`。Scene、参数/材料 Dock 和标题栏在两页之间保持不变；结构状态、选中方块和故障定位进入共享 Dock，不增加第三页。

### 5.1 样板槽位页

```text
144 x 194 workbench
┌────────────────────────┐
│ [样板槽位][合成详情]    │ 18
├────────────────────────┤
│ [ ][ ][ ][ ][ ][ ][ ][ ]│
│ [ ][ ][ ][ ][ ][ ][ ][ ]│
│ [ ][ ][ ][ ][ ][ ][ ][ ]│
│ [ ][ ][ ][ ][ ][ ][ ][ ]│ 8 x 8 stable slot pool
│ [ ][ ][ ][ ][ ][ ][ ][ ]│
│ [ ][ ][ ][ ][ ][ ][ ][ ]│
│ [ ][ ][ ][ ][ ][ ][ ][ ]│
│ [ ][ ][ ][ ][ ][ ][ ][ ]│
├────────────────────────┤
│ [<]  2 / 12  [>]       │ 18
└────────────────────────┘
```

“统一操作”定义为从 Data Core 中统一访问当前 formed 合成结构内所有 Pattern Core 的真实样板槽，而不是创建一份复制库存：

- 服务端按稳定 core 顺序、再按物理 slot index 排列槽位，并切成每页 64 槽。
- 每个显示槽始终映射到 `(layoutRevision, hostId, coreId, slotIndex, slotRevision)`；分页只改变 wrapper 的 backing route，不改变真实所有权。`globalIndex` 只表示当前 layout 内的分页位置，不能作为持久身份。
- 复用 Pattern Core 已有的插入、取出、交换和 quick-move 语义，并执行完全相同的编码样板校验。
- 有 queued batch 或 pending output 的工作槽显示 busy 标记与数量摘要，但插入、取出、交换和 quick-move 仍严格复用独立 Pattern Core 的现有规则。当前 `isSlotWorking` 是状态查询，不是槽操作 gate；若以后决定锁定工作槽，必须作为单独行为变更确认。
- 页切换握手期间保留 64 个稳定槽和 busy overlay；服务端确认前不得对新 backing route 接受点击。
- core 移出结构、结构失效或 layout/slot revision 变化时使相关动作 fail fast，并显示 stale 结果；不能把操作静默落到排序后的另一个槽。
- 本阶段不擅自增加批量删除、批量改写或自动编码等破坏性动作。若以后增加，必须另行定义选择集、事务、库存溢出路由、回滚和审计结果。

结构级退款继续只处理 queued input 与 pending output，不移除已经安装的样板；页面文案必须明确这个边界。

样板页管理的是普通合成样板。通用写入入口不把多方块 Scene、层级、variant/tier/repeat/candidate 或自动搭建草稿编码进样板；只有真正发起自动搭建时，才从当时的 UI 选择生成搭建状态。

### 5.2 合成详情页

```text
144 x 194 workbench
┌────────────────────────┐
│ [样板槽位][合成详情]    │ 18
├────────────────────────┤
│ [当前合成 v]            │ 18
│ [输出] 名称 x 数量      │ 20
│ 状态 / 进度 / 批次数    │ 18
│ [ ][ ][ ]               │
│ [ ][ ][ ] -> [输出]     │ recipe detail
│ [ ][ ][ ]               │
│ Core # / Slot # / route │
│ 队列 / pending output   │
│ 暂停或失败原因          │
└────────────────────────┘
```

- 数据源是服务端当前执行或等待执行的 `TrinityCraftingBatch`/route snapshot，不从客户端动画或 Scene 推断。
- 同时存在多个 active craft 时，顶部 `Selector` 一次选择一个；默认保持上次仍有效的选择，否则选服务端稳定顺序中的第一项。
- 显示 pattern definition/snapshot、逻辑 craft count、3x3 输入、core/slot route、queued tick、queued group 数和 pending output。
- 执行完成前只能把安全 decode 得到的输出标为“预计输出”；definition unresolved/mismatch 时显示 unavailable，不能伪造实际输出。执行完成后才把 pending output 作为实际结果。
- 当前执行层只有无原因的 `paused` 与日志，尚无结构化失败原因。若详情页要显示暂停/失败原因，必须新增结构化 execution diagnostic API；在该 API 完成前只显示“暂停，原因不可用”及日志关联信息。
- 详情是普通配方执行详情，不把多方块结构本身伪装成 AE 专用配方，也不承担 CPU 全量状态表的职责。
- 从样板槽位页选择一个正在工作的槽后可切到该 route 的详情；route 已结束时显示 completed/empty，不跳到无关任务。
- 本页只读。取消 CPU 任务仍在 AE 原生 CPU 状态界面执行，避免两个界面对同一任务提供冲突动作。

## 6. 建议的数据与 UI 契约

若现有 LDLib2/DataE 适配层缺少能力，优先补充以下面向接口的通用契约；DataE 业务对象不下沉到共享库：

| 契约 | 作用 | 共享库边界 |
| --- | --- | --- |
| `AeKeyDisplay` | 按 key type 渲染图标、名称和 tooltip | 可通用时进入 Modular-Data-lib |
| `RevisionedListSource<T>` | 完整 snapshot、delta、stale 检测 | 与 AE 无关，可进入 Modular-Data-lib |
| `PagedSlotBinding` | 固定槽池与服务端确认后的 backing route 切换 | 与菜单协议无关时可进入 Modular-Data-lib |
| `CpuTaskSource` | 提供 Trinity CPU task snapshot | 留在 DataE |
| `CraftingSlotSource` | 提供真实 core/slot 路由与工作状态 | 留在 DataE |
| `AeCpuNavigator` | 校验并打开选中的 AE CPU 菜单 | 留在 DataE/AE 集成层 |

接口实现类遵循 `XxxImpl` 命名；snapshot value 使用不可变 record。不得以反射、完全限定名或可变业务对象泄漏代替正式入口。

## 7. 状态与验收

| 分类 | 必须覆盖 |
| --- | --- |
| 存储 | 物品/流体/其他 `AEKey`、数量增减、删除、排序、搜索、精确数量、revision 断档 |
| CPU | 0/1/多任务、任务完成、number 复用、CPU 重建、grid 变化、无权限、正确预选 AE CPU、跳转 rejected |
| 样板槽位 | 多 core、跨页、空槽、quick-move、工作槽 busy 但沿用现有操作语义、core 移除、页切换 pending、溢出拒绝 |
| 合成详情 | 单/多 active craft、route 完成、普通配方输入/预计输出/实际 pending output、queued、诊断可用与不可用 |
| Scene | 两页和任务/存储刷新时相机、layer、选择不重置，各窗口资源独立释放 |
| 资源 | UI 不引用 DataE GUI PNG、sprite atlas、九宫格或状态图集；只使用 Modular UI、LSS/Style 和通用 Icons |

完成标准不是“能打开页面”，而是业务行、槽和任务均绑定到稳定服务端身份，刷新后不会错项；所有副作用都有 pending/result，所有 stale 路径都可见且不会写错对象。
