# 结构预览与 XEI 布局

## 1. 共用 StructurePreviewPanel

hosted window 与 XEI 继续复用同一个结构预览组件。差异只在外层 composition：hosted window 提供窗口专属 business workbench，XEI 提供 formal recipe rail。

```text
StructurePreviewPanel
├── StructurePreviewSceneElement
│   └── internal Scene
├── ItemSlot selectedBlock        // IngredientIO.NONE
├── UIElement sceneToolbar
│   ├── Button fit
│   ├── Button resetCamera
│   └── PreviewLayerSelector
└── UIElement SelectionDock
    ├── Selector<ControlTarget> context
    ├── Button previous
    ├── Button/Selector currentValue
    └── Button next
```

hosted window 将 `SelectionDock` 放进底部 `contextDock/parameterView`，以便与材料和检查摘要切换；XEI 没有这层业务 TabView，直接把同一个 `SelectionDock` 挂在 Scene 下方。两种 composition 复用同一控件和 selection state，不是两套实现。

### Scene 必须保留

- `main`、`cpu`、`crafting` 和 `auto_build` 的每个子 UI 都保留独立 Scene。
- Scene 是最大连续区域，不被材料、状态或动作面板覆盖。
- 每个 Scene 继续独占 dummy world、renderer、相机和释放周期。
- 切换 layer 不重建 world；切换 recipe-affecting 参数时默认保留相机。
- 自动适配只在首次加载、bounds 不兼容或用户点击 fit 时发生。

## 2. Selection Dock

variant、tier、repeat 和 candidate 不再同时横排。Dock 一次只显示一个当前编辑目标。

```text
196 x 28
┌──────────────────────────────────────────┐
│ [目标 v] [<] [当前值 / 直选 v] [>]       │
└──────────────────────────────────────────┘
```

### ControlTarget 顺序

1. Shape/variant。
2. 每个 tier domain。
3. 每个 variable repeat unit。
4. 每个拥有多个候选的 predicate。

### 收起规则

- `variantCount == 1`：不显示 Shape。
- tier domain 为 0 个：不显示 tier。
- tier 只有一个 option：显示只读摘要或不加入 Dock。
- fixed repeat：不加入 Dock。
- predicate 没有替代候选：不加入 Dock。
- 只有一个 ControlTarget：左侧 selector 降级为只读 Label。
- 没有可编辑目标：Dock 保持稳定高度，显示选中方块的角色/位置摘要。

### candidate 规则

- 点击 Scene 中有替代项的方块时，Dock 自动切换到对应 predicate。
- 当前值可以显示物品图标、短名称和 `n/N`。
- 左右按钮循环；点击当前值打开 Selector 直选。
- candidate 图标不是 `ItemSlot`，不得成为 XEI formal ingredient。
- Dock 使用稳定元素池，不在每次候选切换后销毁重建整条 scroller。

## 3. Scene overlay

Scene overlay 只保留与观察直接相关的控件：

```text
┌──────────────────────────────────────────┐
│ [选中方块][适应][复位]      [全部 < 3/8 >]│
│                                          │
│                  Scene                   │
│                                          │
└──────────────────────────────────────────┘
```

- layer 只有一层时整组隐藏。
- 多层时使用 ALL Button、previous Button、Selector、next Button。
- “Layer”标题放入 tooltip/narration，不额外占一行。
- Scene 空、definition stale 或 bind/refresh 失败时，用固定 UIElement overlay 显示空态/错误态。
- 错误 overlay 可以包含重试 Button，但不能吞掉原始异常日志。

## 4. JEI/EMI 固定四区布局

保持现有 `196x232` category 尺寸，同时把候选合并进 Selection Dock，让 Scene 从 `128px` 增加到 `152px`。

```text
196 x 232
┌────────────────────────────────────┐ y=0
│ 子结构 rail                    20  │
├────────────── 2px gap ─────────────┤
│                                    │
│ Scene + overlay                152 │
│                                    │
├────────────── 2px gap ─────────────┤
│ Selection Dock                 28  │
├────────────── 2px gap ─────────────┤
│ formal INPUT  →  owner OUTPUT  26  │
└────────────────────────────────────┘ y=232
```

