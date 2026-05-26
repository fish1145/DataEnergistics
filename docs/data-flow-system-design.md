# Data Flow System Design

## 1. Scope

本文档用于定义 DataEnergistics 中“Data Flow（数据流）”这一概念的**预期含义、设计边界、当前实现线索与未来演进方向**。

本文档是**设计与文档工作**，不是功能实现。

本文档：

1. **不实现代码**；
2. **不修改现有能量行为**；
3. **不改变任何现有玩法逻辑**；
4. **不宣称 Data Flow 已经完整实现**。

本文档只基于当前仓库真实源码、既有审计文档与已有验证记录给出谨慎结论。

## 2. Current Status Summary

当前仓库可确认的状态如下：

- 部分物品已经实现 **AE2 item energy**，例如 `DataCaptureBallItem`、`MatterConvergingCrossbowItem`、`DataFlowPortableCellItem` 一类物品围绕 `IAEItemPowerStorage` / `AEComponents.STORED_ENERGY` 工作。
- 部分机器已经实现 **AE2 network-powered** 运行模式，例如 `DataExtractorBlockEntity`、`DataRipperReassemblerBlockEntity`、`DataTeleportAnchorBlockEntity`、`DataSolarPanelBlockEntity`、`DataMimeticFieldBlockEntity`。
- 部分 **FE / NeoForge Energy capability** 已存在，但根据 `docs/energy-system-audit.md`，明确证据主要集中在 `DataDistributionTowerBlockEntity`。
- `DataEnergyStorage` / `MutableDataEnergyStorage` 及其 adapter 已存在，但目前仍是 **abstraction foundation / seam**，不是已被证明接入全运行时的统一能源系统。
- **AE -> FE conversion** 未实现。
- **FE -> AE conversion** 未实现。
- **全机器共享能源网络** 未实现 / 未被证明。
- **Data Flow 作为完整统一玩法系统尚未实现**。

因此，当前状态最合适的描述是：

> **有雏形、有机器、有方向，但不是完整数据流系统。**

## 3. Definition of Data Flow

### 长定义

在 DataEnergistics 中，Data Flow 应被定义为：

> 一种建立在 AE2 之上的**数据化自动化信息层**。  
> 它把物质、样板、机器状态、自动化请求与执行结果表示为可被捕获、分析、路由、转换、模拟、执行和回报的“数据对象”，并通过 AE2 相关机器、终端和网络语义来处理这些对象。

更具体地说，Data Flow 的关注点不是“机器有没有电”，而是：

1. 某种对象是否可以被表示为数据；
2. 这些数据是否可以被采集、暂存、编码、显示、比较与路由；
3. 这些数据是否可以驱动上层自动化判断、诊断与执行。

### 短定义

> **数据流不是电力，而是自动化的信息层。**

或更简洁地说：

> **Data Flow is not power. Data Flow is automation information.**

## 4. What Data Flow Is Not

Data Flow 必须与以下概念明确区分：

1. **不是 FE**。  
   FE 是 NeoForge Energy capability；Data Flow 不是 FE 单位。
2. **不是 AE**。  
   AE2 item energy / AE2 network energy 是能量系统；Data Flow 不是 AE 电量。
3. **不是 Lightning Energy**。  
   `docs/ae2lt-lightning-capability-design.md` 已明确 Lightning capability deferred。
4. **不是 AE/FE converter**。  
   当前仓库并未实现 AE <-> FE conversion。
5. **不是共享机器能源网络**。  
   `docs/energy-system-audit.md` 已明确当前不能宣称所有机器共享能源。
6. **不等于 AE2 autocrafting 本身**。  
   AE2 autocrafting 是底层自动化能力；Data Flow 是可建立在其上的上层语义层。
7. **不因为启动验证通过就成立**。  
   `clean build` / `runData` / `runClient` / `runServer` 只能证明启动级健康，不等于玩法系统已完成。
8. **不因为 `DataEnergyStorage` 存在就成立**。  
   `DataEnergyStorage` 只是 energy abstraction seam，不是 Data Flow 本体，也不是统一运行时证明。

## 5. Data Flow Categories

### 5.1 Matter Data Flow

**概念**

Matter Data Flow 指的是：  
把物品、方块、实体样本或其特征转化为“可被记录、存储、回放、重组或消费的数据表示”。

**潜在机器 / 物品**

