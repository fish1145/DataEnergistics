# Trinity CPU 计算与增殖循环架构

## 1. 文档状态

- 方案状态：已确定，按功能提交实施
- 实现进度：网格图、Trinity/AE2 资格分流入口、DAG/SCC/循环求解、完整正 `long` 请求域的精确 radix MIP、事件执行、动态借料、schema 4 和数量/诊断界面均已完成
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

存在合格 Trinity CPU 时，当前网关只提交并等待 Trinity 计算；传入的 AE2 supplier 不会在该分支启动。没有合格 Trinity
CPU 时，入口在捕获图和库存前直接走 AE2。Trinity 对 DAG 或 cyclic SCC 精确证明缺料时可发布常量空间 diagnosis
simulation；其它失败返回 Trinity 诊断，不应把循环或动态输出请求描述为可由 AE2 通用接管。

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
provider、BlockEntity 或世界引用。展开前按输入槽做动态规划：聚合消耗与输入余留物效果完全相同的绑定只保留首个
合法 Cartesian representative，规划和运行时动态选料共用同一枚举边界。`maxBindingVariants` 统计经过单 pattern 等价
binding 压缩后、跨 pattern transition-effect family 压缩前实际物化的请求级 variant 总数，而不是原始笛卡尔组合数；
每个唯一绑定样板同样占用一个 variant 名额。后续跨 pattern 严格压缩只减少 MIP firing axes，不返还该确定性展开预算。已有
计划保存的非规范 raw ordinal 仍按原笛卡尔语义直接解码，避免恢复时静默改绑。

### 4.2 Revision 与重建

- 图 revision 来源于 `NetworkCraftingProviders` 每次有效 mount/unmount 递增的 Trinity 单调 revision bridge；不使用同
  tick 内可能重复的 `getLastModifiedOnTick()`。
- 每 tick 最多使用 `graphRebuildBudgetMs` 构建快照。
- 构建开始和发布前都校验 revision；中途变化则丢弃未发布结果。
- 完整快照构建后一次性原子替换，规划线程不会看到半成品。
- Grid 关闭或重建时取消关联计算并释放快照。

### 4.3 库存快照与创造来源

库存快照只查询本次图中的 exact `AEKey`。物品注册名相同但 data components 不同仍是不同 key；规划器不会把花自动视为染料、
把甘蔗视为糖，也不会在库存捕获阶段增加 fuzzy/tag fallback。

普通小数量直接读取 AE2 cached inventory。缓存值达到 `Integer.MAX_VALUE` 哨兵区间时，服务器线程使用同一请求的
`IActionSource` 调用 `extract(key, Long.MAX_VALUE, SIMULATE)`，以真实可模拟提取量作为规划上界。这样原生创造存储元件在
AE2 正 `long` 执行域内表现为 `Long.MAX_VALUE`，普通大容量有限存储仍保留真实有限值。该上界只进入不可变请求库存和 solved
cache key；最终计划仍只保存实际需要提取的有限数量，不把“无限”写入执行 NBT。高数量探测失败会产生
`phase=inventory_capture` 的终端诊断，不能退回可能过时的缓存值。

## 5. 计划接口

内部类型 `TrinityCraftingPlan` 实现 `TrinityCpuExecutablePlan`。除 AE2 原字段外，
计划至少包含：

- graph revision 与请求数量模式；
- 初始预计原料和聚合 pattern 次数；
- DAG stage、cycle repeat block 和稳定 pattern binding；
- minimum seed、目标净增量和最终交付量；
- 保守 `bytes()`、诊断与算法统计。

`TrinityDiagnosedCraftingPlan` 只用于 UI，不允许提交：普通 fallback 原样委托 AE2 simulation；DAG 或 joint-cycle root
可精确证明输入不足时，则以常量空间投影 `available` 与 `missing` 计数，不再等待 AE2 大数量展开。只有完成 ordinary/radix
整数解码、`BigInteger` 守恒和逐 key 缺料复验的结果才能成为权威缺料；虚拟输入和诊断 firing 不进入 executable plan。

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

