# LDLib2 API 接入设计

## 已核对 API

| 能力 | LDLib2 API | 本项目用途 |
| --- | --- | --- |
| 既有菜单挂载 | `IModularUIHolderMenu`、`AbstractContainerMenuMixin`、`ClientEventListener` | 给现有 AE2 菜单附着 `ModularUI`，不替换菜单类型 |
| UI 根 | `ModularUI`、`UI.of`、`UIElement` | 构建独立的核心和舱室界面树 |
| 布局与样式 | `UIElement.layout`、`UIElement.style`、LSS | 取代绝对坐标 `GuiGraphics` 绘制 |
| 既有库存槽 | `ItemSlot.bind`、`IModularUIHolderMenu.ldlib2$addSlot` | 包装 AE2 已创建的 `Slot` 并登记 existing-slot 映射 |
| 复合控件 | `Button`、`Label`、`ProgressBar`、`TabView`、`ScrollerView` | 状态卡、分页、标签、列表和建造配置 |
| 3D 预览 | `Scene`、`TrackedDummyWorld` | 可拖拽、缩放、选中与分层的结构预览 |
| XEI 桥接 | `ModularUIRecipeCategory`、`ModularUIEMIRecipe`、`IngredientIO` | JEI/EMI 共用 UI factory/元素树定义，各自创建运行时实例 |
| 同步 | `DataBindingBuilder.*S2C`、`BindableValue` | 服务端结构状态、容量、故障提示、页码 |
| 服务端交互 | `Button#setOnServerClick`、UI RPC | 静态树按钮动作；动态窗口生命周期统一经始终挂载的 root coordinator |

ECO 已验证的实践是：以 `UIElement` 作为面板边界，以 `DataBindingBuilder` 将服务端 Supplier 绑定到 `Label`、`ProgressBar` 和可见性元素，并以 `setOnServerClick` 执行会改动服务端状态的按钮操作。

## 已落地模块结构

同步/RPC-bearing 的 host 树、provider 接口、`StructurePreviewSceneElement` common shell 和服务端动作放在非 client 包，使菜单 factory 能在 client/server 构造相同注册拓扑；实际 `Scene`、`TrackedDummyWorld` 与渲染生命周期由 `client.gui.ldlib2.multiblock.StructurePreviewSceneBinderImpl` 挂到无 id 的 internal child。纯客户端视觉节点不得改变双方 sync value/RPC 的数量与注册顺序，也不得把专用客户端类泄漏到方块实体。

| 已落地组件 | 职责 |
| --- | --- |
| `AeMenuBridge` / `AeMenuBridgeImpl`、`AeItemSlot` | 将既有 AE2 菜单槽登记到 LDLib2，保持 fake-slot、wrapped stack、tooltip 与菜单索引协议 |
| `HostUiExtension` / `HostUiExtensionImpl` | 注册、打开、关闭、置顶与约束多个 hosted window，并保存每个 host 实例的客户端窗口状态 |
| `HostUiCoordinator` / `HostUiCoordinatorImpl` | 以单调 sequence 协调两端动态树 membership，拒绝过期、重复和无效 provider 请求 |
| `HostSubUiProvider`、`HostSubUiRoot`、`HostWindowPlacement` | 每次打开创建新树，聚合清理资源，并把窗口位置约束到真实 screen bounds；仅对首次默认位置做碰撞级联 |
| `StructurePreviewUiFactory` / `StructurePreviewUiFactoryImpl` | 从纯预览 spec/selection 创建新的 `StructurePreviewSession`、panel 与 `Scene` 绑定；host 与 XEI 不共享实例 |
| `TrinityDataCoreStructureProvider`、`TrinityDataCoreAutoBuildProvider` | 实现 `main`、`cpu`、`crafting` 与 `auto_build` 四个真实 provider |
| `TrinityHostedWindowChrome`、`TrinityDataCoreHostLauncherPanel` | 统一四窗几何、拖动标题栏、关闭按钮与始终可达的四按钮 launcher rail |
| `CompartmentHostUi`、`CompartmentSlotPanel`、四类 compartment panel | 按 `CompartmentType` 挂载独立容器根树，并按 `SlotSemantic` 包装既有菜单槽 |
| `MultiblockXeiUiFactory`、`TrinityMultiblockJeiCategory`、`TrinityMultiblockEmiRecipe` | 让 JEI/EMI 消费同一份普通多方块配方视图和 UI composition，同时各自持有运行时 UI/Scene |

公共逻辑继续面向接口；实现类只在各自 factory/入口内部使用。上述名称均来自当前生产代码，不再保留迁移期的占位命名。

## 容器策略

