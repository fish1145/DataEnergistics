# Modular UI 样式与验收

## 1. 资源约束

本轮不新增：

- GUI 背景 PNG。
- 按钮精灵图。
- 九宫格纹理。
- 状态图集。
- JEI/EMI 专用贴图。

所有视觉由 Modular UI 元素、LSS/Style、颜色、描边、间距、opacity、文字和 LDLib2 通用图标构成。Data Core 根界面、四个 hosted 子 UI、Pattern Core 和 XEI 均属于此约束；不存在“迁移期继续承载旧 GUI 背景”的例外。

允许复用 LDLib2 通用 `Icons` 与主题基础样式，不允许引用 DataE 专用 GUI 纹理、通过 sprite 坐标裁切 UI 状态，或为某个窗口单独制作 idle/task overlay。

## 2. LSS 组件类

建议建立一套多方块共享样式类，由 host、auto_build、JEI 和 EMI 复用。

| LSS class | 作用 |
| --- | --- |
| `.de-mb-root` | 多方块 Modular UI 根节点 |
| `.de-mb-header` | 标题栏与拖动区域 |
| `.de-mb-launcher-rail` | 根界面四入口 |
| `.de-mb-scene` | Scene bounds、overflow 和焦点边界 |
| `.de-mb-scene-toolbar` | Scene 上方图标命令 |
| `.de-mb-layer-control` | compact layer selector |
| `.de-mb-context-dock` | 参数/候选编辑 Dock |
| `.de-mb-material-view` | 材料 ScrollerView |
| `.de-mb-business-tabs` | 存储/CPU/合成/自动搭建各自的业务 TabView |
| `.de-mb-inspection-summary` | Scene Dock 内的选中方块/故障摘要 |
| `.de-mb-status-list` | 状态 ScrollerView |
| `.de-mb-storage-list` | `AEKey -> amount` 虚拟列表 |
| `.de-mb-storage-row` | 稳定绑定一个 AEKey 与数量的行 |
| `.de-mb-cpu-task-list` | CPU 合成任务 ScrollerView |
| `.de-mb-cpu-task-row` | CPU number、目标、进度与选择状态 |
| `.de-mb-pattern-grid` | 聚合样板页的稳定 8x8 槽池 |
| `.de-mb-crafting-detail` | 当前普通合成详情页 |
| `.de-mb-action-bar` | 底部主动作 |
| `.de-mb-recipe-rail` | XEI input -> output 区域 |
| `.de-mb-popup` | root-mounted transient Selector popup |
| `.de-mb-busy-overlay` | pending/loading 遮罩 |
| `.de-mb-empty-overlay` | 空 Scene 状态 |
| `.de-mb-error-overlay` | 可见错误状态 |

状态类：

- `.is-active`
- `.is-front`
- `.is-disabled`
- `.is-pending`
- `.is-success`
- `.is-warning`
- `.is-error`
- `.is-compact`

业务状态通过切换 class 或绑定 Style 状态表达，不进入坐标计算。

## 3. 尺寸 token

| Token | 值 | 用途 |
| --- | ---: | --- |
| `space-xs` | 2 | 紧凑控件内部 gap |
| `space-sm` | 4 | 区域间 gap |
| `space-md` | 6 | 外层 padding |
| `control-sm` | 18 | 图标按钮、槽和最小命中区 |
| `control-md` | 24 | Selector、Dock 主控件 |
| `header-height` | 18-20 | 标题与关闭按钮 |
| `dock-height` | 28 | Selection Dock |
| `slot-size` | 18 | ItemSlot |
| `workbench-width` | 144 | 正常视口业务工作区 |
| `workbench-compact` | 112 | 存储/CPU compact 业务工作区下限 |
| `pattern-workbench` | 144 | 合成样板 workbench 的所有视口下限 |

固定格式元素必须使用稳定 bounds；hover、pending、长文本和数值变化不能推动相邻元素。

## 4. 视觉层级

从强到弱：

1. 主动作与错误。
2. Scene 和当前选中对象。
3. active 参数/Tab/窗口。
4. 状态与材料。
5. 次要说明。

布局使用完整区域和分隔 gap，不把每个 Label 再包成独立卡片。

### 推荐表现

- root 保持透明或使用统一 Modular UI 背景色。
- Scene 使用最安静的背景，保持结构方块为视觉主角。
- rail/Dock/side panel 使用同一中性层级，只通过间距和 1px 描边分区。
- active 使用稳定 accent 描边或底线。
- hover 只提亮，不改变尺寸。
- pressed 可以产生 1px 内移感，但不移动整体布局。
- disabled 降低对比并保持文字可读。
- pending 使用 opacity/spinner/Label 组合，不使用整窗闪烁。

若 LSS 支持 SDF 背景和描边，可使用 SDF；否则使用 LDLib2 现有通用主题样式。两种方式都不新增 DataE 图像资源。

## 5. 图标与文字

