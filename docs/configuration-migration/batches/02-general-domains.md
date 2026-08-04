# 批次 02：通用与机器领域消费者切换

## 范围

切换 41 个目标 YAML 字段：

- 通用 9；
- Data Extractor 14；
- Flattening TNT / Data Nuke 14；
- Solar 4。

Trinity 的 22 项留在批次 03。规则 YAML 继续独立，由批次 01 注册为单独的 Configuration 配置，并在运行期通过内置 watcher 实时更新。

## 领域读取边界

为 Data Ripper、Tower、Sanctum、Data Extractor、TNT/Data Nuke 和 Solar 定义窄接口。`DataEnergisticsConfiguration.INSTANCE` 指向同一不可变根快照并提供这些领域视图；业务代码不得读取可变框架实例或 `INTERNAL_INSTANCE`。

集中转换：

- Data Ripper regex 和 multiplier 预编译；
- Data Extractor 黑白名单数组、mapping 数组和 double → float 可表示性；
- fillBlock 的词法及注册表存在性；
- 所有派生集合一次构造为不可变值。

非法候选不回退到默认值，也不替换上一份有效快照。

## 生效语义

| 领域 | 策略 |
| --- | --- |
| Data Ripper | 修订号变化使目标缓存失效 |
| Tower、Sanctum | 下一次读取使用新快照 |
| Data Extractor | 新工作使用新值；已持久化 requiredAmount 不追溯 |
| Flattening TNT | 每次爆炸开始固定完整快照 |
| 活动 Data Nuke | 每服务器 tick 读取最新完整快照 |
| Solar 发电 | 新快照即时生效 |
| Solar 容量 | 机器加载或卡片变化时重算 |

## 切换顺序

每个领域依次完成全部调用点搜索、接口替换与旧静态入口消费检查。一个领域切换后不得回退到 legacy-backed 值，旧 spec 仅留给尚未切换的领域。

## 验收门禁

- 现有业务测试只适配新的只读 Settings 类型，不增加配置字段测试；
- 静态调用点审查确认 41 项消费者均从同一实例读取；
- 客户端生成文件确认 regex、倍率与 ID 黑白名单为原生数组；
- 不直接修改静态字段，不使用反射。

门禁：41 项消费者全部通过领域边界读取，规则 YAML 行为未被破坏，Trinity 未被顺带重构。
