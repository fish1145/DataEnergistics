# Trinity CPU 计算与循环能力审计及修复报告

## 1. 审计结论

当前 Trinity CPU 的 Phase 0 至 Phase 2 已解决候选选择、公平轮询、派发拒绝、输入所有权和物理预算问题。当前分支也
已完成 Trinity 独立 DAG/SCC 计算内核、schema 2、seed 输出门、动态借料和事件执行器。扩展计划已经可以由真实
Trinity CPU 按阶段安全消费；玩家数量页、确认页和 `beginCraftingCalculation` 双轨入口也已接入。Phase 3 同步
派发已改为 publication identity、不可变容量快照、公平 target slice 和唯一 commit 边界；无法证明等价的 addon 路线
保留原生单次语义。

本报告只记录当前证据和修复映射。目标架构见 `trinity-cpu-planning-and-cycle-architecture.md`，派发事务不变量见
`trinity-cpu-dispatch-architecture.md`。

## 2. 当前链路

```text
Craft Amount / 外部请求
  -> CraftingService.beginCraftingCalculation
  -> AE2 CraftingCalculation
  -> ICraftingPlan(patternTimes + usedItems + emittedItems)
  -> CraftingService.submitJob
  -> TrinityDataCoreExecutingCraftingJob
  -> TrinityDataCoreCpuLogic.executeCrafting
  -> provider
  -> TrinityDataCoreCpuLogic.insert
```

当前分支已建立网格图、扩展计划契约、`TrinityPlanningGateway` 双路 Future、完整 DAG/SCC 规划器和 schema 2
执行器。玩家请求会携带数量模式；存在合格 Trinity CPU 时，入口并行启动 Trinity 与 AE2 计算，Trinity 结果只有在
预算、容量和计划所有权均通过时才优先采用，否则保留 AE2 结果和 Trinity 诊断。机器及外部请求使用 COMMON 默认模式。

## 3. 缺陷证据

### C-001：计划在提交前已丢失阶段与循环信息

AE2 `ICraftingPlan` 只公开最终输出、初始原料、emitted items 和 `Map<IPatternDetails, Long> patternTimes()`。
`TrinityDataCoreExecutingCraftingJob` 构造时直接把该 map 写入 `ScheduledTasks`。

影响：

- 无法表达阶段顺序；
- 无法区分 seed 与可交付目标物；
- 无法保存 repeat block、binding variant 或动态借料；
- 仅删除 AE2 递归检查仍会在执行阶段死锁。

修复：增加 `TrinityCraftingPlan` 扩展契约和 schema 2；普通 AE2 计划继续走 schema 1 兼容路径。

当前状态：扩展计划、stage、repeat block、seed、净变化、精确 byte 边界、schema 2 与运行时消费均已实现。

### C-002：AE2 明确拒绝递归生产路径

AE2 19.2.17 的 `CraftingTreeNode.notRecursive` 会沿祖先链拒绝输出或输入匹配当前节点的 pattern；
`CraftingCalculation` 还会对最终输出调用 `craftingInventory.ignore(output)`。

影响：

- `A -> 2A` 无法形成计划；
- `A -> B -> 2A` 在祖先检查处被截断；
- 网络中已有 A 不能作为受控 seed 参与“净新增”计算。

修复：不修改 AE2 递归树；Trinity 使用样板超图、Tarjan SCC、闭式循环和受限 MIP 独立规划，失败时仍保留 AE2 结果。

当前状态：多路线 SCC 会把 MIP 守恒 seed 作为下界，再由压缩排程计算真实前缀 seed；真实 seed 高于松弛下界但仍在库存范围内时，不再误报无整数解。
库存容量约束采用逐项激活：只有无约束最优解实际超过某项库存时才向 ojAlgo 加入该项上界，避免无限存储单元发布的巨量可用值让本可行的整数模型数值失稳。
单路线多步 SCC 不再假定“每个 transition 各执行一次”：实现以精确有理数消元求平衡中间产物的最小正整数 firing ratio，
再按完整循环的仿射余额断点合并排程。真实 `64 certus + water -> 64 charged`、`certus -> dust`、
`16 charged + 16 dust + water -> 64 certus` 路径可在玩家请求数量全域内保持压缩计划，不再落入大规模 MIP 分支树。
ojAlgo 返回的整数变量允许按其本次 integrality tolerance 消除浮点残差，但规范化结果仍逐项经过 `BigInteger` 守恒与边界复验。

