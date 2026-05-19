package taboolib.common.env;

import org.tabooproject.reflex.LazyEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ParsedDependency {

    /**
     * 依赖地址，格式为：
     * <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
     */
    private final String value;

    /**
     * 测试类
     * <p>
     * <code>
     * test = "!org.bukkit.Bukkit" // 前面带个感叹号避免在编译时重定向
     * </code>
     */
    private final String test;

    /**
     * 仓库地址，留空默认使用 <a href="https://maven.aliyun.com/repository/central">阿里云中央仓库</a>
     */
    private final String repository;

    /**
     * 是否进行依赖传递
     */
    private final boolean transitive;

    /**
     * 忽略可选依赖
     */
    private final boolean ignoreOptional;

    /**
     * 忽略加载异常
     */
    private final boolean ignoreException;

    /**
     * 依赖范围
     */
    private final List<DependencyScope> scopes;

    /**
     * 依赖重定向
     * <p>
     * <code>
     * relocate = ["!taboolib.", "!taboolib610."] // 同 test 参数
     * </code>
     */
    private final List<String> relocate;

    /**
     * 是否外部库（不会被扫到）
     */
    private final boolean external;

    /**
     * 过滤库
     */
    private final List<String> excludes;

    public ParsedDependency(String value, String test, String repository, boolean transitive, boolean ignoreOptional, boolean ignoreException, List<DependencyScope> scopes, List<String> relocate, boolean external, List<String> excludes) {
        this.value = value;
        this.test = test;
        this.repository = repository;
        this.transitive = transitive;
        this.ignoreOptional = ignoreOptional;
        this.ignoreException = ignoreException;
        this.scopes = scopes;
        this.relocate = relocate;
        this.external = external;
        this.excludes = excludes;
    }

    @SuppressWarnings("unchecked")
    public ParsedDependency(Map<String, Object> map) {
        this.value = (String) map.get("value");
        this.test = (String) map.getOrDefault("test", "");
        this.repository = (String) map.getOrDefault("repository", "");
        this.transitive = (boolean) map.getOrDefault("transitive", true);
        this.ignoreOptional = (boolean) map.getOrDefault("ignoreOptional", true);
        this.ignoreException = (boolean) map.getOrDefault("ignoreException", false);
        this.relocate = (List<String>) map.getOrDefault("relocate", new ArrayList<>());
        this.external = (boolean) map.getOrDefault("external", true);
        this.scopes = new ArrayList<>(Arrays.asList(DependencyScope.RUNTIME, DependencyScope.COMPILE));
        List<LazyEnum> scopesEnums = (List<LazyEnum>) map.getOrDefault("scopes", new ArrayList<>());
        scopesEnums.forEach(it -> this.scopes.add((DependencyScope) it.getInstance()));
        this.excludes = (List<String>) map.getOrDefault("excludes", new ArrayList<>());
    }

    public String value() {
        return value;
    }

    public String test() {
        return test;
    }

    public String repository() {
        return repository;
    }

    public boolean transitive() {
        return transitive;
    }

    public boolean ignoreOptional() {
        return ignoreOptional;
    }

    public boolean ignoreException() {
        return ignoreException;
    }

    public List<DependencyScope> scopes() {
        return scopes;
    }

    public List<String> relocate() {
        return relocate;
    }

    public boolean external() {
        return external;
    }

    public List<String> excludes() {
        return excludes;
    }

    @Override
    public String toString() {
        return "ParsedDependency{" +
                "value='" + value + '\'' +
                ", test='" + test + '\'' +
                ", repository='" + repository + '\'' +
                ", transitive=" + transitive +
                ", ignoreOptional=" + ignoreOptional +
                ", ignoreException=" + ignoreException +
                ", scopes=" + scopes +
                ", relocate=" + relocate +
                ", external=" + external +
                ", exclude =" + excludes +
                '}';
    }
}
