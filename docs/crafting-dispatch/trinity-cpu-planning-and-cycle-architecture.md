# Trinity CPU 计算与增殖循环架构

## 1. 文档状态

- 方案状态：已确定，按功能提交实施
- 实现进度：网格图、双轨规划入口、DAG/SCC/循环求解、完整正 `long` 请求域的精确 radix MIP、事件执行、动态借料、schema 4 和数量/诊断界面均已完成
- 适用范围：Trinity CPU 专属计算计划、无环大数量计算、自增殖、多步增殖和多路线生产循环
- 前置基础：派发架构 Phase 0 至 Phase 2
- 非依赖项：派发架构 Phase 3 容量切片、Phase 4 Actor/Shard、Phase 5 Governor

本设计不修改 AE2 公共接口，也不改变第三方 `ICraftingProvider` 的物理派发语义。扩展计划只能提交给
`TrinityDataCoreVirtualCpu`。

## 2. 目标与非目标

### 2.1 目标

1. 普通无环、多路径、大数量任务不按目标数量逐个展开。
2. 支持 `A -> 2A` 自增殖和 `A -> B -> 2A` 多步增殖。
3. 支持受配置上限约束的多路线 SCC。
4. 规划线程只处理不可变快照，服务器线程保持唯一写入权。
5. 循环任务完整保存 seed、阶段、重复次数、动态借料和最终交付状态。
6. 所有失败提供稳定诊断，并在安全时回退 AE2。

### 2.2 非目标

1. 不求解任意无界 Petri net 可达性。
2. 不让 AE2 原生 CPU 执行 Trinity 扩展计划。
3. 不要求第三方 provider 实现新的批量接口。
4. 不在异步线程读取 Grid、Level、BlockEntity、provider 或网络库存。
5. 不通过反射访问 AE2 或第三方内部状态。

## 3. 总体数据流

```mermaid
flowchart LR
    A["服务器线程：请求与库存快照"] --> B["TrinityPlanningGateway"]
    B --> C["AE2 原计算 Future"]
    B --> D["不可变样板图"]
    D --> E["DAG 批量传播"]
    D --> F["Tarjan SCC"]
    F --> G["闭式循环或 ojAlgo MIP"]
    G --> H["精确压缩排程验证"]
    E --> I["TrinityCraftingPlan"]
    H --> I
    I --> J["仅 Trinity CPU 可提交"]
    J --> K["ready queue 与循环执行状态机"]
    K --> L["completion buffer"]
    L --> M["精确交付与剩余物回收"]
```

当存在在线、空闲的 Trinity CPU 时，Trinity 与 AE2 计算并行启动。Trinity 生成有效计划且存在容量匹配的 Trinity CPU
时优先；调用方取消、确定性复杂度边界耗尽或数学不支持时采用 AE2 结果。若 Trinity 已对单 transition 自环精确证明循环输入不足，则立即发布常量
空间的 Trinity diagnosis simulation 并取消 AE2，避免大数量请求继续进入 AE2 的逐量级计算；多步顺序相关缺料及
不支持、超限、超时等非权威失败仍采用 AE2，AE2 返回 simulation 时保留其原始 missing 内容并附加 Trinity 诊断。

## 4. 网格级不可变样板图

### 4.1 权威边界

`TrinityPatternCatalog` 继续只管理单个 Trinity 主机内部样板发布。新增的网格图是
`NetworkCraftingProviders` 的只读派生缓存，不成为第二个权威目录。

服务器线程按 craftable key 枚举全部 `ICraftingProvider` 发布的 `IPatternDetails`，捕获：

- encoded pattern definition；
- 有序输入槽、multiplier 和全部 possible inputs；
- 有序输出；
- push-to-external-inventory 语义；
- 稳定 publication signature。

每组合法输入选择形成一个 pattern binding variant。variant 只包含不可变 `AEKey`、数量和稳定签名，不保存
provider、BlockEntity 或世界引用。

### 4.2 Revision 与重建

- 图 revision 来源于 `NetworkCraftingProviders` 每次有效 mount/unmount 递增的 Trinity 单调 revision bridge；不使用同
  tick 内可能重复的 `getLastModifiedOnTick()`。
