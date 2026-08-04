# 批次 05：原子切换、旧链清理与发布

## 目标

在 63 个目标字段、64 个旧输入、独立规则 YAML、本地化和生命周期全部通过后，启用最终 Bootstrap，使 Configuration 根快照成为唯一主配置来源。

## 原子切换顺序

1. 预校验或迁移主 YAML。
2. 注册 Configuration 并核对 Holder。
3. 发布首份不可变根快照。
4. 预校验 Data Extractor 规则 YAML，缺失时导入旧 JSON v0/v1，再注册独立 Configuration Holder 并从框架实例发布规则。
5. 创建服务器启动期资源。
6. 才允许业务消费者运行。

规则失败不得留下部分规则；主配置失败不得启动旧 spec 作为兜底。

## 清理

- 停止注册六份旧 COMMON `ModConfigSpec`。
- 删除旧 `ModConfigEvent` 复制链和无消费者静态入口。
- 删除过渡期 `LegacyConfigBridge`。
- 删除历史 `displayName` 消费链；无其他消费者后删除 `ConfigurableTntBlockItem`。
- 保留 TOML 导入模型和迁移夹具所需的明确类型。
- 不删除用户磁盘上的 TOML，也不顺带移除仍有用途的依赖。

实现类继续使用职责型名称，不以统一实现后缀命名。

## 调用点与行为审计

- 生产代码只依赖领域接口或不可变快照，不直接访问 schema/Holder。
- 通用 41 项和 Trinity 22 项分别回归。
- 旧 `displayName` 和 `mipTimeoutMs` 仅存在于迁移识别层。
- Data Extractor 规则在启动加载，并支持内置 watcher 驱动的严格原子实时重载。
- watcher、GAME_RESTART 与各领域混合生效策略和实际行为一致。
- 并发消费者只能观察到旧或新完整修订。

## 文件状态

- 全新安装生成 63 项主 YAML 和原生数组规则 YAML。
- 上一版六份 TOML 的 64 项输入正确迁移。
- 已迁移安装不重新读取 TOML 或 v0 备份。
- malformed YAML 在框架注册前失败。
- 损坏规则 YAML 或 future/损坏旧 JSON 精确失败。
- 客户端与专用服务器均能启动、停止和二次启动。

## 发布说明

必须说明主/规则 YAML 路径、旧 JSON 与六份旧 TOML 映射、YAML 优先级、旧文件不删除、特殊字段转换、规则 v0 备份、两个线程池字段需重启，以及单人世界 watcher 的已知进程生命周期。

## 最终门禁

- `test`；
- `spotlessCheck`；
- `build`；
- 一次开发客户端启动；
- 打包依赖和 NeoForge 元数据；
- 英文/简体中文 GUI；
- 一次升级迁移、生成文件重读与有效二次启动。

任何失败都阻止提交本批次结果为可发布状态。未经授权不推送。
