# Incision 是什么？一篇讲清 TabooLib 里的运行时“动刀”模块

如果你写 Bukkit / Paper 插件时遇到过这些事：

- 想在某个现成方法进出时插一段逻辑
- 想拦住一次 NMS / Bukkit 调用，改参数或者直接短路
- 想临时打一针诊断 patch，看完就撤
- 想读写目标对象的 private 字段，又不想把整套逻辑改成反射地狱

那 `module/incision` 基本就是给这种场景准备的。

它不是一个“大而全”的通用字节码框架，也不是把 Mixin 原样搬到 Bukkit 里来。它更像一套给 TabooLib、Paper、NMS 场景准备的手术工具：下刀点要准，生命周期要能控，出了问题要能查，必要时还得能撤。

## 先把它理解对

最容易把 Incision 想歪的地方有三个。

第一，它不是动态代理。  
它不会新建一个 `$Proxy` 类，也不是“你得先拿到代理对象，后面所有调用才会被拦”。Incision 改的是目标方法体里的字节码，所以连目标类自己内部的调用、`final` 方法、NMS 那种根本不走代理链的路径，它也能碰到。

第二，它也不是编译期 Mixin。  
Mixin 更像“程序真正启动前，先把目标类改好”；Incision 更像“类已经在 JVM 里了，我现在给它做一次运行时织入”。两者能做的事有交集，但工作的时机不一样。

第三，它不是拿来把业务逻辑全都写成 patch 的。  
这点很重要。Incision 适合切入口、出口、调用点、返回值、临时补丁。它不适合把一整层正常业务都堆进字节码改写里长期维护。能正常写接口、事件、服务层的地方，还是正常写。

## 它平时拿来干什么

最常见的几类用法其实很朴素：

- 在方法入口打前置探针
- 在方法结束时收尾、记日志、做统计
- 在某次调用发生前后插一段逻辑
- 改一次调用的参数，或者干脆把这次调用换掉
- 直接短路原方法，返回你自己的结果
- 给排障场景挂一个临时 patch，用完立刻撤
- 读写目标类的 private / final / static 字段，或者调用 private 方法

如果你是从 Mixin 体系过来的，可以先这么粗暴对照：

| 你熟悉的东西 | 在 Incision 里更接近谁 |
| --- | --- |
| `@Inject(at = @At("HEAD"))` | `@Lead` |
| `@Inject(at = @At("RETURN"))` | `@Trail` |
| “around + cancel / proceed”的思路 | `@Splice` |
| `@Redirect` | `@Bypass` |
| `@ModifyArg` / 一部分 `@ModifyVariable` | `@Trim` |
| `@Overwrite` | `@Excise` |

如果你熟悉 AccessWidener，也可以顺手记一件事：Incision 不是去改访问修饰符本身，而是给 handler 提供一套运行时访问器。所以它解决的是“我在 patch 里怎么摸到 private 成员”这个问题，而不是“把整个类永久放开”。

## 先学会最常用的写法：`@Surgeon`

如果你的 patch 是“模块启动后就该一直在”，那就别绕弯子，先上注解模式。

```kotlin
import taboolib.module.incision.annotation.Lead
import taboolib.module.incision.annotation.Operation
import taboolib.module.incision.annotation.Splice
import taboolib.module.incision.annotation.Surgeon
import taboolib.module.incision.annotation.Trail
import taboolib.module.incision.api.Theatre

@Surgeon(priority = 50)
object DemoSurgeon {

    @Lead(scope = "method:top.example.Target#greet(java.lang.String)java.lang.String")
    fun beforeGreet(theatre: Theatre) {
        println("before: ${theatre.arg<String>(0)}")
    }

    @Splice(scope = "method:top.example.Target#greet(java.lang.String)java.lang.String")
    @Operation(id = "rewrite-name", priority = 100)
    fun aroundGreet(theatre: Theatre): Any? {
        val name = theatre.arg<String>(0) ?: return theatre.resume.proceed()
        if (name == "Admin") {
            return theatre.override("blocked")
        }
        return theatre.resume.proceed(name.uppercase())
    }

    @Trail(
        scope = "method:top.example.Target#greet(java.lang.String)java.lang.String",
        onThrow = true
    )
    fun afterGreet(theatre: Theatre) {
        if (theatre.throwable != null) {
            println("throw: ${theatre.throwable?.message}")
        } else {
            println("after greet")
        }
    }
}
```

这段代码里最值得记住的不是语法，而是分工：

- `@Surgeon` 说明“这个 `object` 里放的是注解式 patch”
- `@Lead` 负责入口
- `@Trail` 负责出口
- `@Splice` 负责“我要不要继续执行原方法”这件事

