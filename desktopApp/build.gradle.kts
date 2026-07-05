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

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
//        mainClass = "org.wy.helper.MainKt"
        mainClass = "org.wy.engine.DemoMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.wy.helper"
            packageVersion = "1.0.0"
        }
    }
}