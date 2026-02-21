import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

group = "com.jimandreas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.apache.pdfbox:pdfbox:3.0.6")
    implementation("org.apache.pdfbox:xmpbox:3.0.6")
    implementation("org.apache.pdfbox:preflight:3.0.6")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("net.sourceforge.tess4j:tess4j:5.18.0")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "com.jimandreas.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "ScanWithOCRtoPDF"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))
        }
    }
}

tasks.withType<JavaExec> {
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.desktop/java.awt.image=ALL-UNNAMED"
    )
}
