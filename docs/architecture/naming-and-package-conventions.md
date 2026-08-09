# 命名与包结构约定

本文约束 Data Energistics 仓库内部代码的归类和命名。它不把内部包转化为外部 API；外部兼容边界见[API 版本与稳定性](../api/versioning-and-stability.md)。

## 按职责分包

同一上层目录存在多个可辨认职责时，应建立语义子包，不把大量类平铺在一个目录中。包名描述业务职责或技术边界，例如：

- `api`：明确支持的外部契约；
- `common`：客户端与服务端共享或服务端运行的内部业务编排；
- `client`：只允许客户端加载的 screen、render 和 UI 适配；
- `integration`：可选模组兼容；
- `registry`：Data Energistics 自身的注册持有者；
- 功能包下继续按 `model`、`runtime`、`callback`、`binding`、`discovery` 等实际职责细分。

内部类应直接迁移到正确包；不要为了保留内部 FQCN 建兼容 wrapper。外部模组本来就不得依赖内部 FQCN。

## 类名表达实际含义

不要仅把接口名加上 `Impl` 作为实现类名。类名应说明它采用的策略、数据来源、生命周期或宿主，例如 `MountedCorePatternCatalog`、`PlayerInventoryRefundDelivery`、`VirtualNodePatternTerminalPartition`。

只有 `Impl` 本身确实提供辨识价值且没有更准确的业务名称时才考虑保留；默认应选择能从类名判断职责的名称。接口仍可用于隔离契约，但实现类不应把“实现了某接口”当作唯一身份。

同样避免新建容易持续膨胀的 `XxxService`、`XxxManager`、`XxxController`。把解析、路由、存储、计算等职责拆成可独立命名的逻辑单元。

## Data Energistics 注册类前缀

Data Energistics 自己持有的注册项和注册入口使用 `DE` 前缀，例如 `DEItems`、`DEBlocks`、`DEDataComponents`、`DEMenus`。这能把“属于本模组的注册项”与外部模组常见的 `ModItems` 区分开。

`Mod` 前缀可以保留给职责本身就是“判断或描述模组加载状态”的类型，例如 `ModFlags`。判断标准是类的业务含义，不是机械替换所有包含 `Mod` 的名称。

## Nullability 与基础类型

- 当一个包的大多数类型默认非空时，在 `package-info.java` 使用包级 `@NotNullByDefault`。
- 只在确实允许空值的边界保留 `@Nullable`；未知的外部回调返回值可在验证边界标记 `@UnknownNullability`。
- 不为已由非空契约保证的内部参数重复堆叠 `Objects.requireNonNull`。
- 外部插件回调、序列化输入、registry 查询等不受信任边界仍应校验，并给出可诊断错误。
- 没有“缺省/未知”三态语义时使用 `boolean`、`int`、`long` 等基础类型，不使用 `Boolean`、`Integer`、`Long` 包装类。
- 数值约束属于业务校验，不应因为移除无意义判空而删除；例如正数计数、非负容量和稳定 ID 非空白仍需 fail fast。

## 依赖方向

- 外部扩展只依赖 `api/**` 和文档明确列出的过渡 API。
- `api` 不依赖内部实现包。
- common 代码不得加载 client-only 类型。
- optional-mod 实现放在 `integration`，并通过 entrypoint 的 `requiredMods` 或仅使用 registry ID 的无链接声明避免缺失类加载。
- Mixin/Accessor/Bridge 是内部适配边界，不得成为跨模组协议。

## 文档组织

API 用法按功能拆分到 `docs/api` 子目录。每个文件只解释一个入口或契约，包含最小但可运行思路的示例和该契约特有的警告。不要把所有 API、迁移说明和架构原则堆进单个 README。
