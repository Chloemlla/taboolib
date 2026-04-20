package taboolib.module.incision.api

import taboolib.module.incision.diagnostic.Trauma

/**
 * Lambda 工厂 — handler 字段/方法访问的主推 API。
 *
 * 在类级别声明 accessor，handler 内直接 invoke：
 * ```kotlin
 * private val teleportOwner = field<IUser>("teleportOwner")
 * private val moveConstant  = staticField<Int>(AsyncTimedTeleport::class.java, "MOVE_CONSTANT")
 * private val setRespawn    = fieldSet<Boolean>(AsyncTimedTeleport::class.java, "timer_respawn")
 * private val doCheck       = method<Boolean>("checkPermission")
 *
 * @Surgeon(...)
 * fun cooldown(t: Theatre) {
 *     val owner = teleportOwner(t)
 *     val limit = moveConstant()
 *     setRespawn(t, true)
 *     val ok = doCheck(t, "essentials.teleport")
 * }
 * ```
 *
 * 每个 accessor 在首次调用时解析字段/方法并缓存，后续调用只做一次 volatile read + JNI/反射 dispatch。
 *
 * **注意**：修改 `static final` 原始类型或 String 字段可能不会对已 JIT 过的调用点生效（常量折叠）。
 * 实例 final 字段不受影响。
 */

// ============================================================
//  顶层工厂函数
// ============================================================

/** 读 self 上的实例字段（ownerClass 从 self.javaClass 继承链自动解析） */
fun <T> field(name: String): FieldAccessor<T> = FieldAccessor(null, name)

/** 读实例字段，指定声明类（解决 private 字段在父类的场景） */
fun <T> field(ownerClass: Class<*>, name: String): FieldAccessor<T> = FieldAccessor(ownerClass, name)

/** 读 static 字段 */
fun <T> staticField(ownerClass: Class<*>, name: String): StaticFieldAccessor<T> = StaticFieldAccessor(ownerClass, name)

/** 写 self 上的实例字段 */
fun <T> fieldSet(name: String): FieldSetter<T> = FieldSetter(null, name)

/** 写实例字段，指定声明类 */
fun <T> fieldSet(ownerClass: Class<*>, name: String): FieldSetter<T> = FieldSetter(ownerClass, name)

/** 写 static 字段 */
fun <T> staticFieldSet(ownerClass: Class<*>, name: String): StaticFieldSetter<T> = StaticFieldSetter(ownerClass, name)

/** 调用 self 上的实例方法（descriptor 可选，省略则按 args 类型匹配） */
fun <T> method(name: String, descriptor: String? = null): MethodAccessor<T> = MethodAccessor(null, name, descriptor)

/** 调用实例方法，指定声明类 */
fun <T> method(ownerClass: Class<*>, name: String, descriptor: String? = null): MethodAccessor<T> = MethodAccessor(ownerClass, name, descriptor)

/** 调用 static 方法 */
fun <T> staticMethod(ownerClass: Class<*>, name: String, descriptor: String? = null): StaticMethodAccessor<T> = StaticMethodAccessor(ownerClass, name, descriptor)

// ============================================================
//  Accessor 类族
// ============================================================

class FieldAccessor<T>(private val ownerClass: Class<*>?, private val name: String) {

    @Volatile private var resolved: IncisionAccessor.ResolvedField? = null

    @Suppress("UNCHECKED_CAST")
    operator fun invoke(theatre: Theatre): T? {
        val self = theatre.self ?: throw Trauma.Accessor.StaticOnInstance(name)
        val r = resolved ?: IncisionAccessor.resolveField(ownerClass ?: self.javaClass, name).also { resolved = it }
        return IncisionAccessor.fieldGet(self, r)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun invoke(receiver: Any): T? {
        val r = resolved ?: IncisionAccessor.resolveField(ownerClass ?: receiver.javaClass, name).also { resolved = it }
        return IncisionAccessor.fieldGet(receiver, r)
    }
}

class FieldSetter<T>(private val ownerClass: Class<*>?, private val name: String) {

    @Volatile private var resolved: IncisionAccessor.ResolvedField? = null

    operator fun invoke(theatre: Theatre, value: T?) {
        val self = theatre.self ?: throw Trauma.Accessor.StaticOnInstance(name)
        val r = resolved ?: IncisionAccessor.resolveField(ownerClass ?: self.javaClass, name).also { resolved = it }
        IncisionAccessor.fieldSet(self, r, value)
    }

    operator fun invoke(receiver: Any, value: T?) {
        val r = resolved ?: IncisionAccessor.resolveField(ownerClass ?: receiver.javaClass, name).also { resolved = it }
        IncisionAccessor.fieldSet(receiver, r, value)
    }
}

class StaticFieldAccessor<T>(private val ownerClass: Class<*>, private val name: String) {

    @Volatile private var resolved: IncisionAccessor.ResolvedField? = null

    @Suppress("UNCHECKED_CAST")
    operator fun invoke(): T? {
        val r = resolved ?: IncisionAccessor.resolveField(ownerClass, name).also { resolved = it }
        return IncisionAccessor.staticFieldGet(r)
    }

    operator fun invoke(@Suppress("UNUSED_PARAMETER") theatre: Theatre): T? = invoke()
}

class StaticFieldSetter<T>(private val ownerClass: Class<*>, private val name: String) {

    @Volatile private var resolved: IncisionAccessor.ResolvedField? = null

    operator fun invoke(value: T?) {
        val r = resolved ?: IncisionAccessor.resolveField(ownerClass, name).also { resolved = it }
        IncisionAccessor.staticFieldSet(r, value)
    }

    operator fun invoke(@Suppress("UNUSED_PARAMETER") theatre: Theatre, value: T?) = invoke(value)
}

class MethodAccessor<T>(private val ownerClass: Class<*>?, private val name: String, private val descriptor: String?) {

    @Volatile private var resolved: IncisionAccessor.ResolvedMethod? = null

    @Suppress("UNCHECKED_CAST")
    operator fun invoke(theatre: Theatre, vararg args: Any?): T? {
        val self = theatre.self ?: throw Trauma.Accessor.StaticOnInstance(name)
        val a = arrayOf(*args)
        val r = resolved
            ?: IncisionAccessor.resolveMethod(ownerClass ?: self.javaClass, name, descriptor, a).also { resolved = it }
        return IncisionAccessor.invokeResolved(self, r, a)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun invoke(receiver: Any, vararg args: Any?): T? {
        val a = arrayOf(*args)
        val r = resolved
            ?: IncisionAccessor.resolveMethod(ownerClass ?: receiver.javaClass, name, descriptor, a).also { resolved = it }
        return IncisionAccessor.invokeResolved(receiver, r, a)
    }
}

class StaticMethodAccessor<T>(private val ownerClass: Class<*>, private val name: String, private val descriptor: String?) {

    @Volatile private var resolved: IncisionAccessor.ResolvedMethod? = null

    @Suppress("UNCHECKED_CAST")
    operator fun invoke(vararg args: Any?): T? {
        val a = arrayOf(*args)
        val r = resolved
            ?: IncisionAccessor.resolveMethod(ownerClass, name, descriptor, a).also { resolved = it }
        return IncisionAccessor.invokeResolved(null, r, a)
    }

    operator fun invoke(@Suppress("UNUSED_PARAMETER") theatre: Theatre, vararg args: Any?): T? = invoke(*args)
}