- `DataExtractorBlockEntity`
- `DataRipperReassemblerBlockEntity`
- `DataCaptureBallItem`
- `DataFlowPortableCellItem`

**潜在未来能力**

- 将物质或样本捕获为数据；
- 从实体、矿物、作物中提取数据样本；
- 按规则把数据重新组装回物质或输出结果；
- 做完整性校验；
- 防止复制与错误回滚。

**当前实现状态**

- **已有实现证据（局部）**：
  - `DataExtractorBlockEntity` 明确围绕 `DataFlowKey`、`BiologyDataCarrierData`、`OreDataCarrierData`、`CropDataCarrierData` 工作；其 `performWork()` 会向 AE2 inventory 插入 `DataFlowKey`，`recordOreSample()` / `recordCropSample()` / `applyDamageAndCollectBiology()` 会记录样本数据。
  - `DataCaptureBallItem` 是 `IBasicCellItem`，并使用 `DataKeyType` / `DataKey` 作为单独的数据存储类型；`captureDispersingData(...)` 会把 `DataKey` 写入 cell inventory。
  - `DataMimeticFieldBlockEntity` 使用 `DataFlowKey` 作为输入数据；`hasEnoughDataFlowForWorkCycle()`、`consumeDataFlowPerWorkCycle()`、`refillKeyFromNetwork()` 明确读写 `DataFlowKey`。
  - `DataRipperReassemblerBlockEntity` 明确存在 item/fluid/key 输入输出与 recipe processing，说明其具备“多类型输入 -> 处理 -> 输出”的数据化配方机器形态。
- **仍不能证明的部分**：
  - 不能证明当前已经形成完整的“物质 -> 标准 DataPacket -> 统一路由 -> 重组回滚”系统。
  - 不能证明当前已经有统一的反复制规则框架。

**结论**

> Matter Data Flow 当前可视为**部分实现**：已经有真实机器与 data key / carrier 语义，但还不是完整统一系统。

### 5.2 Pattern Data Flow

**概念**

Pattern Data Flow 指的是：  
将 AE2 pattern、provider 状态、编码结果、上传目标和终端交互视为可被观察、筛选、预览、比较、上传和路由的数据对象。

**潜在机器 / UI**

- `AdaptivePatternProviderLogic`
- `PatternEncodingPreviewScreen`
- `WirelessPatternEncodingTermScreen`
- `UniversalTerminalScreenHook`
- `UniversalTerminalClientHelper`

**潜在未来能力**

- pattern scan；
- pattern preview；
- pattern upload；
- pattern diff；
- pattern routing；
- provider load balancing；
- duplicate pattern detection；
- invalid pattern detection。

**当前实现状态**

- **已有实现证据（局部）**：
  - `AdaptivePatternProviderLogic` 继承 `PatternProviderLogic`，并有扩展的 send list、direction map、watcher、wireless/provider 适配逻辑，说明 pattern provider 行为已被增强。
  - `PatternEncodingPreviewScreen` 与 `WirelessPatternEncodingTermScreen` 都存在“上传面板 / provider 列表 / 搜索 / 选择 / 传输编码样板”等界面语义。
  - `UniversalTerminalScreenHook` 与 `UniversalTerminalClientHelper` 已经实现终端切换、选择器注入、terminal 状态缓存与同步辅助。
- **不能证明的部分**：
  - 不能证明当前已实现完整的 pattern diff、重复样板检测、失效样板诊断、provider 负载均衡策略。
  - 现有 UI 与 compat 逻辑只能证明“pattern data flow 有明显方向与局部能力”，不能证明“完整 Pattern Data Flow 系统已落地”。

**结论**

> Pattern Data Flow 当前可视为**部分实现**，但主要表现为 provider logic 扩展与 UI/terminal 层面的能力线索，而非完整统一系统。

### 5.3 Task Data Flow

**概念**

Task Data Flow 指的是：  
把自动化意图、执行目标、依赖关系、优先级与反馈结果建模成可路由的任务对象。

**潜在未来能力**

- `TaskPacket`
- task queue
- priority
- dependency graph
- retry
- batch planning
- progress report

**当前实现状态**

当前源码中**未发现**明确的 `TaskPacket`、统一任务队列、依赖图、批处理计划器或失败重试框架。

`AdaptivePatternProviderLogic` 与 AE2 crafting 逻辑说明项目已经接触自动化执行层，但**不能据此证明 Task Data Flow 作为独立系统已实现**。