- 每 tick 最多使用 `graphRebuildBudgetMs` 构建快照。
- 构建开始和发布前都校验 revision；中途变化则丢弃未发布结果。
- 完整快照构建后一次性原子替换，规划线程不会看到半成品。
- Grid 关闭或重建时取消关联计算并释放快照。

## 5. 计划接口

内部接口 `TrinityCraftingPlan extends TrinityCpuExecutablePlan` 由 `TrinityCraftingPlanImpl` 实现。除 AE2 原字段外，
计划至少包含：

- graph revision 与请求数量模式；
- 初始预计原料和聚合 pattern 次数；
- DAG stage、cycle repeat block 和稳定 pattern binding；
- minimum seed、目标净增量和最终交付量；
- 保守 `bytes()`、诊断与算法统计。

`TrinityDiagnosedCraftingPlan` 只用于 UI，不允许提交：普通 fallback 原样委托 AE2 simulation；单 transition
自环可精确证明输入不足时，则以常量空间投影 `available` 与 `missing` 计数，不再等待 AE2 大数量展开。多步循环的
候选顺序不能单独构成不可行证明，仍保留 AE2 fallback。

所有 Trinity 提交入口共享 `TrinityPlanAdmission` 接纳边界。AE2 原生 `CraftingPlan` 和显式实现
`TrinityCpuExecutablePlan` 的扩展计划可执行；其它 `ICraftingPlan` 可能携带专用 CPU 的 host、seed、借用或排程语义，
不能按普通计划接管。显式目标和直接 CPU 调用返回不适用，自动选择和 fallback 则交还原路由。Thunderbolt
`LoopCraftingPlan` 因此继续由 Thunderbolt/TimeWheel 执行，不引入硬依赖或反射识别。

三层隔离必须同时存在：

1. Craft Confirm 菜单不把原生 CPU 列为扩展计划候选。
2. 自动选核只选择 Trinity CPU。
3. `CraftingServiceMixin.submitJob` 和 `TrinityDataCoreVirtualCpu.submitJob` 都调用统一接纳边界。

## 6. 图算法

### 6.1 无环区域

对凝聚 DAG 使用 `BigInteger` 批量需求传播：

1. 从目标需求向上游聚合缺口。
2. 对同一 pattern 一次计算完整 craft 数，不按数量生成节点。
3. 多路径按资源目标选择 binding，并用不可变差分验证。
4. 容器返还和输入输出重叠使用前缀余额计算；无法证明时回退 AE2，不猜测。

### 6.2 SCC 分类

使用 Tarjan 算法生成 SCC 和凝聚 DAG：

- 无自环单节点：普通 DAG stage；
- 单路线生产 SCC：闭式循环；
- 多路线且在限制内：局部 MIP；
- 非生产、超上限或无法排程：明确诊断。

目标位于凝聚 DAG 下游、上游原料位于一个或多个循环 SCC 时，下游传入的是循环原料的最终余额需求，而不是新的
`NET_NEW` 请求。已有库存足以覆盖该余额时直接预留并跳过对应循环；仅部分覆盖时，求解器同时把现有数量作为 seed 与
最终余额的一部分，再把各循环 block 排在下游 DAG stage 之前。该规则逐 SCC 应用，因此不要求最终请求物本身属于循环。

一个 SCC 同时承担多个下游原料或直接产生 SCC 外输出时，规划器构造 component-wide demand：内部 key 使用最终余额下界，
boundary output 使用净变化下界，并由同一个整数 firing vector 与压缩排程共同满足。任何含内部反馈边的 variant 只归属于唯一
cyclic owner；其环外输出需求回传给 owner，禁止再作为普通 DAG producer 生成缺少前缀 seed 的重复 stage。串联 SCC 的
启动材料不足时向上游传播完整余额而不是局部缺口；普通混合路线按稳定 identity 做有界回溯，找到可行路线后才发布计划。

### 6.3 确定性循环

当 SCC 中每个内部 key 都只有一个生产者时，先以精确有理数高斯消元求 primitive positive integer firing ratio。
该比例必须让所有非目标内部 key 的净变化严格为零，并让目标净变化为正；存在多维解空间、零/负 firing 或未覆盖 transition
时不猜测路线，转入多路线 MIP。

确定性规划是可选的证明型快速路径，不是独立的失败终点。其结果分为三类：

