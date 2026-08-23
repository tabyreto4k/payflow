import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
  java
  jacoco
  checkstyle
  id("com.diffplug.spotless")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "ru.payflow"
version = "0.1.0"

java {
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

val integrationTest: SourceSet by sourceSets.creating

configurations[integrationTest.implementationConfigurationName]
  .extendsFrom(configurations.getByName("testImplementation"))
configurations[integrationTest.runtimeOnlyConfigurationName]
  .extendsFrom(configurations.getByName("testRuntimeOnly"))

tasks.withType<Test>().configureEach { useJUnitPlatform() }

val integrationTestTask =
  tasks.register<Test>("integrationTest") {
    description = "Интеграционные тесты (Testcontainers)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = configurations[integrationTest.runtimeClasspathConfigurationName] + integrationTest.output
    shouldRunAfter(tasks.named("test"))
  }

spotless {
  java {
    palantirJavaFormat(libs.findVersion("palantirJavaFormat").get().requiredVersion)
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

checkstyle {
  toolVersion = libs.findVersion("checkstyle").get().requiredVersion
  configFile = rootProject.file("config/checkstyle/checkstyle.xml")
  isIgnoreFailures = false
  maxWarnings = 0
}

jacoco { toolVersion = libs.findVersion("jacoco").get().requiredVersion }

tasks.named<JacocoReport>("jacocoTestReport") {
  dependsOn(tasks.named("test"))
  reports {
    xml.required = true
    html.required = true
  }
}

// Из счёта исключены точки входа, конфигурация, DTO и сгенерированный код.
val coverageExcludes =
  listOf(
    "**/*Application.class",
    "**/config/**",
    "**/dto/**",
    "**/generated/**",
  )

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  dependsOn(tasks.named("jacocoTestReport"))
  violationRules {
    rule {
      element = "BUNDLE"
      limit {
        counter = "INSTRUCTION"
        value = "COVEREDRATIO"
        minimum = "0.70".toBigDecimal()
      }
    }
  }
  classDirectories.setFrom(
    files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }),
  )
}

tasks.named("check") {
  dependsOn(integrationTestTask, tasks.named("jacocoTestCoverageVerification"))
}