**结论**

> Task Data Flow 当前应视为**设计方向**。

### 5.4 State Data Flow

**概念**

State Data Flow 指的是：  
把机器状态、网络状态、红石状态、库存状态、样板状态、阻塞状态和可观测报告视为数据对象。

**潜在未来能力**

- machine probe
- network sampler
- missing material diagnosis
- blocked output diagnosis
- low energy report
- stuck pattern detection
- state snapshot

**当前实现状态**

- **已有实现线索**：
  - 多个 block entity 有明显的状态字段、`saveAdditional()` / `loadTag()` / `writeToStream()` / `readFromStream()`。
  - `DataDistributionTowerBlockEntity` 提供 `getEnergyDisplayText()`、`getBoundTargetDisplayLines(...)` 等 UI 级状态展示入口。
  - `UniversalTerminalClientHelper` / `UniversalTerminalScreenHook` 说明终端状态在 client 侧已被组织与切换。
- **不能证明的部分**：
  - 未发现统一 machine probe、故障诊断器、阻塞检测器或状态快照系统。

**结论**

> State Data Flow 当前主要是**设计方向 + 局部实现线索**。

### 5.5 Energy Metadata Flow

**概念**

Energy Metadata Flow 指的是：  
把能量状态当作**被报告的数据**，而不是把 Data Flow 当作能量本体。

可包含的未来内容：

- energy report
- AE network pressure report
- FE tower storage report
- estimated task energy cost
- energy bottleneck diagnosis

**重要边界**

这里定义的是“能量元数据的可观测与可报告”，**不是 AE/FE conversion**。

**当前实现状态**

根据 `docs/energy-system-audit.md`：

- AE2 item energy：部分物品已实现；
- AE2 network energy：部分机器已实现；
- FE capability：部分实现，主要证据在 `DataDistributionTowerBlockEntity`；
- AE/FE conversion：未实现；
- all-machine shared energy network：未实现 / 未证明。

因此，当前可以支持“**把已有能量状态当作报告对象**”这一设计方向，但**不能把 Data Flow 宣称为能量共享系统**。

**结论**

> Energy Metadata Flow 当前应视为**设计方向**，其边界必须服从 `docs/energy-system-audit.md`。

### 5.6 Spatial / Teleport Data Flow

**概念**

Spatial / Teleport Data Flow 指的是：  
把数据、任务、状态采样或执行请求按空间锚点、区域范围或远端端点进行路由。

**潜在机器**

- `DataTeleportAnchorBlockEntity`
- `DataDistributionTowerBlockEntity`

**潜在未来能力**

- endpoint routing
- anchor-based transfer
- remote data dispatch
- remote state collection
- no item duplication
- rollback on failed transfer

**当前实现状态**

- `DataTeleportAnchorBlockEntity` 已有目标维度 / 目标坐标 / anchor registry / 传送能耗等真实实现，说明“空间锚点”语义已存在。
- `DataDistributionTowerBlockEntity` 已有 linked positions、cluster、range、endpoint 解析与路由逻辑，说明“远端端点连接 / 传输聚合”语义已存在。
- 但这些实现目前主要服务于**传送 / FE 聚合 / 链接管理**，不能直接证明“Spatial Data Packet 路由系统”已存在。

**结论**

> Spatial / Teleport Data Flow 当前是**部分实现 + 明显设计方向**。

### 5.7 Mimetic / Simulation Data Flow

**概念**

Mimetic / Simulation Data Flow 指的是：  
利用模拟场、拟态环境或虚拟上下文来预测、复制、采样或执行某些数据化结果。

**潜在机器**

- `DataMimeticFieldBlockEntity`

**潜在未来能力**

- simulated machine context
- virtual execution environment
- state prediction
- task feasibility simulation
- data reassembly modifiers

**当前实现状态**

- `DataMimeticFieldBlockEntity` 的真实行为已经包含：
  - 读取 `DataFlowKey` 作为输入；
  - 根据 carrier 类型执行 biology / ore / crop mimetic work；
  - 通过模拟实体与 loot 结果生成物品输出。
- 这说明它并非纯装饰命名，而是确实有“用已记录样本生成结果”的**拟态处理语义**。
- 但仍不能证明项目已经实现完整的“数字孪生 / 全局仿真 / 预测引擎”。

**结论**

> Mimetic / Simulation Data Flow 当前应视为**部分实现**，但能力范围仍是局部机器级。

