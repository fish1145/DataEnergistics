# 参考实现与 API 边界

## GregTech Modern

| 文件 | 可借鉴点 | 不可直接照搬点 |
| --- | --- | --- |
| `PatternPreviewWidget` | `Scene` 预览、shape variant 分页、逐层过滤、材料和候选展示 | `P` 是 shape variant、`L` 才是 visible layer，不是具名子结构；材料汇总槽和候选检查槽都标成 input，会污染 transfer；静态 dummy world/cache 与共享 fake controller 不适合四个并行 Scene |
| `MultiblockInfoCategory` | JEI 的 `ModularUIRecipeCategory` 薄适配 | 不能让类别类承担结构计算 |
| `MultiblockInfoEmiRecipe` | EMI 的 `ModularUIEMIRecipe` 薄适配和 controller output 思路 | 当前 GT 分支的 EMI 多方块注册存在未启用代码，不能当作完整生产验证 |
| `MultiblockMachineDefinition` | 具名 structure 和匹配 shape 入口 | XEI `getMatchingShapes()` 只展开默认结构；shape DFS 可能组合爆炸，不适合预注册每个 Trinity 组合 |
| `AutoBuildRequest` / `MultiblockAutoBuild` | structure name/options 的独立服务端请求、分阶段规划、材料 reservation、验证后提交 | 请求不能序列化进 AE 样板；客户端快照不能成为权威，方向与精确 `BlockState` 只在真实放置阶段应用 |
| `MEAutoBuildSource` | simulate/reserve/commit 的材料事务顺序 | 不照搬其玩家无线终端上下文发现；DataE 使用当前菜单、host 与明确材料入口 |
| `MultiblockInWorldPreviewRenderer` | 世界内分层投影的交互参考 | 只取第一个 shape 且使用全局静态状态，不适合具名子结构、XEI session 或四窗口 |

GT 没有“从多方块页面写入 AE Processing 样板”的专用实现。其 Pattern Buffer 只消费已有 encoded pattern，JEI 多方块页没有完整 controller output，EMI 注册又未启用。因此 DataE 的普通 recipe projection、role 隔离与 typed transfer 是本项目新增契约，不能写成 GT 已验证的 AE writer。

## NeoECOAEExtension

| 文件 | 可借鉴点 | 不可直接照搬点 |
| --- | --- | --- |
| `MultiBlockInfoWrapper` | JEI/EMI 共用 UI factory、`TrackedDummyWorld`、expand、显示层、formed、材料 ingredient | 同时持有模型、UI 状态和虚拟世界，通用实现需拆层；没有具名子结构/variant 模型，层级固定使用 Y；`RequiredItem.count()` 被传给 chance 参数而不是材料数量 |
| `MultiBlockContext.DummyDelegated` | 将结构投影到虚拟世界并聚合材料 | 是 ECO 私有 definition 的投影器 |
| `MultiBlockPlacementPlan` | all/missing/conflict/required/reused 的不可变计划边界 | 世界坐标建造计划不应直接用作 XEI 预览快照 |
| `MultiblockBuilderUI` | Config/Supplier/回调工厂、S2C 状态和 server click | 窗口在 host 构造时预挂载并以 `display: none` 隐藏，close 只隐藏；不 remove/release/recreate，也没有通用四窗 z-order/Esc 生命周期 |
| `MultiBlockInfoCategory` / `MultiblockEmiRecipe` | JEI 普通 wrapper 的材料 input 与 invisible owner output、EMI 薄适配 | owner output 只在 JEI category 补充，EMI wrapper 未补等价 output；两端 recipe role 不完整一致 |
| `PatternEncodingTermMenuMixin` | 展示既有 AE 菜单动作边界 | `EncodingMode.PROCESSING` 时明确拒绝 upload；它上传已编码 crafting pattern，不负责普通多方块 recipe transfer |

