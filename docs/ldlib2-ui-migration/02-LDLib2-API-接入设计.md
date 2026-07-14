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

## 推荐模块结构

同步/RPC-bearing 的 host 树、provider 接口和服务端动作放在非 client 包，使菜单 factory 能在 client/server 构造相同注册拓扑；`Scene`、`TrackedDummyWorld` 填充及纯渲染装饰放在 `client.ldlibui`。纯客户端视觉节点不得改变双方 sync value/RPC 的数量与注册顺序，也不得把专用客户端类泄漏到方块实体。

| 建议组件 | 职责 |
| --- | --- |
| `MultiblockUiFactory` | 选择对应核心的 `ModularUI` 根树 |
| `MultiblockStatusPanel` | 共用结构状态、失败提示与 tooltip |
| `AutoBuildPanel` | 将 `MultiBlockAutoBuildOverlayDescription` 映射为 LDLib2 非模态子面板 |
| `CompartmentUiFactory` | 按 `CompartmentType` 创建页面 |
| `AeItemSlot` | 包装既有 `AppEngSlot`/`FakeSlot`，保持 AE2 展示与输入协议 |
| `CompartmentSlotPanel` | 按 `SlotSemantic` 将已创建的菜单槽映射成 `AeItemSlot` |
| `CompartmentUiActions` | 明确的翻页、退款、自动建造、模式切换服务端动作 |
| `HostUiExtension` | 主机 UI 挂载多个可拖动、可开关子面板的稳定接口 |
| `DraggableSubUi` | 用 `WindowDragHelper` 管理窗口位置和显示状态，不持有业务权威状态 |

上述名称是迁移期建议；落地时应遵循项目既有包和命名风格，并为公共接口补齐动机与成员注释。

## 容器策略

保留全部现有 `MenuTypeBuilder`、`MenuOpener`、`AEBaseMenu`、`SlotSemantic` 与菜单注册。在每个目标菜单完成槽位、client action 和初始状态创建后，由显式 bridge 将 `ModularUI` 附着到菜单。`MenuTypeBuilder` 会在服务端打开菜单和客户端解包 locator 时分别调用同一个 factory，因此必须在两端菜单构造期间建立同构 host root，不能只在客户端构造，也不能等 Screen `init` 后再挂载。

既有 `Slot` 由对应的 LDLib2 `AeItemSlot` 包装，但不能重新创建或再次加入菜单。bridge 必须验证底层槽已属于目标菜单、尚未映射，然后调用 holder 的 existing-slot 映射入口。禁止使用 `InventorySlots` 重建玩家 36 槽；必须绑定 `SlotSemantics.PLAYER_INVENTORY` 与 `PLAYER_HOTBAR` 返回的原槽。`FakeSlot`/`OptionalFakeSlot` 继续走 AE2 输入协议，不得改成 LDLib2 `FluidSlot` 或直接启用会绕过服务端数据包的 phantom 写入。`AEBaseScreen` 继续保留 fake-slot 点击包、wrapped stack、AE tooltip 等输入协议，最终缩为只提供尺寸和协议的薄适配器；仅在每类交互回归通过后删除旧手工绘制逻辑。

不得以反射读取 AE2 私有菜单字段；需要的宿主、库存和状态必须由现有公开接口、显式访问器或新增受控接口提供。

## 依赖接入工作项

1. 在项目仓库声明 FirstDark snapshots 仓库和 LDLib2 NeoForge all artifact；Data Energistics 的权威版本固定为 `2.2.8`。ECO 使用的 `2.2.1` 和 `F:/mc/ldlib/LDLib2` 当前 `2.2.28` 只能作版本差异参考，不能用它们的 API 推断本项目行为。
2. 执行前读取系统环境变量 `GRADLE_USER_HOME`；只在其已设置且解析路径位于 `E:/` 盘时解析依赖。不得自行创建、重定向或通过 `-g` 覆盖 Gradle 缓存目录。
3. 添加 `ILDLibPlugin` 实现，集中注册项目 LSS、纹理和可选 XEI 集成。
4. 以 Trinity Data Core 作为首个垂直切片，在现有 AE2 菜单上验证 LDLib2 挂载、既有玩家槽映射、单次渲染和网络初始化顺序。

