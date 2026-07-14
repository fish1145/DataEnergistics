# LDLib2 UI 迁移计划

## 目标与边界

本目录规划将 Data Energistics 的多方块 UI 和舱室 UI 迁移至 LDLib2，并补齐可复用的 XEI 多方块预览、子结构配置和 AE 样板写入链路。迁移必须复用现有多方块匹配、自动建造、舱室库存和 AE2 业务逻辑，不允许在客户端 UI 中复制权威逻辑。

当前范围：

| 范围 | 现有入口 | 迁移目标 |
| --- | --- | --- |
| 多方块主界面 | `TrinityDataCoreScreen`、`TrinityPatternCoreScreen` | LDLib2 `ModularUI` 面板、状态与操作控件 |
| 多方块自动建造 | `MultiBlockAutoBuildOverlay` | 与 `main`/`cpu`/`crafting` 并列的第四个 LDLib2 可拖动子 UI |
| 舱室 | `CompartmentMenu`、`CompartmentScreen` 及各专用 Screen | LDLib2 槽位网格、容量状态、升级与分页控件 |
| XEI 多方块预览 | 当前尚未注册多方块类别 | JEI/EMI 共用的 LDLib2 `ModularUI` 预览页 |
| 子结构配置 | Trinity 的 main/cpu/crafting 定义及自动建造选项 | 子结构切换、等级、重复层数和显示切片控制 |
| AE 样板 | 当前不存在多方块 XEI recipe transfer | 把材料输入/控制器输出的普通 XEI 配方传入 AE Processing 编码界面 |

不在本次范围：通用终端、模式编码终端、数据萃取器、数据神殿、太阳能板和非舱室单方块机器 UI。

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [00-需求基线与上下文恢复.md](00-需求基线与上下文恢复.md) | 不可变需求、决策、约束和恢复步骤 |
| [01-现状与迁移边界.md](01-现状与迁移边界.md) | 现有类、功能和保留项清单 |
| [02-LDLib2-API-接入设计.md](02-LDLib2-API-接入设计.md) | 已核对的 LDLib2 API 与项目接入设计 |
| [03-多方块-UI-迁移计划.md](03-多方块-UI-迁移计划.md) | 多方块状态、自动建造与交互的分阶段计划 |
| [04-舱室-UI-迁移计划.md](04-舱室-UI-迁移计划.md) | 四类舱室界面和槽位迁移计划 |
| [05-实施排期与验收.md](05-实施排期与验收.md) | 依赖顺序、工作包、验收与回滚条件 |
| [06-通用-XEI-多方块预览.md](06-通用-XEI-多方块预览.md) | 通用预览模型、XEI 适配和子结构控件 |
| [07-AE-样板写入设计.md](07-AE-样板写入设计.md) | 样板语义决策、事务边界和安全校验 |
| [08-参考实现与-API-边界.md](08-参考实现与-API-边界.md) | GT、ECO、LDLib2 与 MDLib 的参考结论 |
| [GOAL.md](GOAL.md) | 可直接粘贴使用的后续实施 Goal 文本 |

## 结论

采用“纯预览模型 + 共用 LDLib2 UI factory + XEI 薄适配器 + 服务端动作”的架构。方块 UI 与 XEI 页面只共享纯模型和元素工厂，各自创建 provider、元素树、`Scene` 与虚拟世界；XEI 页面不得直接改世界、库存或样板。实施已在 `qy/ldlib2-ui-migration` 分支和 Draft PR [#99](https://github.com/fish1145/DataEnergistics/pull/99) 启动；中立结构投影与 paired-candidate API 已在 MDLib Draft PR [#2](https://github.com/ModularMCLib/Modular-Data-lib/pull/2) 完成，并已将验证后的 jar 接入本项目。Data Energistics 的 atomic definition snapshot、common preview 模型、Trinity `main/cpu/crafting` catalog、AE menu bridge、既有槽包装、四窗 `HostUiExtension` 与 P2A 双端权威 lifecycle coordinator 已完成。P2A 按 `8eb22095`、`7fc43b88`、`faecf369`、`e524687d` 拆分并通过 317/317 GameTest；下一步是 Pattern Core 与 `main`、`cpu`、`crafting`、`auto_build` 四个真实业务 provider。每个工作包按功能独立提交，未通过对应验收前保留旧入口。
