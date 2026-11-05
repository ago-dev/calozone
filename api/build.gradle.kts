import net.ltgt.gradle.errorprone.errorprone

plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.spotless)
  alias(libs.plugins.errorprone)
}

group = "app.calozone"

version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.flyway)
  implementation(libs.spring.boot.starter.security)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.boot.starter.webmvc)
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.testcontainers.postgresql)

  implementation(libs.flyway.database.postgresql)
  runtimeOnly(libs.postgresql)

  testImplementation(libs.spring.boot.starter.actuator.test)
  testImplementation(libs.spring.boot.starter.data.jpa.test)
  testImplementation(libs.spring.boot.starter.flyway.test)
  testImplementation(libs.spring.boot.starter.security.test)
  testImplementation(libs.spring.boot.starter.validation.test)
  testImplementation(libs.spring.boot.starter.webmvc.test)
  testRuntimeOnly(libs.junit.platform.launcher)

  errorprone(libs.errorprone.core)
}

tasks.withType<JavaCompile>().configureEach {
  // "processing" only reports unclaimed annotations, which Spring triggers constantly with no real
  // meaning.
  options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))
  options.errorprone.disableWarningsInGeneratedCode = true
}

tasks.withType<Test> { useJUnitPlatform() }

spotless {
  java { googleJavaFormat(libs.versions.google.java.format.get()) }
  kotlinGradle { ktfmt() }
}
