# 参考实现与 API 边界

## GregTech Modern

| 文件 | 可借鉴点 | 不可直接照搬点 |
| --- | --- | --- |
| `PatternPreviewWidget` | `Scene` 预览、形状分页、逐层过滤、材料和候选展示 | 预览只面向 GT definition，未实现具名子结构 selector |
| `MultiblockInfoCategory` | JEI 的 `ModularUIRecipeCategory` 薄适配 | 不能让类别类承担结构计算 |
| `MultiblockInfoEmiRecipe` | EMI 的 `ModularUIEMIRecipe` 薄适配 | 当前 GT 分支的 EMI 多方块注册存在未启用代码，不能当作完整生产验证 |
| `MultiblockMachineDefinition` | 具名 structure 和匹配 shape 入口 | GT 的 shape DFS 可能产生组合爆炸，不适合直接预注册每个 Trinity 组合 |

## NeoECOAEExtension

| 文件 | 可借鉴点 | 不可直接照搬点 |
| --- | --- | --- |
| `MultiBlockInfoWrapper` | JEI/EMI 共用 UI、`TrackedDummyWorld`、expand、显示层、formed、材料 ingredient | 同时持有模型、UI 状态和虚拟世界，通用实现需拆层；没有子结构模型 |
| `MultiBlockContext.DummyDelegated` | 将结构投影到虚拟世界并聚合材料 | 是 ECO 私有 definition 的投影器 |
| `MultiBlockPlacementPlan` | all/missing/conflict/required/reused 的不可变计划边界 | 世界坐标建造计划不应直接用作 XEI 预览快照 |
| `MultiblockBuilderUI` | Supplier/回调配置、S2C 状态和 server click | 是方块内服务端 UI，不可直接放入纯客户端 XEI 页面 |
| `PatternEncodingTermMenuMixin` | 展示既有 AE 菜单动作边界 | ECO upload 与本需求不同；本需求走普通 recipe transfer |

## LDLib2

已确认的公共能力包括 `Scene`、`TrackedDummyWorld`、`ModularUIRecipeCategory`、`ModularUIEMIRecipe`、`IngredientIO`、`ItemSlot`、`ScrollerView`、`DataBindingBuilder` 与服务端点击。首轮方案不需要修改 LDLib2。

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

### PR 与剩余边界

MDLib PR 按功能拆为：空 enum extension 修复、数据目录延迟解析、可重复结构投影 API、谓词候选 API、投影与候选测试，以及恢复 Data Energistics vendored jar 已依赖的 `BlockPattern.getMinX/getMinY/getMinZ`。已使用系统 `GRADLE_USER_HOME=E:\.gradle` 通过 `spotlessCheck test runGameTestServer build`，GameTest 为 1/1，并生成 binary/source jar。

`MDLib.MDLIB_FOLDER` 已改为 `getMdlibFolder()`，这是需要在 PR 中显著说明的源码与二进制兼容性变化。Data Energistics 更新依赖后必须迁移任何字段访问。

MDLib PR 不包含子结构业务目录、tier 替换、材料选择/聚合策略、definition catalog/revision、LDLib2 `Scene`、JEI/EMI/REI 或 AE2 transfer；这些均留在 Data Energistics。除非 PR 审查发现中立 API 缺陷，不再向 MDLib 扩大范围。修复后的 jar/source jar 已同步到 Data Energistics，并通过 IDEA 编译与 `spotlessCheck test`。
