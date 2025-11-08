package taboolib.common.platform.command.component

import taboolib.common.platform.command.*
import kotlin.enums.EnumEntries

/**
 * 默认的命令建议提供者实现
 */
class DefaultCommandSuggestProvider : CommandSuggestProvider {

    override fun provideIntSuggest(component: CommandComponentDynamic, comment: String, suggest: List<String>) {
        // 如果没有额外建议则约束参数输入
        if (suggest.isEmpty()) {
            component.restrictInt()
        } else {
            component.suggestUncheck { suggest }
        }
    }

    override fun provideDecimalSuggest(component: CommandComponentDynamic, comment: String, suggest: List<String>) {
        // 如果没有额外建议则约束参数输入
        if (suggest.isEmpty()) {
            component.restrictDouble()
        } else {
            component.suggestUncheck { suggest }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun provideEnumSuggest(component: CommandComponentDynamic, enums: EnumEntries<*>, comment: String, suggest: List<String>) {
        component.suggestEnums(enums, suggest)
    }

    override fun provideBoolSuggest(component: CommandComponentDynamic, comment: String) {
        component.suggestBoolean()
    }

    override fun providePlayerSuggest(component: CommandComponentDynamic, comment: String, suggest: List<String>) {
        component.suggestPlayers(suggest)
    }
}