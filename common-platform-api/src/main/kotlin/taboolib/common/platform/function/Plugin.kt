package taboolib.common.platform.function

import taboolib.common.util.unsafeLazy

/**
 * 获取当前插件名称
 */
val pluginId by unsafeLazy { ioService.pluginId }

/**
 * 获取当前插件版本
 */
val pluginVersion by unsafeLazy { ioService.pluginVersion }

/**
 * 当前是否在主线程中运行
 */
inline val isPrimaryThread: Boolean
    get() = ioService.isPrimaryThread