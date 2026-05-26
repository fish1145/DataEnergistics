# DataEnergistics 设计书

## 1. 项目定位

DataEnergistics 是一个 **AE2 附属 / 扩展型模组**。

它不是独立能源模组，也不应被设计成 AE2 的平行替代系统。AE2 继续提供：

- 存储基础
- 网络与频道基础
- 自动合成基础
- 部分机器与物品的能量基础

DataEnergistics 的合理定位，是在 AE2 之上提供**更高层的数据化自动化玩法扩展**。

建议采用的项目定位表述：

> **DataEnergistics 是 AE2 的数据流自动化扩展：它将物品、样板、机器状态和自动化意图抽象为可处理的数据流，并通过 AE2 相关设备进行采集、路由、转换、重组与执行。**

这里的“转换”应理解为**数据表达与流程转换**，而不是 AE/FE/Lightning 能量转换。

## 2. 当前实现状态

基于当前仓库真实源码与既有审计结论，可以确认：

1. **JDK 21 / Gradle 8.14.5 基线已稳定**。
2. 早期的 `runServer` 启动问题已经修复。
3. `clean build` 已通过。
4. `runData` 已通过。
5. `runClient` / `runServer` 已做过**启动级验证**。
6. AE2LT Lightning capability 已明确 **deferred**。
7. AE2WTLib runtime optional 已完成**启动级验证**。
8. License audit 结论保持 **MIT**，且未发现 ExtendedAE Plus 源码复制 / 派生证据。
9. Energy audit 已确认：当前**不能**宣称所有机器之间能够相互连接共享能源。
10. Data Flow 设计已经建立概念边界：**Data Flow 是 AE2 上层的数据化自动化信息层，不是能源。**
11. **完整 Data Flow 系统尚未实现**。

因此，当前仓库的整体状态应描述为：

> **有基础、有边界、有机器、有方向，但还没有形成完整闭环系统。**

## 3. 非目标与边界

当前项目边界必须明确如下：

1. **不做 Lightning capability。**
2. **不做 AE2LT `ILightningEnergyHandler`。**
3. **不做 AE / FE / Lightning conversion。**
4. **不做“所有机器共享能源”的宣称。**
5. **不把 Data Flow 当能源。**
6. **不把 `DataEnergyStorage` 当成已完成的统一运行时能源系统。**
7. **不把启动验证当成功能验证。**
8. **不把 optional runtime 验证当成 UI 交互验证。**
9. **不把设计方向当成已实现功能。**

这些边界来自已有源码事实和下列证据文档：

- `docs/energy-system-audit.md`
- `docs/compatibility-test-matrix.md`
- `docs/ae2lt-lightning-capability-design.md`
- `docs/data-flow-system-design.md`

## 4. Data Flow 核心概念

Data Flow 的建议定义是：

> **Data Flow 是 AE2 上层的数据化自动化信息层。**

它不是电力，不是 FE，不是 AE，不是 Lightning Energy，也不是 AE/FE bridge，更不是全机器共享能源网络。

最简洁的定义应为：

> **数据流不是电力，而是自动化的信息层。**

更完整的定义应为：

> **Data Flow 是 DataEnergistics 将物质、样板、机器状态和自动化意图表达为可处理数据对象，并通过 AE2 相关机器与工具进行采集、分析、路由、转换、模拟和执行的系统层。**

这里的“系统层”是**设计目标**，不是当前已完整实现的现实。

当前状态应保持如下表述：

> **有雏形、有机器、有方向，但不是完整数据流系统。**

## 5. 机器角色设计