### C-003：多路径和复用路径按数量逐次模拟

`CraftingTreeNode` 在同一资源存在多个 pattern 时循环调用 `pro.request(child, 1)`。
`CraftingTreeProcess.limitQty` 在输入同时为输出或存在 container item 时同样强制逐次模拟。

影响：复杂度可从按图规模批量传播退化到约 `O(Q × depth)`；把输出改成两倍只能按比例减少次数，不能消除根因。

修复：无环区域使用批量需求传播；输入输出重叠使用精确前缀余额；多路线 SCC 使用整数模型和压缩排程验证。

### C-004：最终输出被提前交付，循环失去 seed

`TrinityDataCoreCpuLogic.insert` 对非 standalone 作业使用：

```text
receiveLocally = !finalOutput || link.isStandalone()
```

最终输出会直接进入 requester，不进入 CPU working inventory。

影响：即使规划器生成 `A -> 2A`，第一轮返回的 A 也会被交付，下一轮无法继续。

修复：Trinity 循环计划启用最终输出门；目标先进入 working inventory，全部阶段完成后再移动到独立 completion buffer。

### C-005：运行时每 tick 扫描整个任务表

`TrinityDataCoreCpuLogic.executeCrafting` 从 `currentJob.tasks.entrySet()` 开始扫描，缺输入或 provider 不可用的任务仍会在
后续 tick 重复进入扫描。

影响：任务图大、等待链长或 provider 长期 busy 时产生与无关任务数量相关的服务器线程开销。

修复：ready queue、输入反向索引和 provider retry 队列；只有相关输入、输出、revision 或重试时间变化时重新唤醒。

### C-006：持久化 schema 1 只有扁平任务

`TrinityDataCoreExecutingCraftingJob` 当前 schema 只保存最终输出、remaining amount、waitingFor、time tracker 和扁平
task progress。

影响：无法在重载后恢复循环阶段、seed reserve、completion buffer 和动态借料所有权。

修复：新增 schema 2，保留 schema 1 普通作业读取；ready queue 等派生状态在载入后重建。

### C-007：AE2 pattern 排序缓存持续重复排序（当前分支已修复）

AE2 19.2.17 的 `NetworkCraftingProviders.PatternsForKey` 在 mount/unmount 时设置 `needsSorting=true`，但
`sortPatterns()` 后没有复位。高频 `getCraftingFor` 会重复排序。

当前分支已有 `NetworkCraftingProvidersPatternsForKeyMixin`，只在 `sortPatterns()` 成功返回后清除 dirty flag，
没有改变 pattern 内容、优先级或公共接口。新增 Trinity 图构建仍应在一个 revision 内每个 craftable key 只抓取
一次，并缓存不可变结果。

### C-008：Phase 2 时间预算边界存在可避免的超调

供应器 busy 或准入准备已经消耗完时间预算时，现有路径仍可能继续准备账本、额外输入和能源，直到物理额度获取失败
才回滚。

修复：在每个可恢复准备边界后检查活动 scope；预算耗尽时立即结束当前 scope。行为测试必须验证预算耗尽后的
MODULATE 库存和能源调用次数，而不只检查最终净值。

### C-009：256-worker 正确性测试隐含墙钟断言

正确性场景要求 256 次派发全部完成，但使用 30 ms 生产时间窗口；当前样本最大值已接近该窗口，CI 负载可能造成
无功能回归的随机失败。

修复：正确性测试保留相同调用额度并使用 `Long.MAX_VALUE` 时间额度；真实耗时只记录，30 ms 边界由 fake clock
测试验证。

### C-010：未知扩展计划可能被 Trinity 错误接管

