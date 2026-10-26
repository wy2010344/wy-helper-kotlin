plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    androidLibrary {
        namespace = "org.wy.engine"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.lib)
            implementation(projects.signal)
            implementation(projects.mve)
            implementation(projects.layout)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonMain {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // SkiaTestBase（commonTest）用 TestScope 替换批次调度，保证测试线程串行消费
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            // 组件测试（RichEditableTest 等）读组件包 Theme
            implementation(projects.components)
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}