| 对象 | 建议中文名 | 角色 | 理论类比 | 当前状态 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `DataExtractorBlockEntity` | 数据提取器 | 物质/状态数据采集入口 | 传感器、ETL extractor、数据采集器 | 已有实现证据 | 真实源码中会记录样本，并向 AE2 inventory 写入 `DataFlowKey`。 |
| `DataRipperReassemblerBlockEntity` | 数据撕裂重组器 | 拆解、解析、重组数据 | 反编译器 + 编译器、逆向工程工具、材料重构设备 | 部分实现 | 已有 item/fluid/key 输入输出与 recipe 处理路径，但不能证明完整统一 packet 系统。 |
| `DataTeleportAnchorBlockEntity` | 数据传送锚 | 数据路由端点 / 远程定位点 | endpoint、路由地址、传送锚点 | 部分实现 | 已有 anchor / target / teleport 语义，但不是已被证明的数据包路由系统。 |
| `DataDistributionTowerBlockEntity` | 数据分发塔 | 数据/状态/局部能源能力的分发与聚合节点 | 基站、交换机、边缘网关、配电塔 | 部分实现 | 真实强项是 tower 范围内 FE 聚合与连接管理，不能外推为全机器共享能源。 |
| `DataSolarPanelBlockEntity` | 数据太阳能板 | AE2 网络相关供能设备 / 能源输入概念 | 发电面板、供能底座 | 部分实现 | 它是 AE2 供能相关机器，不是 Data Flow 本体。 |
| `DataMimeticFieldBlockEntity` | 数据拟态场 | 拟态/模拟/场效应方向的数据处理环境 | 数字孪生、仿真场、模拟器 | 已有实现证据 | 真实源码中会消耗 `DataFlowKey` 并对 recorded carrier 执行 mimetic work。 |
| `AdaptivePatternProviderLogic` | 自适应样板供应逻辑 | Pattern Data Flow / 样板任务调度方向 | MES、生产调度器、任务编排器 | 部分实现 | 真实存在 provider 行为扩展、send list、direction map 等逻辑。 |
| `MeSolarPanelPart` | ME 太阳能面板部件 | AE2 网络部件侧能源输入 | 网络部件式发电单元 | 部分实现 | 是 AE2 网络部件，不应被当成 Data Flow 本体。 |
| `DataCaptureBallItem` | 数据捕获球 | 可携带数据捕获工具 | 采样胶囊、数据封装容器 | 已有实现证据 | 使用 `DataKey` cell 语义，且能捕获 `DispersingDataEntity`。 |
| `DataFlowPortableCellItem` | 数据流便携元件 | 便携式数据/存储载体 | 移动硬盘、U 盘、离线数据包 | 部分实现 | 使用 `DataFlowKeyType` 作为 portable cell key type。 |
| `MatterConvergingCrossbowItem` | 物质汇聚弩 | 工具/武器方向的数据或物质作用设备 | 定向作用工具、特殊载荷发射器 | 部分实现 | 有数据/能量语义，但不应夸大为核心数据流机器。 |
| `PatternEncodingPreviewScreen` | 样板编码预览界面 | 样板数据流 UI | 样板预览/上传终端 | 不是机器本体，只是 UI / compat | 真实存在 provider 搜索、预览、上传交互。 |
| `WirelessPatternEncodingTermScreen` | 无线样板编码终端界面 | 无线样板数据 UI | 无线样板上传终端 | 不是机器本体，只是 UI / compat | UI 行为存在，手动交互仍未完整验证。 |
| `UniversalTerminalScreenHook` | 通用终端兼容钩子 | AE2WTLib / Universal Terminal UI 兼容层 | 界面注入层 | 不是机器本体，只是 UI / compat | 负责按钮和 selector 注入。 |
| `UniversalTerminalClientHelper` | 通用终端客户端助手 | 终端状态与切换辅助层 | 状态助手、客户端路由辅助层 | 不是机器本体，只是 UI / compat | 负责终端状态、tooltip、icon、切换 payload。 |

## 6. 数据流分类设计

### 6.1 Matter Data Flow / 物质数据流

物质数据流负责：

- 捕获物质数据；
- 提取样本；
- 拆解对象；
- 重组对象；
- 校验完整性；
- 防复制。

当前状态：

- 已有相关机器和物品雏形，例如 `DataExtractorBlockEntity`、`DataCaptureBallItem`、`DataRipperReassemblerBlockEntity`、`DataMimeticFieldBlockEntity`；
- 已存在 `DataKey` / `DataFlowKey` 两类数据键语义；
- 但**完整统一的 MatterPacket / 回滚 / 反复制框架未实现，也未被证明**。

### 6.2 Pattern Data Flow / 样板数据流

样板数据流负责：

- 样板扫描；
- 样板预览；
- 样板上传；
- 样板差异比较；
- 样板路由；
- 重复样板检测；
- 无效样板检测；
- Provider 负载均衡。

当前状态：

- 有 `AdaptivePatternProviderLogic` 与 pattern terminal / wireless terminal / universal terminal 的真实实现线索；
- 完整 Pattern Data Flow 尚未实现；
- **推荐作为下一步 MVP 方向。**

### 6.3 Task Data Flow / 任务数据流

任务数据流负责：

- 任务包；
- 任务队列；
- 优先级；
- 依赖图；
- 批处理；
- 重试；
- 进度报告。

当前状态：

- 目前仍是**设计方向**；
- 不能声称已实现统一任务系统。

### 6.4 State Data Flow / 状态数据流

状态数据流负责：

- 机器状态采样；
- AE 网络状态采样；
- 缺料诊断；
- 输出堵塞诊断；
- 能源不足报告；
- 样板卡住检测；
- 状态快照。

当前状态：

- 目前主要是**设计方向**；
- 已有局部状态字段、同步与 UI 展示线索；
- 推荐作为下一步 MVP 候选。

### 6.5 Energy Metadata Flow / 能源元数据流

能源元数据流负责：

- 报告能源状态；
- 估算任务耗能；
- 发现能源瓶颈；
- 观察 AE 网络压力；
- 观察 FE tower 储能。

必须强调：

1. 它**不是能源本体**；
2. 它**不做 AE/FE 转换**；
3. 它**不证明共享能源网络**；
4. 它必须遵守 `docs/energy-system-audit.md` 的边界。

### 6.6 Spatial / Teleport Data Flow / 空间与传送数据流

空间与传送数据流负责：

