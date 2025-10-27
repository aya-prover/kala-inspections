import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = providers.gradleProperty(key).get()
fun environment(key: String) = providers.environmentVariable(key)

plugins {
  id("java") // Java support
  alias(libs.plugins.kotlin) // Kotlin support
  alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
  mavenCentral()
  intellijPlatform.defaultRepositories()
}

intellijPlatform.pluginConfiguration {
  name = properties("pluginName")
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
  testImplementation(libs.kala.collection)
  intellijPlatform {
    intellijIdeaCommunity("2025.2")
    bundledPlugin("com.intellij.java")
    testFramework(TestFrameworkType.Platform)
  }
}

tasks {
  patchPluginXml {
    version = properties("pluginVersion")
    sinceBuild = properties("pluginSinceBuild")
    // untilBuild = properties("pluginUntilBuild")
  }
}