- `PROVED_OPTIMAL`：内部 key 与 boundary output 路线唯一，完整 firing vector、minimum seed 和压缩顺序均通过精确验证；
- `NOT_APPLICABLE`：存在多路线、残余路线歧义、未覆盖 transition 或局部证明不完整，必须继续通用规划；
- `TERMINAL`：共享取消或全局预算已经耗尽，必须停止后续求解并保留原诊断。

只有 `PROVED_OPTIMAL` 可以进入计划。局部快速路径的 `UNSUPPORTED_PATTERN`、无可执行顺序或向量分歧不得直接成为用户可见的
“无解”诊断，也不得以局部成功覆盖尚未求解的全局路线。

若唯一生产者结构同时具有封闭输出边界，残余反向 DAG 可证明 firing vector 为所有可行解的逐分量下界；再结合外部输入下界、
守恒所需初始库存下界和可执行压缩顺序，即构成完整词典序目标的全局证明。此时首个通过证明的 reservoir 可以立即接纳，禁止继续
枚举其他 reservoir 后因局部结构不适用而降级到通用 MIP。守恒所需初始库存与纯执行前缀亏空分别计算，执行 reserve 使用前者，
不得把较小的前缀亏空误当作完整作业初始库存。

`TrinityCyclePlanSelector` 统一持有标量闭式、确定性 component 与通用 MIP 的选择策略；`TrinityGraphPlannerImpl` 只消费其
不可变 selection 结果，不再直接组合各循环求解器。

对求得整数比例并已验证顺序的阶段序列，定义：

```text
effect[key] = Σ(outputs[key] - inputs[key])
minimumSeed[key] = max(0, 每个执行前缀的最大亏空)
```

目标必须满足 `effect[target] > 0`。计划存储阶段和 `BigInteger` 重复次数，不展开重复节点。重复排程按当前余额求完整循环的
最大安全合并数，并在循环旋转的精确仿射余额断点推进；因此 `1`、`10000`、`1256000000` 和玩家数量页上限
`Integer.MAX_VALUE` 使用同一算法路径，状态数不会随请求数量线性增长。

数量模式：

| 模式 | 重复与交付 |
| --- | --- |
| `NET_NEW` | 请求 N 表示净新增 N；seed 不计入交付，交付 N 后退回 seed 与过量产物 |
| `FINAL_TOTAL` | 请求 N 表示最终总量；至少执行一个完整生产周期，交付 N 后退回多余部分 |

### 6.4 多路线 MIP

每个 binding variant 对应非负整数 firing 变量。MIP 对完整剩余请求求解，并在同一总超时预算内执行词典序目标：

1. 最小化按 AE2 存储单位求和的外部输入；
2. 固定第一阶段最优值后最小化 seed；
3. 固定前两阶段最优值后最小化 firing 数；
4. 按完整稳定 pattern identity 顺序逐项固定数值 firing，优先较早 identity，得到确定性结果。

不使用 big-M 合并目标。ojAlgo 整数变量只允许按本次求解器自身的 integrality tolerance 规范化到最近整数，随后必须用
`BigInteger` 重新验证全部输入、输出、库存上界、词典序固定层、余额和目标约束。超出该 tolerance 的真实小数、规范化后
不守恒、负余额、溢出或目标不足均拒绝。

第四层使用完整、排序后的 variant domain；稀疏 firing map 中缺失的 variant 必须补零，并以 `BigInteger` 数值逐项比较。
禁止用 publication 字符串、哈希、`double` 或 big-M 权重代替该 identity。shifted optimization 的 reduction 仅允许使用精确的
`0..baselineFirings` 结构边界；LP 松弛值、ULP guard 或经验常数不得作为整数硬上界。

库存上界使用精确的 lazy constraint generation：先求省略非约束性容量上界的最优解；若某个 seed 或外部输入超过实际库存，
只加入被违反项的精确上界并重新求解。这样既保留有限库存语义，也不会把无限存储单元发布的巨量容量直接交给数值求解器。

普通范围只有在全部变量、系数、守恒行和目标值都能在二进制浮点精确整数窗口内表达时，才进入 ordinary ojAlgo model。
超过该窗口时，firing、余额和目标值使用以 `2^15` 为基数的非负整数 digit 与有符号整数 carry 编码；每个 digit/carry 都是
真正的整数变量，不把连续 LP 值四舍五入成可执行计划。词典序目标逐层、逐 digit 固定，精确 `BigInteger` 界限负责裁剪，LP
近似值不得作为证明型上下界。解码后再次用 `BigInteger` 回放完整守恒、库存、目标和压缩排程，最后才在 AE2 边界执行
`longValueExact`。因此数量上限只来自 AE2 的正 `long` 请求域，不来自具体配方或经验 guard。