无环多路线在建模前先以 producer 索引执行事件驱动的正向可执行性与反向目标可达性剪枝，复杂度为
`O(V + E)`，不会为每个目标反复扫描整张样板表。剪枝后只有一条可行路线时直接进入 `BigInteger` 批量传播；
仍有多路线时先把唯一生产者后缀批量传播到竞争前沿。只有至少两个前沿的完整上游 variant、pattern identity、有限库存 key、
输入、完整输出（含副产物与余留物）和守恒 touched key 均可证明互不相交时，才分别建立局部 MIP；局部结果合并后重新执行
全局 `BigInteger` 守恒、目标语义和完整前缀非负复验。任何交叉、预算不足或复验失败都回退完整目标可达区域的原全局 MIP。
词典序优化只在 source-capacity 无法证明当前 firing 已达到精确上界时继续求解 identity MIP。这些证明只依赖图结构、库存与
守恒，不依赖配方名称或请求数量。

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

唯一生产者 SCC 若存在一个内部 seed 不减少、所有被请求边界输出均为正净变化的 primitive firing ray，则形成
`TrinityCycleMacro`。对每个被请求输出 `key` 计算 `ceil(demand[key] / unitEffect[key])`，取最大值作为完整单元重复次数；
不为请求数量展开 firing。若上层 DAG 同时要求多个循环输出，唯一 residual DAG 只保留一次性 prefix/suffix；minimum seed
证明核由“完整 residual + 足以覆盖 residual reservoir 亏空的最少 primitive 单元”组成。精确 Dijkstra 只搜索该有界证明核，
其余 primitive 单元因内部净变化非负而直接追加为一个 repeat block。相邻同样板 firing 合并为一个 unit stage，状态数只随
样板拓扑、residual 和余额断点变化，不随 256M 或十亿级请求量展开。

宏对外只发布完整单元的正 `unitNetChange`。内部 seed、阶段中途产物和任意未完成前缀都保持 SCC 私有；下游 DAG 只能捕获
`repetitions * exportableNet`，不能把循环内部任意位置的临时余额当作可用产物。一次性 residual 只有在唯一生产者 DAG、完整
守恒与证明核排程全部成立时才能放在 repeat block 两侧；若任一内部 key 在完整单元后减少、residual 有歧义或精确排程证明
失败，则该宏不适用并继续通用规划。

通用 joint 路径同样显式携带已经证明的 `exportableNet`，不得从任意 firing vector 的正余额反推可导出材料。当上层请求 SCC
内部 key 时，未被请求的内部 key 不得留下正净增中间产物；允许出现已经由最终余额约束验证的负净变化，因为它只表示消耗了
预先保留的现有循环材料，并不会越过完成边界。当上层只请求环外边界输出时，内部 key 允许保持非负工作余额，但这些余额仍不
进入导出集合。非全局目标的循环需求按“所需最终余额减现有库存”的净缺口传播，现有 seed 只参与
可执行性验证，不会被重复计作新增需求，也不会提前供给其它分支。

`TrinityCyclePlanSelector` 统一持有标量闭式、确定性 component 与通用 MIP 的选择策略；
`ExactTrinityGraphPlanningPipeline` 只消费其不可变 selection 结果，不再直接组合各循环求解器。

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

每个 binding variant 对应非负整数 firing 变量。MIP 对完整剩余请求求解，并在同一最优性证明预算内执行词典序目标：

1. 最小化按 AE2 存储单位求和的外部输入；
2. 固定第一阶段最优值后最小化 seed；
3. 固定前两阶段最优值后最小化 firing 数；
4. 按完整稳定 pattern identity 顺序逐项固定数值 firing，优先较早 identity，得到确定性结果。

不使用 big-M 合并目标。ojAlgo 整数变量只允许按本次求解器自身的 integrality tolerance 规范化到最近整数，随后必须用
`BigInteger` 重新验证全部输入、输出、库存上界、词典序固定层、余额和目标约束。超出该 tolerance 的真实小数、规范化后
不守恒、负余额、溢出或目标不足均拒绝。

第四层使用完整、排序后的 variant domain；稀疏 firing map 中缺失的 variant 必须补零，并以 `BigInteger` 数值逐项比较。
禁止用 publication 字符串、哈希、`double` 或 big-M 权重代替该 identity。仓库保留的 shifted optimizer 当前没有生产调用方；
未来若重新接入，其 reduction 只能接收已经完整验证的 baseline，并使用精确的 `0..baselineFirings` 结构边界。LP 松弛值、
ULP guard 或经验常数不得作为整数硬上界，超时也不得把未验证 firing upper bound 当作 executable incumbent。

