package taboolib.common;

import java.lang.annotation.*;

/**
 * 条件注入注解，用于控制类是否参与 ClassVisitor 注入。
 * <p>
 * 所有条件之间是 AND 关系（必须全部满足），可重复标注实现 OR 关系。
 * </p>
 *
 * <pre>
 * // 示例 1: 要求某类存在
 * &#64;Requires(classes = "net.milkbowl.vault.economy.Economy")
 * class VaultSupport { }
 *
 * // 示例 2: 系统属性判断（服务器类型）
 * // 启动参数: -Dserver.type=main
 * &#64;Requires(systemProperty = "server.type=main")
 * class MainServerFeature { }
 *
 * // 示例 3: 环境变量判断
 * &#64;Requires(env = "ENABLE_DEBUG=true")
 * class DebugFeature { }
 *
 * // 示例 4: 组合条件（AND 关系）
 * &#64;Requires(classes = "com.example.API", systemProperty = "feature.enabled=true")
 * class ConditionalFeature { }
 *
 * // 示例 5: 多个注解（OR 关系）
 * &#64;Requires(systemProperty = "server.type=main")
 * &#64;Requires(systemProperty = "server.type=backup")
 * class MultiServerFeature { }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Requires {

    /**
     * 要求存在的类（全类名）
     * <p>
     * 所有指定的类都必须存在才满足条件。
     * </p>
     */
    String[] classes() default {};

    /**
     * 要求不存在的类（全类名）
     * <p>
     * 所有指定的类都必须不存在才满足条件。
     * 用于实现降级方案或兼容旧版本。
     * </p>
     */
    String[] missingClasses() default {};

    /**
     * 系统属性条件
     * <p>
     * 格式: "key=value" 或 "key"（仅检查存在）
     * </p>
     * <ul>
     *   <li>"server.type=main" - 要求系统属性 server.type 等于 main</li>
     *   <li>"debug.enabled" - 要求系统属性 debug.enabled 存在（任意值）</li>
     * </ul>
     */
    String[] systemProperty() default {};

    /**
     * 环境变量条件
     * <p>
     * 格式: "KEY=value" 或 "KEY"（仅检查存在）
     * </p>
     * <ul>
     *   <li>"SERVER_TYPE=main" - 要求环境变量 SERVER_TYPE 等于 main</li>
     *   <li>"ENABLE_FEATURE" - 要求环境变量 ENABLE_FEATURE 存在（任意值）</li>
     * </ul>
     */
    String[] env() default {};
}
