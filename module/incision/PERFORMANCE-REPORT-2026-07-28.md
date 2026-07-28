# Incision 运行时性能测试报告

> 本文是早期 `System.nanoTime()` 单节点批量计时记录。正式的多版本、多 JVM、双 Backend
> JMH 结果见 `JMH-PERFORMANCE-REPORT-2026-07-28.md`。

测试日期：2026-07-28

## 测试结论

本轮基准测量 Incision advice 已完成织入后的单次调用成本。所有耗时均以纳秒/次表示，统计值来自
同一 JVM 进程内 11 个独立样本的批量计时。

| 场景 | 中位数（ns） | P95（ns） | 最小值（ns） | 相对基线开销（ns） |
| --- | ---: | ---: | ---: | ---: |
| 未织入基线 | 3.222 | 9.034 | 2.493 | 0.000 |
| Lead | 110.791 | 131.785 | 88.836 | 107.569 |
| Lead + Trail | 247.246 | 284.203 | 189.373 | 244.024 |
| Lead + predicate=true | 108.744 | 148.847 | 89.931 | 105.522 |
| Splice + proceed | 142.169 | 183.967 | 112.456 | 138.947 |
| Site INVOKE | 220.420 | 249.326 | 162.787 | 217.199 |

在本次环境中，单个入口 advice 的中位额外成本约为 108 ns；Lead 与 Trail 各执行一次时约为
244 ns；需要构造 resume/proceed 链的 Splice 约为 139 ns；调用点 Site INVOKE 约为 217 ns。
predicate=true 与普通 Lead 接近，说明已编译 predicate 在该稳定真值场景中没有形成可观测的额外
中位开销。该结论只适用于本次表达式、目标签名和运行环境。

## 测试环境

| 项目 | 实际值 |
| --- | --- |
| 服务端 | Paper 1.21.11 build 132 |
| Java | Zulu OpenJDK 21.0.10+7 LTS，64 位 Server VM |
| Backend | JVMTI |
| CPU | AMD Ryzen 9 9950X3D，16 核 / 32 线程 |
| JVM 内存参数 | `-Xms512M -Xmx1536M` |
| 测试插件 SHA-256 | `2D5FF3F175C64AE13D8CD7E245C6438E123560F08F3D8381D6A9FD6AF9A23919` |
| 功能回归 | 372 pass / 0 fail / 0 not-applicable |

## 测试方法

1. 每个场景执行 4 轮预热，每轮 250,000 次调用。
2. 每个场景采集 11 个样本，每个样本连续执行 500,000 次调用。
3. 使用 `System.nanoTime()` 包围整批热循环，再用批次总耗时除以固定迭代次数。
4. 所有目标方法执行相同的 `value + 1` 逻辑；结果累加至 volatile blackhole，防止 JIT 删除调用。
5. 基准在独立调度线程运行，避免阻塞服务端 tick；日志输出、排序和格式化不进入计时区间。
6. 性能测试前先运行完整 372 项功能回归，确认 advice、Backend 和 ClassLoader 状态有效。

对应实现位于 Incision-Test 的 `PerformanceRunner.kt`、`PerformanceCases.kt` 和
`PerformanceFixture.kt`，执行命令为 `incisiontest perf`。

## 结果边界

- 这是面向真实服务端织入路径的进程内微基准，不是跨进程 JMH 基准。
- 数值包含 JVM JIT、CPU 调频、调度和当前服务端后台线程带来的噪声，因此以中位数作为主要判断值。
- Backend 影响安装和重转换过程；完成织入后的调用路径主要由 Bridge、dispatcher、advice 类型和
  predicate 决定。本报告未测量类扫描、字节码生成或 retransform 的一次性安装耗时。
- 不同 Java、CPU、服务端实现、advice 数量、参数数量和 predicate 复杂度需要重新实测，不能直接
  套用本报告数值。