### 6.5 压缩排程验证

状态搜索不逐个执行 firing，而按“当前最大安全批次”或下一个余额断点推进。状态包含剩余 firing vector、相关余额和
阶段游标，并受 `maxScheduleStates` 限制。

MIP 中的 seed 变量是最终守恒的整数下界，压缩排程负责求出真正满足所有执行前缀的 minimum seed。同一最优 firing
层内按真实前缀 seed 比较候选；只要真实 seed 未超过可用库存，就不能因为它高于 MIP 松弛下界而把可执行循环判为无解。

只有满足以下条件的结果才能进入计划：

- 加入 minimum seed 后每个执行前缀余额非负；
- 每个阶段都存在可执行 binding；
- 最终目标增量满足数量模式；
- 逻辑次数和聚合 pattern 次数一致；
- 所有 AE2 `long` 边界可精确转换。

## 7. 执行状态机

### 7.1 事件驱动任务

执行作业维护：

- 去重 ready queue；
- `AEKey -> stage` 反向输入索引；
- provider retry 队列；
- 动态输入等待索引；
- pending planning proposal。

输出、库存、借料或重试时间到达时只唤醒相关 stage。预算耗尽只结束当前 tick，不扫描或删除未完成任务。

AE2 Crafting CPU 界面的暂停/继续控制作业级调度状态：暂停时不再派发 provider、借料或应用重规划结果，但仍接收在途
输出并允许已完成生产封存、交付和结束。该状态不复用主机结构级 pause，且随作业持久化。

显式状态至少包括：

```text
READY
WAITING_INPUT
WAITING_DYNAMIC_INPUT
WAITING_PROVIDER
PLANNING
BUDGET_EXHAUSTED
COMPLETED
FAILED
```

真死锁要求仍有任务，同时不存在 ready、在途输出、规划、动态输入等待和 provider retry。真死锁记录结构化日志并走
唯一取消/退款路径。

### 7.2 合批能力

- `CountedCraftingProvider` 及已证明等价的 admission 可按当前余额和预算合批。
- 未证明原子计数能力的 provider 每次只提交一个逻辑 craft。
- Blocking、Lock、Pulse 和专用 `ICraftingMachine` 继续使用单次路径。

`A -> 2A` 在 counted provider 上可按当前可用 A 形成 `1, 2, 4, ...` 的物理批次；generic provider 保留逐次物理
调用，但逻辑计划仍保持紧凑。

### 7.3 Seed 与最终输出门

循环计划执行时，目标输出先进入 working inventory，不直接交付 requester。全部 stage 完成且在途输出归零后：

1. 按数量模式计算精确可交付量；
2. 将该数量移动到 completion buffer；
3. completion buffer 与循环输入完全隔离；
4. requester 实际接受多少就扣减多少；
5. seed、过量目标和其它剩余物通过现有网络回收路径返还。

### 7.4 动态替代与借料

动态替代只用于循环 stage。每批提交前基于服务器线程捕获的 CPU 库存、网络库存和剩余任务重新选择合法 binding。

借料账本使用三态：

- `RESERVED`：已从网络提取，仍由 CPU 拥有，可退款；
- `COMMITTED`：provider 已取得所有权，不能凭账本复制退款；
- `RELEASED`：未使用库存已返还网络。

当前没有可行材料时先改路，仍失败则进入 `WAITING_DYNAMIC_INPUT`。相关库存变化立即唤醒，同时使用
`1, 2, 4, ... 200 tick` 的封顶退避防止漏事件；不自动超时。

### 7.5 配方变化

普通 graph revision 变化不打断任务。即将使用的 pattern signature 不再存在或语义变化时，保留现有库存并对剩余量
重规划。语义上无有效方案时等待后续目录 revision；异步计算异常或 replacement-input 预留竞态则在同一 revision 上按
`1, 2, 4, ... dynamicRetryMaxTicks` 退避重试。有效 replacement 被执行器接纳后结束本次重规划 episode，使未来独立的
pattern 失效仍可在相同 revision 上重新发起计算。所有等待均允许用户取消任务。