- 命令图标只使用 LDLib2 已有通用 `Icons`；业务内容使用通用 `AEKey` renderer 或物品本身的 `ItemStackTexture`，不能把内容图标当作 UI 背景纹理。
- 关闭、上一项、下一项、适应、复位、层级、设置等命令优先图标加 tooltip。
- 自动搭建、退还队列等有明显副作用的命令使用图标加短文本。
- 标题 `8-8.5px`，紧凑标签 `7-7.5px`；不随 viewport 缩放字号。
- letter spacing 保持 `0`。
- 最长中英文不越界；使用固定 bounds、静态截断、tooltip，必要时再使用延迟 `HOVER_ROLL`。

## 6. 可访问性

- normal、active、pending、warning、error 同时使用图标、文字和颜色。
- 图标的视觉尺寸可以小于命中区，但命中区不得小于 `18x18`。
- 所有 Button、Selector、Toggle 提供 tooltip 和 narration。
- Tab/方向键焦点顺序与视觉顺序一致。
- Enter/Space 激活动作。
- Esc 保持 popup -> 最上层 hosted window -> Screen 的顺序。
- focus 必须有非 hover 的可见描边。
- GUI scale 2/3/4 下不依赖亚像素线条表达状态。

## 7. Selector 与 popup

- structure、ControlTarget、current value、page selector 复用同一 popup 规则。
- popup 挂到 root，添加 `HostUiExtension.TRANSIENT_POPUP_CLASS`。
- z-index 高于 hosted window。
- `maxItemCount` 有上限，超出使用垂直 ScrollerView。
- 选中后关闭；Esc 先关闭 popup。
- owning element `onRemoved` 时必须 hide popup。
- popup 不注册额外 recipe role。

## 8. 性能与生命周期

保留：

- 每个窗口独立 Scene/dummy world。
- layer-only 更新保留相机且不重建 world。
- 关闭时 exactly-once 释放 renderer。
- XEI refresh 在客户端线程合并。

优化方向：

- 同一 tick 内合并连续 stepper 变化。
- 存储条目、CPU 任务和 active craft 使用 revisioned delta；数量或进度变化不能触发整页元素树重建。
- 聚合样板分页复用固定 64 槽 wrapper，服务端确认 backing route 后再接受点击。
- 以 `projectionFingerprint` 缓存不可变 snapshot，不缓存 UIElement 或 Scene。
- snapshot 改变时按位置 diff 更新 dummy world。
- 非活动 Scene 降低刷新频率，但不能预挂载隐藏或共享 renderer。
- 记录 projection、material、world rebuild 和 render refresh 耗时。
- cleanup 失败保留首异常和 suppressed failure，不吞异常。

## 9. 实施优先级

| 阶段 | 内容 |
| --- | --- |
| UI-O0 | catalog 驱动 XEI、动态 tier domain、自动搭建单目标语义、准确退款文案 |
| UI-O1 | 根界面总览、launcher 打开/置顶、子 UI Scene + Dock + 专属 workbench |
| UI-O2 | 存储 AEKey 清单、CPU 任务与 AE 跳转、合成“样板槽位/合成详情”双页 |
| UI-O3 | pending/action result、故障定位、自动搭建与退款确认闭环 |
| UI-O4 | XEI 四区 composition、稳定 Selection Dock、candidate 直选 |
| UI-O5 | compact/横向布局、键盘和 narration |
| UI-O6 | snapshot diff、刷新合并、非活动 Scene 降频与指标 |

## 10. 验收矩阵

| 分类 | 验收内容 |
| --- | --- |
| 资源 | 目标 UI 不引用 DataE GUI PNG、sprite atlas、九宫格或状态图集；不存在旧背景例外 |
| 元素树 | host/JEI/EMI 均使用约定的 Modular UI 组件和共享 Style class |
| 布局 | Scene 始终是子 UI 最大连续区域；参数、材料、状态、检查和动作不同时堆叠 |
| 窗口 | 打开、置顶、拖动、compact、Esc、popup、关闭、重开 |
| Scene | 旋转、缩放、fit/reset、layer、选中方块、空态、错误态 |
| 参数 | 0/1/多 tier、variant/repeat/candidate 边界和直选 |
| 存储 | 通用 AEKey 类型、`BigInteger` 精确数量、搜索/排序、delta、stale 与空态 |
| CPU | 0/1/多任务、`hostId + number + taskRevision`、number 复用、进度、adapter/hook 打开并预选正确 AE CPU |
| 合成 | 顶层严格两页；跨 core 的 64 槽分页保留 layout/host/core/slot/revision；普通合成输入、预计输出、pending output 和诊断缺失态 |
| XEI | JEI/EMI 同构、formal role、动态刷新、view state 保留、transfer 错误 |
| 自动搭建 | 单目标、摘要、前置条件、确认、pending、结果与草稿保留 |
| Pattern Core | 常规/横向布局、页切换 overlay、退款确认和多人 stale 状态 |
| 可访问性 | GUI scale 2/3/4、中文/英文、focus、tooltip、narration、颜色非唯一 |
| 生命周期 | 四 Scene 并开、资源重载、关闭、断线和 exactly-once release |
