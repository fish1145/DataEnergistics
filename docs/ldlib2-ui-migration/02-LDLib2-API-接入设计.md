# LDLib2 API 接入设计

## 已核对 API

| 能力 | LDLib2 API | 本项目用途 |
| --- | --- | --- |
| 方块 UI 入口 | `BlockUIMenuType.BlockUI`、`BlockUIHolder`、`BlockUIMenuType.openUI` | 方块交互后创建 LDLib2 容器菜单 |
| UI 根 | `ModularUI`、`UI.of`、`UIElement` | 构建独立的核心和舱室界面树 |
| 布局与样式 | `UIElement.layout`、`UIElement.style`、LSS | 取代绝对坐标 `GuiGraphics` 绘制 |
| 库存 | `ItemSlot`、`FluidSlot`、`InventorySlots` | 绑定现有 `Slot` 与玩家背包 |
| 复合控件 | `Button`、`Label`、`ProgressBar`、`TabView`、`ScrollerView` | 状态卡、分页、标签、列表和建造配置 |
| 3D 预览 | `Scene`、`TrackedDummyWorld` | 可拖拽、缩放、选中与分层的结构预览 |
| XEI 桥接 | `ModularUIRecipeCategory`、`ModularUIEMIRecipe`、`IngredientIO` | JEI/EMI 共用一棵 ModularUI 元素树 |
| 同步 | `DataBindingBuilder.*S2C`、`BindableValue` | 服务端结构状态、容量、故障提示、页码 |
| 服务端交互 | `Button#setOnServerClick`、UI RPC | 翻页、退款、自动建造确认、模式切换 |

ECO 已验证的实践是：以 `UIElement` 作为面板边界，以 `DataBindingBuilder` 将服务端 Supplier 绑定到 `Label`、`ProgressBar` 和可见性元素，并以 `setOnServerClick` 执行会改动服务端状态的按钮操作。

## 推荐模块结构

在 `client.ldlibui` 下建立 UI 构造代码；服务端动作和读取模型放在同级的非 client 包，避免专用客户端类泄漏到方块实体。

| 建议组件 | 职责 |
| --- | --- |
| `MultiblockUiFactory` | 选择对应核心的 `ModularUI` 根树 |
| `MultiblockStatusPanel` | 共用结构状态、失败提示与 tooltip |
| `AutoBuildDialog` | 将 `MultiBlockAutoBuildOverlayDescription` 映射为 LDLib2 控件 |
| `CompartmentUiFactory` | 按 `CompartmentType` 创建页面 |
| `CompartmentSlotPanel` | 将已创建的菜单槽映射成 `ItemSlot`/`FluidSlot` |
| `CompartmentUiActions` | 明确的翻页、退款、自动建造、模式切换服务端动作 |
| `HostSubUiExtension` | 主机 UI 挂载可拖动、可开关子面板的稳定接口 |
| `DraggableSubUi` | 用 `WindowDragHelper` 管理窗口位置和显示状态，不持有业务权威状态 |

上述名称是迁移期建议；落地时应遵循项目既有包和命名风格，并为公共接口补齐动机与成员注释。

## 容器策略

阶段一使用 LDLib2 的方块容器机制建立新入口，并让 holder 定位实际 `BlockEntity`。阶段二将现有菜单中稳定的槽位构造逻辑提取为可复用的槽位装配器，由 LDLib2 容器菜单调用。只有在每一类槽位通过交互回归后，才删除对应的 AE2 Screen/Menu 注册。

不得以反射读取 AE2 私有菜单字段；需要的宿主、库存和状态必须由现有公开接口、显式访问器或新增受控接口提供。

## 依赖接入工作项

1. 在项目仓库声明 FirstDark snapshots 仓库和 LDLib2 NeoForge all artifact，版本固定到与 Minecraft 1.21.1 兼容的已验证版本。
2. 执行前读取系统环境变量 `GRADLE_USER_HOME`；只在其已设置且指向允许位置时解析依赖。不得自行创建、重定向或通过 `-g` 覆盖 Gradle 缓存目录。
3. 添加 `ILDLibPlugin` 实现，集中注册项目 LSS、纹理和可选 XEI 集成。
4. 先做一个开发环境方块 UI 冒烟入口，确认 NeoForge、AE2、LDLib2 的菜单和网络初始化顺序。

依赖版本不在本计划中硬编码：应在实施分支锁定后，以 LDLib2 的发布元数据、NeoForge 版本及现有 AE2 依赖解析结果共同确认。

## 通用预览分层

`Scene` 和 XEI 适配器只消费不可变预览快照。结构展开、候选解析和材料聚合应由 common 层接口完成；虚拟世界填充、相机、hover 与 ingredient 标注留在 client 层。不得把 `TrackedDummyWorld`、JEI 或 EMI 类型放入结构定义接口。

## 主机子 UI 接口

主机 UI 根元素通过扩展接口接收子面板 provider，并把 provider 创建的新元素挂到独立 overlay layer。子面板必须支持显式 open/close、标题栏拖动、点击提升 z-order、Esc 优先关闭、viewport 约束、XEI extra area 更新和关闭时释放 `Scene` 资源。自动建造使用普通绝对定位元素配合 `WindowDragHelper` 保持非模态；`Dialog.windowMode` 只用于真正需要模态交互的窗口。每个 host UI 实例独立保存位置；拖动位置、显示层、相机和 hover 不写入结构选择、普通 XEI 配方或服务端数据。
