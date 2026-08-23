plugins {
  alias(libs.plugins.spotless)
}

group = "ru.payflow"
version = "0.1.0"

repositories { mavenCentral() }

spotless {
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktlint()
  }
}
