# 项目结构分类说明

本文用于快速说明 `Data_Energistics` 当前仓库的目录分类、主要职责，以及基于现状识别出的整理建议与后续优化优先级。

## 1. 顶层目录概览

| 路径 | 类型 | 主要职责 |
| --- | --- | --- |
| `src/main/java` | 核心源码 | 模组主逻辑、方块/物品/方块实体、AE2 交互、客户端界面、集成与注册 |
| `src/main/resources` | 主资源 | 语言、模型、贴图、方块状态、配方、战利品表、Mixin 配置 |
| `src/generated/resources` | 生成资源 | 数据生成输出，当前已纳入 `main.resources`，以生成配方等数据资源为主 |
| `src/main/templates` | 模板资源 | `neoforge.mods.toml` 模板，由 Gradle 在构建时展开 |
| `docs/` | 文档 | 项目说明与后续架构/参考资料沉淀位置 |
| `gradle/`、`gradlew*` | 构建基础设施 | Gradle Wrapper 与构建入口 |
| `build/` | 构建产物 | 编译输出、生成资源、报告、临时文件 |
| `run/` | 本地运行目录 | NeoForge 开发环境下的实例数据、日志、存档、截图、模组等 |
| `.idea/`、`.vscode/` | IDE 配置 | 本地开发工具配置 |
| `.codex/` | 代理/辅助配置 | 本地协作或自动化工具相关配置 |

## 2. 代码目录分类

Java 主包位于 `src/main/java/com/fish_dan_/data_energistics`，当前职责划分整体清晰：

| 子目录 | 主要职责 |
| --- | --- |
| `ae2/` | AE2 相关核心能力与适配逻辑，例如 Key、总线策略、模式提供器、无线连接能力 |
| `block/` | 方块定义 |
| `blockentity/` | 方块实体与运行时状态、交互逻辑 |
| `client/` | 客户端专属内容，包括界面、渲染、颜色、控件、JEI 展示 |
| `integration/` | 外部模组兼容层，如 Jade、AE2 Wireless Terminals、Applied Flux 等 |
| `item/` | 物品定义与物品行为 |
| `menu/` | 容器菜单与服务端界面交互 |
| `mixin/` | 对外部实现的 Mixin 注入与兼容补丁 |
| `part/` | AE2 Part 定义 |
| `recipe/` | 自定义配方、配方输入、时间转换逻辑 |
| `registry/` | 各类注册入口，如方块、物品、菜单、方块实体、配方 |
| `util/` | 通用工具类与载体数据结构 |

补充说明：

- `Data_Energistics.java` 是模组主入口，承担注册与公共初始化职责。
- `Config.java` 负责配置定义。
- 当前 `ae2/` 与 `integration/` 都包含“兼容/桥接”含义，边界总体可用，但后续值得继续收敛。

## 3. 资源目录分类

人工维护资源主目录位于 `src/main/resources`，数据生成输出位于 `src/generated/resources`。

### 3.1 `assets/data_energistics`

用于模组自身资源展示：

- `blockstates/`：方块状态定义
- `lang/`：本地化文本，当前包含 `en_us`、`zh_cn`
- `models/block`：方块模型
- `models/item`：物品模型
- `models/part`：AE2 Part 模型
- `textures/block`、`textures/item`、`textures/part`：贴图资源

### 3.2 `assets/ae2`

用于复用或对接 AE2 屏幕定义与 GUI 资源：

- `screens/`：AE2 风格界面布局定义
- `textures/guis/`：界面贴图

### 3.3 `data/`

用于数据驱动内容：

- `data/data_energistics/recipe/`：本模组配方，已按 `ae2`、`crafting`、`time_shift` 分类
- `data/data_energistics/loot_table/blocks/`：方块掉落表
- `data/ae2/tags/`、`data/c/tags/`、`data/minecraft/tags/`：跨模组或原版 Tag

### 3.4 `src/generated/resources`

用于保存数据生成器产物，当前已存在并被 Gradle 通过 `main.resources` 纳入构建输入：

- `data/data_energistics/recipe/`：由数据生成流程输出的配方资源
- `.cache/`：数据生成缓存，用于增量判断

维护约定：

- 人工编辑优先发生在 `src/main/resources` 或对应数据生成代码中，不直接把生成产物当作手写资源长期维护。
- 生成资源可以作为构建输入和审阅对象，但提交前应确认其来源可复现，避免出现“只改产物、不改来源”的断层。
- 若生成资源与手写资源存在同名内容，应优先明确唯一来源，避免两边同时维护。

### 3.5 其他资源

- `data_energistics.mixins.json`：Mixin 配置入口
- `src/main/templates/META-INF/neoforge.mods.toml`：构建时展开的模组元数据模板

