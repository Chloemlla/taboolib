package taboolib.common.inject;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tabooproject.reflex.*;
import taboolib.common.*;
import taboolib.common.io.ProjectInfoKt;
import taboolib.common.io.ProjectScannerKt;
import taboolib.common.platform.Ghost;
import taboolib.common.platform.Platform;
import taboolib.common.platform.PlatformSide;
import taboolib.common.platform.SkipTo;
import taboolib.common.platform.DelayTo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TabooLib
 * taboolib.common.inject.VisitorHandler
 *
 * @author sky
 * @since 2021/8/14 12:18 上午
 */
@SuppressWarnings("CallToPrintStackTrace")
public class ClassVisitorHandler {

    private static final NavigableMap<Byte, VisitorGroup> propertyMap = Collections.synchronizedNavigableMap(new TreeMap<>());
    private static final Map<LifeCycle, Set<ReflexClass>> delayedClasses = Collections.synchronizedMap(new HashMap<>());
    private static Set<ReflexClass> classes = null;

    /**
     * 初始化函数
     */
    static void init() {
        for (LifeCycle lifeCycle : LifeCycle.values()) {
            if (lifeCycle == LifeCycle.NONE) {
                continue;
            }
            // 只有 CONST 生命周期下优先级为 1，因为要在 PlatformFactory 之后运行
            int priority = lifeCycle == LifeCycle.CONST ? 1 : 0;
            // 注册任务
            TabooLib.registerLifeCycleTask(lifeCycle, priority, () -> ClassVisitorHandler.injectAll(lifeCycle));
        }
        PrimitiveIO.debug("ClassVisitorHandler 初始化完成。");
    }

    /**
     * 获取能够被 ClassVisitor 访问到的所有类
     */
    public static Set<ReflexClass> getClasses() {
        if (classes == null) {
            HashSet<ReflexClass> cache = new LinkedHashSet<>();
            long time = TabooLib.execution(() -> {
                // 获取所有类
                // 这里会首次触发 runningClassMapInJar 的初始化
                for (Map.Entry<String, ReflexClass> entry : ProjectScannerKt.getRunningClassMap().entrySet()) {
                    String key = entry.getKey();
                    ReflexClass value = entry.getValue();
                    // 排除非本项目 && 排除第三方库 && 排除匿名内部类
                    if (!isProjectClass(key) || isLibraryClass(key) || isAnonymousInnerClass(key)) {
                        continue;
                    }
                    // 排除属于 TabooLib 但没有 Inject 注解的类
                    if (isTabooLibClass(key) && !value.getStructure().isAnnotationPresent(Inject.class)) {
                        continue;
                    }
                    // 检测有效平台 & 条件注解
                    if (checkPlatform(value) && checkRequires(value)) {
                        cache.add(value);
                    }
                }
                classes = cache;
            });
            PrimitiveIO.debug("ClassVisitor 收集到 {0} 个有效类，用时 {1} 毫秒。", classes.size(), time);
        }
        return classes;
    }

    /**
     * 检查指定类是否允许在当前平台运行
     */
    public static boolean checkPlatform(ReflexClass cls) {
        if (cls.getStructure().isAnnotationPresent(PlatformSide.class)) {
            ClassAnnotation annotation = cls.getStructure().getAnnotation(PlatformSide.class);
            List<String> value = annotation.enumNameList("value");
            return value.isEmpty() || value.contains(Platform.CURRENT.name());
        }
        return true;
    }

    /**
     * 检查指定类是否满足 @Requires 条件
     * <p>
     * 单个 @Requires 内的所有条件是 AND 关系（必须全部满足）
     * 多个 @Requires 注解之间是 OR 关系（满足任意一个即可）
     * </p>
     */
    public static boolean checkRequires(ReflexClass cls) {
        if (cls.getStructure().isAnnotationPresent(Requires.class)) {
            ClassAnnotation annotation = cls.getStructure().getAnnotation(Requires.class);
            return checkSingleRequires(annotation);
        }
        return true;
    }