实际开发里，九成长期 patch 都可以先从这三种里选。

### `@Lead`：方法刚进来时做点事

它最适合做这些：

- 看参数
- 记日志
- 做轻量前置判断
- 改一下 `theatre.args`，让后面的流程吃到新参数

别拿它做整段控制流接管。想“放不放行”，请直接用 `@Splice`。

### `@Trail`：方法准备离开时做点事

它适合：

- 正常返回前收尾
- 异常抛出前补日志
- 做统计
- 看一下这次是不是异常出口

`@Trail` 不是中段插桩，它盯的是“方法准备结束”这个时刻。

### `@Splice`：控制力最强，也最容易写出坑

`@Splice` 是 Incision 里最像“环绕通知”的东西。它的核心不是“能不能执行”，而是“你必须明确表态”。

命中 `@Splice` 后，你要么：

- `theatre.resume.proceed()`：放行
- `theatre.resume.proceed(newArgs...)`：改参后放行
- `theatre.resume.proceedResult(value)`：带着一个新结果继续往下传
- `theatre.override(value)` 或 `resume.skip(value)`：直接短路

如果什么都不干，就会触发 `ResumeMissing`。这不是温柔提示，而是明确告诉你：这段环绕逻辑没把路走完。

## 如果 patch 不是长期常驻，再用 DSL

Incision 还有一套 DSL，入口是 `Scalpel`。这套不是不能用，而是别一上来就用。

推荐的心法就一句话：

> 能写成 `@Surgeon` 的长期 patch，就别先写成 DSL。  
> 只有 patch 的生命周期本身要动态控制时，再上 DSL。

比如你要做临时诊断 patch，就很适合：

```kotlin
import taboolib.module.incision.annotation.SurgeryDesk
import taboolib.module.incision.dsl.Scalpel

@SurgeryDesk
object DemoDesk {

    fun patchOnce() {
        Scalpel.transient {
            splice("top.example.Target#greet(java.lang.String)java.lang.String") { theatre ->
                println("args = ${theatre.args.contentToString()}")
                theatre.resume.proceed()
            }
        }.use {
            // 只在这个作用域里生效
        }
    }
}
```

DSL 这套东西最实用的几种场景是：

- `transient`：打一针临时 patch，用完就回收
- `scoped`：只在某个代码块里生效
- `threadLocal`：只影响当前线程
- `armOn` / `disarmOn`：按事件启停
- `exclusive`：块内临时压住同目标上的其他 patch

所以你会发现，DSL 的重点根本不是“语法更帅”，而是“生命周期能不能动态管理”。

## Incision 不只是 before / after，它还能动中段

很多人第一次看 Incision，会以为它只是 AOP 风格的入口出口钩子。其实不是。

它还能对方法中段下刀。最常用的几把刀如下。

### `@Graft`：在某个锚点前后加一段，但原指令照跑

适合做探针、补日志、埋点、轻量联动。

```kotlin
import taboolib.module.incision.annotation.Graft
import taboolib.module.incision.annotation.Site
import taboolib.module.incision.api.Anchor
import taboolib.module.incision.api.Shift
import taboolib.module.incision.api.Theatre

@Graft(
    method = "top.example.Target#greet(java.lang.String)java.lang.String",
    site = Site(
        anchor = Anchor.INVOKE,
        target = "top.example.Logger#print(java.lang.String)void",
        shift = Shift.BEFORE
    )
)
fun beforePrint(theatre: Theatre) {
    println("logger is about to run")
}
```

这类 patch 的关键词是“追加”，不是“替换”。

### `@Bypass`：把某个调用点直接换掉

这就更像 Mixin 的 `@Redirect` 了。

```kotlin
import taboolib.module.incision.annotation.Bypass
import taboolib.module.incision.annotation.Site
import taboolib.module.incision.api.Anchor
import taboolib.module.incision.api.Theatre

@Bypass(
    method = "top.example.Target#greet(java.lang.String)java.lang.String",
    site = Site(
        anchor = Anchor.INVOKE,
        target = "top.example.Service#load(java.lang.String)java.lang.String"
    )
)
fun replaceLoad(theatre: Theatre): Any? {
    return "mocked"
}
```

原来的那次调用不会再执行，改成走你的 handler。

### `@Trim`：不接管流程，只改值

它适合这种需求：

- 改第 0 个参数
- 改返回值
- 改局部变量槽位里的值

