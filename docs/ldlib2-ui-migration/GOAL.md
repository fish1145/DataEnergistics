# 可直接使用的 Goal 文本

以下代码块可直接作为新的持久 Codex Goal。它包含需求、已完成实现、当前 Git 锚点、剩余工作、验收和禁止项。上下文压缩后必须先重新完整读取本目录全部 Markdown，再按“当前状态锚点”继续；不得根据旧上下文重做 P0-P8。

```text
目标

在 F:/mc/Fish_Dan_/DataEnergistics 的 qy/ldlib2-ui-migration 分支和现有 Draft PR #99 中，完成并发布 Data Energistics 全部多方块 UI 与全部舱室 UI 向 LDLib2 的迁移，以及通用 JEI/EMI XEI 多方块预览、可配置子结构投影和普通多方块配方到 AE Processing 配置的 typed transfer。P0-P8 的生产实现已经完成并按功能提交，后续不得退回规划或重复实现；客户端测试运行依赖和 DataE 自身 EMI 注册告警也已收口，当前重点是文档、最终质量审计、推送/PR CI 以及尚未完成的真实客户端 UI 与多人验收。

发生上下文压缩或任务转交时，先完整阅读 F:/mc/Fish_Dan_/DataEnergistics/docs/ldlib2-ui-migration 下全部 11 份 Markdown，以 00-需求基线与上下文恢复.md 和本 Goal 为权威入口；随后读取当前分支、HEAD、远端和工作区，不依赖记忆补全状态。

参考仓库与 API 基线

1. 并行参考 F:/1.21.1/NeoECOAEExtension 的 LDLib2 UI/XEI factory、F:/mc/ExtremelyFrozen/fork/GregTech-Modern 的多方块 XEI 页面，以及 F:/mc/ldlib/LDLib2 的 Scene、XEI 和生命周期 API。
2. Data Energistics 当前权威 LDLib2 版本是 2.2.28；依赖下限、源码导航、生命周期、sync/RPC、XEI 与 mixin 结论均以 2.2.28 为准。ECO 的旧版本只能作设计参考。
3. 优先使用 IntelliJ IDEA MCP 做源码导航、inspection、重构和定向 build；终端用于 Git、Gradle 和 IDEA MCP 不支持的审计。不得用反射解决 AE2、LDLib2 或测试可达性。
4. Modular Data Lib 的中立投影/paired-candidate API 位于隔离 worktree F:/mc/Fish_Dan_/Modular-Data-lib-preview-api、分支 qy/multiblock-preview-api、Draft PR #2。原始 F:/mc/Fish_Dan_/Modular-Data-lib 含用户改动，仅只读。除非先证明正常公共入口仍有新的中立 API 缺口，否则不得扩大 MDLib 范围。

不可变功能契约

1. Trinity Data Core、Trinity Pattern Core、自动搭建，以及 INPUT、OUTPUT、ME_INPUT、ME_OUTPUT、PATTERN_BUFFER 全部生产 UI 使用 LDLib2。继续复用现有菜单、Slot、AE fake-slot、库存、权限、容量、退款、匹配和自动搭建业务，不在 UI 复制权威逻辑。
2. Trinity host 固定提供 main、cpu、crafting、auto_build 四个可同时打开、可拖动、可置顶、可独立关闭和重开的非模态子 UI。auto_build 是第四个独立子 UI，不是结构页签。每窗拥有独立位置、z-order、元素树、StructurePreviewSession、Scene/资源和释放周期；Esc 每次只关闭最上层窗口，关闭 removeChild 并 exactly-once 释放，重开创建新实例。
3. 四窗统一使用 292x210 chrome、196px Scene 区和 84px 状态/动作侧栏；标题长文本使用 HOVER_ROLL，关闭使用图标和 tooltip，状态区可滚动，自动搭建长动作文本固定宽度且不改变布局。launcher rail 使用 important z-index 400。首次默认位置发生碰撞时才级联；已保存位置和用户拖动位置优先，不得被级联覆盖。最小 320x240 GUI 中四窗仍须可访问且初始位置可区分。
4. client/server 菜单按确定顺序构造同构 sync/RPC 拓扑；始终挂载的 root coordinator 处理服务端权威 open/close/reopen、sequence、拒绝和乱序。客户端不得乐观移除窗口，禁止用 ECO 式预挂载隐藏窗规避动态 ID。
5. 通用模型明确区分 controller、稳定 structureKey、结构内 variantIndex、每个子结构独立 tier/repeat/candidate selection、definition revision、projectionFingerprint，以及只影响显示的 visibleLayer/相机/hover/formed。不可变快照包含坐标、BlockState、predicate/候选、材料、bounds 和逻辑层；显示层与 repeat 永不共用状态。
6. 共用 LDLib2 预览 UI 支持 Scene 旋转、缩放和选中方块，通用多子结构横向 scroller（按钮最小宽度 64px），variant/tier/repeat/candidate，全部/上一层/下一层/指定层，以及规范材料 scroller。控件不得硬编码 Trinity 三项，host 与 XEI 只共享纯模型/factory，不共享元素树、Scene 或视图状态。
7. JEI 使用 ModularUIRecipeCategory，EMI 使用 ModularUIEMIRecipe；两者复用同一 composition、候选栏、规范 input 与 owner output。动态选择后 JEI 合并延迟刷新请求并调用公开 showRecipes 重建页面/formal slots，接受导航历史增加；runtime stop 必须释放 category uiCache 并允许同一插件实例重新注册。EMI 的 getInputs/getOutputs 返回 live ingredients，slot pool 扩容后延迟 focusRecipe 刷新。JEI 公共 API 没有原位 formal-slot invalidation，禁止反射或调用私有实现绕过。
8. 多方块以普通 XEI 配方表达：当前展开投影聚合的 ItemStack/amount 为 INPUT，controller/owner 为 OUTPUT。Scene、候选检查器、谓词详情、hover、层级和 formed 控件均为 recipe role NONE。canonical registeredRecipeId 保持 controller 级稳定，动态选择使用完整 projectionFingerprint 校验；EMI 对合成 recipe 的 `getId()` 使用 slash-prefixed synthetic id，但 typed source、JEI 和服务端请求继续使用 canonical id，二者不得混用。
9. JEI/EMI transfer 在点击瞬间只读取一次 session.currentRecipeView()，不信任展示缓存或客户端材料。服务端按当前 catalog、revision、selection 和 fingerprint 权威重建普通 input/output，再使用目标终端真实 ConfigInventory.size()、isAllowedIn() 和 getMaxAmount() 预检。
10. transfer 只原子填入 AE Pattern Encoding Terminal 的 Processing 配置并切换 Processing 模式；不得调用 encode、写 encodedPatternSlot、消耗空白样板或搬运玩家/网络物料。只有用户随后点击 AE 自带编码按钮，才由现有流程生成普通 Processing Pattern。
11. AE 样板必须保持通用，只保存普通 stack/amount。structureKey、variantIndex、tier/repeat、candidate selection、visibleLayer、方向、镜像和精确 BlockState 不得作为 metadata/custom pattern details 写入样板。只有实际自动搭建时，独立且经服务端校验的 build request 才携带结构与搭建参数，并从权威定义自动补全放置状态；不得从 Processing Pattern 反推结构。
12. transfer 把 mode、input/output 与 pending/last source、pending key input/output、pending fluid input/output、key display fallback 纳入同一快照、清理、读回验证和回滚事务。81 input 成功，82 input 或实际容量更小必须在菜单零变更下失败；前向写入、模式切换、状态清理、batch publication 或 endBatch 失败均精确回滚。不完整回滚使菜单失效/关闭，并保留 primary failure 与 suppressed rollback failure。
13. Data Ripper resolver 只接受明确的 DataRipperReassemblerRecipe 或其 RecipeHolder；编码前自动 key 解析还要求 pending source 明确指向 Data Ripper。普通多方块配方不得继承历史 source/key/fluid/display 状态。
14. 舱室是各自独立的容器屏幕，不属于 Trinity 可拖动四窗。INPUT/OUTPUT 保持 7 行、fake fluid/key、容量门控和受保护升级槽；ME_INPUT 保持 25 对配置/缓冲槽；ME_OUTPUT 保持 36 个只读槽；PATTERN_BUFFER 保持 pattern、聚合、催化剂和 fake slot 语义。标题、状态、玩家背包标签和升级侧栏不得改变底层菜单协议。
15. 服务端 GameTest/JUnit 不能替代真实客户端验收。客户端 JEI/EMI 页面、Scene、Taffy 最终 bounds、根节点外命中、tooltip、拖拽、z-order、XEI extra area、Shift-click、fake slot 和多人交互仍需实际运行验证。

代码质量约束

1. 所有源码和文档 UTF-8 无 BOM；Java 通过 import 使用类型，禁止代码体完全限定名。
2. 遵循 Fail Fast；异常记录足够上下文，不允许空 catch。核心业务须在边界捕获并使菜单/session 失效，避免未控制地崩溃进程。
3. 本任务新增或直接修改范围内不得滥用 Objects.requireNonNull；只在公开构造不变量、延迟 supplier 或必要过滤边界保留。
4. Lombok 只在 getter/setter 等确实减少机械样板时使用；不得为使用 Lombok 改写清晰领域模型。适合不可变数据载体和结构化值的 record 必须保留。
5. 逻辑层优先依赖接口；新增公共接口/成员说明动机和职责。禁止占位实现、超级 Service/Manager/Controller、反射测试、源码 contain 测试和仅证明删除的测试。
6. 直接修改目标文件，不创建临时 `.patch` 文件；不得用磁盘 patch 作为拆分暂存或跨步骤传递手段。
7. 长任务在关键阶段报告整体完成百分比和持久 Goal 累计 token 用量；用户询问进度时先给当前数据，再继续执行。

Gradle 与 Git 约束

1. 每次 Gradle 前重新读取系统 GRADLE_USER_HOME；它必须已设置且解析到 E:/ 盘。当前机器值是 E:/.gradle，但不得依赖历史值。禁止 -g，禁止创建项目内、用户目录或其他盘符的替代 Gradle 缓存，也不得修改构建脚本或 IDE 设置覆盖环境变量。
2. Data Energistics 当前分支为 qy/ldlib2-ui-migration，Draft PR 为 https://github.com/fish1145/DataEnergistics/pull/99。保留未跟踪 logs/、wenli/ 和所有非任务改动，逐文件暂存，禁止 git add .。
3. DataE 与 MDLib 是独立 Git 事务；只有确认新的 MDLib 中立 API 缺陷时才在隔离 worktree 单独修改、测试、提交、推送和更新 PR #2。不得把 LDLib2、JEI/EMI、AE2、Trinity 业务或样板编码放入 MDLib。
4. Git 事务由主代理处理；提交遵循仓库既有中文风格，并按 common/network、JEI、EMI、通用 XEI、host UI、舱室、本地化、文档等功能边界拆分。

当前状态锚点（2026-07-15；实际 Git/PR 优先）

1. 分支为 qy/ldlib2-ui-migration。当前发布批次以 d546ae7c 为基线，最后一个 UI 实现锚点为 b5780774，两者之间有以下 8 个提交；其后还有 24f35593 文档提交、39b37fe6 客户端 GameTest 运行依赖提交和 1b332d0e EMI 多方块注册修复提交，本 Goal 所在的后续文档提交位于这些提交之后。恢复时必须重新读取 HEAD、tracking ref 和 PR #99，不能把发布前基线当成当前远端：
   36620546 完善 LDLib2 主机浮窗层级交互
   e1027e0a 重构多方块预览交互布局
   186bff09 重构通用 XEI 多方块预览布局
   d14ee1ed 同步刷新 JEI 与 EMI 多方块原料
   69ca9992 避免 LDLib2 子窗口初始重叠
   956f2034 统一 Trinity 四窗 LDLib2 布局
   cd7577ad 优化舱室 LDLib2 信息布局
   b5780774 补充 LDLib2 UI 本地化文本
2. P8 common/network、JEI 和 EMI typed transfer 已在 d546ae7c 之前按多个生产/测试小提交完成；不得再把 P8 描述为工作区待拆分。LDLib2 已由 cfa49f8d/d546ae7c 升级并适配至 2.2.28。
3. 最新代码已通过 IDEA MCP 定向 build/inspection，以及使用系统 GRADLE_USER_HOME=E:/.gradle 的 `spotlessCheck compileJava compileTestJava test runGameTestServer build --no-daemon`；required GameTest 为 367/367，Gradle `BUILD SUCCESSFUL`。
4. 本地质量审计已再次通过：`git diff --check`；1732 个 tracked 文本严格 UTF-8、无 BOM；文档相对链接 13/13 有效；语言 JSON 可解析且英文键均有中文对应；`d546ae7c..HEAD` 的 47 个 Java 文件无 FQN、反射、`Objects.requireNonNull`、空 catch、TODO/FIXME、占位或源码 contain 测试，全仓 `Objects.requireNonNull` 为 0。恢复时只需检查文档提交、推送和 PR #99 CI 是否已完成，不得重复修改已验证代码。
5. 39b37fe6 已新增隔离 `clientTest` source set，并只在 `clientTestRuntimeOnly` 加入 Athena；生产 runtime 和服务端 test runtime 均不含 Athena。可复现 clientTest run 已发现 Athena 4.0.6、LDLib2 2.2.28、Oritech 1.2.8，进入 `Test` 世界并完成 JEI/EMI reload。1b332d0e 修复 EMI synthetic recipe id 与 category 翻译，后续 bake 未再报告对应两条 DataE 告警。当前 run 没有客户端测试执行/通过汇总，也没有真实打开 JEI/EMI 页面、四窗、舱室或执行 transfer/多人交互，因此这些验收仍未完成。若当前 IDEA Gradle model 仍把 `Game Tests (Client)` 指向 `data_energistics.test`，先执行 Reload All Gradle Projects，并确认客户端模块变为 `data_energistics.clientTest`、服务端模块保持 `data_energistics.test`。

继续执行顺序

阶段 A（已完成）：审阅本目录全部文档和当前 diff，固化 LDLib2 2.2.28、367/367、P8 已提交、8 个 UI 实现提交、JEI/EMI 动态刷新、四窗级联、Trinity/舱室布局、隔离的客户端运行依赖和 EMI 注册修复。
阶段 B（已完成）：确认 GRADLE_USER_HOME 位于 E:/，执行 `./gradlew.bat spotlessCheck compileJava compileTestJava test runGameTestServer build --no-daemon`，不使用 -g；结果为 BUILD SUCCESSFUL 和 367/367。
阶段 C（已完成）：执行 diff、JSON、UTF-8/BOM、Java FQN/反射/Objects/空 catch/占位/源码 contain 测试和旧视觉入口审计；保留的 Screen 已确认只是 AE2 输入协议薄壳。
阶段 D：先读取实际 Git/PR 状态。若本 Goal 所在的 11 份文档尚未提交，则逐文件暂存并检查 staged diff，使用独立中文文档提交；若分支尚未推送，则确认 logs/、wenli/ 未暂存后推送 qy/ldlib2-ui-migration，更新/检查 Draft PR #99 和 CI，禁止强推。已经完成的子步骤不得重复。
阶段 E：在现已可启动的真实客户端分别打开并验证 JEI/EMI 页面、原生与 Universal Pattern Encoding Terminal、四窗、舱室和多人场景。启动、进入世界和插件 reload 只能作为环境前置证据；没有页面操作、渲染/bounds 检查和双端记录时不得标记通过。

客户端验收矩阵

1. JEI/EMI 对同一 controller/selection 展示相同 Scene、子结构、variant/tier/repeat/candidate、逻辑层、材料数量和 owner output；横向子结构控件在长标题/多结构下不溢出。
2. 切换会改变投影的选择后 ingredient 和 transfer 立即更新；visibleLayer、相机、hover、formed 不改变材料。JEI 合并延迟刷新后通过 showRecipes 重建页面/formal slots，接受导航历史增加；runtime stop/start 不复用已释放 uiCache。EMI live inputs/outputs 与扩容后的 formal slots 一致。
3. transfer 成功只填 Processing 配置并清空旧临时状态；失败零菜单变更；用户点击 AE 编码按钮后生成内容仅为普通 stack/amount 的 Processing Pattern。
4. 四窗可同时打开、独立拖动/置顶/关闭/重开；最小 GUI 初始级联可访问，保存/拖动位置不被覆盖；Esc 按 z-order 逐窗关闭；断线、菜单替换和资源重载 exactly-once 释放。
5. 舱室最终像素 bounds、升级侧栏根外渲染/命中、标题 hover-roll、tooltip、Shift-click、拖拽、fake slot、容量升级和只读槽在单人/多人均保持 AE2 语义。
6. 自动搭建只在明确确认时发一次服务端请求；未知/越界/过期/重放/无权限/区块未加载/库存变化/目标冲突均零世界写入、零净消耗且无残留 session，具体 BlockState 只在搭建时补全。

非目标

1. 不新增 AE 专属多方块 RecipeType、蓝图 item、custom pattern details 或自动编码按钮。
2. 不从样板恢复结构身份或 BlockState，不把预览选择序列化进样板。
3. 不让 transfer 执行自动搭建、世界写入、库存搬运或样板编码。
4. 不把 JEI/EMI、LDLib2 UI、AE2 或 DataE 业务依赖加入 MDLib。
5. REI 不是首轮交付，但 common 模型和 factory 必须保持薄适配边界。
6. Athena 只允许存在于隔离的 `clientTest` runtime；不加入生产依赖、发布产物或服务端测试 runtime。

完成规则

只有代码、文档、最终自动化和质量审计通过，分支已推送且 PR #99 CI 无未处理失败，并且真实客户端/多人验收完成，或用户明确接受剩余客户端/多人验收未执行时，才可结束 Goal。预算接近耗尽、服务端测试通过、可进入世界、插件 reload 或文档完成都不能单独作为完成条件。
```
