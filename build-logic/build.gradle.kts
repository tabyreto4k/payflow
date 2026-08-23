plugins {
  `kotlin-dsl`
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(libs.plugin.spring.boot)
  implementation(libs.plugin.spring.dependency.management)
  implementation(libs.plugin.spotless)
}
