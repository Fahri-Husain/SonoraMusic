import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.fahrii.musicapp.shared"
        compileSdk {
            version = release(37)
        }
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        // Kode khusus JVM (NewPipe, OkHttp) yang dipakai sama persis oleh Android dan desktop.
        // Folder ini dikompilasi dua kali (sekali per target), jadi dependensinya dipasang di keduanya.
        val jvmSharedDir = "src/jvmShared/kotlin"

        jvmMain {
            kotlin.srcDir(jvmSharedDir)
            dependencies {
                implementation(libs.newpipe.extractor)
                implementation(libs.okhttp)
            }
        }
        getByName("androidMain") {
            kotlin.srcDir(jvmSharedDir)
            dependencies {
                implementation(libs.newpipe.extractor)
                implementation(libs.okhttp)
            }
        }
    }
}