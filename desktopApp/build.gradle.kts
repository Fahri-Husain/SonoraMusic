import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jna)
    implementation(libs.jna.platform)
}

compose.desktop {
    application {
        mainClass = "com.fahrii.musicapp.desktop.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        System.getenv("PACKAGING_JDK")?.let { javaHome = it }

        nativeDistributions {
            // Isi folder resources/ ikut dikemas dan terbaca lewat compose.application.resources.dir
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "SonoraMusic"
            packageVersion = "1.0.1"

            windows {
                menuGroup = "Sonora Music"
                upgradeUuid = "7c1f2a9e-4b3d-4e58-9a6b-2d5f0c8e1a47"
                iconFile.set(project.file("icons/sonora.ico"))
                shortcut = true   // pintasan di desktop selain di Start Menu
            }
        }
    }
}