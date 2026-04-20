package taboolib.module.incision.api

import taboolib.module.incision.diagnostic.Trauma

/**
 * 手术现场上下文 — 暴露给 advice handler 的唯一入口。
 *
 * 涵盖目标方法的实参、自身实例、目标方法坐标，以及对原指令的放行控制。
 *
 * ## 字段与方法访问
 *
 * 推荐使用类级 Lambda 工厂（[field] / [staticField] / [method] 等顶层函数），
 * 解析一次、多次复用、泛型参数化、JIT 友好。
 *
 * 此接口上的 `field` / `staticField` / `setField` / `invoke` 等 default 方法
 * 适用于一次性、不值得声明 val 的场景。
 *
 * **注意**：修改 `static final` 原始类型或 String 字段可能不会对已 JIT 过的调用点生效（常量折叠）。
 * 实例 final 字段不受影响。
 */
interface Theatre {

    /** 目标方法坐标 */
    val target: MethodCoordinate

    /** 调用方实例（静态方法时为 null） */
    val self: Any?

    /** 实参数组（可读写；直接修改会反映到放行的调用） */
    val args: Array<Any?>

    /** Resume 句柄；@Lead/@Trail 中调用会被忽略并标记为非法 */
    val resume: Resume

    /**
     * 强制覆盖返回值并终止后续 advice 与原方法。
     * 等价于 `resume.skip(value)`。
     */
    fun override(value: Any?): Any? = resume.skip(value)

    /** 当前是否处于异常出口（@Trail 中可读） */
    val throwable: Throwable?

    // ---- 字段读取 ----

    /** 读 self 上的实例字段 */
    fun <T> field(name: String): T? {
        val s = self ?: throw Trauma.Accessor.StaticOnInstance(name)
        return IncisionAccessor.fieldGet(s, s.javaClass, name)
    }

    /** 读 self 上的实例字段，指定声明类 */
    fun <T> field(ownerClass: Class<*>, name: String): T? {
        val s = self ?: throw Trauma.Accessor.StaticOnInstance(name)
        return IncisionAccessor.fieldGet(s, ownerClass, name)
    }

    /** 读任意实例的字段 */
    fun <T> field(receiver: Any, ownerClass: Class<*>, name: String): T? =
        IncisionAccessor.fieldGet(receiver, ownerClass, name)

    /** 读 static 字段 */
    fun <T> staticField(ownerClass: Class<*>, name: String): T? =
        IncisionAccessor.staticFieldGet(ownerClass, name)

    // ---- 字段写入 ----

    /** 写 self 上的实例字段 */
    fun setField(name: String, value: Any?) {
        val s = self ?: throw Trauma.Accessor.StaticOnInstance(name)
        IncisionAccessor.fieldSet(s, s.javaClass, name, value)
    }

    /** 写 self 上的实例字段，指定声明类 */
    fun setField(ownerClass: Class<*>, name: String, value: Any?) {
        val s = self ?: throw Trauma.Accessor.StaticOnInstance(name)
        IncisionAccessor.fieldSet(s, ownerClass, name, value)
    }

    /** 写任意实例的字段 */
    fun setField(receiver: Any, ownerClass: Class<*>, name: String, value: Any?) =
        IncisionAccessor.fieldSet(receiver, ownerClass, name, value)

    /** 写 static 字段 */
    fun setStaticField(ownerClass: Class<*>, name: String, value: Any?) =
        IncisionAccessor.staticFieldSet(ownerClass, name, value)

    // ---- 方法调用 ----

    /** 调用 self 上的实例方法 */
    fun <T> invoke(name: String, vararg args: Any?): T? {
        val s = self ?: throw Trauma.Accessor.StaticOnInstance(name)
        return IncisionAccessor.invoke(s, s.javaClass, name, null, arrayOf(*args))
    }

    /** 调用任意实例的方法 */
    fun <T> invoke(receiver: Any, ownerClass: Class<*>, name: String, descriptor: String? = null, vararg args: Any?): T? =
        IncisionAccessor.invoke(receiver, ownerClass, name, descriptor, arrayOf(*args))

    /** 调用 static 方法 */
    fun <T> invokeStatic(ownerClass: Class<*>, name: String, vararg args: Any?): T? =
        IncisionAccessor.invoke(null, ownerClass, name, null, arrayOf(*args))

    // ---- 参数便捷访问 ----

    /** 取第 [index] 个参数并强转为 [T]；越界或类型不匹配返回 null */
    @Suppress("UNCHECKED_CAST")
    fun <T> arg(index: Int): T? = args.getOrNull(index) as? T

    /** 取第 [index] 个参数并强转为 [T]；失败抛 ClassCastException / IndexOutOfBoundsException */
    @Suppress("UNCHECKED_CAST")
    fun <T> argOrThrow(index: Int): T = args[index] as T

    /** 安全转型：self 转为 [T]，失败返回 null */
    @Suppress("UNCHECKED_CAST")
    fun <T> selfAs(): T? = self as? T
}

// ============================================================
//  顶层扩展 — 任何地方都能用，不受 Theatre receiver 作用域限制
// ============================================================

/** 安全转型：失败返回 null */
inline fun <reified T> Any?.cast(): T? = this as? T

/** 强制转型：失败抛 ClassCastException */
inline fun <reified T> Any?.castOrThrow(): T = this as T

/** 在任意对象上读字段（自动沿继承链解析声明类，绕过访问控制） */
fun <T> Any.readField(name: String): T? =
    IncisionAccessor.fieldGet(this, this.javaClass, name)

/** 在任意对象上写字段（自动沿继承链解析声明类，绕过访问控制） */
fun Any.writeField(name: String, value: Any?) =
    IncisionAccessor.fieldSet(this, this.javaClass, name, value)

/** 在任意对象上调用方法（自动沿继承链解析，按参数类型匹配，绕过访问控制） */
fun <T> Any.callMethod(name: String, vararg args: Any?): T? =
    IncisionAccessor.invoke(this, this.javaClass, name, null, arrayOf(*args))
