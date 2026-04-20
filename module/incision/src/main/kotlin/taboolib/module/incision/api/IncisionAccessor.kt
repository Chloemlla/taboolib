package taboolib.module.incision.api

import org.objectweb.asm.Type
import taboolib.module.incision.diagnostic.Forensics
import taboolib.module.incision.diagnostic.Trauma
import taboolib.module.incision.loader.JvmtiBackend
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * 字段/方法访问的底层门面。
 *
 * 所有 handler 侧的字段读写、方法调用最终都走这里。调度优先级：
 * 1. JvmtiBackend（JNI，无视 Java 访问控制与 final）
 * 2. 反射 `setAccessible(true)`（JDK 17+ 非开放模块可能拒绝）
 * 3. `sun.misc.Unsafe`（仅对 final 字段写入使用的兜底）
 *
 * 解析结果缓存在 [fieldCache] / [methodCache]，避免热路径上反复反射查表。
 */
object IncisionAccessor {

    // ---- 对外 API ----

    fun <T> fieldGet(receiver: Any, ownerClass: Class<*>, name: String): T? {
        val r = resolveField(ownerClass, name)
        return fieldGet(receiver, r)
    }

    fun fieldSet(receiver: Any, ownerClass: Class<*>, name: String, value: Any?) {
        val r = resolveField(ownerClass, name)
        fieldSet(receiver, r, value)
    }

    fun <T> staticFieldGet(ownerClass: Class<*>, name: String): T? {
        val r = resolveField(ownerClass, name)
        return staticFieldGet(r)
    }

    fun staticFieldSet(ownerClass: Class<*>, name: String, value: Any?) {
        val r = resolveField(ownerClass, name)
        staticFieldSet(r, value)
    }

    fun <T> invoke(receiver: Any?, ownerClass: Class<*>, name: String, descriptor: String?, args: Array<Any?>): T? {
        val r = resolveMethod(ownerClass, name, descriptor, args)
        return invokeResolved(receiver, r, args)
    }

    // ---- 解析层（供 Accessors.kt 预热） ----

    @Suppress("UNCHECKED_CAST")
    fun <T> fieldGet(receiver: Any, resolved: ResolvedField): T? {
        if (JvmtiBackend.available()) {
            return JvmtiBackend.fieldGet(receiver, resolved.declaringClass, resolved.field.name, resolved.descriptor) as T?
        }
        return try {
            resolved.field.get(receiver) as T?
        } catch (t: Throwable) {
            throw denied(resolved.declaringClass, resolved.field.name, "反射读取失败", t)
        }
    }