## 6. Machine Role Map

| Source class / item | 建议中文名 | Proposed Data Flow role | 类比 | Current implementation confidence | Notes |
| --- | --- | --- | --- | --- | --- |
| `DataExtractorBlockEntity` | 数据提取器 | Matter Data Flow 采样器 / 采集器 | 样本采集台 | **已有实现证据** | `performWork()` 会写入 `DataFlowKey`；同时记录 biology/ore/crop carrier 数据。 |
| `DataRipperReassemblerBlockEntity` | 数据裂解重组机 | Matter Data Flow 处理器 / 重组器 | 多输入工艺重组机 | **部分实现** | 有 item/fluid/key 输入输出与 recipe processing，但“统一数据包模型”未见。 |
| `DataTeleportAnchorBlockEntity` | 数据传送锚点 | Spatial / Teleport Data Flow 锚点 | 远程转发锚点 | **部分实现** | 已实现 anchor/target/teleport；但不是已证明的数据包网络。 |
| `DataDistributionTowerBlockEntity` | 数据分发塔 | Spatial routing / Energy metadata endpoint | 区域分发塔 / 远程端点汇聚器 | **部分实现** | 真实强项是 tower FE aggregation/routing，不等于完整 Data Flow 总线。 |
| `DataSolarPanelBlockEntity` | 数据太阳能板 | Energy support machine，可能提供 Data Flow 机器底座 | 供能底座 | **无法证明** | 它是 AE2 powered generator-like block，不应被直接视为 Data Flow 本体。 |
| `DataMimeticFieldBlockEntity` | 数据拟态场 | Mimetic / Simulation Data Flow machine | 样本拟态生成场 | **已有实现证据** | 明确消耗 `DataFlowKey`，并对 recorded carrier 执行 mimetic work。 |
| `AdaptivePatternProviderLogic` | 自适应样板供应逻辑 | Pattern Data Flow orchestration logic | 样板调度器 | **部分实现** | provider 行为、send list、方向映射、兼容扩展真实存在。 |
| `MeSolarPanelPart` | ME 太阳能板部件 | Energy support part | ME 侧供能部件 | **无法证明** | 它是 AE2 energy part，不应被当作 Data Flow 机器本体。 |
| `DataCaptureBallItem` | 数据捕获球 | Matter Data Flow capture item | 样本捕获容器 | **已有实现证据** | 使用 `DataKey` cell 语义，且可 `captureDispersingData(...)`。 |
| `DataFlowPortableCellItem` | 数据流便携存储单元 | Matter/Data storage carrier | 便携数据盒 | **部分实现** | 使用 `DataFlowKeyType` 作为 portable cell key type，但不等于完整 Data Flow 系统。 |
| `MatterConvergingCrossbowItem` | 物质汇聚弩 | Matter-oriented powered tool / weapon | 数据化物质发射器 | **部分实现** | 使用 AE item power 与数据存储；但更接近工具/武器，不应夸大为核心数据流枢纽。 |
| `PatternEncodingPreviewScreen` | 样板编码预览界面 | Pattern Data Flow UI | 样板预览/上传终端 | **不是机器本体，只是 UI / compat** | 有 provider 搜索、预览、上传等强烈线索。 |
| `WirelessPatternEncodingTermScreen` | 无线样板编码终端界面 | Pattern Data Flow UI | 无线样板上传终端 | **不是机器本体，只是 UI / compat** | 真实存在预览、provider 选择、上传与搜索交互。 |
| `UniversalTerminalScreenHook` | 通用终端界面挂钩 | Terminal integration hook | 多终端切换注入层 | **不是机器本体，只是 UI / compat** | 主要负责按钮与 selector panel 注入。 |
| `UniversalTerminalClientHelper` | 通用终端客户端辅助 | Terminal state helper | 终端状态路由器 | **不是机器本体，只是 UI / compat** | 负责 terminal 状态、tooltip、icon、切换发送。 |

## 7. Relationship with AE2

Data Flow 与 AE2 的关系应定义为：

1. **AE2 提供底层基础能力**  
   包括存储、网络、样板、自动合成、channels 与部分能量基础。
2. **DataEnergistics 不应重复造一个 AE2**  
   不应把 Data Flow 写成 AE2 的平行替代品。
3. **Data Flow 应位于 AE2 之上**  
   它更适合被定义为“AE2 上层的数据化自动化语义层”。
