# 批次 04：本地化与重载加固

## 本地化

- en_us 与 zh_cn 覆盖配置标题、分组和 63 个字段，键集合完全相同。
- 所有 schema 项使用 `LocalizationKey.FULL`。
- YAML literal 注释按英文、中文顺序成对写入。
- `CraftingQuantityMode` 使用客户端显示适配器，磁盘值保持稳定 enum 名。
- `tntConfigurable.displayName` 不进入 schema；显示名使用 `block.data_energistics.tnt_configurable`。

## Holder 指纹桥接

服务器主线程每 tick：

1. 比较 Holder 指纹；
2. 变化后在 Holder 锁内复制完整候选；
3. 严格解析当前 YAML 的缺失、重复、未知字段、单字段和跨字段约束；
4. 再次确认 Holder 指纹未变化；
5. 核对 YAML 与 Holder saved/pending 值一致；
6. 原子发布单个根快照。

无效 YAML、框架自动纠正或并发变化都不发布，消费者保留上一份有效值。修复文件后等待下一次内置 `FileWatchManager` 事件。

## watcher 生命周期

不添加 `NoAutoSync`，不创建项目 `WatchService`。自动化与人工测试固定覆盖：

- 第一个服务器生命周期内热重载；
- 退出第一个单人世界后内置 watcher 永久关闭；
- 同一客户端进程第二个世界不再热重载；
- 重启客户端后恢复。

只记录并公开这一 Configuration 3.1.1 限制，不使用 Mixin 修复。

## 错误诊断

所有失败包含文件、完整路径、数组索引、实际值和原因。运行期候选失败不使核心 tick 崩溃，也不得悄悄改写磁盘或发布默认值。

## GUI 与编码验收

- English 与简体中文各检查一次界面；
- 标题、分组、63 个字段、Advanced、Slider 和 enum 无原始 key；
- 两个 planner 字段明确显示 GAME_RESTART；
- 保存后 YAML 顺序、双语注释和 Unicode 可重读；
- UTF-8 无 BOM；
- 客户端连接专用服务器无同步或客户端类加载错误。

## 门禁

语言键集合、双语注释、枚举显示、严格候选校验、原子发布和 watcher 两世界生命周期全部通过；规则 JSON 的 v1 行为由独立文档与批次 01 测试保持。