`ICraftingPlan` 只表达 AE2 基础字段，第三方实现还可能携带专用 CPU 才理解的隐藏语义。Thunderbolt
`LoopCraftingPlan` 包含 TimeWheel 的 host 限制、循环 seed 与借用语义；Trinity 若只读取 `patternTimes()`，会在
显式目标、自动选择或原路由失败后的 fallback 中丢失这些约束。

修复：建立唯一的 `TrinityPlanAdmission`。AE2 原生 `CraftingPlan` 与显式 opt-in 的
`TrinityCpuExecutablePlan` 可提交；未知扩展计划在显式/直接路径拒绝，在自动/fallback 路径交回原实现。Mixin 和
`TrinityDataCoreVirtualCpu` 均调用同一契约，不硬依赖 Thunderbolt，也不使用反射。

### C-012：重载后的绝对 retry tick 与未标脏迁移会冻结作业

schema 2 曾直接保存进程内绝对 `retryAt`/`budgetRetryAt`；服务器重启后计数从零开始，旧 deadline 可能让作业等待数十万
tick。另有 provider、动态材料、预算和 planning 等 durable 状态迁移没有统一触发宿主 dirty。

修复：执行快照升级 schema 3，保存时钟基准并在恢复时重基剩余延迟；旧 schema 2 deadline 立即到期一次。执行状态机
维护 transient durable revision，CPU tick 统一比较并标脏；AE2 作业级暂停也随 job 保存，暂停时仍接收在途输出。

### C-013：非循环终点把上游循环中间料误算为净新增

凝聚 DAG 的下游需求进入循环 SCC 时，旧实现把中间料 gross demand 作为新的 `NET_NEW` 请求，忽略网络中已经存在的
循环产物。已有库存足够时仍会无意义增殖；部分库存时还会把本可作为 seed 的数量从最终余额语义中丢失。

修复：非根循环需求使用 final-balance 约束。库存完整覆盖时直接预留并跳过循环；部分覆盖时由循环求解器同时处理 seed、
现有余额和缺口。同一 SCC 的全部余额轴与环外输出轴一次联合求解；跨 SCC hyper-transition 只归属于唯一 cyclic owner，
环外产物需求回传给 owner，不再作为普通 DAG firing 重复接管。串联 SCC 在上游库存不足时传播完整 seed 余额，混合路线则在
统一状态上限内按稳定顺序回溯。该规则逐 SCC 应用，最终请求物无需位于循环内。

### C-014：确认页重算与 CPU 过滤存在跨 tick 提交竞态

AE2 开始新计算时服务端会清空 `result`，但客户端和服务端仍可能短暂保留上一份计划摘要；同时计划 Future 完成的当个
`broadcastChanges` 中，CPU 列表会先于新 `result` 被重新过滤。玩家在这个窗口点击“开始”时，可能无声命中
`result == null`，或使用尚未按 Trinity 计划重新筛选的 CPU 状态。

修复：确认菜单同步一个计划就绪门。开始计算或重算时立即清除旧摘要并关闭门；只有服务端结果已存在，且下一轮 CPU
资格过滤已经观察到该结果后才重新开放。按钮和 Enter 提交统一受门控制，提前到达服务端的手动提交返回
`INCOMPLETE_PLAN`，AE2 auto-start 仍可在结果产生后直接提交。

### C-015：剩余量重规划会被同 revision gate 永久冻结

pattern 失效后，剩余量计算曾把 graph revision 同时作为去重键和永久尝试标记。异步 Future 异常或有效 replacement
在服务器线程预留输入时输掉库存竞态后，执行仍停留在 `PLANNING`，但同 revision 的后续调用只返回 `Waiting`；若样板目录
不再变化，作业会永久停在 CPU 内。

修复：区分语义拒绝与可恢复结果。语义拒绝继续等待新 revision；异步异常与预留竞态在同 revision 上按配置上限指数
退避重试。replacement 成功接纳后显式结束本次重规划 episode，释放 revision gate，避免未来独立失效事件被旧状态拦截。

