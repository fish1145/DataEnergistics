# 可直接使用的 Goal 文本

以下文本是当前已经启动的持久 Codex Goal；上下文恢复时以本文件和 `00-需求基线与上下文恢复.md` 为准。

```text
在 F:/mc/Fish_Dan_/DataEnergistics 中完成多方块 UI 与全部舱室 UI 向 LDLib2 的迁移，并实现通用 XEI 多方块预览、子结构配置，以及普通多方块 XEI 配方到 AE Processing 样板的 transfer/编码。开始前完整阅读 docs/ldlib2-ui-migration 下全部文档，以 00-需求基线与上下文恢复.md 为不可变需求入口；上下文压缩后必须重新读取该目录，不得依赖记忆补全需求。

参考源码：F:/mc/ldlib/LDLib2、F:/1.21.1/NeoECOAEExtension、F:/mc/ExtremelyFrozen/fork/GregTech-Modern。依赖库：F:/mc/Fish_Dan_/Modular-Data-lib。先审计工作区与现有实现，保留所有非本任务改动。采用主代理负责需求、集成验证和 Git，子代理仅处理边界清晰的只读分析、实现、测试或审查；主代理必须审阅实际 diff 和验证结果。

必须交付：
1. LDLib2 方块 UI：Trinity 多方块状态、故障、容量、Pattern Core 分页/退款、自动建造，以及 INPUT/OUTPUT、ME_INPUT、ME_OUTPUT、PATTERN_BUFFER 全部舱室界面。
2. 通用预览模型：controller、具名 substructure、每个子结构独立 tier/repeat、definition revision，以及不可变的坐标/predicate/候选/材料/bounds/layer 快照。显示层状态必须与 repeat 分离。
3. 共用 LDLib2 预览 UI：Scene 可旋转缩放和选中方块；支持子结构切换、tier、repeat、全部/上一层/下一层/指定层、材料 scroller。JEI 使用 ModularUIRecipeCategory，EMI 使用 ModularUIEMIRecipe，二者复用同一个 UI factory 和快照模型；为 REI 保持薄适配边界。
4. XEI ingredient：材料随选择重算并正确暴露；显示层和 formed 视觉开关不得改变材料。
5. AE 样板：严格按 GT/ECO 的普通 XEI recipe wrapper 方式，把当前展开结构转换为“聚合材料 input + 控制器/owner output”的普通配方。JEI/EMI 对该配方执行现有 AE recipe transfer，填入 Processing 模式，再复用项目现有编码流程。不得新增 AE 专属配方、建造蓝图 item、custom pattern details 或直接写 encodedPatternSlot。超出终端容量时返回可见错误，不能截断或部分覆盖。
   写入结果必须是通用 Processing 样板：只含普通材料 input 与 controller/owner output，不持久化子结构、tier/repeat、显示层、方向、镜像或精确 BlockState。具体放置状态仅在实际搭建时由服务端根据权威结构定义和当次搭建参数自动补全。
6. 业务边界：复用现有多方块匹配、自动建造、舱室库存和 AE2 规则；UI 不复制权威业务逻辑，不使用反射，不写占位实现。
7. 主机 UI 扩展：提供可嵌入、可开关、可拖动的子 UI provider/host 接口，以 WindowDragHelper 或 LDLib2 等价能力实现拖动和 viewport 约束。主机与 XEI 可复用纯预览组件工厂，但不得共享 UI 实例、Scene、相机、显示层或拖动状态。

如果通用结构投影 API 缺失，先证明正常入口已穷尽，再在 Modular-Data-lib 中设计最小但完整的中立公共 API。MDLib 不得依赖 LDLib2 UI、JEI/EMI 或 AE2。MDLib 是独立 Git 事务；先检查其已有未提交改动并保留。只有用户明确授权该实施阶段后，才可创建 qy/ 前缀分支、测试、提交、推送并创建 PR。DataEnergistics 也按独立提交处理，未授权不得推送。

Gradle 只能使用系统环境变量 GRADLE_USER_HOME。每次构建前读取并记录该变量；未设置则停止并报告。禁止创建或重定向项目内/用户目录 Gradle 缓存，禁止使用 -g 或修改构建脚本覆盖缓存位置。源码与文档统一 UTF-8 无 BOM；Java 使用 import，不写完全限定名；新增接口和成员按项目规范说明动机和作用；异常 Fail Fast 并记录上下文日志，核心业务避免未捕获异常导致进程崩溃。

按 docs/ldlib2-ui-migration/05-实施排期与验收.md 的 P0-P9 依赖顺序推进。测试必须直接调用目标逻辑，禁止源码 contain 测试、删除功能测试和反射测试。完成每个工作包后更新文档状态，执行对应逻辑/容器测试；最终在 JEI、EMI、单人和多人环境验证 UI、材料、槽位、自动建造、资源重载、恶意请求、样板原子性与渲染资源释放。只有所有验收通过且无未处理阻塞项才可结束 Goal。
```