ECO 的浮窗策略通过预先注册隐藏树保持 sync/RPC ID 稳定，适合解释其 S2C/server-click 为什么能工作，但与 DataE “关闭 removeChild、释放资源、重开新实例”的要求直接冲突。DataE 只复用 factory/config/binding 模式，动态 membership 必须走始终挂载的 coordinator。

## LDLib2

Data Energistics 当前实际解析并构建于 LDLib2 `2.2.28`，`F:/mc/ldlib/LDLib2` 工作区源码也是 `2.2.28`；ECO 使用的 `2.2.1` 仅作旧版实现参考。已按 `2.2.28` source jar 核对 `Scene`、`TrackedDummyWorld`、`ModularUIRecipeCategory`、`ModularUIEMIRecipe`、`IngredientIO`、`ItemSlot`、`ScrollerView`、`DataBindingBuilder` 与服务端点击等公共能力，所有生命周期、sync/RPC 和 XEI 结论都以该版本为权威。正常生命周期下，本轮业务 UI 不需要修改 LDLib2。

`MenuTypeBuilder` 会在服务端和客户端分别调用同一菜单 factory，`AbstractContainerMenuMixin#setModularUI` 把整树注册到 `UISyncManager`；初始数据随菜单打开包写入并在客户端菜单创建后读取。sync value 与 RPC 使用按注册顺序增长、移除不复用的 ID，因此动态树必须在两端按同一 sequence open/close/reopen。

`Button#setOnServerClick` 最终注册元素 RPC；`UIEventDispatcher` 在目标元素的本地 listener 之后才发送 server event。关闭按钮若同时本地 `requestClose()` 并注册 server click，本地移除会先清掉元素的 MUI/RPC，导致服务端事件无法发送。正确边界是在静态 root coordinator 上 request/ack，服务端先改权威树，客户端收到 ack 后再改镜像树；Esc 和外部 close 使用相同入口。

已知上游边界：LDLib2 `2.2.28` 的 `UIElement.removeChild` 在 `REMOVED` 或 `MUI_CHANGED` listener 抛错时，会在清除 `parent`、后代 `ModularUI` 和结构缓存前退出；这些字段没有公开恢复入口。Data Energistics 的 `HostSubUiRoot` 已实现 post-order sibling continuation、异常聚合、外部 `removeSelf()` terminal 通知和资源 exactly-once 防护，但不能修复已经残留的 LDLib2 私有结构状态。异常后 host 必须转为 terminal 并禁止复用残留树。完整上游修复需要让 `UIElement.onRemoved`、`_setModularUIInternal`、`removeChild`、lifecycle event dispatcher、`Scene` 与 `ModularUI` 在异常下继续清理并最后重抛首异常；未取得针对 LDLib2 仓库的明确 Git 授权前只记录并测试该边界，不在本任务分支伪造反射或重复释放补偿。

JEI 公共 API 没有对已经建立的 formal slots 做原位 invalidation 的入口。Data Energistics 先合并并延迟刷新请求，再调用公开 `showRecipes` 重建页面/formal slots，并接受导航历史增加；runtime stop 时释放 category `uiCache`，下一次 start 可重新注册。不得通过反射、访问 JEI 私有实现或依赖内部缓存规避这一边界。EMI 可以通过 live `getInputs()`/`getOutputs()` 暴露新材料，并在槽池扩容后延迟 `focusRecipe`，但两端展示状态都不能替代点击瞬间的 typed `currentRecipeView()`。

## Modular Data Lib 边界

