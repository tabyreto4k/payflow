plugins {
  id("payflow.java-conventions")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

dependencies {
  "implementation"("org.springframework.boot:spring-boot-starter-actuator")
  "implementation"("org.springframework.boot:spring-boot-starter-validation")
  "runtimeOnly"("io.micrometer:micrometer-registry-prometheus")

  "testImplementation"("org.springframework.boot:spring-boot-starter-test")
  "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")

  "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-test")
  "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
  "integrationTestImplementation"("org.testcontainers:junit-jupiter")
}