## 8. 持久化

作业 schema 2 持久化：

- plan kind、graph revision、数量模式；
- stage/repeat block、阶段游标和剩余次数；
- seed reserve、working inventory、completion buffer 和剩余交付量；
- 动态借料账本；
- 稳定 pattern signature 与重规划所需摘要。

ready queue 和反向索引是派生状态，载入后确定性重建。schema 1 继续按普通线性作业恢复；未知 schema 和损坏数据
fail fast、记录日志，且在能解析已有库存时只走统一回收路径。

作业外层仍为 schema 2；其执行快照使用 schema 4。schema 3 引入重试时钟基准，恢复时把 provider、动态材料和预算 retry
的剩余延迟重基到新会话 tick；schema 4 为每个 firing 保存不含输入余留物的真实声明输出，使 CPU 状态页可从紧凑游标精确
推导剩余待合成量。旧执行 schema 2 的绝对 deadline 无法可靠换算，迁移时只令其立即到期一次；schema 2/3 没有声明输出
元数据，恢复时记录警告并省略未知待合成行，不猜测数量。已接受 firing 仍以持久游标和 `waitingFor` 为准，不会重复派发。
所有 durable 状态迁移通过 revision 统一触发宿主 dirty 标记。

## 9. 配置与线程

COMMON 默认值：

| 配置 | 默认值 |
| --- | ---: |
| `maxSccKeys` | 64 |
| `maxBindingVariants` | 512 |
| `maxScheduleStates` | 500000 |
| `graphRebuildBudgetMs` | 4 |
| `plannerThreads` | `max(1, min(8, availableProcessors / 2))` |
| `plannerQueueCapacity` | 128 |
| `dynamicRetryMaxTicks` | 200 |
| `defaultQuantityMode` | `NET_NEW` |

规划使用有界执行器和有界队列。MIP 不设置 wall-clock 结果截止时间并响应 Future 的协作取消；图、variant、排程状态
和队列容量使用确定性边界，外层执行器控制并发，禁止每个样板或每个 firing 创建线程。

## 10. 诊断

稳定诊断码至少包括：

```text
NO_PRODUCTIVE_CYCLE
SCC_KEY_LIMIT
VARIANT_LIMIT
MIP_TIMEOUT
MIP_NO_INTEGER_SOLUTION
ORDER_SEARCH_LIMIT
ARITHMETIC_OVERFLOW
STALE_GRAPH
NO_ELIGIBLE_TRINITY_CPU
PLANNER_QUEUE_FULL
RUNTIME_DEADLOCK
```

日志包含 job、target、数量模式、graph revision、SCC/variant 数、求解时间、搜索状态数、fallback 和所有权状态。
等待状态只在状态迁移或限频周期记录，避免日志刷屏。

## 11. 实施顺序

1. 网格图、计划接口、规划入口和配置。**已实现**
2. DAG、Tarjan、闭式循环、MIP 和精确排程验证。**已实现**
3. ready queue、seed 门、动态借料和 schema 2。**已实现**
4. Craft Amount 数量模式、确认页诊断和 Trinity-only 过滤。**已实现**
5. 全量逻辑测试、GameTest、性能计数和重载验收。**已实现**

### 11.1 已落地的基础边界

第一步已经建立以下可独立验证的生产基础：

- `NetworkCraftingProviders` 每次变更递增的真实 revision，避免同 tick 多次 mount/unmount 漏失效；
- 服务器线程分预算捕获、revision 中途变化重启、失败丢弃半成品和完整快照原子发布；
- component-aware 的稳定 pattern identity，以及不持有 Grid、Level、provider 或 decoded pattern 的只读图；
- `TrinityCraftingPlan`/`TrinityCraftingPlanImpl`、stage、repeat block、seed、净变化、统计和保守 AE2 byte 估算；
- 单服务器共享的有界规划线程池、队列拒绝诊断、Trinity/AE2 双 Future 取消与限时优先选择；
- 独立 COMMON 配置和 ojAlgo 57.1.0 jar-in-jar/许可证声明。

### 11.2 已落地的计算内核

第二步已经建立以下可独立验证的计算能力：

