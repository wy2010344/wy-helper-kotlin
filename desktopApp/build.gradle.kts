import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.lib)
    implementation(projects.signal)
    implementation(projects.layout)
    implementation(projects.mve)
    implementation(projects.shared)
    implementation(projects.skiaEngine)
    implementation(projects.components)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

// 离屏截图：./gradlew :desktopApp:shot → desktopApp/build/shots/*.png
tasks.register<JavaExec>("shot") {
    group = "demo"
    mainClass.set("org.wy.engine.DemoShotKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// 布局依赖审计：./gradlew :desktopApp:layoutAudit → 列出全部成环点
tasks.register<JavaExec>("layoutAudit") {
    group = "demo"
    mainClass.set("org.wy.engine.DemoAuditKt")
    classpath = sourceSets["main"].runtimeClasspath
}

compose.desktop {
    application {
        mainClass = "org.wy.engine.DemoGrandKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.wy.helper"
            packageVersion = "1.0.0"
        }
    }
}