# Trinity CPU 计算与循环能力审计及修复报告

## 1. 审计结论

当前 Trinity CPU 的 Phase 0 至 Phase 2 已解决候选选择、公平轮询、派发拒绝、输入所有权和物理预算问题。当前分支也
已完成 Trinity 独立 DAG/SCC 计算内核，但生产请求与执行器仍沿用 AE2 的扁平计划契约。因 schema 2、seed 输出门和
动态借料尚未接入，现阶段仍不能在真实 CPU 上安全执行自增殖或多步增殖。

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

当前分支已建立网格图、扩展计划契约、`TrinityPlanningGateway` 双路 Future 和完整 DAG/SCC 规划器，但尚未在
`CraftingServiceMixin` 接管 `beginCraftingCalculation`。真实请求仍走 AE2；必须先由下一执行轨道完成 schema 2、
seed/completion buffer 和动态借料所有权，再激活扩展入口。

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

当前状态：扩展计划、stage、repeat block、seed、净变化和精确 byte 边界已经实现；schema 2 与运行时消费仍待执行轨道
提交。

### C-002：AE2 明确拒绝递归生产路径

AE2 19.2.17 的 `CraftingTreeNode.notRecursive` 会沿祖先链拒绝输出或输入匹配当前节点的 pattern；
`CraftingCalculation` 还会对最终输出调用 `craftingInventory.ignore(output)`。

影响：

- `A -> 2A` 无法形成计划；
- `A -> B -> 2A` 在祖先检查处被截断；
- 网络中已有 A 不能作为受控 seed 参与“净新增”计算。

修复：不修改 AE2 递归树；Trinity 使用样板超图、Tarjan SCC、闭式循环和受限 MIP 独立规划，失败时仍保留 AE2 结果。

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

## 4. 修复映射

| 缺陷 | 修复组件 | 当前状态 | 主要证据 |
| --- | --- | --- | --- |
| C-001 | graph snapshot、扩展计划、schema 2 | 计划契约已完成；schema 2 待实现 | 计划不持有 decoded pattern、阶段聚合和边界测试 |
| C-002 | Tarjan、闭式循环、Trinity planner | 计算内核已完成；真实 CPU 执行待实现 | 自增殖、多步增殖、MIP 与 seed 前缀逻辑测试 |
| C-003 | DAG 批量传播、revision 图缓存 | 已完成 | 同 tick 失效、大数量状态计数与 `long` 溢出测试 |
| C-007 | 已有 pattern-sort dirty flag Mixin | 已完成 | 重复读取不会重复排序 |
| C-004 | working inventory、completion buffer | 待实现 | seed 保留和精确交付测试 |
| C-005 | ready queue、反向索引、retry queue | 待实现 | 无关任务不被重复访问 |
| C-006 | schema 2 | 待实现 | 各阶段重载与 schema 1 兼容测试 |
| C-008 | dispatch scope 边界检查 | 已完成 | 零额外 MODULATE 调用 |
| C-009 | 确定性测试窗口 | 已完成 | 256-worker 测试不依赖墙钟 |

## 5. 风险与控制

| 风险 | 控制 |
| --- | --- |
| MIP 数值解不精确 | `BigInteger` 二次验证，失败即拒绝 |
| 任意 Petri net 搜索失控 | SCC/variant/time/state 四重上限 |
| 异步线程访问世界 | 只传不可变值对象，服务器线程二次校验 |
| 动态借料复制或误退款 | RESERVED/COMMITTED/RELEASED 所有权状态 |
| 配方热更新执行旧语义 | pattern signature 校验，只重规划剩余量 |
| 扩展计划进入原生 CPU | UI、自动选择、submit 三层隔离 |
| 缺料等待造成忙轮询 | key 唤醒加最高 200 tick 退避 |
| 大数量溢出 | 内部 `BigInteger`，AE2 边界精确转换 |

## 6. 验证矩阵

### 6.1 规划

- 单路径与多路径 DAG；
- `A -> 2A`；
- `A -> B -> 2A`；
- 多路线 SCC 和确定性 tie-break；
- 非生产 SCC、无 seed、MIP 超时、SCC/variant/search 上限；
- `NET_NEW` 与 `FINAL_TOTAL`；
- `long` 边界与溢出。

### 6.2 执行与守恒

- counted provider 几何批次；
- generic provider 单次语义；
- 动态替代、追加借料、改路和无限期缺料等待；
- 部分 requester 接受、standalone、取消和在途输出；
- 配方 revision 变化和剩余量重规划；
- 真死锁诊断和唯一退款路径。

### 6.3 恢复与集成

- schema 1 普通任务恢复；
- schema 2 在每个 cycle phase、借料后和封存后恢复；
- Trinity 与原生 CPU 同网格；
- 无 Trinity 时 AE2 回退；
- 扩展计划被原生 CPU 拒绝；
- `test`、`runGameTestServer`、`build` 和 IDEA inspections。

## 7. 完成判定

只有以下证据同时成立才可关闭本审计：

1. C-001 至 C-009 均有直接行为测试或集成证据；
2. 自增殖和多步增殖在真实 Trinity CPU GameTest 中完成且数量守恒；
3. 大数量规划没有按 Q 展开；
4. schema 1/2 重载、取消和动态借料不丢失或复制；
5. 原生 CPU 无法接收扩展计划；
6. 全量构建、GameTest 和 IDE 检查通过。
