# Incision API v2 运行时测试报告

测试日期：2026-07-28

本报告只记录实际执行结果。`NOT_APPLICABLE` 独立统计，不计入通过；Adyeshach NMS helper 在
26.1/26.2 自身仍引用已不存在的旧 NMS 类型，因此该一项标记为不适用，Incision 自身 NMS/remap
用例仍在这两个节点执行并通过。

| 服务端 | Java | Backend | 结果 |
| --- | --- | --- | --- |
| Paper 1.12.2 build 1620 | Zulu 8 | JVMTI | 370 pass / 0 fail / 2 N/A |
| Paper 1.16.5 build 794 | Zulu 16.0.2 | JVMTI | 372 pass / 0 fail / 0 N/A |
| Paper 1.20.6 build 151 | Azul 21.0.10 | Instrumentation | 372 pass / 0 fail / 0 N/A |
| Paper 1.21.11 build 132 | Azul 21.0.10 | JVMTI | 372 pass / 0 fail / 0 N/A |
| Spigot 26.1.2 | Zulu 25.0.2 | JVMTI | 371 pass / 0 fail / 1 N/A |
| Paper 26.2 build 84 stable | Zulu 26.0.0 | JVMTI | 371 pass / 0 fail / 1 N/A |

额外后端验证：Paper 1.20.6 使用 `-Djdk.attach.allowAttachSelf=true`、
`-XX:+EnableDynamicAgentLoading` 和 `-Dtaboolib.incision.backend=instrumentation` 强制走标准
Instrumentation，完整 372 项通过。默认能力选择在 attach 不可用时回退 JVMTI。

Backend 合约验证随 372 项矩阵在每个节点执行，覆盖 `UNAVAILABLE`、`PENDING_LOAD`、
`INSTALLED`、`FAILED_ROLLED_BACK`、transformer 异常回滚、Pipeline token 生命周期和
ClassLoaderHook 能力边界。`ClassLoaderHookBackend` 仅保留为旧二进制引用的兼容占位并明确返回
不可用；没有 agent/JVMTI 时，JVM 不提供替换任意既有 ClassLoader 定义流程的可移植能力。

关键集成结果：

- Adyeshach `DefaultAdyeshachAPI#getMinecraftAPI()` 的 Lead/Trail 真调用通过。
- NMSProxy `DefaultMinecraftHelper#literalChatBaseComponent(String)` 的 Lead/Trail/Splice 通过。
- helper 内部 CraftChatMessage INVOKE Site 在支持节点通过。
- 1.16.5 同名 helper 同时存在于 PluginClassLoader 与 AsmClassLoader；JVMTI 按名对两个 defining
  Loader 都执行重转换后，测试由零命中恢复为一次命中。
- 运行日志未出现未处理的 `VerifyError`、`zip file closed`、Bridge dispatch failure 或未回滚的
  retransform failure。

构建验证：

- `:module:incision:publishToMavenLocal -PdevLocal --no-daemon`
- Incision-Test `clean build --no-daemon`