```kotlin
import taboolib.module.incision.annotation.Trim
import taboolib.module.incision.api.Theatre

@Trim(
    method = "top.example.Target#greet(java.lang.String,int)java.lang.String",
    kind = Trim.Kind.ARG,
    index = 0
)
fun rewriteName(theatre: Theatre): Any? {
    return "patched"
}
```

这种写法很适合“我就想把值改一下，别把整段流程都接过去”。

### `@Excise`：整段方法我接管了

这个就是重型武器了。

```kotlin
import taboolib.module.incision.annotation.Excise
import taboolib.module.incision.api.Theatre

@Excise(scope = "method:top.example.Target#greet(java.lang.String)java.lang.String")
fun overwrite(theatre: Theatre): Any? {
    return "direct result"
}
```

它的语义很干脆：原方法体不跑了，全部交给你。

所以也别乱用。`Excise` 能少用就少用，原因很现实：

- 风险最大
- 和别人冲突的概率最高
- 同一 target 只允许一个 `Excise`

真要用，也尽量让目标非常明确，不要扫一大片。

## Handler 里最重要的三个东西：`Theatre`、`Resume`、`Suture`

### `Theatre`：你在现场能看到的上下文

它就是 advice 的工作台。里面常用的东西有：

- `self`：当前实例，静态方法时是 `null`
- `args`：参数数组，能读也能改
- `target`：当前命中的方法坐标
- `throwable`：异常出口时能看到异常
- `arg<T>(index)`：按类型取参数
- `selfAs<T>()`：把 `self` 安全转型

如果你只记一个词，就记 `Theatre`。因为几乎所有 handler 都是围着它转。

### `Resume`：只有 `@Splice` 最依赖它

前面提过，`Resume` 解决的是“后面到底怎么走”。

它的存在，让 `@Splice` 不只是一个 before / after 混合体，而是真正的流程控制器。

### `Suture`：这针 patch 在运行期的句柄

不管是 DSL 还是注解扫描出来的 patch，最终都会有自己的 `Suture`。

你可以拿它做这些事：

- `heal()`：彻底卸载
- `suspend()`：临时停用，但不拆字节码
- `resume()`：恢复
- 看当前状态是 `ARMED`、`SUSPENDED` 还是 `HEALED`

这点很实用。很多字节码工具只能“加上去”，撤的时候很难看；Incision 至少把启停和回收这层管理做成了正式能力。

## 它读 private 字段、调 private 方法，靠的不是“玄学”

如果你是从 Mixin + AccessWidener 的思路过来的，这里很好理解。

Incision 不会把目标类永久改成“所有东西都 public”。它的做法更像是：

- 运行期给 handler 提供一层访问器
- 优先走 JVMTI
- 不行再降级到反射 / Unsafe

所以你可以在 handler 里很自然地拿到 private 字段或方法。

最推荐的写法是类级 accessor 工厂：

```kotlin
import taboolib.module.incision.api.field
import taboolib.module.incision.api.method
import taboolib.module.incision.annotation.Lead
import taboolib.module.incision.annotation.Surgeon
import taboolib.module.incision.api.Theatre

@Surgeon
object AccessDemo {

    private val secret = field<String>("secret")
    private val doCheck = method<Boolean>("checkPermission")

    @Lead(scope = "method:top.example.Target#run()void")
    fun beforeRun(theatre: Theatre) {
        val value = secret(theatre)
        val ok = doCheck(theatre, "example.use")
        println("$value / $ok")
    }
}
```

这种写法比你在 handler 里满地 `reflect` 干净得多，缓存也更友好。

但还是提醒一句：`static final` 的原始类型和 `String` 在 JIT 场景下可能会碰到常量折叠，这种问题不该算 Incision 独有，是 JVM 本身的老毛病。

## 它为什么能在 Bukkit / NMS / 跨 ClassLoader 场景下跑起来

这部分不用一开始就抠太深，但最好知道个轮廓，不然排障时会犯懵。

Incision 能在这些场景下工作，靠的是几件事一起配合。

### 1. 注解扫描和织入会尽量前推到 `CONST`

这意味着 `@Surgeon` 不是等插件 `ENABLE` 了才慢悠悠开始扫。它会尽量在 TabooLib 可用的最早窗口把扫描、注册、物理织入做掉。

这样 `INIT`、`LOAD`、`ENABLE` 阶段的宿主逻辑也更有机会被命中。

当然，再早的东西还是拦不住，比如：

- 某些静态初始化块
- 比 `CONST` 还早的引导行为

这不是设计偷懒，是生命周期客观边界。

### 2. 字节码里写的是桥，不是你的业务 handler

Incision 不会把 handler 本体直接塞进目标字节码，而是插一个固定的桥调用，后面再进 dispatcher 分发。

