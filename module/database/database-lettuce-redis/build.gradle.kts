import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// 文档参考: https://www.cnblogs.com/throwable/p/11601538.html
// LettuceGithub： https://github.com/lettuce-io/lettuce-core

dependencies {
    compileOnly("io.lettuce:lettuce-core:6.6.0.RELEASE")
    compileOnly("org.apache.commons:commons-pool2:2.12.1")
    compileOnly(project(":common"))
    compileOnly(project(":common-env"))
    compileOnly(project(":common-util"))
    compileOnly(project(":common-platform-api"))
    compileOnly(project(":module:basic:basic-configuration"))
}

tasks {
    withType<ShadowJar> {
        relocate("org.reactivestreams.", "org.reactivestreams_1_0_4.")
        relocate("reactor.", "reactor_3_6_6.")
        relocate("org.apache.commons.pool2.", "org.apache.commons.pool2_2_12_1.")
        relocate("io.netty.", "io.netty._4_1_107_final.")
    }
}