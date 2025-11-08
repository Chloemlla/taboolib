package taboolib.common.platform.command.component

import kotlin.enums.EnumEntries

/**
 * 命令建议提供者接口
 *
 * 这是一个可选的扩展接口，用于统一管理不同类型参数的建议行为
 * 如果你想要统一定制所有类型的建议，可以实现此接口并通过 CommandComponent.applySuggestProvider() 应用
 */
interface CommandSuggestProvider {

    /**
     * 提供整数类型的建议
     *
     * @param component 动态命令组件
     * @param comment 注释
     * @param suggest 额外的建议列表
     */
    fun provideIntSuggest(component: CommandComponentDynamic, comment: String, suggest: List<String>)

    /**
     * 提供小数类型的建议
     *
     * @param component 动态命令组件
     * @param comment 注释
     * @param suggest 额外的建议列表
     */
    fun provideDecimalSuggest(component: CommandComponentDynamic, comment: String, suggest: List<String>)

    /**
     * 提供枚举类型的建议
     *
     * @param component 动态命令组件
     * @param comment 注释
     * @param suggest 额外的建议列表
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun provideEnumSuggest(component: CommandComponentDynamic, enums: EnumEntries<*>, comment: String, suggest: List<String>)

    /**
     * 提供布尔类型的建议
     *
     * @param component 动态命令组件
     * @param comment 注释
     */
    fun provideBoolSuggest(component: CommandComponentDynamic, comment: String)

    /**
     * 提供玩家类型的建议
     *
     * @param component 动态命令组件
     * @param comment 注释
     * @param suggest 额外的建议列表
     */
    fun providePlayerSuggest(component: CommandComponentDynamic, comment: String, suggest: List<String>)
}