精确高度：`20 + 2 + 152 + 2 + 28 + 2 + 26 = 232`。

### Modular UI 元素树

```text
UIElement CompositionRoot
├── UIElement structureRail
│   ├── 1 structure: Label
│   ├── 2-3 structures: equal-width Button group
│   └── 4+ structures: Selector<String>
├── UIElement previewHost
│   └── StructurePreviewPanel
│       ├── Scene
│       ├── sceneToolbar
│       ├── PreviewLayerSelector
│       └── SelectionDock
└── UIElement recipeRail
    ├── PreviewMaterialStrip inputs
    ├── UIElement directionIcon
    └── ItemSlot ownerOutput
```

### 建议 bounds

| 元素 | Bounds |
| --- | --- |
| structure rail | `0,0,196,20` |
| preview host | `0,22,196,182` |
| Scene | `0,0,196,152`，相对 preview host |
| Selection Dock | `0,154,196,28`，相对 preview host |
| recipe rail | `0,206,196,26` |
| Dock context | `2,2,70,24` |
| Dock previous | `74,2,18,24` |
| Dock current value | `94,2,80,24` |
| Dock next | `176,2,18,24` |
| input scroller | `2,0,154,26` |
| direction icon | `160,7,12,12` |
| owner output | `176,4,18,18` |

## 5. 子结构 rail

- 1 个 structure：静态 Label，不做伪按钮。
- 2-3 个 structure：等宽 Button group，并显示 active 状态。
- 4 个及以上：单一 Selector 或 overflow menu，不再增加顶部横向滚动条。
- rail 高度始终为 `20px`，切换结构不能推动 Scene。
- 每个结构保留自己的 variant/tier/repeat/candidate selection。

## 6. recipe rail

- 输入材料使用 `PreviewMaterialStrip` 的稳定槽池。
- 图标与数量文本分离，大数量不能覆盖物品图标。
- owner output 始终为唯一 `18x18 ItemSlot`。
- 材料数量缩减时隐藏多余槽，不销毁 formal slot identity。
- material scroller 无溢出时不显示滚动条。
- input/output 分区通过 LSS 背景、间距和方向图标表达，不需要专用纹理。

## 7. recipe role 不变量

| 元素 | IngredientIO |
| --- | --- |
| PreviewMaterialStrip 正规材料槽 | `INPUT` |
| owner output | `OUTPUT` |
| Scene、selected block、layer、structure rail | `NONE` |
| Selection Dock、candidate Selector、方向图标 | `NONE` |

structure/variant/tier/repeat/candidate 改变 recipe；layer/camera/selected block 只改变 view state。

## 8. 通用化前置

在继续视觉优化前先消除两个硬编码：

1. JEI/EMI 从 preview catalog/controller descriptor 枚举 recipe，不只注册 Trinity。
2. tier control 动态支持 0、1、多个 domain，不再假设 `getFirst()` 永远存在且唯一。

category 可以共享，但标题、图标、catalyst、owner output 和稳定 recipe id 必须来自当前 controller spec。

## 9. XEI 与样板写入边界

- 当当前多方块选择需要写入样板时，由 controller 把有效选择投影为普通 recipe descriptor；不发明 DataE 专用 AE recipe 类型。
- JEI/EMI 只把正式 input/output 和普通配方身份交给通用样板写入入口，不在 category 内增加只能处理 Trinity 的编码按钮。
- 写入内容不包含 structure tab、layer、相机、选中方块、variant/tier/repeat/candidate 的 UI 状态，也不包含自动搭建开关。
- 自动搭建只在用户实际确认搭建时读取当时的结构选择并生成 submission；不能因为写入或查看样板而自动搭建或写回 UI 草稿。
- JEI 与 EMI 必须消费同一 descriptor 和 transfer result，不能一端写普通配方、另一端仍写旧的专用状态。
