# Incision Scope 到 Pointcut 迁移指南

本文用于把 Incision 注解中旧的单行 `scope` 字符串迁移为结构化 `Pointcut`。迁移目标不是立即删除 `scope`，而是让已有简单声明继续工作，同时让组合筛选、NMS 映射和命中数量约束具有明确语义。

## 迁移结论

| 旧写法 | 当前状态 | 建议 |
|---|---|---|
| `类#方法(参数)返回值` | 兼容 | 已有代码可暂时保留，新代码使用 Pointcut |
| `method:类#方法(*)` | 兼容 | 尽快改成 METHOD + GLOB |
| `class:... & method:...` | 不保证完整语义 | 必须迁移到 `allOf` |
| `A | B` | 不保证完整语义 | 必须迁移到 `anyOf` |
| `!A` | 不保证完整语义 | 必须迁移到 `noneOf`，并提供正向边界 |
| `field:...` 参与宿主筛选 | 不保证完整语义 | 必须迁移到 FIELD Selector |

兼容优先级固定为：

```text
scope > method 别名 > pointcut
```

如果同一个 advice 同时声明非空 `scope` 和非空 `pointcut`，Incision 会输出警告、采用 `scope` 并忽略 `pointcut`。迁移时不要长期保留两份声明；应在确认 Pointcut 行为后删除旧 `scope`。

## 基本类型

```kotlin
import taboolib.module.incision.annotation.MatchMode
import taboolib.module.incision.annotation.Pointcut
import taboolib.module.incision.annotation.Selector
import taboolib.module.incision.annotation.SelectorKind
```

`Selector` 字段含义：

| 字段 | 含义 |
|---|---|
| `kind` | `CLASS`、`METHOD` 或 `FIELD` |
| `owner` | JVM internal name，例如 `com/example/Target` |
| `name` | 方法名或字段名；CLASS 可留空 |
| `descriptor` | JVM descriptor，例如 `(Ljava/lang/String;)I` |
| `matchMode` | `EXACT` 或 `GLOB` |

Pointcut 的组合语义固定为：

```text
allOf 全部命中
&& (anyOf 为空或至少一个命中)
&& noneOf 全部不命中
```

`minMatches` 和 `maxMatches` 约束最终选中的宿主方法数量，默认都是 `1`。零命中或超过上限时，Incision 会拒绝注册，而不是静默安装一个不确定的织入计划。

## 单方法迁移

旧写法：

```kotlin
@Lead(scope = "com.example.Target#greet(java.lang.String)java.lang.String")
fun before(theatre: Theatre) = Unit
```

Pointcut 写法：

```kotlin
@Lead(
    pointcut = Pointcut(
        anyOf = [
            Selector(
                kind = SelectorKind.METHOD,
                owner = "com/example/Target",
                name = "greet",
                descriptor = "(Ljava/lang/String;)Ljava/lang/String;",
            )
        ]
    )
)
fun before(theatre: Theatre) = Unit
```

旧 `scope` 允许 Java 风格参数名；结构化 Selector 的 `descriptor` 必须使用 JVM descriptor。常用类型如下：

| Java/Kotlin 类型 | JVM descriptor |
|---|---|
| `void` / `Unit` | `V` |
| `boolean` | `Z` |
| `int` | `I` |
| `long` | `J` |
| `String` | `Ljava/lang/String;` |
| `int[]` | `[I` |
| `String[]` | `[Ljava/lang/String;` |

## 通配迁移

旧写法：

```kotlin
@Lead(scope = "method:com.example.Target#handle(*)")
fun before(theatre: Theatre) = Unit
```

Pointcut 写法：

```kotlin
@Lead(
    pointcut = Pointcut(
        anyOf = [
            Selector(
                kind = SelectorKind.METHOD,
                owner = "com/example/Target",
                name = "handle",
                descriptor = "(*)*",
                matchMode = MatchMode.GLOB,
            )
        ],
        minMatches = 1,
        maxMatches = 8,
    )
)
fun before(theatre: Theatre) = Unit
```

`GLOB` 对 `owner`、`name` 和 `descriptor` 使用统一的 `*`、`?` 规则。通配声明通常可能命中多个重载，因此必须根据预期调整 `maxMatches`；不要为了消除错误直接设置一个无限大的上限。

## AND、OR、NOT 迁移

### AND

旧写法：

```kotlin
@Trail(scope = "class:com.example.* & method:com.example.Target#save(*)")
```

Pointcut 写法：

```kotlin
@Trail(
    pointcut = Pointcut(
        allOf = [
            Selector(
                kind = SelectorKind.CLASS,
                owner = "com/example/*",
                matchMode = MatchMode.GLOB,
            ),
            Selector(
                kind = SelectorKind.METHOD,
                owner = "com/example/Target",
                name = "save",
                descriptor = "(*)*",
                matchMode = MatchMode.GLOB,
            ),
        ],
        minMatches = 1,
        maxMatches = 4,
    )
)
```

### OR

旧写法：

```kotlin
@Lead(scope = "method:com.example.Target#load()V | method:com.example.Target#reload()V")
```

Pointcut 写法：

```kotlin
@Lead(
    pointcut = Pointcut(
        anyOf = [
            Selector(SelectorKind.METHOD, "com/example/Target", "load", "()V"),
            Selector(SelectorKind.METHOD, "com/example/Target", "reload", "()V"),
        ],
        minMatches = 1,
        maxMatches = 2,
    )
)
```

### NOT

旧写法：

```kotlin
@Lead(scope = "class:com.example.Target & !method:com.example.Target#debug(*)")
```

Pointcut 写法：

