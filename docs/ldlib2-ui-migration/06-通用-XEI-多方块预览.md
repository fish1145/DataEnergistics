# 通用 XEI 多方块预览

## 目标架构

XEI 页面、主机 UI 中的可拖动子面板、方块内自动建造面板和未来指南页消费同一个纯预览模型，但各自创建独立 UI 状态和 `TrackedDummyWorld`。JEI/EMI 适配器只负责注册类别、尺寸、recipe id、catalyst 与 LDLib2 容器桥接。

| 层 | 建议职责 | 禁止依赖 |
| --- | --- | --- |
| common 定义层 | 控制器、子结构、参数域、结构投影、材料聚合 | Minecraft 客户端、LDLib2 UI、JEI/EMI |
| common 快照层 | 不可变坐标、谓词/候选、材料、bounds、逻辑层和显示切片索引 | 虚拟世界、屏幕实例 |
| client 展示层 | 将快照填入 `TrackedDummyWorld`，创建 `Scene` 和材料槽 | 世界写入、玩家库存修改 |
| XEI 适配层 | `ModularUIRecipeCategory`、`ModularUIEMIRecipe`、ingredient 标注 | 结构展开和材料业务计算 |
| server 动作层 | 自动建造与 AE 样板写入 | 客户端快照作为权威数据 |

## 建议数据契约

| 模型 | 必要字段 |
| --- | --- |
| `MultiblockPreviewSpec` | controller id、标题、图标、稳定排序的子结构集合、definition revision |
| `SubstructurePreviewSpec` | 稳定 `structureKey`、标题、variant 域、tier 域、repeat 域、默认选择、投影入口 |
| `PreviewSelection` | controller id、当前 `structureKey`、每个子结构各自的 `variantIndex`/tier/repeat/candidate selections、definition revision |
| `StructurePreviewSnapshot` | selection、相对坐标和 BlockState、位置候选、材料汇总、bounds、可见层列表、revision |
| `PreviewViewState` | 相机、选中方块、`visibleLayer`（含明确 `ALL`）、formed 模式；不得参与材料计算或样板序列化 |
| `MultiblockRecipeView` | 当前快照的规范材料 input、控制器/owner output、稳定 `registeredRecipeId` 与动态 `projectionFingerprint`，供 JEI/EMI 和 AE transfer 共用 |

具体类名可以随项目风格调整，但这六种职责不能重新揉成一个 XEI wrapper。

## 控件行为

| 控件 | 行为 |
| --- | --- |
| 子结构 selector | 在 main/cpu/crafting 等成员之间切换；保留各成员自己的 tier/repeat 选择 |
| variant selector | 切换当前具名子结构内部的 shape/variant；不与 repeat 或显示层共用索引 |
| tier selector | 只修改当前子结构的替换部件档位，并重建快照 |
| repeat stepper | 按定义的 min/max 修改重复段次数，并重建快照 |
| 显示层控制 | 支持全部、上一层、下一层、指定层；只过滤渲染坐标 |
| formed toggle | 可选显示 formed 模型，仅影响渲染态，不改变材料和样板 |
| 材料 scroller | 只展示 `MultiblockRecipeView.inputs()` 的规范化材料及数量，并标记为 XEI input |
| 方块选中 | 显示该位置允许的候选、谓词提示和所属逻辑层；检查控件本身不注册 recipe role |
| 样板按钮 | 对当前 `MultiblockRecipeView` 发起标准 JEI/EMI transfer；最终编码仍由 AE 菜单完成 |

主机 UI 嵌入时，以上控件放入可拖动、可关闭的非模态子窗口。子窗口 provider 只能接收纯模型和明确动作接口，不得引用具体主机 Screen。host provider 与 XEI adapter 分别委托同一个无宿主状态的纯 UI factory；不得复用 provider、元素实例或运行时资源。关闭必须从 host overlay `removeChild` 并释放 `Scene`/dummy world，重开由 provider 创建全新实例。