4. **AE2 pattern 可以被视为数据对象**  
   这正是 Pattern Data Flow 的合理来源。
5. **AE2 machine state 可以被视为可观测数据**  
   这可以成为 State Data Flow 的一部分。
6. **AE2 energy state 可以成为元数据**  
   但这只是 metadata，不是 Data Flow 本体。
7. **AE2 autocrafting 可被编排，但 Data Flow 不等于 AE2 autocrafting**  
   Data Flow 更强调“表达、观测、路由、分析与诊断”，而不仅是 craft 执行。

## 8. Relationship with Energy Systems

本节以 `docs/energy-system-audit.md` 为**源事实文档**。

当前已知事实：

1. 一些物品存在 **AE2 item energy**。
2. 一些机器存在 **AE2 network energy**。
3. **FE capability** 只在部分位置有明确证据，主要是 `DataDistributionTowerBlockEntity`。
4. **AE/FE conversion 未实现**。
5. **全机器共享能源网络未实现 / 未证明**。

因此：

- Data Flow **不是新的能量类型**；
- Data Flow **不是 AE 的替代品**；
- Data Flow **不是 FE 的替代品**；
- Data Flow **不是 AE/FE bridge**；
- Data Flow **不能被宣传为“能源共享系统”**。

现有 AE2 / FE / energy abstraction 工作，最多只能视为：

> 某些 Data Flow 机器未来可能依赖的运行基础，  
> **但它们本身不是 Data Flow 的定义。**

## 9. Proposed Data Packet Model

以下内容是**设计提案**，不是当前已被源码证明存在的统一实现。

### 可能的未来 packet 类型

- `MatterPacket`
- `PatternPacket`
- `TaskPacket`
- `StatePacket`
- `EnergyReport`
- `SpatialPacket`
- `EntityPacket`

### 可能的共通字段

- `id`
- `type`
- `source`
- `target`
- `payload`
- `size`
- `checksum`
- `priority`
- `ttl`
- `owner`
- `security`
- `estimatedCost`

### 设计说明

这个模型适合作为未来统一抽象，因为它能把：

- matter sample
- pattern snapshot
- machine state
- automation intent
- energy report

统一放进“可路由的数据对象”框架里。

但当前仓库中：

- 已有 `DataKey` / `DataFlowKey` / `DataKeyType` / `DataFlowKeyType`；
- **尚未证明**存在统一 `DataPacket` Java 模型或统一 packet runtime。

## 10. Proposed Gameplay Loop

未来推荐的数据流玩法循环可以是：

1. **捕获或提取数据**  
   例如由 `DataExtractorBlockEntity`、`DataCaptureBallItem` 产生样本或数据键。
2. **编码为数据对象**  
   例如落入 `DataKey` / `DataFlowKey` 或未来的 packet model。
3. **通过 AE2 相关设备路由**  
   可结合 anchor、tower、pattern provider、terminal 或 AE2 inventory。
4. **进行转换、校验或预览**  
   例如 pattern preview、provider selection、样本检查、状态检查。
5. **重组、生成或执行**  
   例如 `DataRipperReassemblerBlockEntity`、`DataMimeticFieldBlockEntity` 一类机器承担执行面。
6. **输出状态与错误报告**  
   包括 pattern 目标、终端状态、能源压力、阻塞状态等。
7. **把诊断结果反馈到 AE2 自动化**  
   作为上层 orchestration 的输入。

需要强调：

> 以上是**未来设计可采用的推荐方向**，  
> **不是当前仓库已经完整实现的系统流程**。

## 11. Current Implementation Gap Analysis

