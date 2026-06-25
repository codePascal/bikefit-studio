import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    // Rewrites JavaCPP "-platform" artifacts to the host OS only, so we don't pull
    // native binaries (OpenCV/FFmpeg) for every platform.
    id("org.bytedeco.gradle-javacpp-platform") version "1.5.10"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Webcam + USB-phone-as-webcam + video-file decoding (OpenCV + FFmpeg under the hood).
    implementation("org.bytedeco:javacv:1.5.10")
    implementation("org.bytedeco:openblas-platform:0.3.26-1.5.10")
    implementation("org.bytedeco:opencv-platform:4.9.0-1.5.10")
    implementation("org.bytedeco:ffmpeg-platform:6.1.1-1.5.10")
}

compose.desktop {
    application {
        mainClass = "bikefitstudio.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "BikefitDesktop"
            packageVersion = "1.0.0"
        }
    }
}

// Headless end-to-end check of the pose sidecar (no GUI). Override the defaults with
//   ./gradlew :desktop:smokeTest -PposeScript=<abs path> -PposePython=<abs path>
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Runs SmokeMain against a pose sidecar script."
    dependsOn("classes")
    mainClass.set("bikefitstudio.desktop.SmokeMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    val smokeArgs = mutableListOf(
        project.findProperty("poseScript")?.toString() ?: rootProject.file("pose_server_fake.py").absolutePath,
        project.findProperty("posePython")?.toString() ?: "python"
    )
    project.findProperty("poseImage")?.toString()?.let { smokeArgs.add(it) }
    args(smokeArgs)
}

// Headless check of the full fit pipeline with synthetic pedaling (no GUI, no ML).
tasks.register<JavaExec>("fitSmoke") {
    group = "verification"
    description = "Runs FitSmokeMain: synthetic pedaling -> cycle analysis -> FitEngine."
    dependsOn("classes")
    mainClass.set("bikefitstudio.desktop.FitSmokeMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