正常 executable model 为每个有限 seed/外部输入施加捕获库存上界；创造来源在进入模型前已经由服务器线程的真实提取探测规范化。
joint root 在这些上界下返回 `MIP_NO_INTEGER_SOLUTION` 且尚无 incumbent 时，才建立 diagnosis request：每个有限 reserve 拆为
`actual + missing`，先证明最小 missing，再固定 external、seed、firing、稳定 firing identity 和逐 key reserve identity。
ordinary 与 radix 都执行相同目标并用 `BigInteger` 重放；只有正 missing 的完整证明才转换成 `INSUFFICIENT_INPUT`。missing 为零、
诊断超时、状态/模型上限或 relaxed model 仍不可行时保留原 `MIP_NO_INTEGER_SOLUTION`，不会把诊断候选升级成计划。

普通范围只有在全部变量、系数、守恒行和目标值都能在二进制浮点精确整数窗口内表达时，才进入 ordinary ojAlgo model。
超过该窗口时，firing、余额和目标值使用以 `2^15` 为基数的非负整数 digit 与有符号整数 carry 编码；每个 digit/carry 都是
真正的整数变量，不把连续 LP 值四舍五入成可执行计划。词典序目标逐层、逐 digit 固定，精确 `BigInteger` 界限负责裁剪，LP
近似值不得作为证明型上下界。解码后再次用 `BigInteger` 回放完整守恒、库存、目标和压缩排程，最后才在 AE2 边界执行
`longValueExact`。因此数量上限只来自 AE2 的正 `long` 请求域，不来自具体配方或经验 guard。

同一 joint search 请求内，ordinary 路径只装配一次稳定变量与稀疏守恒系数模板；root、firing-box child 和 external-cut child
均从该私有模板复制 model，并重新施加当前 bounds、reserve upper、fixed external 与词典序层。precision 仍逐 child 判断
ordinary/radix，radix-only 请求不会预建 ordinary 模型。radix 的 digit width、upper slack、carry 列和证明域依赖当前 bounds
与 pass，当前仍逐 pass 私有装配，不跨 child、线程或请求共享可变编码器。

### 6.5 压缩排程验证

状态搜索不逐个执行 firing，而按“当前最大安全批次”或下一个余额断点推进。状态包含剩余 firing vector、相关余额和
阶段游标，并受 `maxScheduleStates` 限制。一次请求拥有一份共享的全图 graph-route search budget；纯 DAG route optimization
使用这份全图预算。除此之外，每个 cyclic SCC 各自拥有一份同值的局部 search/schedule budget，因此多个 SCC 的最终统计值可以
相加后超过单个局部上限，但全图路线分支不会按 SCC 重置预算。cycle shortage diagnosis 只能使用 root 后剩余的局部 states；
ordinary 每次实际 `minimise()`、radix 每次 certified/adjacent/digit probe 都先消费一个 state，达到上限即停止诊断并保留原失败。

已证明的 primitive macro 只排程一个完整单元，再以 `BigInteger repetitions` 保存重复次数；不会把 256M 或十亿级请求
铺平成同数量级的 schedule batch。单元内部仍保留精确可执行的余额断点，因此压缩不改变 provider 顺序或 seed 守恒。

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

循环还承担上层材料供给时，只有一个完整 repeat 单元结算后、且列入 `exportableNet` 的正净增输出可以唤醒下游 stage；单元
内部的临时 charged、dust、seed 或其它中间余额不能越过 repeat block 边界。这样同一循环的多个已结算副产物可以共同供给
上层，而不会提前消费下一轮 seed。

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
| `maxBindingVariants` | 32768 |
| `maxScheduleStates` | 500000 |
| `planningBudgetMs` | 30000 |
| `graphRebuildBudgetMs` | 4 |
| `plannerThreads` | `max(1, min(8, availableProcessors / 2))` |
| `cpuPlannerThreads` | `max(1, min(8, availableProcessors / 2))` |
| `plannerQueueCapacity` | 128 |
| `dynamicRetryMaxTicks` | 200 |
| `defaultQuantityMode` | `NET_NEW` |

