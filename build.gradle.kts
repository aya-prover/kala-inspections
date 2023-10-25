plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.9.0"
  id("org.jetbrains.intellij") version "1.15.0"
}

val javaVersion = "17"
val kalaVersion = "0.67.0"

group = "org.aya-prover"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation("org.glavo.kala", "kala-collection", kalaVersion)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  version.set("2023.1")
  type.set("IC") // Target IDE Platform
  
  plugins.set(listOf("com.intellij.java"))
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = javaVersion
  }
  
  patchPluginXml {
    sinceBuild.set("231")
    untilBuild.set("232.*")
  }
  
  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }
  
  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}
