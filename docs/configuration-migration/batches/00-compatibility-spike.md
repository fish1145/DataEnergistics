# 批次 00：Configuration 3.1.1 能力探针

## 目标

在修改生产配置链前，以可执行探针固定框架行为和文档基线。依赖坐标已锁定为：

`dev.toma.configuration:configuration-1.21.1:3.1.1-neoforge`

4.x 不适用于 Minecraft 1.21.1。本批次确认而不重新选择版本。

## 冻结清单

- 64 个旧 TOML 输入；
- 63 个目标 YAML 字段；
- Crafting 8 项，Dispatch 14 项；
- 基线 `displayName`、额外历史 `mipTimeoutMs`，以及旧文件缺少 `safeRetryBackoffTicks` 时补 8 的转换；
- `maxBindingVariants` 的 TOML 512 → 32768 特例；
- Data Extractor 原生规则 YAML 与旧 JSON v0/v1 导入协议。

## 探针矩阵

| 实验 | 固定结论与通过标准 |
| --- | --- |
| 依赖打包 | `jarJar(api(...))` 后开发环境与打包 jar 均能发现框架 |
| Mod 元数据 | 必需 `configuration [3.1.1,4)`、`AFTER`、`BOTH` |
| 文件路径 | 精确生成 `config/data_energistics/data_energistics.yaml` |
| 嵌套 schema | 三层 YAML 路径及 GUI 分组稳定，叶字段恰好 63 |
| FULL key | 根、分组、字段无碰撞，en_us/zh_cn 可一一对应 |
| literal comment | 英文、中文顺序固定，UTF-8 无 BOM |
| enum | 磁盘保持 enum name；客户端显示适配器能本地化选项 |
| restart restriction | 两个 planner 资源字段使用 GAME_RESTART |
| Holder 锁 | 能在锁内复制完整 saved/pending 候选并读取稳定指纹 |
| watcher | 主 YAML 与规则 YAML Holder 均自动加入 `FileWatchManager`；不添加 `NoAutoSync` |
| 无效 YAML | 项目预校验先于框架注册，坏文件不会被默认值覆盖 |
| 专用服务器 | schema 和加载链不触发客户端类加载 |

## watcher 生命周期证据

探针必须记录真实线程与时序：

1. watcher 后台线程在 Holder 锁内重载；
2. 第一个服务器生命周期内文件变化能更新 Holder；
3. 退出第一个单人世界会永久关闭本客户端进程的 watcher；
4. 第二个世界不再收到手工文件变化；
5. 重启客户端后恢复。

该限制作为 Configuration 3.1.1 的已知行为保留，不创建项目 `WatchService`，不使用 Mixin 修正。

## 旧文件探针

以 `ae910ee2` 在隔离 config 根目录生成六份旧 TOML，记录准确文件名、段路径、大小写、列表、enum 和极值表达。夹具必须覆盖 64 个历史输入和三个特殊迁移规则，不访问真实用户配置。

## 产物与门禁

- 可重复运行的框架集成探针；
- 依赖、元数据、路径、FULL key、Holder 锁和 watcher 生命周期证据；
- 64 → 63 字段清单与独立规则配置文档；
- 客户端、专用服务器与打包验证结果。

所有结论均通过公开 API 或真实生命周期验证，不使用反射、源码 contains 或临时项目 watcher。任一探针失败必须在批次 01 前修复。
