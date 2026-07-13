# 可直接使用的 Goal 文本

以下文本是当前已经启动的持久 Codex Goal；上下文恢复时以本文件和 `00-需求基线与上下文恢复.md` 为准。

```text
在 F:/mc/Fish_Dan_/DataEnergistics 中完成多方块 UI 与全部舱室 UI 向 LDLib2 的迁移，并实现通用 XEI 多方块预览、子结构配置，以及普通多方块 XEI 配方到 AE Processing 样板的 transfer/编码。开始前完整阅读 docs/ldlib2-ui-migration 下全部文档，以 00-需求基线与上下文恢复.md 为不可变需求入口；上下文压缩后必须重新读取该目录，不得依赖记忆补全需求。

参考源码：F:/mc/ldlib/LDLib2、F:/1.21.1/NeoECOAEExtension、F:/mc/ExtremelyFrozen/fork/GregTech-Modern。MDLib 本任务隔离 worktree：F:/mc/Fish_Dan_/Modular-Data-lib-preview-api；原始 F:/mc/Fish_Dan_/Modular-Data-lib 含用户改动，只可只读参考。优先使用 IntelliJ IDEA MCP 读取、检索、检查和构建已打开工程，只有未在 IDEA 打开的隔离 worktree 才使用终端。先审计工作区与现有实现，保留所有非本任务改动。采用主代理负责需求、集成验证和 Git，子代理仅处理边界清晰的只读分析、实现、测试或审查；主代理必须审阅实际 diff 和验证结果。

当前状态必须作为续做起点：DataEnergistics 位于 qy/ldlib2-ui-migration，Draft PR https://github.com/fish1145/DataEnergistics/pull/99，已推送 594b79cf、29c889f2、09274429。MDLib 位于 qy/multiblock-preview-api，Draft PR https://github.com/ModularMCLib/Modular-Data-lib/pull/2，已按功能推送 2c967a2、b0e6262、2d9c1fa、2504393、e8734f3、2830c2c；spotlessCheck、JUnit、GameTest 和 build 已通过。修复后的 binary/source jar 已同步到 DataEnergistics，IDEA 编译与 DataEnergistics spotlessCheck/test 已通过。不要重复这些工作，也不要再次等待推送授权。

必须交付：
1. LDLib2 方块 UI：Trinity 多方块状态、故障、容量、Pattern Core 分页/退款、自动建造，以及 INPUT/OUTPUT、ME_INPUT、ME_OUTPUT、PATTERN_BUFFER 全部舱室界面。
2. 通用预览模型：controller、具名 substructure、每个子结构独立 tier/repeat、definition revision，以及不可变的坐标/predicate/候选/材料/bounds/layer 快照。显示层状态必须与 repeat 分离。
3. 共用 LDLib2 预览 UI：Scene 可旋转缩放和选中方块；支持子结构切换、tier、repeat、全部/上一层/下一层/指定层、材料 scroller。JEI 使用 ModularUIRecipeCategory，EMI 使用 ModularUIEMIRecipe，二者复用同一个 UI factory 和快照模型；为 REI 保持薄适配边界。
4. XEI ingredient：材料随选择重算并正确暴露；显示层和 formed 视觉开关不得改变材料。
5. AE 样板：严格按 GT/ECO 的普通 XEI recipe wrapper 方式，把当前展开结构转换为“聚合材料 input + 控制器/owner output”的普通配方。JEI/EMI 对该配方执行现有 AE recipe transfer，填入 Processing 模式，再复用项目现有编码流程。不得新增 AE 专属配方、建造蓝图 item、custom pattern details 或直接写 encodedPatternSlot。超出终端容量时返回可见错误，不能截断或部分覆盖。
   写入结果必须是通用 Processing 样板：只含普通材料 input 与 controller/owner output，不持久化子结构、tier/repeat、显示层、方向、镜像或精确 BlockState。具体放置状态仅在实际搭建时由服务端根据权威结构定义和当次搭建参数自动补全。
6. 业务边界：复用现有多方块匹配、自动建造、舱室库存和 AE2 规则；UI 不复制权威业务逻辑，不使用反射，不写占位实现。
7. 主机 UI 扩展：提供可嵌入、可开关、可拖动的非模态子 UI provider/host 接口，以 WindowDragHelper 或 LDLib2 等价能力实现标题栏拖动、z-order、Esc 优先关闭、viewport 约束和动态 XEI extra area。关闭时必须 removeChild 并释放资源，重开必须创建全新的 UI/Scene。主机与 XEI 可复用纯预览组件工厂，但不得共享 UI 实例、Scene、相机、显示层或拖动状态。

通用结构投影 API 已在 MDLib Draft PR #2 中实现：包含 repeat layout/selection、controller 锚定的 layer/cell/source/bounds snapshot、非默认 StructureDir/flip、100 万 cell 与 matcher 访问预算、精确 BlockState 和 placement ItemStack 候选。MDLib 不得继续扩入 LDLib2 UI、JEI/EMI、AE2、Trinity 子结构或材料选择业务。后续若审查要求修正，只在隔离 worktree 中按独立提交处理并推送现有 PR。

Gradle 只能使用系统环境变量 GRADLE_USER_HOME。每次构建前读取并记录该变量；未设置或解析后的路径不在 E:/ 盘则停止并报告。禁止创建或重定向项目内/用户目录 Gradle 缓存，禁止使用 -g 或修改构建脚本覆盖缓存位置。源码与文档统一 UTF-8 无 BOM；Java 使用 import，不写完全限定名；清理本次新增或直接修改代码中的冗余 Objects.requireNonNull，只在延迟 supplier、公开构造不变量或确有必要的过滤边界保留；新增接口和成员按项目规范说明动机和作用；异常 Fail Fast 并记录上下文日志，核心业务避免未捕获异常导致进程崩溃。

从当前状态继续按以下顺序推进：common preview spec/selection/snapshot/material/revision；AE menu bridge、AeItemSlot、HostUiExtension；Trinity Data Core 垂直切片；Pattern Core 与自动建造浮窗；ME_OUTPUT、ME_INPUT、组合仓库、PATTERN_BUFFER；JEI/EMI 共用预览；typed AE transfer；旧入口清理。测试必须直接调用目标逻辑，禁止源码 contain 测试、删除功能测试和反射测试。完成每个工作包后更新文档状态，执行对应逻辑/容器测试；最终在 JEI、EMI、单人和多人环境验证 UI、材料、槽位、自动建造、资源重载、恶意请求、样板原子性与渲染资源释放。只有所有验收通过且无未处理阻塞项才可结束 Goal。
```