- binding variant 的稳定展开、Tarjan SCC 和凝聚 DAG；
- 不随请求量逐次展开的 `BigInteger` 无环需求传播；
- `A -> 2A` 与 `A -> B -> 2A` 的闭式 repeat block 和最大前缀 seed；
- ordinary/radix ojAlgo 顺序词典序 MIP、`BigInteger` 精确整数复验和有界压缩排程证明；
- 完整 stage/repeat 守恒校验、`NET_NEW`/`FINAL_TOTAL` 数量约束和 AE2 `long` 边界诊断；
- 按 `plan`、`gateway`、`topology`、`dag`、`cycle`、`schedule` 职责组织的规划代码边界；图需求、计划组装、
  deterministic applicability/firing/proof 与 radix codec/model/search 继续使用职责子包，避免重新堆入单一 planner。

`CraftingService.beginCraftingCalculation` 已接入共享规划网关；只有服务器线程捕获合格 CPU 容量、不可变图与库存
快照，后台线程不读取世界状态。Trinity 生产规划不设置墙钟截止时间，通过 Future 协作取消及确定性的图、variant、
状态数量边界控制复杂度；经过容量与所有权校验的 Trinity 结果优先，否则保留 AE2 结果并附加诊断。每次规划先提取
目标的完整反向可达超图，再展开输入绑定和 SCC，因此无关样板不会放大本次求解。

### 11.3 已落地的执行与所有权轨道

第三步已经建立以下可独立验证的运行时能力：

- 去重 ready queue、输入反向索引、provider retry 和动态输入指数退避；
- counted provider 压缩批次，以及 generic provider 保持单次调用的兼容边界；
- 循环工作库存、隔离 completion buffer、requester 异常重试和部分接收精确扣减；
- 动态 variant 选择、服务器线程借料事务和 `RESERVED`/`COMMITTED`/`RELEASED` 守恒账本；
- pattern signature 失效后的有界异步剩余量重规划，以及无可行方案时按 revision 等待；
- 作业 schema 2 的阶段、repeat、seed、账本、封存输出和交付余量持久化，schema 1 普通作业继续恢复；
- 执行快照 schema 4 的声明输出、紧凑剩余待合成投影和 schema 2/3 保守迁移；
- `TrinityPlanAdmission` 统一显式目标、自动选择、fallback 和直接 CPU 的计划接纳语义。

### 11.4 已落地的数量与诊断轨道

第四步已经建立以下玩家入口和最终隔离：

- Craft Amount 页面同步 `NET_NEW`/`FINAL_TOTAL`，返回数量页时保留本次选择；
- 玩家选择通过 AE2 `IActionSource` context 进入本次计算，机器和外部请求使用 COMMON 默认值；
- 有合格 Trinity CPU 时并行启动 Trinity 与 AE2 Future，无合格 CPU 时不捕获图或库存并直接走 AE2；
- 计划超过请求开始时捕获的最大 Trinity 容量时拒绝 Trinity 结果，并保留 AE2 结果与诊断；
- 确认页显示 Trinity-only、循环动态材料警告、诊断以及与 AE2 原生计划一致的可用/待合成数量；
- CPU 状态页从 stage/repeat 游标推导待合成量，并把已封存 completion buffer 计入已存储量；
- 确认页过滤、自动选核与 `submitJob` 都复用同一 CPU-family admission 边界。
- 新计算和重算立即清除旧计划摘要；确认页只在新结果已经过下一轮 CPU 资格过滤后开放按钮与 Enter 提交。

### 11.5 已完成的集成验收

第五步已补齐并验证以下关键证据：

- 真实 Trinity CPU 可执行单样板 `A -> 2A`，一个 seed 对 `NET_NEW 31` 形成紧凑 repeat block，最终守恒为 32；
- DAG 多路线使用完整目标可达区域求解，库存不可用的稳定首选路线不会遮蔽可执行替代路线；
- 运行时 variant ordinal 与图快照采用同一去重 publication signature，不会因重复输入候选绑定到错误材料；
- 大数量 DAG 的状态计数只随图和 variant 数变化，不随请求量逐个展开；
- 既有 `test`、GameTest、`build` 和改动文件 IDEA inspections 基线均通过；每个后续功能提交继续记录本次实际结果，不固化易失真的测试数量；
- 发布 JAR 内含 ojAlgo 57.1.0 的 jar-in-jar 元数据，开发运行通过 additional runtime classpath 加载同一版本。
