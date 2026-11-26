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
        // Intermediate source set: dikompilasi sekali per target tanpa mendaftarkan folder ke dua source set.
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.newpipe.extractor)
                implementation(libs.okhttp)
            }
        }
        jvmMain.get().dependsOn(jvmSharedMain)
        getByName("androidMain").dependsOn(jvmSharedMain)
    }
}