# Trinity CPU 合成派发 Phase 0 基线

## 1. 记录范围

本基线用于冻结架构重组前后的正确性与单次大网络派发成本，不作为 CI 性能阈值。

- 记录日期：2026-07-31
- 基线提交：`f59eb719`
- Minecraft：1.21.1
- NeoForge：21.1.216
- Applied Energistics 2：19.2.17
- JVM：Azul OpenJDK 21.0.5
- 系统：Windows 11 amd64

## 2. 256 Worker 大网络场景

GameTest：

```text
trinity_data_core_256_workers_dispatch_independent_operation_budgets
```

固定条件：

- 1 个 Trinity Data Core runtime；
- 256 个 Worker；
- 每个 Worker 拥有完整 `1 byte` 存储和 `0` 个 co-processor；
- 每个 Worker 独立持有 1 个 inputless pattern task；
- 16 个可接受相同 pattern 的 provider；
- 每 provider 物理窗口为 16 次；
- 网格物理窗口为 256 次。

正确性结果：

| 指标 | 结果 |
| --- | ---: |
| 已分配 Worker | 256 |
| logical craft | 256 |
| physical call | 256 |
| 每 provider physical call | 16 |
| 每 Worker waiting output | 1 |
| JUnit | 329 / 329 通过 |
| GameTest | 455 / 455 通过 |

本次服务器线程 `runtime.tick(...)` 单次样本：

```text
tickNanos=10002100
tickMillis=10.0021
```

阶段 2 物理窗口实现前，从 `run/gametest/server/logs` 中复核到 7 个不同的 256 Worker 样本：

| 样本指标 | `tickNanos` |
| --- | ---: |
| 最小值 | 10,002,100 |
| 中位数 | 12,426,800 |
| 最大值 | 26,108,000 |

据此，阶段 2 使用 `30,000,000 ns` 作为固定服务器提交安全预算：它高于当前样本最大值，同时为
`50 ms` 服务器 tick 留出其它系统工作的时间。该值只约束供应器准备与提交作用域，不是性能断言，也不是最终
Governor 参数；阶段 5 必须按本报告第 5 节所列矩阵重新采样并调优。

256 Worker 正确性与采样用例保留相同的 256 次网格调用上限和每 provider 16 次上限，但把测试窗口的时间额度设为
`Long.MAX_VALUE`。这样墙钟只作为日志指标，不会把 CI 机器负载变成易抖动的正确性断言；30 ms 边界由可控单调时钟
测试独立验证。

完整 GameTest 集合在游戏内报告 `47.39 s`，Gradle 进程总耗时 `97.7 s`，其中包含启动、模组加载和关服保存，不能当作派发 tick 耗时。

## 3. 已冻结的其它行为

- `1 byte + 256 Worker` 合法，每个 Worker 均获得完整存储与 co-processor；
- 第 257 个 Worker job 返回 `CPU_BUSY`；
- 256 个 Worker 操作预算对象互不共享；
- Blocking、`LOCK_UNTIL_RESULT`、`LOCK_UNTIL_PULSE` 和专用 `ICraftingMachine` 均保留 single-craft 路径；
- counted batch 继续验证输入所有权、waiting、scheduled output 和任务余量守恒；
- provider round-robin 保持惰性消费，不完整耗尽 AE2 cyclic iterable。

## 4. 重复方式

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
```

基线日志：

```text
Trinity dispatch Phase 0 baseline: workers=256, providers=16, logicalCrafts=256, physicalCalls=256, tickNanos=...
```

## 5. 解释限制

- 当前只记录单次 256 Worker 大网络样本，主要用于识别数量级回退；
- 不对墙钟时间编写易抖动断言；
- 1、16、64、256 Worker 与 target 的重复采样、平均/P95/P99、Blocking 饱和和容量变化矩阵在性能验收阶段统一执行；
- 后续阶段不得以提升吞吐为由突破 Worker 独立硬件、所有权、Blocking 或锁定语义。