- 端点路由；
- 锚点传输；
- 远程数据派发；
- 远程状态收集；
- 防复制；
- 失败回滚。

当前状态：

- 有 `DataTeleportAnchorBlockEntity` 与 `DataDistributionTowerBlockEntity` 两类真实方向；
- 但完整的空间数据包路由系统**未被证明**。

### 6.7 Mimetic / Simulation Data Flow / 拟态与模拟数据流

拟态与模拟数据流负责：

- 虚拟执行环境；
- 状态预测；
- 任务可行性模拟；
- 数据重组修饰；
- 机器环境拟态。

当前状态：

- 有 `DataMimeticFieldBlockEntity` 真实机器雏形；
- 但完整数字孪生 / 模拟系统**未被证明**。

## 7. 能源系统边界

根据 `docs/energy-system-audit.md`：

- **AE2 item energy**：已实现一部分。
- **AE2 network energy**：部分机器接入。
- **FE capability**：部分实现，明确证据主要覆盖 `DataDistributionTowerBlockEntity`。
- **AE -> FE conversion**：未实现。
- **FE -> AE conversion**：未实现。
- **DataEnergyStorage runtime integration**：未实现。
- **All-machine shared energy network**：无法证明 / 未实现。
- **Existing tests**：没有能源专项功能测试。

明确结论：

> **不能对外宣称“所有机器之间能够相互连接共享能源”。**

Data Flow 不应被包装成能源共享卖点。

## 8. 兼容性边界

当前兼容性结论应维持如下：

1. **AE2LT Lightning capability deferred**。
2. **AE2WTLib runtime optional** 已完成启动级验证。
3. **AE2WTLib UI interaction** 仍需手动验证。
4. **strict without `ae2wtlib_api`** 尚未真正验证，因为其他 mod 仍可能通过 jar-in-jar 带入 API。
5. optional dependency 验证是**启动级验证**，不是完整功能验证。

对应证据文档：

- `docs/compatibility-test-matrix.md`
- `docs/ae2lt-lightning-capability-design.md`

## 9. 许可证边界

当前许可证边界如下：

1. 项目保持 **MIT**。
2. 未发现 ExtendedAE Plus 源码复制 / 派生证据。
3. 当前不需要 LGPL。
4. 当前不需要双许可证。
5. `NOTICE.md` 保留第三方声明。
6. 如果未来引入第三方派生代码，必须重新审计，并按文件粒度重新处理许可证。

对应证据文档：

- `NOTICE.md`
- `docs/license-audit.md`

## 10. 未来路线图

### PR A：文档收尾与设计书

- 当前 PR。
- 清理 Markdown 入口。
- 新增 `docs/design-book.md`。
- 不改运行代码。

### PR B：Pattern Data Flow MVP 设计

- 定义样板数据流最小功能。
- 只做设计或极小范围实现。
- 不碰能源共享。

### PR C：Pattern Data Flow MVP 实现

- 样板扫描；
- 样板预览；
- 样板差异比较；
- 样板无效检测；
- 补测试或手动验证。

### PR D：State Data Flow MVP 设计

- 机器状态采样；
- AE 网络状态采样；
- 诊断报告模型；
- 不直接控制机器。

### PR E：State Data Flow MVP 实现

- 只读状态；
- 缺料 / 堵塞 / 能源不足报告；
- UI 或日志诊断；
- 不自动修改玩家网络。

### PR F：Matter Data Flow 设计

- 明确物质捕获和重组边界；
- 明确防复制规则；
- 明确失败回滚规则；
- 明确存档安全边界。

### PR G：Data Packet 模型

- 仅在设计批准后定义 `DataPacket` / `MatterPacket` / `PatternPacket` 等；
- 先接口或 record；
- 不直接改玩法。

### PR H：AE2WTLib UI 手动交互验证

- 打开相关无线样板终端；
- 验证 screen replacement；
- 更新验证矩阵。

### PR I：能源模型决策

- 仅在项目作者明确要求能源共享时进行；
- 先决定是否需要 FE、AE、AE/FE bridge、shared energy network；
- 不默认实现。

## 11. 最终采纳建议

最终建议如下：

1. 项目当前应以 **Data Flow 自动化信息层** 作为主线。
2. 下一步优先 **Pattern Data Flow MVP** 或 **State Data Flow MVP**。
3. **不建议**下一步做能源共享。
4. **不建议**下一步做 AE/FE bridge。
5. **不建议**下一步做 Lightning capability。
6. 保持 **MIT**。
7. 保持 `NOTICE.md`。
8. 保留审计文档作为边界证据。
9. 根 `README.md` 保持入口定位，详细设计放在 `docs/design-book.md`。
10. 后续每个 PR 都应明确它属于哪一种工作类型：
    - 设计
    - 实现
    - 验证
    - 兼容
    - 许可证
    - 能源

最终应对外保持如下叙述：

> DataEnergistics 目前已经有部分机器、物品、界面和设计边界，能够支持未来的数据流自动化方向；  
> 但完整系统尚未实现，且不应把 Data Flow、能源系统和兼容性验证混为一谈。***
