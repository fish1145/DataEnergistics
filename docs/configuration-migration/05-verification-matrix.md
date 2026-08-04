# 05：验收方式

## 原则

本迁移不新增 Configuration 专项单元测试或 GameTest。字段、注解和 GUI 控件属于 Configuration 框架职责，为它们复制测试没有收益；也不使用反射、源码 `contains` 或删除证明测试。

仓库原有业务测试继续运行，只做新配置类型所需的调用适配，用来确认 Trinity、塔覆盖等既有逻辑没有回归。

## 静态审查

- 主 schema 保持 63 个业务叶字段：通用 9、Data Extractor 14、TNT/Data Nuke 14、Solar 4、Trinity Crafting 8、Trinity Dispatch 14。
- 独立规则 schema 保持两个分组、10 个原生数组。
- 所有配置类按 `api`、`client`、`io`、`migration`、`rules`、`runtime`、`schema`、`snapshot`、`validation` 分类在 `configuration` 下，根目录不平铺类。
- 生产消费者从 `DataEnergisticsConfiguration.INSTANCE` 或 `DataExtractorRulesConfiguration.INSTANCE` 读取，不再引用旧 `config` 包或 NeoForge ConfigSpec。
- 黑白名单、倍率和规则均使用原生数组；组合字符串解析只允许存在于旧 TOML/JSON 导入边界。
- 不存在 Advanced、`NoAutoSync`、自建 `WatchService`、统一实现后缀或业务反射。
- `en_us` 与 `zh_cn` 键集合相同，JSON 可解析，所有任务文件为 UTF-8 无 BOM。

## 实际启动验收

使用开发客户端完成一次真实启动：

1. 从旧 TOML 生成 `config/data_energistics/data_energistics.yaml`。
2. 从旧 JSON 或内置规则生成 `config/data_energistics/data_extractor_rules.yaml`。
3. 日志确认两份配置均由 Configuration 内置 `FileWatchManager` 注册。
4. 生成的主 YAML 包含原生黑白名单和倍率列；规则 YAML 包含 10 个原生数组，不出现 `slot=...`、分号行或 `item@count`。
5. 配置选择页、分组、数组条目和 `CraftingQuantityMode` 使用本地化文本，所有字段默认可见，不要求 Advanced 模式。
6. 启动日志不得出现配置字段创建失败、枚举 `ClassCastException`、Bootstrap 失败或候选拒绝。

内置 watcher 在退出第一个单人世界后停止的 Configuration 3.1.1 生命周期限制只记录在文档中，不为它建立专项测试或项目 watcher。

## 迁移文件审查

- 旧 TOML/JSON 不删除、不截断、不写回；v0 JSON 仅按既定规则保留字节备份。
- 有效 YAML 始终优先，二次启动不再导入旧文件。
- 主 YAML 保持 63 项，独立规则保持完整列长并能被框架重新打开。
- 临时文件、flush/fsync、严格重读和原子移动路径由实现审查确认。

## 最终门禁

- 仓库原有 `test`；
- `spotlessCheck`；
- `build`；
- 一次开发客户端启动及生成文件、日志、GUI 检查；
- 打包 jar 中的 Configuration 嵌入依赖和 NeoForge 依赖元数据检查。

不再运行与本次变更无直接收益的 Configuration 专项测试、GameTest 或重复服务器启动排列。
