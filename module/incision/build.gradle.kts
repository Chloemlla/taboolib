dependencies {
    compileOnly(project(":common"))
    compileOnly(project(":common-env"))
    compileOnly(project(":common-platform-api"))
    compileOnly(project(":common-util"))
    compileOnly(project(":common-platform-api"))
    compileOnly(project(":platform:platform-bukkit"))
    compileOnly(project(":platform:platform-bukkit-impl"))
    // 可选 — 检测到时启用 NMS NameResolver
    compileOnly(project(":module:bukkit-nms"))
    // ASM
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("org.ow2.asm:asm-commons:9.8")
    compileOnly("org.ow2.asm:asm-util:9.8")
    // self-attach 默认路径
    compileOnly("net.bytebuddy:byte-buddy-agent:1.14.18")
    // 测试
    testImplementation(project(":common"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    // BodiesClassGenerator 在运行期直接使用 ASM，测试需要 ASM 运行时
    testImplementation("org.ow2.asm:asm:9.8")
    testImplementation("org.ow2.asm:asm-commons:9.8")
    testImplementation("org.ow2.asm:asm-util:9.8")
}

tasks.test {
    useJUnitPlatform()
}
