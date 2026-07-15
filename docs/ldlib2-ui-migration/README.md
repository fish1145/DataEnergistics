# LDLib2 UI 迁移计划

## 目标与边界

本目录规划将 Data Energistics 的多方块 UI 和舱室 UI 迁移至 LDLib2，并补齐可复用的 XEI 多方块预览、子结构配置和 AE 样板写入链路。迁移必须复用现有多方块匹配、自动建造、舱室库存和 AE2 业务逻辑，不允许在客户端 UI 中复制权威逻辑。

当前范围：

| 范围 | 迁移前入口 | 迁移目标 |
| --- | --- | --- |
| 多方块主界面 | `TrinityDataCoreScreen`、`TrinityPatternCoreScreen` | LDLib2 `ModularUI` 面板、状态与操作控件 |
| 多方块自动建造 | `MultiBlockAutoBuildOverlay` | 与 `main`/`cpu`/`crafting` 并列的第四个 LDLib2 可拖动子 UI |
| 舱室 | `CompartmentMenu`、`CompartmentScreen` 及各专用 Screen | LDLib2 槽位网格、容量状态、升级与分页控件 |
| XEI 多方块预览 | 原先没有多方块类别 | JEI/EMI 共用的 LDLib2 `ModularUI` 预览页 |
| 子结构配置 | Trinity 的 main/cpu/crafting 定义及自动建造选项 | 子结构切换、等级、重复层数和显示切片控制 |
| AE 样板 | 原先没有多方块 XEI recipe transfer | 把材料输入/控制器输出的普通 XEI 配方传入 AE Processing 编码界面 |

不在本次范围：通用终端、模式编码终端、数据萃取器、数据神殿、太阳能板和非舱室单方块机器 UI。

## 当前状态

当前分支为 `qy/ldlib2-ui-migration`，Draft PR 为 [#99](https://github.com/fish1145/DataEnergistics/pull/99)，使用 LDLib2 `2.2.28`。当前发布批次以 `d546ae7c` 为基线，至实现锚点 `b5780774` 包含以下 8 个按功能拆分的提交；本文所在的文档提交位于其后，实际 `HEAD`、tracking ref 和 PR 状态须在恢复时重新读取：

| 提交 | 标题 |
| --- | --- |
| `36620546` | 完善 LDLib2 主机浮窗层级交互 |
| `e1027e0a` | 重构多方块预览交互布局 |
| `186bff09` | 重构通用 XEI 多方块预览布局 |
| `d14ee1ed` | 同步刷新 JEI 与 EMI 多方块原料 |
| `69ca9992` | 避免 LDLib2 子窗口初始重叠 |
| `956f2034` | 统一 Trinity 四窗 LDLib2 布局 |
| `cd7577ad` | 优化舱室 LDLib2 信息布局 |
| `b5780774` | 补充 LDLib2 UI 本地化文本 |

P8 common/network、JEI 和 EMI 已在远端锚点之前按小提交完成，不再是工作区待提交内容。它们只把服务端可信的普通 input/output 填入 AE Processing 配置，不编码、不碰 `encodedPatternSlot`、不搬物料；用户点击 AE 自带编码按钮时才生成普通 Processing Pattern。transfer 同时原子清空并在失败时回滚 pending/last source、Data Ripper key/fluid 和 display fallback，避免普通多方块样板受旧状态污染。

通用 XEI 页面由 JEI/EMI 共用 LDLib2 factory、typed recipe view、候选栏和 owner output，多个子结构通过横向 scroller 切换，按钮最小宽度 64px。JEI 动态原料先合并延迟刷新请求，再调用公开 `showRecipes` 重建页面/formal slots，并接受导航历史增加；runtime stop/start 会释放 `uiCache`。EMI 使用 live ingredients 和可扩容 slot pool。不使用反射或私有 API 绕过 JEI 的原位 invalidation 缺口。

多方块 host 支持 `main/cpu/crafting/auto_build` 四个独立、可拖动且可同时打开的子 UI；首次默认位置碰撞时级联，保存位置和拖动位置优先。Trinity 四窗统一为 `292x210`，包含 196px `Scene`、84px 状态/动作侧栏和 z-index 400 的 launcher rail。全部目标舱室已迁移并优化信息布局，同时保留既有菜单槽、容量、升级和分页语义。

最终 `spotlessCheck compileJava compileTestJava test runGameTestServer build` 已成功，GameTest 为 367/367；diff、UTF-8/BOM、语言 JSON、文档链接和 Java 质量审计也已通过。恢复时只需按实际状态确认文档提交、分支推送和 Draft PR CI。客户端 GameTest 因 Oritech 的 Athena 运行时依赖缺失而在启动阶段阻塞；不得通过修改生产依赖规避，服务端/JUnit 通过也不能替代 JEI/EMI 渲染、按钮、真实 `Scene`、槽位 bounds 和多人交互验收。

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [00-需求基线与上下文恢复.md](00-需求基线与上下文恢复.md) | 不可变需求、决策、约束和恢复步骤 |
| [01-现状与迁移边界.md](01-现状与迁移边界.md) | 现有类、功能和保留项清单 |
| [02-LDLib2-API-接入设计.md](02-LDLib2-API-接入设计.md) | 已核对的 LDLib2 API 与项目接入设计 |
| [03-多方块-UI-迁移计划.md](03-多方块-UI-迁移计划.md) | 多方块状态、自动建造与交互的分阶段计划 |
| [04-舱室-UI-迁移计划.md](04-舱室-UI-迁移计划.md) | 五种舱室类型的界面和槽位迁移计划 |
| [05-实施排期与验收.md](05-实施排期与验收.md) | 依赖顺序、工作包、验收与回滚条件 |
| [06-通用-XEI-多方块预览.md](06-通用-XEI-多方块预览.md) | 通用预览模型、XEI 适配和子结构控件 |
| [07-AE-样板写入设计.md](07-AE-样板写入设计.md) | 样板语义决策、事务边界和安全校验 |
| [08-参考实现与-API-边界.md](08-参考实现与-API-边界.md) | GT、ECO、LDLib2 与 MDLib 的参考结论 |
| [GOAL.md](GOAL.md) | 可直接粘贴使用的后续实施 Goal 文本 |

## 结论

采用“纯预览模型 + 共用 LDLib2 UI factory + XEI 薄适配器 + 服务端权威动作”的架构。方块 UI 与 XEI 页面只共享纯模型和元素工厂，各自创建 provider、元素树、`Scene` 与虚拟世界；XEI 页面不得直接改世界、库存或样板。中立结构投影与 paired-candidate API 已在 MDLib Draft PR [#2](https://github.com/ModularMCLib/Modular-Data-lib/pull/2) 完成，后续仅在确认新的中立 API 缺口时修改该库。P8、最终本地构建和质量审计已完成；当前只需按实际状态确认文档提交、分支推送和 Draft PR #99 CI，并在 Athena 运行环境恢复后补齐真实客户端和多人发布验收。
