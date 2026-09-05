plugins {
  id("payflow.spring-service")
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.liquibase:liquibase-core")
  runtimeOnly("org.postgresql:postgresql")

  integrationTestImplementation("org.testcontainers:postgresql")
  integrationTestRuntimeOnly("org.postgresql:postgresql")
}