初始请求与运行中作业的剩余量重规划使用相互隔离的有界执行轨道，后台作业不会占用确认页规划线程。`plannerThreads`
只限制初始计划，`cpuPlannerThreads` 只限制 CPU 剩余量重规划，两项配置互不拆分也互不借用。两条轨道与
CPU 派发候选计算共享同一个服务器生命周期、线程安全的 computation cache，相同语义键可以跨轨道命中，执行隔离不会
复制或分割缓存。CPU 派发为每个已接纳的 CPU worker ticket 启动独立虚拟线程，同时保留单 CPU、单 Grid 和全局
outstanding 上限；世界状态和资源提交仍只在服务器线程执行。每个 worker 从真正开始计算时捕获不可变限制并共享默认 30 秒
最优性证明预算。结构编译使用取消感知 control；求解在剩余预算内证明词典序最优。预算耗尽且已有完整验证的 incumbent 时返回
`VERIFIED_FEASIBLE`；没有 incumbent 时改用无墙钟截止、仍受图/variant/route/SCC state 上限约束的
`FIRST_FEASIBLE` 路径。Future 取消始终终止请求并丢弃 incumbent。
图、variant、排程状态和有界接纳继续使用确定性边界，禁止每个样板或每个 firing 创建线程。

## 10. 诊断

稳定诊断码至少包括：

```text
NO_PRODUCTIVE_CYCLE
SCC_KEY_LIMIT
VARIANT_LIMIT
INSUFFICIENT_INPUT
MIP_TIMEOUT
MIP_NO_INTEGER_SOLUTION
ORDER_SEARCH_LIMIT
ARITHMETIC_OVERFLOW
STALE_GRAPH
NO_ELIGIBLE_TRINITY_CPU
PLANNER_QUEUE_FULL
RUNTIME_DEADLOCK
```

当前 verbose 规划日志包含 request、target、数量模式、graph revision、cache path、quality、SCC/variant 数、
`planningNanos`、`firstFeasibleNanos`、`mipNanos`、聚合 `scheduleStates`、`solverPasses`、`solverModels`、
`jointStates` 和 `routeStates`；执行日志继续记录 job、fallback 与所有权状态。model 统计基础/编码模型装配，pass 统计实际
ojAlgo minimise/maximise/probe；exact 与 proven-equivalent cache hit 的本请求 MIP 和搜索计数归零。`firstFeasibleNanos` 仍以
最终完整计划可发布时刻为准，不能冒充更早但尚未通过最终 byte/`long` 边界的数值 witness 时间。请求累计指标达到类型上限时
饱和，遥测溢出不得反向终止规划。库存捕获另记录 `inventorySentinelProbes` 与 `effectiveLongMaxKeys`；缺料诊断 metadata
记录 `shortageKinds`、首个稳定 key 的 `required/available/missing`、ordinary/radix model、真实 solver pass/MIP nanos 和
`shortageDiagnosisStates`。`MIP_NO_INTEGER_SOLUTION` 只表示库存诊断没有证明出正缺料的结构性或整数不可行。
等待状态只在状态迁移或限频周期记录，避免日志刷屏。

## 11. 实施顺序

1. 网格图、计划接口、规划入口和配置。**已实现**
2. DAG、Tarjan、闭式循环、MIP 和精确排程验证。**已实现**
3. ready queue、seed 门、动态借料和 schema 2。**已实现**
4. Craft Amount 数量模式、确认页诊断和 Trinity-only 过滤。**已实现**
5. 现有构建检查与用户真实环境验收。**进行中**

### 11.1 已落地的基础边界

第一步已经建立以下可独立验证的生产基础：

- `NetworkCraftingProviders` 每次变更递增的真实 revision，避免同 tick 多次 mount/unmount 漏失效；
- 服务器线程分预算捕获、revision 中途变化重启、失败丢弃半成品和完整快照原子发布；
- component-aware 的稳定 pattern identity，以及不持有 Grid、Level、provider 或 decoded pattern 的只读图；
- `TrinityCraftingPlan`、stage、repeat block、seed、净变化、统计和保守 AE2 byte 估算；
- 单服务器共享的有界规划线程池、队列拒绝诊断和 Trinity Future 协作取消；
- 独立 COMMON 配置和 ojAlgo 57.1.0 jar-in-jar/许可证声明。

### 11.2 已落地的计算内核

第二步已经建立以下可独立验证的计算能力：

- binding variant 的稳定总量边界、显式栈 Tarjan SCC 和凝聚 DAG；
- 不随请求量逐次展开的 `BigInteger` 无环需求传播，以及基于可回滚 journal 的显式 DFS 图需求搜索；
- `A -> 2A` 与 `A -> B -> 2A` 的闭式 repeat block 和最大前缀 seed；
- 多边界输出的 primitive cycle macro、完整单元净增结算和只随图结构增长的 unit stage；
- 等价输入绑定的 transition-effect 动态规划压缩，以及规划/运行时共享的 canonical representative；
- ordinary/radix ojAlgo 顺序词典序 MIP、`BigInteger` 精确整数复验和有界压缩排程证明；
- 高库存哨兵的 live extraction 规范化，以及 root cyclic MIP 的有界 ordinary/radix 精确缺料证明；
- `PROVED_OPTIMAL`/`VERIFIED_FEASIBLE` 质量传播、超时 incumbent 接纳和无 incumbent 的 first-feasible 回退；
- 跨 pattern 严格 transition-effect representative、DAG/ordinary 请求私有模型模板、joint child 的 ordinary 模板复用、
  单轴循环搜索分区和不可行 box 记忆；
