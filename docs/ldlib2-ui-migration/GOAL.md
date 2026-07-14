# 可直接使用的 Goal 文本

以下代码块可以直接粘贴为新的持久 Codex Goal。它独立包含需求范围、当前状态、非目标、阶段、验收、验证和提交策略；上下文压缩后先重新读取本目录，再从“当前状态锚点”继续。

```text
目标

在 F:/mc/Fish_Dan_/DataEnergistics 的 qy/ldlib2-ui-migration 分支和现有 Draft PR #99 中，完成 Data Energistics 全部多方块 UI 与全部舱室 UI 向 LDLib2 的迁移，并完成通用 JEI/EMI XEI 多方块预览、可配置子结构投影，以及普通多方块配方到 AE Processing 配置的 typed transfer。不得把任务缩减为计划或最小实现；上下文压缩后必须重新完整阅读 F:/mc/Fish_Dan_/DataEnergistics/docs/ldlib2-ui-migration 下全部 Markdown，以 00-需求基线与上下文恢复.md 和本 Goal 为恢复入口。

参考与 API 基线

1. 并行参考 F:/1.21.1/NeoECOAEExtension 的 LDLib2 XEI UI factory、F:/mc/ExtremelyFrozen/fork/GregTech-Modern 的清晰多方块 XEI 页面、F:/mc/ldlib/LDLib2 的 UI/Scene/API，以及 Data Energistics 现有 AE2、自动搭建和舱室逻辑。
2. Data Energistics 实际依赖的 LDLib2 权威版本是 2.2.8；ECO 的 2.2.1 和 LDLib2 工作区当前 2.2.28 只能用于理解，不得搬入未经 2.2.8 source jar 核对的 API。
3. 优先使用 IntelliJ IDEA MCP 读取、检索、inspection、格式检查和定向构建已打开工程；只有 IDEA MCP 不支持或明确失败的操作才使用终端，并记录退回原因。
4. 不使用反射解决 AE2、LDLib2 或测试可达性问题，不写占位实现，不写源码 contain 测试。

不可变功能范围

1. 把 Trinity Data Core、Trinity Pattern Core、自动搭建以及 INPUT/OUTPUT、ME_INPUT、ME_OUTPUT、PATTERN_BUFFER 全部生产 UI 入口迁移到 LDLib2；复用既有菜单、槽、库存、权限、容量、退款、匹配和自动搭建业务，不在 UI 复制权威逻辑。
2. Trinity host 必须有四个可同时打开、可拖动、可置顶的独立非模态子 UI，key 固定为 main、cpu、crafting、auto_build。auto_build 是第四个子 UI，不是结构页签。四窗分别拥有位置、z-order、元素树、Scene/资源和释放周期；Esc 每次只关闭当前最上层窗口，关闭 removeChild 并释放，重开创建全新树和资源。
3. client/server 菜单以确定顺序构造同构 sync/RPC 拓扑；始终挂载的 root coordinator 处理服务端权威 open/close/reopen。客户端不得乐观移除窗口，禁止用 ECO 式预挂载隐藏窗规避动态 ID。
4. 提供通用多方块投影和 LDLib2 预览 UI。JEI 与 EMI 使用同一个纯 preview/session/UI factory 和 typed recipe view，但各自创建独立元素树、Scene、TrackedDummyWorld、相机与视图状态；为 REI 保留不依赖 JEI/EMI 类型的薄适配边界。
5. 页面必须支持切换具名 structureKey、结构内 variantIndex、每个子结构独立 tier/repeat、candidate selection，以及全部/上一层/下一层/指定逻辑层。visibleLayer 只属于视图状态，与 variant、repeat 和材料分离；相机、hover、formed 与显示层不得改变配方。
6. 多方块按普通配方表达：当前投影聚合后的材料 ItemStack/amount 是 INPUT，controller/owner 是 OUTPUT。Scene、候选检查器、谓词详情、hover、层级控件和其他辅助槽的 recipe role 都是 NONE。registeredRecipeId 保持 controller 级稳定，动态 revision、structureKey、variantIndex、tier/repeat 和 candidate selections 使用 projectionFingerprint 校验。
7. JEI/EMI transfer 是通用 typed transfer。点击 transfer 时必须从当前 session 读取最新 MultiblockRecipeView，由服务端依据当前 catalog 和完整 fingerprint 权威重建材料；不得信任 XEI ingredient cache 或客户端提交的材料栈。
8. transfer 只把服务端可信的普通材料 input 和 controller/owner output 原子填入 AE Pattern Encoding Terminal 的 Processing 配置槽，并切换 Processing 模式。它不得调用 encode，不得写 encodedPatternSlot，不得消耗空白样板，不得搬运玩家或网络物料。只有用户随后点击 AE 自带编码按钮，才按 AE 既有流程生成普通 Processing Pattern。
9. AE 样板必须保持通用，只保存普通 stack/amount。structureKey、variantIndex、tier/repeat、candidate selection、visibleLayer、方向、镜像和精确 BlockState 都不得作为 metadata 或 custom pattern details 写入样板。只有实际自动搭建时，独立 build request 才携带结构与搭建参数，并由服务端从权威定义自动补全精确状态；不得从 Processing Pattern 反推结构。
10. transfer 使用目标终端真实 ConfigInventory size、isAllowedIn 和 getMaxAmount 预检。81 input 成功、82 input 或实际容量更小原子失败；任何失败都保持原 mode、input、output 不变。批处理、逐槽读回、模式切换或 endBatch 失败必须回滚；不完整回滚使菜单失效或关闭，并保留 primary failure 与 suppressed rollback failure。
11. 通用多方块 transfer 还必须原子隔离 AE 菜单的 recipe-source 临时状态：提交前快照并清空 pending/last source、pending key input/output、pending fluid input/output 和 key display fallback，防止普通多方块 Processing Pattern 继承上一次 Data Ripper/XEI transfer 状态；后续任一步失败时这些字段与 mode、input、output 一起精确回滚。XEI Data Ripper resolver 只接受明确的 DataRipperReassemblerRecipe 或其 RecipeHolder，不得通过模糊上下文推断；编码前的自动 key 解析还必须要求 pending source 明确指向 Data Ripper。

代码质量约束

1. 所有文件 UTF-8 无 BOM；Java 通过 import 使用类型，禁止代码体完全限定名。
2. Fail Fast，异常包含上下文日志，不允许空 catch；核心业务异常不得无控制地崩溃整个进程。
3. 清理本任务新增或直接修改范围内滥用的 Objects.requireNonNull；仅在公开构造不变量、延迟 supplier 或确有必要的过滤边界保留。
4. Lombok 只用于确实减少 getter/setter 等机械样板；不得为了使用 Lombok 改写清晰领域模型。适合不可变数据载体和结构化值的 record 必须保留。
5. 逻辑层优先依赖接口，新增公共接口和成员说明动机、需求和职责；禁止创建会膨胀为超级对象的 Service/Manager/Controller。
6. 测试直接调用目标逻辑；禁止反射测试、源码 contain 测试和仅验证功能已删除的测试。

Gradle、仓库和依赖边界

1. 每次构建前读取系统 GRADLE_USER_HOME。它必须已设置且解析到 E:/ 盘；当前历史值是 E:/.gradle，但每次仍需重新读取。禁止使用 -g，禁止创建项目内或用户目录替代缓存，禁止通过脚本或 IDE 改写缓存路径。
2. Data Energistics 当前分支是 qy/ldlib2-ui-migration，Draft PR 是 https://github.com/fish1145/DataEnergistics/pull/99。保留 logs/、wenli/ 和所有非本任务改动，禁止宽泛暂存。
3. Modular Data Lib 只在确实缺少中立多方块定义/投影 API 时修改；不得把 LDLib2、JEI/EMI、AE2、Trinity 业务或样板编码放入该库。既有隔离 worktree 是 F:/mc/Fish_Dan_/Modular-Data-lib-preview-api，分支 qy/multiblock-preview-api，Draft PR #2；原始 F:/mc/Fish_Dan_/Modular-Data-lib 含用户改动，只读。任何新增 MDLib 修正必须独立提交、验证、推送并更新其 PR，再以独立 Data Energistics 依赖提交接入。
4. Git 事务只由主代理处理。所有实现按功能拆成小提交，遵循仓库既有中文提交风格；禁止把 common/network、JEI、EMI、文档或无关清理塞入一个大提交。

当前状态锚点

1. HEAD 和远端当前均为 b5724470（清理 Objects.requireNonNull 滥用）。在此之前已提交：原子定义快照与 MDLib paired candidate、common preview/catalog、AE/LDLib2 menu bridge、四窗 HostUiExtension 与双端 lifecycle、Trinity Data/Pattern Core、main/cpu/crafting/auto_build 四个真实 provider、退款和自动搭建动作、ME_OUTPUT、ME_INPUT、INPUT/OUTPUT 组合仓库、PATTERN_BUFFER、共用 preview session/panel/factory/Scene 生命周期，以及 JEI/EMI 通用 XEI 页面。旧 Trinity Data Core 与旧自动搭建生产入口已删除。
2. b5724470 后的当前工作区已实现 P8-A common/network typed transfer：bounded payload/codec、服务端权威重建、真实 ConfigInventory 预检、双库存原子写入与完整/失败回滚、菜单失效保护，以及 native/Universal 菜单 typed request 入口。请求日志只记录有界 identity 和 selection 数量，不展开不可信 map/value。transfer 把 pending/last source、key、fluid 与 display fallback 纳入同一快照、清理、验证和回滚事务；XEI Data Ripper resolver 仅处理明确 recipe/holder，编码前自动 key 解析还要求明确的 Data Ripper pending source。IDEA 定向 build、compileJava/compileTestJava、codec JUnit 和完整 runGameTestServer 已通过；GameTest 为 356/356。
3. 当前工作区已实现 P8-B JEI typed handler：原生与 Universal 菜单精确注册，只读 currentRecipeView，不读 IRecipeSlotsView；在发请求前验证 source/identity 异常、真实 slot filter 和 maxAmount，并返回可见 JEI user error。可选 ae2jeiintegration 的直接类型引用隔离在独立类中，只在 mod id 已加载时调用，不使用反射。IDEA/Gradle 编译及定向 JUnit 7/7 已通过。
4. 当前工作区已实现 P8-C EMI handler/core/guard/mixin：仅在 EMI FILL_BUTTON 上下文接管 Data Energistics typed live recipe；具体 EmiEncodePatternHandler 上的 supportsRecipe 修复覆盖父类 catch-all，使 AE2 handler 对该 recipe defer，其他 recipe 保持原行为。拒绝路径有日志，失败不委托 AE2 原生 handler。IDEA/Gradle 编译、Spotless 定向检查和定向 JUnit 7/7 已通过。
5. P8-A/B/C 尚未按功能拆分提交、推送或更新 Draft PR；统一 Spotless、全量集成编译/测试、diff/BOM/FQN/反射/Objects 审计和最终 diff review 仍是提交前门槛。
6. client GameTest 在启动阶段因 Oritech 的 Athena 运行时依赖缺失而阻塞。禁止修改依赖来规避该外部环境问题，也不得把服务端 GameTest 或 JUnit 通过等同于客户端 JEI/EMI 渲染和按钮交互验收；外部运行环境修复后必须补跑。

继续实施阶段

阶段 A：先审阅 b5724470 后完整工作区，保留并理解并行变更；完成统一 Spotless 和编译，修复而不覆盖 P8-A/B/C。
阶段 B：验证 P8 common/network 的成功、stale、未知 controller/structure/variant/tier/repeat/candidate、错误 registeredRecipeId、真实容量、slot filter、amount 超限、前向故障、endBatch 故障、source/key/fluid/display 清理与回滚、完整与不完整回滚、stale container、错误菜单和有界失败日志。
阶段 C：验证 JEI 与 EMI 都在点击瞬间读取最新 currentRecipeView，81/82 边界一致，显示层不改变材料，不触碰 encodedPatternSlot、不搬物料，失败给出可见错误且菜单零变更；JEI 可选集成在 mod 缺失时不加载其类型，EMI 只响应 FILL_BUTTON 且 AE2 catch-all 正确 defer。
阶段 D：按 P8-A common/network、P8-B JEI、P8-C EMI、文档/验收分别拆分小提交；逐提交审阅 staged diff，再推送现有分支并更新 Draft PR #99。
阶段 E：执行 P9 清理和发布验收。扫描全部生产 UI 注册确认多方块与舱室没有旧入口；验证四窗、XEI 页面、资源释放、恶意请求、多人同步和 AE 最终编码行为。Athena 环境修复前明确保留客户端验收阻塞，不伪报完成。

非目标

1. 不新增 AE 专属多方块 RecipeType、蓝图 item、custom pattern details 或自动编码按钮。
2. 不从样板恢复结构身份或 BlockState，不把预览选择序列化进样板。
3. 不让 transfer 执行自动搭建、世界写入、库存搬运或样板编码。
4. 不把 JEI/EMI 特有类型、LDLib2 UI 或 AE2 依赖加入 MDLib。
5. REI 不是首轮交付，但 common 模型和 factory 必须保留薄适配边界。
6. 不为绕过 Athena 客户端启动问题修改生产依赖。

验收标准

1. 所有多方块和舱室生产入口均使用 LDLib2；main/cpu/crafting/auto_build 四窗可独立打开、拖动、置顶、关闭和重开，双端 membership 与 sync/RPC ID 一致，资源 exactly-once 释放。
2. JEI/EMI 对同一 controller 和 selection 展示相同结构、逻辑层、材料和 owner output；切换 structure/variant/tier/repeat/candidate 后立即更新 fingerprint 与材料，切换 visibleLayer/相机/formed 不改变配方。
3. XEI recipe role 只包含规范 INPUT 与 OUTPUT；辅助 UI 不污染 transfer。
4. P8 transfer 由服务端权威重建，使用真实目标容量，成功原子填入 Processing 配置并清空所有旧 source/key/fluid/display 临时状态，失败时配置与临时状态一起回滚；不调用 encode、不访问 encodedPatternSlot、不搬运物料。
5. 用户点击 AE 编码按钮后生成普通 Processing Pattern，内容只有普通 stack/amount；空白样板消耗、覆盖和网络规则完全沿用 AE 既有流程。
6. 自动搭建只接受独立、服务端校验的 build request，搭建时才补全精确状态；未知/越界/过期/重放/无权限/区块或库存变化/目标冲突全部零世界写入和零净消耗。
7. 无反射、无代码体完全限定名、无不必要 Objects.requireNonNull、无空 catch、无占位实现；record 与 Lombok 使用符合约束。
8. 所有必要验证通过；Athena 阻塞消除前，客户端 XEI 渲染与交互项明确标为未验收，Goal 不得宣告全部完成。

验证矩阵

1. 读取并记录 GRADLE_USER_HOME，确认解析到 E:/；所有 Gradle 命令不带 -g。
2. IDEA MCP 定向 build/inspection；Gradle compileJava compileTestJava、相关定向 JUnit、runGameTestServer、spotlessCheck 和最终 build。
3. P8 codec/handler/transaction GameTest，JEI 7/7 与 EMI 7/7 定向 JUnit；完整 runGameTestServer 不得低于当前 356/356 基线。
4. git diff --check；UTF-8 无 BOM；Java FQN、反射、Objects.requireNonNull、空 catch 和占位实现审计。
5. 客户端环境可用后补跑 client GameTest，并在 JEI、EMI、原生与 Universal Pattern Encoding Terminal 验证页面非空、旋转缩放、选择/层级、按钮、错误提示、XEI extra area 和资源释放。
6. 单人和多人验证四窗顺序、Esc、断线、菜单切换、host 替换、资源重载和恶意 payload。

提交与完成规则

主代理逐个审阅并只暂存目标文件，使用 git diff --cached 检查每个小提交；禁止 git add .。建议顺序为 P8-A common/network、P8-B JEI、P8-C EMI、文档与最终验收，每个提交执行匹配测试后再推送现有 qy/ldlib2-ui-migration 并更新 Draft PR #99。只有所有依赖工作包完成、必要自动化和人工验收通过、Athena 客户端阻塞消除或被用户明确接受为外部未完成项、且无未处理风险时，才可结束 Goal。
```