### C-016：Trinity 计划缺少 AE2 原生数量信息投影

Trinity 为避免大数量展开而将 `patternTimes()` 保持为空，确认页因此看不到中间产物与最终产物的待合成量；把目标放入
`emittedItems()` 又会让最终产物同时显示为已存储。执行期的紧凑作业不填充旧 `scheduledTasks`，CPU 状态页的待合成量恒为
零；封存完成缓冲虽参与 key 枚举，却未计入已存储量，可能形成空白或零数量行。

修复：图快照保留每个绑定样板不含输入余留物的真实声明输出，紧凑计划聚合 gross planned outputs，并适配为 AE2 原生
`CraftingPlanSummary`。运行时从 stage/repeat 游标按图规模推导剩余待合成量，不按请求数量展开；执行快照 schema 4 持久化
声明输出，旧 schema 2/3 缺少精确信息时记录警告并省略未知行。completion buffer 同时计入 CPU 已存储量。

### C-017：VirtualGrid 发布网格与执行网格身份不一致

VirtualGrid 主网格能够把 incoming virtual member 的 Trinity CPU 发布到 crafting service，但显式目标、自动选择和
fallback 提交都把主服务网格传入 CPU；CPU、runtime 和 worker 仍以 Access Hatch 的物理从网格进行严格判等，因此可见的
CPU 会在提交时返回 `CPU_OFFLINE`。

修复：建立单一 typed execution route，分别携带 owning grid、service grid、access lease epoch 和 membership
generation。CPU 发布、显式/自动/fallback 提交、runtime 在线判定、状态菜单与输出路由使用同一个 service grid；物理
lease、Access Hatch 选举和 `accessGrid()` 继续使用 owning grid。active member 还必须存在于 primary grid 的 incoming
publication；inactive、未注册或 route token 变化时不返回可执行路由。

当前状态：已完成，P0。现有 VirtualGrid GameTest 直接覆盖未注册 active、inactive subordinate、同 primary 重连失效、
主网格 CPU 发布和跨网格提交；全量 GameTest 验证 470 项通过。

### C-018：局部循环机会缺少可证明的接纳边界

旧 deterministic/shifted 路径用 `Optional<AlgorithmResult>` 同时表达“不适用”和“终端失败”，GraphPlanner 可能把局部路线
歧义、局部 `UNSUPPORTED_PATTERN` 或排程未证明直接提升为用户无解。shifted optimizer 还根据 LP 松弛结果、浮点 ULP 和
经验 guard 预先收紧 reduction 上界；真实整数最优值落在该边界附近时，可能被错误排除。

修复：机会规划统一返回 `PROVED_OPTIMAL`、`NOT_APPLICABLE`、`TERMINAL`。只有完成唯一性、完整 firing、真实 seed 和压缩
顺序证明的结果可以接纳；不适用继续通用 MIP，取消和共享预算耗尽才终止。删除 LP/ULP 预紧与经验 guard，reduction 只受
精确 `0..baselineFirings` 结构界约束。MIP 与候选选择共用完整、缺失补零的 `BigInteger` firing vector，并在前三层目标固定后
按稳定 identity 逐项确定结果。

### C-019：provider 轮询、容量模拟与真实输入事务缺少统一边界

旧同步路径直接消费 AE2 `getProviders` 的轮询 iterable，并在确认 provider 可用前从 CPU 真实库存提取一次图样输入。加入多 target
容量后，若只执行切片表第一项，还会让 busy、quota、拒绝或异常的首个 target 吞掉同 tick 后续 provider 的机会；快照过期时也缺少
provider registration、counted capability 和 pattern identity 的联合重验。

修复：`CraftingService` 维护独立 publication index；CPU 从库存副本生成只读 prototype，只对 ready work 捕获容量。每个候选在准备前
按 publication revision、capability revision、pattern identity 和 route 重验 live provider；稳定 cursor 逐个请求有界 slice，失败后
继续同 tick 后续候选。真实输入、能源和 waiting 只在准备阶段进入事务，最终统一由 `CraftingDispatchCommitter` 按所有权结果结算。
AE2 精确原版 provider、Trinity Access Hatch 和 Adaptive 普通路线使用 `TARGETED`；公共 counted API 使用 `AGGREGATE`；其余无法证明
等价的路线使用 `UNKNOWN` 单次。独立每网格 4 ms 容量采集预算仍是 Phase 3 的最后收尾项。