保留全部现有 `MenuTypeBuilder`、`MenuOpener`、`AEBaseMenu`、`SlotSemantic` 与菜单注册。在每个目标菜单完成槽位、client action 和初始状态创建后，由显式 bridge 将 `ModularUI` 附着到菜单。`MenuTypeBuilder` 会在服务端打开菜单和客户端解包 locator 时分别调用同一个 factory，因此必须在两端菜单构造期间建立同构 host root，不能只在客户端构造，也不能等 Screen `init` 后再挂载。

既有 `Slot` 由对应的 LDLib2 `AeItemSlot` 包装，但不能重新创建或再次加入菜单。bridge 必须验证底层槽已属于目标菜单、尚未映射，然后调用 holder 的 existing-slot 映射入口。禁止使用 `InventorySlots` 重建玩家 36 槽；必须绑定 `SlotSemantics.PLAYER_INVENTORY` 与 `PLAYER_HOTBAR` 返回的原槽。`FakeSlot`/`OptionalFakeSlot` 继续走 AE2 输入协议，不得改成 LDLib2 `FluidSlot` 或直接启用会绕过服务端数据包的 phantom 写入。`AEBaseScreen` 继续保留 fake-slot 点击包、wrapped stack、AE tooltip 等输入协议，最终缩为只提供尺寸和协议的薄适配器；仅在每类交互回归通过后删除旧手工绘制逻辑。

不得以反射读取 AE2 私有菜单字段；需要的宿主、库存和状态必须由现有公开接口、显式访问器或新增受控接口提供。

## 依赖接入工作项

1. 项目已升级并适配 LDLib2 NeoForge all artifact `2.2.28`；实现和审查必须以 Gradle 实际解析的 `2.2.28` source/binary 为准。ECO 与本地 LDLib2 仓库只用于设计和源码对照，不能覆盖本项目解析到的 API 契约。
2. 执行前读取系统环境变量 `GRADLE_USER_HOME`；只在其已设置且解析路径位于 `E:/` 盘时解析依赖。不得自行创建、重定向或通过 `-g` 覆盖 Gradle 缓存目录。
3. Trinity Data Core、Pattern Core 与五类舱室已在现有 AE2 菜单上完成 LDLib2 挂载；既有玩家槽和机器槽均通过 bridge 映射，不创建第二套菜单槽。
4. JEI/EMI 集成保持可选加载边界，XEI 页面与方块 host 只共享纯 spec/selection 和无宿主状态的 factory，不共享 provider、元素树、`Scene` 或 lifecycle RPC。

后续若再次升级 LDLib2，仍须独立审计生命周期、sync/RPC、XEI、Taffy 布局与 mixin 行为；当前迁移的权威基线是 `2.2.28`。

## 双端同构与动态窗口协议

LDLib2 `2.2.28` 的 `UISyncManager` 按注册顺序为 sync value 与 RPC 分配整数 ID，移除后 ID 不复用。因此“同构”至少要求两端所有会注册 sync/RPC 的元素及其注册顺序完全一致；纯客户端 `Scene` 装饰可以通过受控 client factory 创建，但不得插入额外 binding/RPC 或改变动态 provider 的注册顺序。

1. 菜单构造器先建立 AE 槽和 client action，再构造静态 host root、生命周期 coordinator、状态面板与槽 wrapper，最后由 `AeMenuBridge` 挂载。静态 root 在菜单整个生命周期中保持附着。
2. coordinator 在静态 root 上只注册一次 message/RPC，并维护单调 `sequence`。客户端提交 `{operation, providerKey, sequence}`；服务端验证菜单、host、provider key 和当前 membership，先 open/close 服务端树，再返回 ack；客户端只处理与 pending request 匹配且顺序合法的 ack。
3. provider 的 open/close/reopen 必须由两端按同一 sequence 执行。每次 open 都创建全新元素树；close 从 overlay `removeChild` 并注销该树的 sync/RPC；重开所得新 ID 在两端以相同顺序继续递增。
4. 关闭按钮、Esc 和外部 close 都调用 coordinator，不能在客户端先执行 `HostSubUiContext.requestClose()`。`UIEventDispatcher` 会先执行本地 listener，之后才发送元素级 server RPC；若本地 listener 先移除按钮所属树，RPC 将无法发送，服务端树会残留。
5. 第一个动态切片只允许 S2C binding 或在 open ack 前禁用所有动态 C2S 控件，避免服务端尚未注册对应 RPC 时客户端提前发送动作。拒绝、超时或乱序响应只清理 pending 状态，不得乐观改变 membership。
6. 拖动位置、z-order、相机、hover、选中位置和 `visibleLayer` 是客户端 view state，不参与 coordinator membership、sync ID 或普通配方。禁止使用 ECO 的预挂载隐藏窗口模式来固定 ID。

