# 多方块 Modular UI 增强与优化

LDLib2 迁移已经完成。本目录只规划现有 Modular UI 的信息架构、交互和业务能力增强，不再规划第二套 UI 框架，也不回退到纹理驱动的 Screen。

## 已确认方向

1. Data Core 根界面、四个可拖动子 UI、Pattern Core 和 XEI composition 全部使用 LDLib2 Modular UI。
2. 目标 UI 不新增、也不引用 DataE 专用 GUI PNG、精灵图、九宫格或状态图集；禁止用 `SpriteTexture#setSprite` 一类纹理裁切坐标决定布局。
3. 视觉结构由 `UIElement`、`Scene`、`ScrollerView`、`TabView`、`Selector`、`Button`、`Toggle`、`Label`、`ItemSlot` 和 LSS/Style 组合完成。
4. LDLib2 自带的通用 `Icons` 和主题基础样式可以继续使用，但不为本次增强绘制专用纹理资产。
5. 多方块结构预览是子 UI 的核心价值，`main`（存储）、`cpu`、`crafting`（合成）和 `auto_build` 都保留独立 Scene。
6. 主界面只承担系统总览和子 UI 入口，不再把完整结构、库存、任务、样板、材料、参数和动作全部堆在根界面。
7. 子 UI 内采用渐进披露：Scene 始终可见，观察参数使用 Dock；存储内容、CPU 任务、合成样板和当前合成详情使用各自的业务分页。
8. 存储页逐项显示通用 `AEKey` 与其存储量；CPU 页显示合成任务并可经服务端校验跳转 AE 原生 CPU 状态界面；合成页固定拆成“样板槽位”和“合成详情”两页。
9. JEI/EMI 继续共享同一个 Modular UI composition，recipe role 与通用样板写入契约保持同构。

## 文档分类

| 文档 | 内容 |
| --- | --- |
| [01-主界面与子UI布局.md](01-主界面与子UI布局.md) | Data Core 根界面、四个子 UI、Pattern Core 的分区线框 |
| [02-结构预览与XEI布局.md](02-结构预览与XEI布局.md) | Scene、参数 Dock、候选、材料以及 JEI/EMI 四区布局 |
| [03-动作与状态反馈.md](03-动作与状态反馈.md) | launcher、pending、故障、自动搭建、退款和多人反馈 |
| [04-ModularUI样式与验收.md](04-ModularUI样式与验收.md) | LSS 类、尺寸、交互状态、可访问性、优先级与验收 |
| [05-存储CPU与合成增强.md](05-存储CPU与合成增强.md) | 存储 AEKey 清单、CPU 任务与 AE 跳转、合成双页及同步契约 |

## 不堆叠原则

每个区域只回答一个问题：

| 区域 | 只负责回答 |
| --- | --- |
| Data Core 根界面 | 系统是否正常、哪个子结构需要处理 |
| 子 UI Scene | 结构长什么样、当前观察哪一层/哪个方块 |
| 参数 Dock | 当前正在编辑哪个结构参数 |
| 材料视图 | 当前选择需要什么材料 |
| 存储工作区 | 每个 `AEKey` 当前存了多少 |
| CPU 任务工作区 | 哪个 CPU 正在合成什么、进度如何、如何进入 AE 详情 |
| 合成样板页 | 结构内每个真实样板槽当前安装了什么、如何统一访问 |
| 合成详情页 | 当前执行的普通合成内容、路由、输入输出和队列状态 |
| 状态视图 | 结构是否形成、容量与故障是什么 |
| 方块检查视图 | 选中方块的位置、角色与候选是什么 |
| 动作区 | 当前允许执行什么、是否 pending、结果如何 |
| XEI 配方带 | 普通 input 到 owner output 的配方关系 |

禁止做法：

- 在 Scene 上永久叠放结构、层级、variant、tier、repeat、candidate、材料和动作的全部控件。
- 在 `84px` 侧栏同时常驻所有状态行、库存、CPU 任务、样板网格、参数、材料摘要、确认按钮和错误详情。
- 为每一行信息再套一张独立卡片或边框。
- 依赖 hover 才能知道窗口是否打开、操作是否 pending 或请求是否失败。
- 用专用纹理位置决定业务元素坐标。

## 优先级

| 优先级 | 范围 |
| --- | --- |
| P0 | 通用 XEI 注册、动态 tier domain、自动搭建单目标语义、action result、退款文案与确认 |
| P1 | 存储清单、CPU 任务与 AE 跳转、合成双页、子 UI 分区、launcher 打开/置顶、Scene 工具栏、上下文 Dock |
| P2 | 小视口布局、键盘可访问性、投影缓存、Scene 降频与性能指标 |

## 保留的技术边界

- client/server hosted tree 的注册顺序保持同构。
- open/close/reopen 继续经过 root coordinator。
- 关闭继续 `removeChild` 并 exactly-once 释放 Scene；重开创建新实例。
- 每个子 UI 和 XEI composition 继续独占 session、Scene、renderer 与 dummy world。
- layer、相机和方块选择只属于 view state，不改变材料或样板。
- 合成样板聚合视图中的每个槽必须保留真实 `(layoutRevision, hostId, coreId, slotIndex, slotRevision)` 所有权，不能压平成丢失路由身份的虚拟库存。
- 样板写入保持通用并承载普通配方；结构、layer、相机、候选和自动搭建草稿不写入样板，只有实际发起搭建时才从当前选择生成搭建状态。
- “在 AE 中查看”必须由服务端验证 host、grid、CPU 身份和权限后打开 AE 原生菜单，不能只在客户端替换 Screen。
- 只有规范材料槽是 XEI `INPUT`，只有 owner 槽是 `OUTPUT`。
- XEI 的样板写入入口保持通用，不在多方块 category 内另造专用编码协议。