## 4. 修复映射

| 缺陷 | 修复组件 | 当前状态 | 主要证据 |
| --- | --- | --- | --- |
| C-001 | graph snapshot、扩展计划、schema 2 | 已完成 | 计划不持有 decoded pattern、阶段聚合、schema 1/2 恢复测试 |
| C-002 | Tarjan、闭式循环、Trinity planner | 已完成 | 自增殖、多步增殖、多路线 MIP、完整正 `long` 请求域、seed 前缀和真实 CPU 紧凑执行测试 |
| C-003 | DAG 批量传播、revision 图缓存 | 已完成 | 同 tick 失效、大数量状态计数与 `long` 溢出测试 |
| C-007 | 已有 pattern-sort dirty flag Mixin | 已完成 | 重复读取不会重复排序 |
| C-004 | working inventory、completion buffer | 已完成 | 封存、异常重试、部分接收和 standalone 精确交付测试 |
| C-005 | ready queue、反向索引、retry queue | 已完成 | 无关 key 不唤醒、队列去重和独立退避测试 |
| C-006 | schema 4 | 已完成 | cycle/借料/封存/声明输出重载与 schema 1/2/3 兼容测试 |
| C-008 | dispatch scope 边界检查 | 已完成 | 零额外 MODULATE 调用 |
| C-009 | 确定性测试窗口 | 已完成 | 256-worker 测试不依赖墙钟 |
| C-010 | 统一 plan admission | 已完成 | 显式、自动、fallback 直接逻辑测试与 CPU 最终边界 GameTest |
| C-011 | 数量语义与初始规划入口 | 已完成 | 请求上下文、双轨入口、容量拒绝和确认页 CPU-family 过滤 |
| C-012 | retry 时钟重基、durable revision、作业暂停 | 已完成 | 高 tick→低 tick 恢复、在途续接、真实菜单 toggle |
| C-013 | 上游循环 final-balance 与 boundary-output 传播 | 已完成 | 完整/部分库存、串并联 SCC、多轴联合求解、环外输出与混合路线回溯 |
| C-014 | 确认页计划就绪门 | 已完成 | 真实 `CraftConfirmMenu` 首次/二次广播、提前提交和重算提交 GameTest |
| C-015 | 剩余量重规划结果处置协议 | 已完成 | 同 revision 异常与预留竞态退避重试、语义拒绝等待新 revision 的直接状态机测试 |
| C-016 | 声明输出与计划/运行时数量投影 | 已完成 | 确认页直接摘要测试、DAG/单环/多步环游标与 schema 4 重载测试 |
| C-017 | VirtualGrid typed execution route | 已完成 | 完整 route token、VirtualGrid 跨网格提交与 470 项 GameTest |
| C-018 | 机会规划三态边界与精确 firing identity | 已完成 | selector、结构边界命中、ordinary/radix 精确窗口与 `Long.MAX_VALUE` 直接契约测试 |
| C-019 | publication index、容量 resolver、公平 slice 与唯一 commit | 主体完成 | 路由模式参数化契约、拒绝/异常续选、时间边界、256-worker 与 470 项 GameTest；独立容量采集预算待接入 |

## 5. 风险与控制