## 子结构与层级规则

Trinity 的三个结构定义要以稳定 `structureKey` 注册到同一个 controller preview spec。`variantIndex` 只表示一个具名子结构内部的 shape/variant，repeat 仍是独立业务参数；GT 的 `P` shape page、`L` visible layer 和具名子结构不能合并为一个 page index。切换子结构时，页面会话保留其他子结构的 variant/tier/repeat/candidate selections，便于来回比较；这些选择标识绝不写入 AE 样板。`visibleLayer` 以快照坐标的结构轴为准，不能默认世界 Y；只有 UI 文案可显示为第 N 层。

variant、等级和重复层数必须由定义提供合法域。XEI 客户端可以缓存快照，但缓存键必须包含 definition revision、`structureKey`、`variantIndex`、tier、repeat 与会改变材料的 candidate selections；资源重载后全部失效。`visibleLayer`、相机、hover 和 formed 不进入投影缓存键。

## JEI 与 EMI 注册

JEI 参考 GT/ECO 继承 `ModularUIRecipeCategory`，EMI 继承 `ModularUIEMIRecipe`。两者使用同一个纯 UI factory，不复制按钮逻辑。每个 controller 只注册一个稳定 `registeredRecipeId`；子结构和参数是页面内部选择，不为每个组合预注册 recipe，避免组合爆炸。

当前选择改变后，session 计算 `projectionFingerprint = definitionRevision + structureKey + variantIndex + tier/repeat + candidateSelections` 并刷新规范 recipe ingredient；`registeredRecipeId` 不改变。该对象是普通 XEI 配方视图，不注册 AE 专属 RecipeType，也不携带编码后的样板 ItemStack。JEI setup 会固化 ingredient，EMI 也会缓存 inputs/outputs，因此展示缓存不能作为 transfer 权威来源；typed handler 必须在点击瞬间读取 session 的 `currentRecipeView()` 并校验 revision/fingerprint。

## Recipe Role 隔离

1. 只有 `MultiblockRecipeView.inputs()` 对应的规范材料汇总槽调用 XEI ingredient/slot API 并标记 `INPUT`。
2. 只有 controller/owner 规范槽标记 `OUTPUT`。JEI 的 invisible output 与 EMI 的 outputs 必须表达同一个 owner，不能出现 ECO 式只有 JEI 补 output 的分歧。
3. `Scene`、方块 hover、候选检查器、谓词详情、层级/formed 控件和辅助展示槽全部为 `NONE`，不得调用 `xeiRecipeSlot()` 或 `xeiRecipeIngredient()`。
4. 点击候选可以更新 recipe selection 并重建规范汇总槽；候选控件本身始终不参与 transfer。GT 把材料汇总和候选检查槽都标成 input 的做法禁止照搬。
5. 材料数量来自 typed recipe view 的 amount。ECO 将 `RequiredItem.count()` 传给 `xeiRecipeSlot(io, float chance)` 的做法会把数量误作 chance，不能作为数量编码方式。

REI 暂不作为首轮完成条件，但 common preview 与 UI factory 不得引用 JEI/EMI 专有类型，以便后续薄适配。

## 验收表

| 场景 | 预期 |
| --- | --- |
| JEI/EMI 打开同一控制器 | 默认选择、模型、材料与尺寸一致 |
| 切换子结构 | 场景、标题、候选和材料同步变化，其他子结构参数被保留 |
| 修改 variant/tier/repeat/candidate | 快照、fingerprint 与规范材料重建且范围受定义限制；registered recipe id 不变 |
| 切换显示层 | 只改变可见坐标，材料和样板选择不变 |
| 检查方块候选 | 辅助槽保持 recipe role NONE，transfer 输入只来自规范汇总槽 |
| 资源重载 | 旧缓存释放，新 revision 生成新快照 |
| 关闭页面 | Scene 渲染资源和 dummy world 引用被释放 |
