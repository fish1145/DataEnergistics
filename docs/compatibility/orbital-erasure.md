# 轨道实体抹除

轨道攻击将抹除定义为通过授权后的生命或遭遇终止。它不调用 `hurt()`，不使用无限伤害，也不通过耗尽护盾或删除装备达到终止效果。动能、定向能和数位湮灭体共用 `OrbitalEntityErasure`，部件命中先通过 NeoForge `PartEntity.getParent()` 归一为真实主体，再检查冻结的授权豁免、创造/旁观模式与管理员权限。

## 玩家当前生命

`LivingEntity`、`ServerPlayer` 和死亡通知入口采用两条分支：非抹除调用继续原有处理链；已经被本次抹除占有的生命由本模组执行受控逻辑。底层来源保持 `player.damageSources().genericKill()`。

受控玩家死亡主体与本项目 NeoForge 21.1.249 / Minecraft 1.21.1 的结算步骤对应：

1. 进入当前实体对象与执行 UUID 对应的上下文，清零吸收和生命。
2. 记录战斗来源并触发死亡 game event，原 NeoForge 死亡事件仍调用一次，抹除分支不采用其取消结果。
3. 执行死亡通知、队伍死亡消息规则、肩上实体释放、中立生物仇恨处理与掉落流程。
4. 保留 `keepInventory`、消失诅咒、经验与最终 `LivingDropsEvent` 的正常规则。掉落取消与生命终止的决定互相独立。
5. 完成死亡计分、统计、状态清理与最后死亡位置后，才记录 `DEATH_COMPLETED`。已记录发射者且其在线时，击杀结算归属该发射者。

`OrbitalErasureContext` 仅存在于当前同步调用链，使用具体目标对象匹配，不使用全局布尔开关。上下文始终退出，但完成后的 `OrbitalErasureState` 仍附着于旧实体，阻止其正向 `setHealth()`、`heal()` 以及重复死亡结算。新玩家对象通过正常重生交接创建，附件不复制，并在 `restoreFrom` 返回后显式清除新对象上的本模组状态。

生命附件没有序列化器和 `copyOnDeath`。它不修改玩家账号、装备组件或第三方持久化数据，也不取消玩家整个 Tick。参见 [NeoForge 1.21.1 附件生命周期](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/)。

## 打击去重与保存

`OrbitalErasureStrike` 记录本次打击 UUID、可用时的发射者 UUID、主体 UUID、执行 UUID 和真实结果。主体在进入回调前即被占用，从而阻止同一次扫描多 Tick 命中、多个部件重复命中，以及同次打击再次处理重生后的同 UUID 玩家。独立的新打击拥有独立记录。

该记录属于 `OrbitalAttackSavedData` 中的当前攻击，覆盖其载荷后续工作；攻击记录结束后释放，不形成永久 UUID 黑名单。旧存档没有记录时按旧攻击兼容读取。保存中途的 `IN_PROGRESS` 在恢复时视为部分完成，不自动重放潜在死亡副作用。损坏的记录仅使对应攻击进入 `FAULTED`，保留 escrow，禁用无法证明安全的重试。

## 失败边界

| 阶段 | 结果与清理 |
|---|---|
| 未进入死亡副作用 | 记录失败，退出上下文、释放锁；只恢复本模组已确认执行且仍保持该值的临时生命/吸收写入。 |
| 已进入死亡副作用 | 记录部分失败；不重新调用完整死亡主体，不回滚整个背包或血量，释放恢复锁。 |
| 死亡主体完成 | 退出调用上下文，旧生命保留终止锁，直到正常新生命交接。 |

一次返回的 `die()` 调用或零血值均不足以证明完成。未知实体子类如果绕过受控入口提前返回，会被记录为失败。可隔离的运行时异常和兼容链接错误按目标记录；即使错误继续向外传播，失败执行也在 `finally` 中释放其拥有的锁。

## Draconic Evolution

本机代码与运行验证针对 **Draconic Evolution 1.21.1-3.1.4.633**、**BrandonsCore 1.21.1-3.2.1.309**。

- 龙研将 `DEDamage.KILL` 关联到原版 `GENERIC_KILL`，死亡监听在进入免死模块前放行此来源，不需要专门清空护盾或模块充能。
- 混沌龙优先进入 `DraconicGuardianErasureAdapter`，调用自身 `kill()` 一次。该入口已经执行 `guardianUpdate()` 和 `processDragonDeath()`；适配器不再重复调用结算。
- 正常结算结束 Boss 条、维持区块票据和世界遭遇对象，设置岛心击败状态并生成一颗龙心。此入口不运行通常的死亡动画，因此不额外补发动画流程中的 24,000 XP。
- 实体侧遭遇关联丢失时，只查找相同维度且守护者 UUID 完全匹配的唯一活跃遭遇。多个匹配时明确失败，不按位置猜测归属。
- 遭遇自身的持久 UUID 另行去重；已经结束的旧遭遇不再次产出奖励。相同位置未来创建的新遭遇不受永久限制。
- 结算部分失败时，只调用资源释放入口清理 Boss 条、票据和世界实体注册，不重放进度和奖励步骤，并保留部分失败结果。

龙研仍是可选运行依赖；本次只为已有本地运行坐标补充编译期可见性。

## AE2 Lightning Tech 可选保护入口

死亡执行由原版受控分支负责。LT 的独立死亡事件和玩家 Tick 还可能先支付保命资源，再尝试恢复旧生命，因此补充一个针对普通业务类 `CelestweaveArmorUndyingHandler` 的可选入口屏蔽。它不修改 LT 自身的 Mixin 类，也不接管 Thunderbolt Core。

已核对 [LT 2.1.0-beta.5 发布源码](https://github.com/ae2lt/AE2-Lightning-Tech/blob/v2.1.0-beta.5/src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorUndyingHandler.java) 中的五个入口：`tryProtectForcedDeath`、`protectBeforeDeathSideEffect`、`wasProtectedThisTick`、`tryProtectWithinWindow`、`tryTrigger`。只有参数指向被锁定的旧生命时提前返回 `false`，其他目标继续 LT 原有逻辑，不清理其全局配置或装备数据。

LT 注入使用 `@Pseudo`、精确方法描述符以及 `require=0, expect=0`。类或方法缺失时不把兼容缺口升级为启动失败；目标类存在但缺少入口时，Mixin 插件根据已提供的字节码节点记录一次缺失方法提示。不存在的入口意味着这部分 LT 保护未被覆盖，不能据此声称完整兼容未知版本。

按本次要求，未进行 LT 联调，也没有为这类可选注入保留专门测试。官方包的加载限制不在本次修改范围内。