```kotlin
@Lead(
    pointcut = Pointcut(
        allOf = [
            Selector(SelectorKind.CLASS, "com/example/Target")
        ],
        noneOf = [
            Selector(
                kind = SelectorKind.METHOD,
                owner = "com/example/Target",
                name = "debug",
                descriptor = "(*)*",
                matchMode = MatchMode.GLOB,
            )
        ],
        minMatches = 1,
        maxMatches = 32,
    )
)
```

`noneOf` 不能单独建立无限候选集合。Pointcut 必须至少具有一个正向 CLASS 或 METHOD 边界，避免对整个 JVM 类空间求补集。

## FIELD 宿主筛选

FIELD Selector 不是选择字段本身作为 advice 宿主，而是从正向 CLASS/METHOD 候选中筛选“包含对应字段访问”的方法。

```kotlin
@Lead(
    pointcut = Pointcut(
        allOf = [
            Selector(
                kind = SelectorKind.CLASS,
                owner = "com/example/Target",
            ),
            Selector(
                kind = SelectorKind.FIELD,
                owner = "com/example/State",
                name = "enabled",
                descriptor = "Z",
            ),
        ],
        minMatches = 1,
        maxMatches = 4,
    )
)
fun beforeFieldAccess(theatre: Theatre) = Unit
```

FIELD Selector 必须和正向 CLASS 或 METHOD Selector 一起使用，否则 Incision 无法建立有限的宿主方法边界。

## Graft、Bypass 与 Site

宿主方法使用 Pointcut，方法内部锚点使用结构化 `Site.target`。两者使用相同的 Selector 与自动 remap 协议。

```kotlin
@Graft(
    pointcut = Pointcut(
        anyOf = [
            Selector(
                kind = SelectorKind.METHOD,
                owner = "com/example/Target",
                name = "execute",
                descriptor = "()V",
            )
        ]
    ),
    site = Site(
        anchor = Anchor.INVOKE,
        target = Selector(
            kind = SelectorKind.METHOD,
            owner = "com/example/Dependency",
            name = "run",
            descriptor = "()V",
        ),
        ordinal = 0,
        minMatches = 1,
        maxMatches = 1,
    )
)
fun beforeInvoke(theatre: Theatre) = Unit
```

Site 目标与锚点的对应关系：

| `Anchor` | `Site.target.kind` |
|---|---|
| `INVOKE` | `METHOD` |
| `FIELD_GET` / `FIELD_PUT` | `FIELD` |
| `NEW` | `CLASS` |
| `HEAD` / `TAIL` / `RETURN` / `THROW` | 默认 `NONE` |

`ordinal = 0` 表示第一个命中，`ordinal = -1` 才表示全部命中。Site 的 `minMatches/maxMatches` 约束过滤后的实际锚点数量。

## NMS 坐标迁移

Selector 不需要也不提供 `Namespace`。调用者可以使用任意一个受支持版本的 Mojang、Spigot 或旧版本号包名声明逻辑坐标，Incision 会沿用 NMSProxy 的映射策略，把下列内容统一转换成当前服务端的运行时坐标：

- 宿主 `owner`
- 方法名和字段名
- descriptor 中的所有对象和数组类型
- Site 的 INVOKE、FIELD 和 NEW 目标

示例：

```kotlin
@Lead(
    pointcut = Pointcut(
        anyOf = [
            Selector(
                kind = SelectorKind.METHOD,
                owner = "net/minecraft/server/MinecraftServer",
                name = "getPlayerCount",
                descriptor = "()I",
            )
        ]
    )
)
fun beforePlayerCount(theatre: Theatre) = Unit
```

不要根据运行服务端手工切换 `RUNTIME/MOJANG/SPIGOT`，也不要把 CraftBukkit relocated 名称硬编码进多套分支。非 NMS 坐标会原样进入运行时解析。

## 推荐迁移步骤

1. 先记录旧 advice 预期命中的类、方法和数量。
2. 把 Java 风格类型转换成 JVM internal name 与 descriptor。
3. 简单方法改为一个 METHOD Selector。
4. `&`、`|`、`!` 分别改为 `allOf`、`anyOf`、`noneOf`。
5. 为 `noneOf` 和 FIELD 筛选补充正向 CLASS/METHOD 边界。
6. 根据重载和通配范围填写 `minMatches/maxMatches`。
7. Graft、Bypass、Trim 继续检查 Site 的 `ordinal` 和命中数量。
8. 删除旧 `scope`，避免优先级规则让新 Pointcut 被忽略。
9. 在所有目标 Minecraft/JVM/Backend 组合上运行对应织入用例。

## 上线检查清单

- 日志中没有“同时声明 scope/method 与 pointcut”的警告。
- 日志中没有零命中、过量命中或拒绝注册诊断。
- 通配方法的 `maxMatches` 与真实重载数量一致。
- Site 默认只命中第一个位置；需要全部命中时明确填写 `ordinal = -1`。
- NMS Selector 没有按服务端版本写多套分支。
- 插件卸载、重新加载后每个 advice 仍只执行一次。
- 目标 Minecraft 版本对应的 Instrumentation/JVMTI Backend 均完成实际运行测试。

## 当前兼容测试范围

Incision-Test 已在 Paper 1.12.2、1.16.5、1.20.6、1.21.11、26.2 和 Spigot 26.1.2 上验证 `method:类#方法(*)` 单行 Scope 可以命中目标。纳秒级性能测试也使用了无前缀的 `类#方法(参数)返回值`，覆盖 Lead、Trail、Splice 和带 Site 的 Graft。

这些结果只能证明简单单方法 Scope 的兼容性，不能证明旧组合 DSL 仍具有完整语义。带 `class:`、`field:`、`&`、`|`、`!` 的声明应按本文迁移为 Pointcut。
