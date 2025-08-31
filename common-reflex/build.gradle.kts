import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

dependencies {
    implementation("org.tabooproject.reflex:reflex:1.2.0")
    implementation("org.tabooproject.reflex:analyser:1.2.0")
}

tasks {
    withType<ShadowJar> {
        dependencies {
            include(dependency("org.tabooproject.reflex:reflex:1.2.0"))
            include(dependency("org.tabooproject.reflex:analyser:1.2.0"))
        }
        relocate("org.taboooproject", "taboolib.library")
    }
}