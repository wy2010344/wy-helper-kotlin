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
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}
