plugins {
    id("java-library")
    alias(libs.plugins.kotlinJvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.skiaEngine)
    implementation(projects.layout)
    implementation(projects.mve)
    implementation(projects.signal)
    implementation(projects.lib)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesSwing)
    testImplementation(libs.kotlinx.coroutines.test)
}
