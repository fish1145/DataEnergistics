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

Data Energistics 当前通过 MDLib `BlockPattern` 表达可重复结构。MDLib 当前工作树已经提供尺寸、最小偏移、`getPredicate`、`getActualRelativeOffset`，以及 predicate 的 `blockStateCandidates`/`placementCandidates`；`StructurePatternKey` 也能表达 owner 与具名结构。但是这些能力位于已有未提交改动中，后续不能假定已发布。

| 可能需要的 API | 所属库 | 原因 |
| --- | --- | --- |
| 获取重复段合法范围的不可变视图 | MDLib | 属于 pattern 定义本身 |
| 按 repeat selection 展开为相对坐标/predicate 投影 | MDLib | 避免消费者复制 aisle 展开算法 |
| 取得 predicate 的候选 BlockState/placement ItemStack | MDLib | XEI、指南和诊断均可复用，且必须保持一一对应 |
| Trinity 子结构集合、tier 替换、材料选择策略 | Data Energistics | 是具体模组业务 |
| `TrackedDummyWorld` 填充与 LDLib2 `Scene` | Data Energistics client | MDLib 不应依赖 LDLib2 UI |
| 普通多方块 recipe 到 AE 菜单的 transfer | Data Energistics AE2 integration | MDLib 不得依赖 AE2 或 XEI |

### 已确认的 MDLib 缺口

1. repeat 元数据仍以 `aisleRepetitions`、`unitStarts`、`unitDepths` 等 public 可变数组暴露，没有不可变 selection 和范围校验。
2. 没有把 source slice 按所选 repeat 展开成 cell/layer 的公共 projector；消费者手工遍历会复制 matcher 算法。
3. `getMinZ()` 依据最大 repeat 边界。控制器前方存在可变段时，非最大展开可能产生 anchor 偏移，预览不能直接套用。
4. 没有 cell 的 unit/repeat/source slice 来源、按层查询或候选到 placement ItemStack 的一一映射。
5. 没有保留替代集合的材料快照；tag/fluid tag 候选还需要 registry/tag reload 感知。
6. 没有 pattern catalog、原子 reload snapshot、单调 revision 和 cache invalidation 契约。

### 未来 MDLib PR 边界

| API 组 | 目标 |
| --- | --- |
| `PatternLayout` / `PatternUnit` / `RepeatRange` | 只读暴露结构布局，并兼容弃用现有 public 数组 |
| `PatternRepeatSelection` | 按 unit 保存次数并 Fail Fast 校验 min/max |
| `PatternProjector` | 生成不可变 expanded pattern/layer/cell snapshot，统一 anchor、方向和分层 |
| `PatternCandidateSet` | 保持 state 与 placement stack 的对应关系，区分 exact/any/air/controller/unresolved |
| `PatternMaterialSnapshot` | 按候选集合聚合数量，不在库层擅自选择替代材料 |
| `StructurePatternCatalog` | reload 成功后原子替换 snapshot，并提供单调 revision |

MDLib PR 不包含子结构业务目录、tier 替换、LDLib2 Scene、JEI/EMI/REI 或 AE2 transfer。测试直接覆盖多个 variable unit、min/max 混合选择、控制器前后重复段、非默认 `StructureDir`/flip、逐层坐标、tag reload 和带 components 的 ItemStack；禁止反射和源码 contain 测试。

未来 MDLib PR 必须只包含经审计确认缺失的中立 API 和直接逻辑测试。当前 MDLib 工作区已有未提交变更，因此不能在未确认所有权的情况下建立提交。