实现和审查以 Gradle 已解析的 LDLib2 `2.2.8` source jar 为准；若未来升级版本，必须作为独立工作包重新审计生命周期、sync/RPC、XEI 与 mixin 行为，不能在本迁移中顺带采用 `2.2.28` 新 API。

## 双端同构与动态窗口协议

LDLib2 `2.2.8` 的 `UISyncManager` 按注册顺序为 sync value 与 RPC 分配整数 ID，移除后 ID 不复用。因此“同构”至少要求两端所有会注册 sync/RPC 的元素及其注册顺序完全一致；纯客户端 `Scene` 装饰可以通过受控 client factory 创建，但不得插入额外 binding/RPC 或改变动态 provider 的注册顺序。

1. 菜单构造器先建立 AE 槽和 client action，再构造静态 host root、生命周期 coordinator、状态面板与槽 wrapper，最后由 `AeMenuBridge` 挂载。静态 root 在菜单整个生命周期中保持附着。
2. coordinator 在静态 root 上只注册一次 message/RPC，并维护单调 `sequence`。客户端提交 `{operation, providerKey, sequence}`；服务端验证菜单、host、provider key 和当前 membership，先 open/close 服务端树，再返回 ack；客户端只处理与 pending request 匹配且顺序合法的 ack。
3. provider 的 open/close/reopen 必须由两端按同一 sequence 执行。每次 open 都创建全新元素树；close 从 overlay `removeChild` 并注销该树的 sync/RPC；重开所得新 ID 在两端以相同顺序继续递增。
4. 关闭按钮、Esc 和外部 close 都调用 coordinator，不能在客户端先执行 `HostSubUiContext.requestClose()`。`UIEventDispatcher` 会先执行本地 listener，之后才发送元素级 server RPC；若本地 listener 先移除按钮所属树，RPC 将无法发送，服务端树会残留。
5. 第一个动态切片只允许 S2C binding 或在 open ack 前禁用所有动态 C2S 控件，避免服务端尚未注册对应 RPC 时客户端提前发送动作。拒绝、超时或乱序响应只清理 pending 状态，不得乐观改变 membership。
6. 拖动位置、z-order、相机、hover、选中位置和 `visibleLayer` 是客户端 view state，不参与 coordinator membership、sync ID 或普通配方。禁止使用 ECO 的预挂载隐藏窗口模式来固定 ID。

Screen 继续继承 `AEBaseScreen` 以保留 AE fake-slot 点击包、wrapped stack 与 tooltip 协议。LDLib2 在 `ScreenEvent.Init.Pre` 自动把 `ModularUI` widget 加入 Screen；`render()` 只调用一次 `super.render()`，不得手动再次绘制 MUI。完整迁移后 `drawBG`/`drawFG`、旧 overlay、toolbar 和旧 style text 都必须移除或改为空的协议适配内容。`1aeb1cf8` 已迁移状态面板与 36 个玩家槽，旧自动搭建入口留到其独立 provider 工作包移除。

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

host provider 与 XEI recipe adapter 不共享 provider 实例。两者只委托同一个无宿主状态的纯 UI factory；factory 每次接收纯 selection/snapshot 和明确动作接口并返回新树。XEI 版本不注册 host lifecycle RPC，host 版本也不能持有 XEI session。

## LDLib2 2.2.8 清理边界

`UIElement.removeChild` 会先从父 `children` 删除目标，再依次调用 `onRemoved()`、清除 `ModularUI`、清除 parent 与结构缓存；任一 `REMOVED`/`MUI_CHANGED` listener 抛错都会使后续清理中断。`HostSubUiRoot` 的 post-order 清理、异常聚合和 exactly-once 资源回调只能继续释放可达同级资源，不能恢复 LDLib2 已残留的私有 parent/MUI/cache 状态。出现该异常后 host 必须转为 terminal、记录完整日志并禁止树复用；发布验收必须注入 listener 失败，验证后续资源仍被尝试释放且不会反射修补或二次释放。