- reachable/compiled/solved computation cache 只保留成功结果；所有失败只共享给当时已经等待同一计算的调用方，完成后立即移除，
  不允许后续请求以 `EXACT_HIT` 复用过时诊断；
- exact solved cache 与仅对最优计划生效的库存证明等价索引：库存逐项减少且旧计划仍可行时可复用；纯 DAG 的库存增加
  只有在该 key 是已证明不参与约束的 `NET_NEW` target，或参考值和新值都高于非绑定 consumption cap 时可复用；cycle 或
  无法证明的增加继续普通 solve；
- 完整 stage/repeat 守恒校验、`NET_NEW`/`FINAL_TOTAL` 数量约束和 AE2 `long` 边界诊断；
- 按 `plan`、`gateway`、`topology`、`dag`、`cycle`、`schedule` 职责组织的规划代码边界；图需求、计划组装、
  deterministic applicability/firing/proof 与 radix codec/model/search 继续使用职责子包，避免重新堆入单一 planner。

`CraftingService.beginCraftingCalculation` 已接入共享规划网关；只有服务器线程捕获合格 CPU 容量、不可变图与库存
快照和单次请求 limits，后台线程不读取世界状态或可变 Configuration。Trinity 生产规划默认使用 30 秒最优性证明预算，
并结合 Future 协作取消及确定性的图、variant、全图 route state 与逐 SCC 局部 state 边界控制复杂度。每次规划先提取目标
的完整反向可达超图，再展开输入绑定和 SCC，因此无关样板不会放大本次求解。纯 DAG
计划使用数量化 material-token provenance 建立依赖：初始余额足以覆盖多个消费者时，它们可以并发 lease；只有确实复用
先前 stage 返回的稀缺 seed/catalyst，或消费先前 stage 的净产出时才保留前驱。repeat cursor独立保证一个 cycle block
内部顺序，cycle身份本身不再形成跨图全局屏障。恢复旧执行快照时，完整输出元数据按 shared-input 与 positive-net
producer安全稀疏化旧依赖；缺少输出元数据的旧快照继续保留原保守依赖。

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
- 有合格 Trinity CPU 时只启动 Trinity Future；无合格 CPU 时不捕获图或库存并直接走 AE2；
- 计划超过请求开始时捕获的最大 Trinity 容量时拒绝 Trinity 结果并返回对应诊断；
- 确认页显示 Trinity-only、循环动态材料警告、诊断以及与 AE2 原生计划一致的可用/待合成数量；
- CPU 状态页从 stage/repeat 游标推导待合成量，并把已封存 completion buffer 计入已存储量；
- 确认页过滤、自动选核与 `submitJob` 都复用同一 CPU-family admission 边界。
- 新计算和重算立即清除旧计划摘要；确认页只在新结果已经过下一轮 CPU 资格过滤后开放按钮与 Enter 提交。

### 11.5 当前验证边界

历史实现曾覆盖以下场景，但当前工作树未保留对应的核心 planner 测试与性能基准，不能把历史记录视为本分支的自动化证据：

- 真实 Trinity CPU 可执行单样板 `A -> 2A`，一个 seed 对 `NET_NEW 31` 形成紧凑 repeat block，最终守恒为 32；
- DAG 多路线仅在严格不相交的多个竞争前沿使用局部 MIP，其余情况保守回退完整目标可达区域；
- 运行时 variant ordinal 与图快照采用同一去重 publication signature，不会因重复输入候选绑定到错误材料；
- 大数量 DAG 的状态计数只随图和 variant 数变化，不随请求量逐个展开；
- 本分支只运行现有 `test`、`build`、Spotless 和改动文件 IDEA inspections；整套 Trinity 配方的正确性与实际耗时由用户环境验收；
- 发布 JAR 内含 ojAlgo 57.1.0 的 jar-in-jar 元数据，开发运行通过 additional runtime classpath 加载同一版本。