| Area | Current evidence | Status | Missing pieces | Recommended next step |
| --- | --- | --- | --- | --- |
| Unified DataPacket model | 有 `DataKey` / `DataFlowKey`，无统一 packet abstraction | **设计方向** | 统一接口、字段、生命周期、权限模型 | 单独输出 packet model proposal |
| Matter data flow | `DataExtractorBlockEntity`、`DataCaptureBallItem`、`DataMimeticFieldBlockEntity`、`DataRipperReassemblerBlockEntity` 有真实数据相关实现 | **部分实现** | 统一编码模型、完整重组规则、反复制与失败回滚 | 先做 Matter Data Flow MVP 设计 |
| Pattern data flow | `AdaptivePatternProviderLogic` + preview / wireless / universal terminal UI | **部分实现** | pattern diff、重复检测、失效检测、provider 负载策略 | 优先做 Pattern Data Flow MVP |
| Task data flow | 未见统一任务对象 / 队列 / 依赖图 | **未实现** | TaskPacket、priority、dependency graph、retry | 先做设计，不立即实现 |
| State data flow | 有大量状态字段与局部展示入口 | **设计方向** | probe、diagnostic、snapshot、统一状态报告 | 做 State Data Flow MVP |
| Energy metadata flow | 能量事实已存在，但只是 energy audit 支持的现状 | **设计方向** | report schema、诊断视图、压力/瓶颈定义 | 在不碰 conversion 的前提下做 report-only 设计 |
| Spatial / teleport data flow | teleport anchor / tower link / range routing 语义存在 | **部分实现** | 真正的数据包路由与失败回滚模型 | 先文档化 endpoint / anchor 语义 |
| Mimetic / simulation data flow | `DataMimeticFieldBlockEntity` 已真实消耗 `DataFlowKey` 并生成结果 | **部分实现** | 更清晰的模拟范围、可行性预测、约束说明 | 补设计说明与测试矩阵 |
| UI / terminal integration | preview screen / wireless terminal / universal terminal helper/hook 均存在 | **部分实现** | 明确哪些 UI 是 Data Flow UI，哪些只是 terminal QoL | 先整理 UI 角色边界 |
| Tests / validation | 当前主要是 build / datagen / startup 级验证 | **未实现** | 功能级验证矩阵、样板/数据/状态场景测试 | 单独增加 Data Flow validation matrix |

## 12. Minimum PR Roadmap

### PR A: Data Flow design documentation

- 本文档。
- 不改 runtime code。

### PR B: Data packet model proposal

- 若获批准，定义 packet 接口 / record / schema。
- 仍不引入 gameplay behavior。

### PR C: Pattern Data Flow MVP

- 优先做 pattern scan / preview / diff / upload。
- 不碰 energy bridge。
- 不碰 shared machine network。

### PR D: State Data Flow MVP

- 增加 machine state probe。
- 增加 AE network state report。
- 增加基础 diagnostics。
- 不做自动控制闭环。

### PR E: Task Data Flow design

- 先设计 task queue / dependency graph / retry。
- 不在设计未批准前实现。

### PR F: Matter Data Flow MVP

- 明确 matter capture / reassembly 的允许范围。
- 必须定义 anti-duplication 与 rollback 规则。

### PR G: Data Flow validation matrix

- 增加测试或手动验证场景。
- 严格区分 startup verification 与 functional verification。

## 13. Non-goals

本 PR / 本文档的非目标包括：

1. 不实现 Lightning Energy capability。
2. 不实现 `AE2LT ILightningEnergyHandler`。
3. 不实现 AE/FE conversion。
4. 不实现 shared machine energy network。
5. 不修改 recipes。
6. 不修改 GUI 行为。
7. 不修改玩法逻辑。
8. 不宣称完整 Data Flow 已经实现。

## 14. Recommended README Summary

### English

> DataEnergistics adds a data-flow automation layer on top of AE2. It treats matter, patterns, machine states, and automation requests as processable data that can be captured, analyzed, routed, transformed, and executed through AE2-connected devices. Data Flow is not a new energy type and does not imply AE/FE conversion or all-machine shared energy.

### 中文

> DataEnergistics 在 AE2 之上增加了一层数据流自动化语义。它把物质、样板、机器状态与自动化请求视为可处理的数据对象，并尝试通过 AE2 连接的设备来完成捕获、分析、路由、转换与执行。数据流不是新的能量类型，也不意味着 AE/FE 已互通，更不意味着所有机器已经共享同一能源网络。

## Required Conclusion

当前的 DataEnergistics **确实已经有一些机器、物品、界面和能源相关基础**，能够与未来的 Data Flow 概念形成对应关系。

但：

> **完整的 Data Flow 系统尚未实现。**

当前状态最准确的总结应是：

> **有雏形、有机器、有方向，但不是完整数据流系统。**

当前可以安全设计为：

> **AE2 上层的数据化自动化信息层。**

当前**不安全**的说法包括：

1. 完整数据流系统已经实现；
2. 所有机器共享能源；
3. AE/FE 已互通；
4. `DataEnergyStorage` 已经成为统一运行时能源系统；
5. Data Flow 是一种新电力。