## 4. 当前结构特点

从目录组织上看，当前仓库已经形成了“主逻辑 + 客户端 + 集成兼容 + 数据资源”四层结构，适合继续扩展。

同时也能看到几个现状特征：

- 仓库根目录混有运行产物与开发辅助内容，例如 `build/`、`run/`。
- 根目录存在 `meteorite_logic.java`、`resonating_logic.java` 这类未纳入 `src/main/java` 的零散 Java 文件。从位置判断，它们更像实验稿、备忘逻辑或临时分析文件，而不是正式构建输入。
- `docs/reference/` 已预留，但当前文档体系仍较薄，缺少面向协作者的结构说明和约定文档。
- `build.gradle` 已声明 `src/generated/resources` 作为生成资源目录，仓库中当前也已存在生成配方与缓存；后续重点不是“是否启用”，而是明确其生成来源、审阅方式与提交流程。

## 5. 已识别的整理建议

### 高优先级

1. 明确“源码”和“运行产物”的边界。
   建议将 `build/`、`run/` 明确视为本地构建/调试输出，不作为人工维护内容来源；团队协作时避免把运行现场当成正式资料区。若需要保留运行中验证出的结论，应沉淀为文档、测试或可复现的数据生成输入，而不是引用 `run/` 下的存档、日志或截图作为唯一依据。

2. 清理或归档根目录零散源码文件。
   `meteorite_logic.java`、`resonating_logic.java` 不在标准源码路径下，建议确认是否仍有保留价值：
   - 若仍需使用，迁入 `docs/reference/` 或专门的实验目录并补充说明。
   - 若已废弃，可在团队确认后删除。

3. 补齐文档入口与测试迁移方向。
   当前新增的结构文档可作为起点，后续建议继续补充“模块说明”“资源命名约定”“兼容层约定”等文档，降低协作成本。
   对需要长期保留的运行验证，应优先迁移为可被 `runGameTestServer` 执行的已注册 GameTest，或进入合适的测试源码目录；CI 继续负责调用 `runGameTestServer`，而不是把 `run/` 目录中的临时现场当作测试资产。

### 中优先级

1. 收敛 `ae2/` 与 `integration/` 的职责边界。
   建议逐步统一规则：
   - `ae2/` 放 AE2 原生扩展能力与本模组核心抽象。
   - `integration/` 放可选模组兼容、运行时桥接、第三方适配。

2. 为客户端与服务端边界补充约定。
   当前 `client/` 已独立，但仍建议补一条团队约定：所有仅客户端可加载内容只能进入 `client/` 或客户端注册流程，避免后续扩展时引入环境侧错误。

3. 规范生成资源目录。
   `src/generated/resources` 已配置并存在内容，建议后续补充数据生成来源说明、审阅规则和提交边界。原则上应通过数据生成流程更新该目录，只有在确认来源设计之前才允许短期人工修正，并应尽快回补到生成逻辑。

### 低优先级

1. 统一部分命名风格。
   例如目录有 `blockentity/`、`block/`、`item/`，整体偏单数；后续新增目录时建议继续保持一致，不再混入复数或语义重叠目录。

2. 完善 `docs/reference/` 的用途定义。
   可以约定该目录专门放外部机制调研、原型记录、兼容性笔记，避免参考资料散落到仓库根目录。

## 6. 后续优化优先级建议

建议按以下顺序推进：

1. 先做仓库治理。
   优先确认根目录零散文件、`run/` 内容、`build/` 产物、`src/generated/resources` 生成产物的协作边界，减少“哪些内容算正式资产”的歧义。

2. 再做包结构治理。
   重点梳理 `ae2/`、`integration/`、`mixin/` 的分工，形成可持续扩展的兼容层结构。

3. 最后补自动化与文档化。
   在结构趋稳后，再逐步引入更完整的数据生成流程、模块说明和命名规范，收益更高。

## 7. 建议的协作约定

- 新增正式代码优先进入 `src/main/java` 的既有分类目录，不在仓库根目录放临时 Java 文件。
- 新增资源优先遵守现有 `assets/data_energistics` 与 `data/data_energistics` 的分类方式。
- 新增或更新生成资源时，优先通过数据生成流程落到 `src/generated/resources`，并同步审阅生成来源；不要把生成产物与手写资源混作同一维护入口。
- 需要长期保留的服务端验证，应迁移为可被 `runGameTestServer` 执行的 GameTest 或其他正式测试；`run/` 下内容只作为本地调试现场，不作为协作交付边界。
- 临时研究材料、外部机制分析、兼容性验证结论，优先沉淀到 `docs/`，不要散落在顶层目录。