    /**
     * 检查单个 @Requires 注解的所有条件（AND 关系）
     */
    private static boolean checkSingleRequires(ClassAnnotation annotation) {
        // 1. 检查必须存在的类
        List<String> requiredClasses = annotation.list("classes");
        for (String className : requiredClasses) {
            if (!isClassPresent(className)) {
                return false;
            }
        }
        // 2. 检查必须不存在的类
        List<String> missingClasses = annotation.list("missingClasses");
        for (String className : missingClasses) {
            if (isClassPresent(className)) {
                return false;
            }
        }
        // 3. 检查系统属性
        List<String> systemProperties = annotation.list("systemProperty");
        for (String prop : systemProperties) {
            if (!checkSystemProperty(prop)) {
                return false;
            }
        }
        // 4. 检查环境变量
        List<String> envVars = annotation.list("env");
        for (String env : envVars) {
            if (!checkEnvironmentVariable(env)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查类是否存在
     */
    static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, ClassVisitorHandler.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检查系统属性条件
     *
     * @param condition 格式: "key=value"、"key!=value" 或 "key"（仅检查存在）
     */
    static boolean checkSystemProperty(String condition) {
        // 先检查不等于操作符（必须在等于之前，因为 != 包含 =）
        int neqIdx = condition.indexOf("!=");
        if (neqIdx != -1) {
            String key = condition.substring(0, neqIdx);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("系统属性条件格式错误: key 不能为空");
            }
            String unexpectedValue = condition.substring(neqIdx + 2);
            String actualValue = System.getProperty(key);
            return !unexpectedValue.equals(actualValue);
        }
        int idx = condition.indexOf('=');
        if (idx == -1) {
            // 仅检查存在
            return System.getProperty(condition) != null;
        } else {
            // 检查键值匹配
            String key = condition.substring(0, idx);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("系统属性条件格式错误: key 不能为空");
            }
            String expectedValue = condition.substring(idx + 1);
            String actualValue = System.getProperty(key);
            return expectedValue.equals(actualValue);
        }
    }

    /**
     * 检查环境变量条件
     *
     * @param condition 格式: "KEY=value"、"KEY!=value" 或 "KEY"（仅检查存在）
     */
    static boolean checkEnvironmentVariable(String condition) {
        // 先检查不等于操作符（必须在等于之前，因为 != 包含 =）
        int neqIdx = condition.indexOf("!=");
        if (neqIdx != -1) {
            String key = condition.substring(0, neqIdx);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("环境变量条件格式错误: key 不能为空");
            }
            String unexpectedValue = condition.substring(neqIdx + 2);
            String actualValue = System.getenv(key);
            return !unexpectedValue.equals(actualValue);
        }
        int idx = condition.indexOf('=');
        if (idx == -1) {
            // 仅检查存在
            return System.getenv(condition) != null;
        } else {
            // 检查键值匹配
            String key = condition.substring(0, idx);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("环境变量条件格式错误: key 不能为空");
            }
            String expectedValue = condition.substring(idx + 1);
            String actualValue = System.getenv(key);
            return expectedValue.equals(actualValue);
        }
    }

    /**
     * 注册依赖注入接口
     *
     * @param classVisitor 接口
     */
    public static void register(@NotNull ClassVisitor classVisitor) {
        VisitorGroup injectors = propertyMap.computeIfAbsent(classVisitor.getPriority(), i -> new VisitorGroup(classVisitor.getPriority()));
        injectors.getAll().add(classVisitor);
    }

    /**
     * 对给定类进行依赖注入
     *
     * @param clazz 类
     */
    public static void injectAll(@NotNull ReflexClass clazz) {
        for (Map.Entry<Byte, VisitorGroup> entry : propertyMap.entrySet()) {
            inject(clazz, entry.getValue(), null, false);
        }
    }

    /**
     * 根据生命周期对所有类进行依赖注入
     *
     * @param lifeCycle 生命周期
     */
    public static void injectAll(@NotNull LifeCycle lifeCycle) {
        // 处理延迟注入的类
        final Set<ReflexClass> delayedForThisCycle = delayedClasses.get(lifeCycle);
        if (delayedForThisCycle != null) {
            final List<LifeCycle> cyclesUtilNow = Arrays.stream(LifeCycle.values()).filter(cycle -> cycle.ordinal() < lifeCycle.ordinal()).collect(Collectors.toList());
            for (final LifeCycle cycle : cyclesUtilNow) {
                for (final ReflexClass clazz : delayedForThisCycle) {
                    for (Map.Entry<Byte, VisitorGroup> entry : propertyMap.entrySet()) {
                        inject(clazz, entry.getValue(), cycle, true);
                    }
                }
            }
            delayedClasses.remove(lifeCycle);
        }
        // 处理正常的类注入
        for (Map.Entry<Byte, VisitorGroup> entry : propertyMap.entrySet()) {
            for (ReflexClass clazz : getClasses()) {
                inject(clazz, entry.getValue(), lifeCycle, false);
            }
        }
    }

    /**
     * 对给定类进行依赖注入
     *
     * @param clazz     类
     * @param group     注入组
     * @param lifeCycle 生命周期
     * @param isDelayTo 是否为 @DelayTo 的注入行为
     */
    public static void inject(@NotNull ReflexClass clazz, @NotNull VisitorGroup group, @Nullable LifeCycle lifeCycle, boolean isDelayTo) {
        // 跳过注入
        if (clazz.getStructure().isAnnotationPresent(Ghost.class)) {
            return;
        }
        // 检查 SkipTo
        if (lifeCycle != null && clazz.getStructure().isAnnotationPresent(SkipTo.class)) {
            int skip = clazz.getStructure().getAnnotation(SkipTo.class).getEnum("value", LifeCycle.CONST).ordinal();
            if (skip > lifeCycle.ordinal()) return;
        }
        // 检查 DelayTo
        if (lifeCycle != null && clazz.getStructure().isAnnotationPresent(DelayTo.class) && !isDelayTo) {
            final LifeCycle delayTo = clazz.getStructure().getAnnotation(DelayTo.class).getEnum("value", LifeCycle.CONST);
            if (delayTo.ordinal() > lifeCycle.ordinal()) {
                delayedClasses.computeIfAbsent(delayTo, k -> Collections.synchronizedSet(new HashSet<>())).add(clazz);
                return;
            }
        }
        // 依赖注入
        visitStart(clazz, group, lifeCycle);
        visitField(clazz, group, lifeCycle);
        visitMethod(clazz, group, lifeCycle);
        visitEnd(clazz, group, lifeCycle);
    }

    static void visitStart(ReflexClass clazz, VisitorGroup group, LifeCycle lifeCycle) {
        for (ClassVisitor visitor : group.get(lifeCycle)) {
            try {
                visitor.visitStart(clazz);
            } catch (Throwable ex) {
                new ClassVisitException(clazz, group, lifeCycle, ex).printStackTrace();
            }
        }
    }

    static void visitField(ReflexClass clazz, VisitorGroup group, LifeCycle lifeCycle) {
        for (ClassVisitor visitor : group.get(lifeCycle)) {
            for (ClassField field : clazz.getStructure().getFields()) {
                try {
                    visitor.visit(field, clazz);
                } catch (Throwable ex) {
                    new ClassVisitException(clazz, group, lifeCycle, field, ex).printStackTrace();
                }
            }
        }
    }

    static void visitMethod(ReflexClass clazz, VisitorGroup group, LifeCycle lifeCycle) {
        for (ClassVisitor visitor : group.get(lifeCycle)) {
            for (ClassMethod method : clazz.getStructure().getMethods()) {
                try {
                    visitor.visit(method, clazz);
                } catch (Throwable ex) {
                    new ClassVisitException(clazz, group, lifeCycle, method, ex).printStackTrace();
                }
            }
        }
    }

    static void visitEnd(ReflexClass clazz, VisitorGroup group, LifeCycle lifeCycle) {
        for (ClassVisitor visitor : group.get(lifeCycle)) {
            try {
                visitor.visitEnd(clazz);
            } catch (Throwable ex) {
                new ClassVisitException(clazz, group, lifeCycle, ex).printStackTrace();
            }
        }
    }

    /**
     * 是否为匿名内部类
     */
    static boolean isAnonymousInnerClass(String name) {
        int lastSpectator = name.lastIndexOf("$");
        if (lastSpectator == -1) return false;
        String className = name.substring(lastSpectator + 1);
        for (int i = 0; i < className.length(); i++) {
            if (!Character.isDigit(className.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否为本项目的类
     */
    static boolean isProjectClass(String name) {
        return name.startsWith(ProjectInfoKt.getGroupId()) || name.startsWith(ProjectInfoKt.getTaboolibId());
    }

    /**
     * 是否为 TabooLib 类
     */
    static boolean isTabooLibClass(String name) {
        return name.startsWith(ProjectInfoKt.getTaboolibPath()) || name.startsWith(ProjectInfoKt.getTaboolibId());
    }

    /**
     * 是否为可能的第三方库
     * 通过包名判断
     */
    static boolean isLibraryClass(String name) {
        return name.contains(".library.") || name.contains(".libs.");
    }
}