Data Energistics 当前通过 MDLib `BlockPattern` 表达可重复结构。所需中立 API 已在隔离 worktree `F:/mc/Fish_Dan_/Modular-Data-lib-preview-api` 的 `qy/multiblock-preview-api` 实现、推送并创建 Draft PR [#2](https://github.com/ModularMCLib/Modular-Data-lib/pull/2)。原始 `F:/mc/Fish_Dan_/Modular-Data-lib` 含用户改动，禁止用于本任务写入。

| 可能需要的 API | 所属库 | 原因 |
| --- | --- | --- |
| 获取重复段合法范围的不可变视图 | MDLib | 属于 pattern 定义本身 |
| 按 repeat selection 展开为相对坐标/predicate 投影 | MDLib | 避免消费者复制 aisle 展开算法 |
| 取得 predicate 的候选 BlockState/placement ItemStack | MDLib | XEI、指南和诊断均可复用，且必须保持一一对应 |
| Trinity 子结构集合、tier 替换、材料选择策略 | Data Energistics | 是具体模组业务 |
| `TrackedDummyWorld` 填充与 LDLib2 `Scene` | Data Energistics client | MDLib 不应依赖 LDLib2 UI |
| 普通多方块 recipe 到 AE 菜单的 transfer | Data Energistics AE2 integration | MDLib 不得依赖 AE2 或 XEI |

### 已实现的 MDLib API

1. `PatternLayout`、`PatternUnit`、`RepeatRange` 与 `PatternRepeatSelection` 提供不可变 repeat 元数据和 min/max Fail Fast 校验；legacy public 数组保留为 deprecated detached snapshot，外部修改不再改变 matcher。
2. `PatternProjector` 按选择展开为 `ExpandedPatternSnapshot`、`PatternLayerSnapshot`、`PatternCellSnapshot`、source metadata 与 `PatternBounds`，支持多个 variable unit、controller 前后 repeat、非默认 `StructureDir` 和 flip。
3. controller 在所有合法 repeat 选择下保持 `BlockPos.ZERO` 锚定；controller 所在 unit 必须固定 repeat=1；结构只能包含一个 `~`。
4. 定义最大 1,000,000 cells，repeat 与坐标算术使用 exact 方法；matcher 按实际访问 cell 计数，超限返回 `match_budget`，避免首层重试绕过预算。
5. `SimplePredicate`、`TraceabilityPredicate` 与 block/state/tag/fluid/concatenated/restricted predicates 暴露精确 `BlockState` 和 component-aware placement `ItemStack` 候选；返回值防御性复制，null/empty supplier Fail Fast。
6. 公开 layer/cell/snapshot 构造器验证 unit、repeat、inner/source layer、扁平 cells 与精确 bounds 的完整一致性，禁止外部构造矛盾快照。
7. `PatternCandidate` 和 `patternCandidates()` 提供 exact preview state 与 component-aware placement stack 的稳定一一配对；内置 predicates 直接产生 pair，legacy 分离列表只在默认 item 映射、一对多或多对一可证明时转换，歧义 many-to-many Fail Fast。MDLib 不决定 Data Energistics 的舱室优先级，具体 wrapper 必须显式声明候选顺序。

### PR 与剩余边界

MDLib PR 按功能拆为：空 enum extension 修复、数据目录延迟解析、可重复结构投影 API、谓词候选 API、投影与候选测试、恢复 Data Energistics vendored jar 已依赖的 `BlockPattern.getMinX/getMinY/getMinZ`，以及 `cbbbbd7` 成对候选 API。已使用系统 `GRADLE_USER_HOME=E:\.gradle` 通过 `spotlessCheck test runGameTestServer build`，GameTest 为 4/4，并生成已同步到 Data Energistics 的 binary/source jar。

`MDLib.MDLIB_FOLDER` 已改为 `getMdlibFolder()`，这是需要在 PR 中显著说明的源码与二进制兼容性变化。Data Energistics 更新依赖后必须迁移任何字段访问。

MDLib PR 不包含子结构业务目录、tier 替换、材料选择/聚合策略、definition catalog/revision、LDLib2 `Scene`、JEI/EMI/REI 或 AE2 transfer；这些均留在 Data Energistics。除非 PR 审查发现中立 API 缺陷，不再向 MDLib 扩大范围。修复后的 jar/source jar 已同步到 Data Energistics，并通过 IDEA 编译与 `spotlessCheck test`。