Screen 继续继承 `AEBaseScreen` 以保留 AE fake-slot 点击包、wrapped stack 与 tooltip 协议。LDLib2 在 `ScreenEvent.Init.Pre` 自动把 `ModularUI` widget 加入 Screen；`render()` 只调用一次 `super.render()`，不得手动再次绘制 MUI。Data Core、Pattern Core、舱室和自动搭建的旧手工绘制生产入口均已切换；保留的 Screen 只承担尺寸和 AE2 输入协议适配，不得再次绘制同一 MUI。

## 通用预览分层

`Scene` 和 XEI 适配器只消费不可变预览快照。结构展开、候选解析和材料聚合应由 common 层接口完成；虚拟世界填充、相机、hover 与 ingredient 标注留在 client 层。不得把 `TrackedDummyWorld`、JEI 或 EMI 类型放入结构定义接口。

## 主机子 UI 接口

主机 UI 根元素通过扩展接口接收子面板 provider，并把 provider 创建的新元素挂到非全屏 overlay layer。四个 provider 的业务边界固定如下：

| Provider | 只读状态/模型 | 服务端动作 |
| --- | --- | --- |
| `main` | online、主结构 formed/matched/failure、存储类型/数量/容量；可嵌入主结构预览 | 无直接世界写入；结构选择只更新客户端 recipe/view state |
| `cpu` | CPU 子结构 formed/matched/failure、partition、busy partition、storage bytes、co-processors、全网 busy CPU 数 | 无直接世界写入；不得把 busy crafting CPU 与 Trinity partition 混为一个数 |
| `crafting` | crafting formed/matched/failure、P Core 数/容量、当前目标、退款可用状态 | 退款只调用菜单既有 `sendRefundAll()`/服务端 host 契约 |
| `auto_build` | main/cpu/crafting 三结构选择及每结构独立 tier/repeat 的暂存状态、权威 preview plan | 确认后生成既有 `TrinityAutoBuildRequest` 并走服务端 payload；不得从 AE 样板反推搭建参数 |

四窗允许同时打开，自动搭建不嵌入任一结构窗口。每个子面板必须支持显式 open/close、标题栏拖动、点击提升 z-order、Esc 按最上层逐个关闭、viewport 约束、XEI extra area 更新和关闭时释放 `Scene` 资源。`close()` 必须调用 overlay layer 的 `removeChild`，重新打开时 provider 创建全新的元素树和 `Scene`，不能只隐藏或复用旧实例。自动建造使用普通绝对定位元素配合 `WindowDragHelper` 保持非模态；`Dialog.windowMode` 只用于真正需要模态交互的窗口。每个 host UI 实例独立保存四窗的位置和层级；拖动位置、显示层、相机和 hover 不写入结构选择、普通 XEI 配方或服务端数据。

`TrinityHostedWindowChrome` 已把四窗统一为 `292x210`：标题和关闭控件占顶部，左侧 `196px` 预览区持有各自的 `StructurePreviewPanel`/`Scene`，右侧 `84px` 区域承载状态滚动列表或自动搭建动作。`TrinityDataCoreHostLauncherPanel` 使用 important `z-index: 400`，始终高于 `HostUiExtensionImpl` 从 300 开始分配的 hosted window 层级，避免四窗遮住重新打开入口。

首次打开且没有已保存/拖动位置时，host 使用真实 screen bounds 检测窗口是否会完全遮住下层标题，并按 `7px` 步长级联；在最小 `320x240` GUI 中，四个 `292x210` 窗口依次落在 `(4,4)`、`(11,11)`、`(18,18)`、`(24,25)`。已保存位置和用户拖动位置优先，不参与默认碰撞级联；互不重叠的默认位置也不应被移动。

host provider 与 XEI recipe adapter 不共享 provider 实例。两者只委托同一个无宿主状态的纯 UI factory；factory 每次接收纯 selection/snapshot 和明确动作接口并返回新树。XEI 版本不注册 host lifecycle RPC，host 版本也不能持有 XEI session。

## LDLib2 2.2.28 清理边界

`UIElement.removeChild` 会先从父 `children` 删除目标，再依次调用 `onRemoved()`、清除 `ModularUI`、清除 parent 与结构缓存；任一 `REMOVED`/`MUI_CHANGED` listener 抛错都会使后续清理中断。`HostSubUiRoot` 的 post-order 清理、异常聚合和 exactly-once 资源回调只能继续释放可达同级资源，不能恢复 LDLib2 已残留的私有 parent/MUI/cache 状态。出现该异常后 host 必须转为 terminal、记录完整日志并禁止树复用；发布验收必须注入 listener 失败，验证后续资源仍被尝试释放且不会反射修补或二次释放。