    fun fieldSet(receiver: Any, resolved: ResolvedField, value: Any?) {
        if (JvmtiBackend.available()) {
            JvmtiBackend.fieldSet(receiver, resolved.declaringClass, resolved.field.name, resolved.descriptor, value)
            return
        }
        val f = resolved.field
        try {
            f.set(receiver, value)
            return
        } catch (t: Throwable) {
            if (Modifier.isFinal(f.modifiers)) {
                // final 字段，反射通常被拒 — 走 Unsafe
                if (UnsafeBridge.setInstanceFinal(receiver, f, value)) return
            }
            throw denied(resolved.declaringClass, f.name, "反射写入失败 (final=${Modifier.isFinal(f.modifiers)})", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> staticFieldGet(resolved: ResolvedField): T? {
        if (JvmtiBackend.available()) {
            return JvmtiBackend.staticFieldGet(resolved.declaringClass, resolved.field.name, resolved.descriptor) as T?
        }
        return try {
            resolved.field.get(null) as T?
        } catch (t: Throwable) {
            throw denied(resolved.declaringClass, resolved.field.name, "反射静态读取失败", t)
        }
    }

    fun staticFieldSet(resolved: ResolvedField, value: Any?) {
        if (JvmtiBackend.available()) {
            JvmtiBackend.staticFieldSet(resolved.declaringClass, resolved.field.name, resolved.descriptor, value)
            return
        }
        val f = resolved.field
        try {
            f.set(null, value)
            return
        } catch (t: Throwable) {
            if (Modifier.isFinal(f.modifiers)) {
                if (UnsafeBridge.setStaticFinal(f, value)) return
            }
            throw denied(resolved.declaringClass, f.name, "反射静态写入失败 (final=${Modifier.isFinal(f.modifiers)})", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> invokeResolved(receiver: Any?, resolved: ResolvedMethod, args: Array<Any?>): T? {
        if (JvmtiBackend.available()) {
            return JvmtiBackend.invokeMethod(
                receiver,
                resolved.declaringClass,
                resolved.method.name,
                resolved.descriptor,
                args as Array<Any?>?,
            ) as T?
        }
        val m = resolved.method
        return try {
            m.invoke(receiver, *args) as T?
        } catch (t: java.lang.reflect.InvocationTargetException) {
            throw t.cause ?: t
        } catch (t: Throwable) {
            throw denied(resolved.declaringClass, m.name, "反射调用失败", t)
        }
    }

    // ---- 缓存解析 ----

    fun resolveField(ownerClass: Class<*>, name: String): ResolvedField {
        val key = FieldKey(ownerClass, name)
        fieldCache[key]?.let { return it }
        var c: Class<*>? = ownerClass
        while (c != null) {
            val f = try { c.getDeclaredField(name) } catch (_: NoSuchFieldException) { null }
            if (f != null) {
                runCatching { f.isAccessible = true }
                val desc = Type.getDescriptor(f.type)
                val r = ResolvedField(f, c, desc)
                fieldCache.putIfAbsent(key, r)
                return r
            }
            c = c.superclass
        }
        throw Trauma.Accessor.FieldNotFound(ownerClass.name, name)
    }

    fun resolveMethod(ownerClass: Class<*>, name: String, descriptor: String?, args: Array<Any?>): ResolvedMethod {
        val key = MethodKey(ownerClass, name, descriptor ?: argsSignature(args))
        methodCache[key]?.let { return it }
        val candidates = collectMethods(ownerClass, name)
        if (candidates.isEmpty()) {
            throw Trauma.Accessor.MethodNotFound(ownerClass.name, name, descriptor, emptyList())
        }
        val picked = if (descriptor != null) {
            candidates.firstOrNull { Type.getMethodDescriptor(it) == descriptor }
                ?: throw Trauma.Accessor.MethodNotFound(
                    ownerClass.name, name, descriptor,
                    candidates.map { Type.getMethodDescriptor(it) },
                )
        } else {
            val matched = candidates.filter { isArgCompatible(it, args) }
            when (matched.size) {
                0 -> throw Trauma.Accessor.MethodNotFound(
                    ownerClass.name, name, null,
                    candidates.map { Type.getMethodDescriptor(it) },
                )
                1 -> matched.single()
                else -> throw Trauma.Accessor.AmbiguousMethod(
                    ownerClass.name, name, matched.map { Type.getMethodDescriptor(it) },
                )
            }
        }
        runCatching { picked.isAccessible = true }
        val r = ResolvedMethod(picked, picked.declaringClass, Type.getMethodDescriptor(picked))
        methodCache.putIfAbsent(key, r)
        return r
    }

    // ---- 内部辅助 ----

    private fun collectMethods(ownerClass: Class<*>, name: String): List<Method> {
        val out = ArrayList<Method>()
        var c: Class<*>? = ownerClass
        while (c != null) {
            c.declaredMethods.filterTo(out) { it.name == name }
            c = c.superclass
        }
        return out
    }

    private fun isArgCompatible(m: Method, args: Array<Any?>): Boolean {
        val p = m.parameterTypes
        if (p.size != args.size) return false
        for (i in p.indices) {
            val a = args[i] ?: continue
            if (!wrap(p[i]).isAssignableFrom(a.javaClass)) return false
        }
        return true
    }

    private fun wrap(c: Class<*>): Class<*> = when (c) {
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> c
    }

    private fun argsSignature(args: Array<Any?>): String = buildString {
        append('#')
        args.forEach { a -> append(a?.javaClass?.name ?: "*"); append(';') }
    }

    private fun denied(ownerClass: Class<*>, member: String, reason: String, cause: Throwable): Trauma.Accessor.AccessDenied {
        val t = Trauma.Accessor.AccessDenied(ownerClass.name, member, reason, cause)
        Forensics.debug("Accessor 降级失败: ${ownerClass.name}.$member — $reason (${cause.javaClass.simpleName}: ${cause.message})")
        return t
    }

    // ---- 数据类型 ----

    data class FieldKey(val ownerClass: Class<*>, val name: String)
    data class MethodKey(val ownerClass: Class<*>, val name: String, val descriptorOrArgSig: String)

    class ResolvedField(val field: Field, val declaringClass: Class<*>, val descriptor: String)
    class ResolvedMethod(val method: Method, val declaringClass: Class<*>, val descriptor: String)

    private val fieldCache = ConcurrentHashMap<FieldKey, ResolvedField>()
    private val methodCache = ConcurrentHashMap<MethodKey, ResolvedMethod>()
}

/**
 * Unsafe 兜底 — 仅当 JVMTI 不可用且反射写入 final 字段被拒时启用。
 *
 * 实例字段覆盖所有基元 + 对象类型；静态同理。任何一步失败直接返回 false，由上游抛 AccessDenied。
 */
private object UnsafeBridge {

    private val unsafe: Any? by lazy {
        try {
            val c = Class.forName("sun.misc.Unsafe")
            val f = c.getDeclaredField("theUnsafe")
            f.isAccessible = true
            f.get(null)
        } catch (_: Throwable) { null }
    }

    private val objectFieldOffset = method("objectFieldOffset", java.lang.reflect.Field::class.java)
    private val staticFieldBase = method("staticFieldBase", java.lang.reflect.Field::class.java)
    private val staticFieldOffset = method("staticFieldOffset", java.lang.reflect.Field::class.java)

    fun setInstanceFinal(receiver: Any, field: Field, value: Any?): Boolean {
        val u = unsafe ?: return false
        val off = try { objectFieldOffset!!.invoke(u, field) as Long } catch (_: Throwable) { return false }
        return putByType(u, receiver, off, field.type, value, false)
    }

    fun setStaticFinal(field: Field, value: Any?): Boolean {
        val u = unsafe ?: return false
        val base = try { staticFieldBase!!.invoke(u, field) } catch (_: Throwable) { return false }
        val off = try { staticFieldOffset!!.invoke(u, field) as Long } catch (_: Throwable) { return false }
        return putByType(u, base, off, field.type, value, true)
    }

    private fun putByType(u: Any, target: Any, off: Long, type: Class<*>, value: Any?, @Suppress("UNUSED_PARAMETER") isStatic: Boolean): Boolean {
        return try {
            when (type) {
                java.lang.Integer.TYPE -> call(u, "putInt", target, off, value as Int)
                java.lang.Long.TYPE -> call(u, "putLong", target, off, value as Long)
                java.lang.Short.TYPE -> call(u, "putShort", target, off, value as Short)
                java.lang.Byte.TYPE -> call(u, "putByte", target, off, value as Byte)
                java.lang.Boolean.TYPE -> call(u, "putBoolean", target, off, value as Boolean)
                java.lang.Character.TYPE -> call(u, "putChar", target, off, value as Char)
                java.lang.Float.TYPE -> call(u, "putFloat", target, off, value as Float)
                java.lang.Double.TYPE -> call(u, "putDouble", target, off, value as Double)
                else -> call(u, "putObject", target, off, value)
            }
            true
        } catch (_: Throwable) { false }
    }

    private fun method(name: String, vararg params: Class<*>): java.lang.reflect.Method? = try {
        unsafe?.javaClass?.getMethod(name, *params)
    } catch (_: Throwable) { null }

    private fun call(u: Any, name: String, target: Any, off: Long, value: Any?) {
        val cls = u.javaClass
        val m = when (name) {
            "putInt" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Integer.TYPE)
            "putLong" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE)
            "putShort" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Short.TYPE)
            "putByte" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Byte.TYPE)
            "putBoolean" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Boolean.TYPE)
            "putChar" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Character.TYPE)
            "putFloat" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Float.TYPE)
            "putDouble" -> cls.getMethod(name, Any::class.java, java.lang.Long.TYPE, java.lang.Double.TYPE)
            else -> cls.getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
        }
        m.invoke(u, target, off, value)
    }
}