| 风险 | 控制 |
| --- | --- |
| MIP 数值解不精确 | 精确窗口内使用 ordinary model，超出窗口使用整数 digit/carry radix model；两者都经 `BigInteger` 二次验证，失败即拒绝 |
| 任意 Petri net 搜索失控 | SCC/variant/time/state 四重上限 |
| 异步线程访问世界 | 只传不可变值对象，服务器线程二次校验 |
| 动态借料复制或误退款 | RESERVED/COMMITTED/RELEASED 所有权状态 |
| 配方热更新执行旧语义 | pattern signature 校验，只重规划剩余量 |
| 扩展计划进入原生 CPU | UI、自动选择、submit 三层隔离 |
| 专用第三方计划进入 Trinity | 未知扩展计划交回原路由，显式 Trinity 目标 fail fast |
| 缺料等待造成忙轮询 | key 唤醒加最高 200 tick 退避 |
| 大数量溢出 | 内部 `BigInteger`，AE2 边界精确转换 |
| 单样板自环缺料后继续等待 AE2 大数量展开 | 发布 Trinity 权威诊断 simulation 并协作取消 AE2；多步顺序相关结果继续 fallback |
| UI 数量与紧凑执行游标偏离 | 计划使用 gross 声明输出；运行时从持久游标精确推导，不维护第二份可漂移计数器 |
| 容量快照提前修改库存或按旧 route 提交 | 从 CPU 库存副本捕获 prototype；提交前联合重验 provider、capability、pattern 与 target revision |

## 6. 验证矩阵

### 6.1 规划

- 单路径与多路径 DAG；
- `A -> 2A`；
- `A -> B -> 2A`；
- 多路线 SCC 和确定性 tie-break；
- `Long.MAX_VALUE` 请求、radix digit/carry 解码和 `BigInteger` 守恒回放；
- 非生产 SCC、无 seed、MIP 超时、SCC/variant/search 上限；
- `NET_NEW` 与 `FINAL_TOTAL`；
- 非循环终点复用已库存循环中间料，串并联 SCC 的 final-balance 传播，同 SCC 多轴与 cyclic-owned 环外输出；
- 十亿级确定性循环缺料在 scheduler 前完成，组合 Future 不等待 AE2；
- `long` 边界与溢出。

### 6.2 执行与守恒

- counted provider 几何批次；
- generic provider 单次语义；
- `TARGETED`、`ORDERED`、`AGGREGATE`、`UNKNOWN` 路由契约和失败 target 的同 tick 续选；
- provider 拒绝、所有权前异常、时间预算耗尽和 256 worker 物理 attempt 守恒；
- 动态替代、追加借料、改路和无限期缺料等待；
- 部分 requester 接受、standalone、取消和在途输出；
- 配方 revision 变化和剩余量重规划；
- 同 revision 异步异常与 replacement-input 预留竞态可重试，语义拒绝不忙轮询；
- 真死锁诊断和唯一退款路径。

### 6.3 恢复与集成

- schema 1 普通任务恢复；
- 作业 schema 2 在每个 cycle phase、借料后和封存后恢复；
- 执行快照 schema 4 保留声明输出，schema 2/3 迁移不伪造未知待合成量；
- 新会话 tick 重基 retry、旧执行 schema 2 迁移和 AE2 CPU 作业级暂停；
- Trinity 与原生 CPU 同网格；
- 无 Trinity 时 AE2 回退；
- 扩展计划被原生 CPU 拒绝；
- Thunderbolt 风格 LoopCraftingPlan 在显式、自动和 fallback 路径均不被 Trinity 接管；
- 玩家 `NET_NEW`/`FINAL_TOTAL` 上下文与机器 COMMON 默认模式；
- 初始 Trinity 计划超过所有合格 CPU 容量时保留 AE2 结果；
- 确认页计划完成与 CPU-family 过滤同步后才允许提交，重算期间旧摘要不可复用；
- 确认页显示外部可用量和各阶段待合成量，CPU 状态页随 DAG/循环游标及 completion buffer 更新；
- `test`、`runGameTestServer`、`build` 和 IDEA inspections。

## 7. 完成判定

只有以下证据同时成立才可关闭本审计：

1. C-001 至 C-019 均有直接行为测试或集成证据；
2. 自增殖和多步增殖在真实 Trinity CPU GameTest 中完成且数量守恒；
3. 大数量规划没有按 Q 展开；
4. schema 1/2 重载、取消和动态借料不丢失或复制；
5. 原生 CPU 无法接收扩展计划；
6. 全量构建、GameTest 和 IDE 检查通过。
