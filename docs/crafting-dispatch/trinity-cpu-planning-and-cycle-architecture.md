# Trinity CPU 计算与增殖循环架构

## 1. 文档状态

- 方案状态：已确定，按功能提交实施
- 实现进度：网格图与规划入口、DAG/SCC/循环求解内核已经完成；运行时与界面轨道待实现
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

当存在在线、空闲的 Trinity CPU 时，Trinity 与 AE2 计算并行启动。Trinity 在配置预算内生成有效计划且存在容量匹配
的 Trinity CPU 时优先；否则采用 AE2 结果。两者都失败时保留 AE2 的 simulation/missing 结果，并附加 Trinity
诊断。

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

`TrinityDiagnosedCraftingPlan` 委托原 AE2 simulation 结果并附加诊断，只用于 UI，不允许提交。

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

### 6.3 确定性循环

对已验证顺序的阶段序列，定义：

```text
effect[key] = Σ(outputs[key] - inputs[key])
minimumSeed[key] = max(0, 每个执行前缀的最大亏空)
```

目标必须满足 `effect[target] > 0`。计划存储阶段和 `BigInteger` 重复次数，不展开重复节点。

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
4. 使用稳定 pattern identity 得到确定性结果。

不使用 big-M 合并目标。ojAlgo 解必须取精确整数，并用 `BigInteger` 重新验证全部输入、输出、余额和目标约束。
近似整数、负余额、溢出或目标不足均拒绝。

### 6.5 压缩排程验证

状态搜索不逐个执行 firing，而按“当前最大安全批次”或下一个余额断点推进。状态包含剩余 firing vector、相关余额和
阶段游标，并受 `maxScheduleStates` 限制。

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
重规划。无有效方案时等待后续目录 revision，用户仍可取消任务。

## 8. 持久化

作业 schema 2 持久化：

- plan kind、graph revision、数量模式；
- stage/repeat block、阶段游标和剩余次数；
- seed reserve、working inventory、completion buffer 和剩余交付量；
- 动态借料账本；
- 稳定 pattern signature 与重规划所需摘要。

ready queue 和反向索引是派生状态，载入后确定性重建。schema 1 继续按普通线性作业恢复；未知 schema 和损坏数据
fail fast、记录日志，且在能解析已有库存时只走统一回收路径。

## 9. 配置与线程

COMMON 默认值：

| 配置 | 默认值 |
| --- | ---: |
| `maxSccKeys` | 64 |
| `maxBindingVariants` | 512 |
| `mipTimeoutMs` | 250 |
| `maxScheduleStates` | 500000 |
| `graphRebuildBudgetMs` | 4 |
| `plannerThreads` | `max(1, min(8, availableProcessors / 2))` |
| `plannerQueueCapacity` | 128 |
| `dynamicRetryMaxTicks` | 200 |
| `defaultQuantityMode` | `NET_NEW` |

规划使用有界执行器和有界队列。每次 MIP 使用剩余 wall-clock 预算并响应取消；外层执行器控制并发，禁止每个样板或
每个 firing 创建线程。

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
3. ready queue、seed 门、动态借料和 schema 2。
4. Craft Amount 数量模式、确认页诊断和 Trinity-only 隔离。
5. 全量逻辑测试、GameTest、性能计数和重载验收。

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
- ojAlgo 顺序词典序 MIP、精确整数复验和有界压缩排程证明；
- 完整 stage/repeat 守恒校验、`NET_NEW`/`FINAL_TOTAL` 数量约束和 AE2 `long` 边界诊断；
- 按 `plan`、`gateway`、`topology`、`dag`、`cycle`、`schedule` 职责组织的规划代码边界。

计算内核完成后仍不直接包装 `CraftingService.beginCraftingCalculation`。现有玩家和机器请求继续使用 AE2，直到 schema 2
执行器、seed/completion buffer 和动态借料所有权轨道可以完整消费 `TrinityCraftingPlan`，避免生成执行器无法安全消费的
扩展计划。