这么做的好处很实际：

- patch 可以统一启停
- handler 可以集中注册、注销
- 排错入口稳定
- 不用每动一次 handler 都重新改一遍目标类

### 3. 跨 ClassLoader 问题，它是认真处理过的

Minecraft 插件环境的 ClassLoader 本来就够绕，TabooLib 还有 relocate。

所以 Incision 专门用 `IncisionBridge` 去兜这件事，避免“这个插件织进去的是 A 包名，那个插件找 dispatcher 时看到的是 B 包名”。

如果你只把它当成“一个普通 AOP 模块”，很多 NMS / Bukkit / 跨插件场景下的行为会看不懂。它其实是把这类脏活一起包进去了。

### 4. 后端不只一条路

Incision 现在会优先看 `Instrumentation`，不行再走 JVMTI native。

这也是为什么它在 Paper、JDK 新版本这种 attach 条件不稳定的环境里，依然还有一条更稳的后路。

普通使用者不一定要盯着这些实现细节看，但有个印象很重要：

> Incision 不是“只在理想 Java 环境里能跑”的玩具，它是真把 Bukkit、Paper、NMS、ClassLoader 这些现实问题算进去设计的。

## 什么时候该用，什么时候别用

### 适合用的时候

- 你要补一个现有方法的入口 / 出口逻辑
- 你要改一次调用点，而不是重写整段类
- 你要做运行期诊断 patch，而且还想方便撤回
- 你要兼容不同 NMS 版本，愿意配合 `@Version` / remap 去做筛选
- 你要在 handler 里访问目标类的 private 状态

### 不太适合的时候

- 你只是想正常扩展自己可控的业务代码
- 你准备把大量核心逻辑长期堆进 patch 里
- 你根本不知道目标字节码长什么样，就先上复杂 `InsnPattern`
- 你想用它代替完整的编译期 mixin 系统
- 你要做的是“新增字段 / 新增方法 / 改继承关系”这种 JVM retransform 本来就不擅长的事

说白了，Incision 擅长的是“精准改现有行为”，不是“把类结构改成另一套东西”。

## 上手时最容易踩的几个坑

### 1. 能用 `@Surgeon` 就别先上 DSL

这是 README 和实现里都反复强调的推荐方向。注解式 patch 更稳定，也更容易统一管理。

### 2. `@Splice` 一定要明确 `proceed` 还是 `override`

别写着写着忘了。忘了不是“默认继续”，而是直接出问题。

### 3. 先把 target 写准，再谈 `pattern`

很多人第一次写字节码工具，最爱一上来就上复杂匹配。其实更稳的顺序应该是：

1. 先把 descriptor / scope / site 写准
2. 真不够用，再加 `InsnPattern`
3. 再复杂的条件，用 `where` 做二次筛选

### 4. Kotlin companion 和 `@JvmStatic` 不是一条路

你以为自己 patch 到了一个 Kotlin “静态方法”，实际命中的可能只是一条路径。这时要记得 `@KotlinTarget`。

### 5. `@Excise` 别当常规手段

它很好用，但也最像“重写半个世界”的做法。能 `Lead`、`Trail`、`Splice` 解决的，尽量别直接 `Excise`。

## 一套比较稳的学习顺序

如果你现在第一次碰 Incision，我建议别一口气把所有注解全学完，按下面的顺序来最顺：

1. 先学 `@Surgeon`、`@Lead`、`@Trail`
2. 再学 `@Splice`，把 `Resume` 的语义搞明白
3. 再去看 `@Graft`、`@Bypass`、`@Trim`
4. 最后再碰 `@Excise`、`InsnPattern`、复杂 `Site`、`where`
5. 需要动态启停时，再去学 DSL 的 `Scalpel`

这个顺序的好处是，你会先拿到“能干活”的能力，再去接触“控制力更大但也更容易出事故”的部分。

## 最后一句

如果只用一句话概括 `module/incision`，我会这样说：

> 它不是给你“乱改代码”用的，而是给你“有边界地动刀”用的。

想改入口、出口、调用点，它很好用。想做运行时诊断、临时 patch、版本兼容，它也很对路。但真到了该上重型覆盖的时候，也别忘了先问自己一句：

这刀，真的非下不可吗？

想继续往下抠的话，下一步最值得看的不是更多介绍文，而是这三样东西：

- `README.md`：完整的用户向导和术语说明
- `TECHNICAL.md`：底层桥接、后端、织入链路
- `Incision-Test`：真实可跑的行为基线，DSL 生命周期、注解扫描、Kotlin 目标、平台版本、字节码锚点和诊断，里面都有对应案例
