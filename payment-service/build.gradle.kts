plugins {
  id("payflow.spring-service")
}

dependencies {
  implementation(project(":events-contract"))

  implementation("org.springframework.boot:spring-boot-starter-web")
  // Только спека: единственный Swagger UI живёт на gateway и собирает спеки сервисов.
  implementation(libs.springdoc.webmvc.api)
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.liquibase:liquibase-core")
  implementation("org.springframework.kafka:spring-kafka")
  // Кэш баланса: cache-aside поверх Redis, который уже стоит в стеке.
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  runtimeOnly("org.postgresql:postgresql")

  integrationTestImplementation("org.testcontainers:postgresql")
  integrationTestImplementation("org.testcontainers:kafka")
  integrationTestImplementation("org.springframework.kafka:spring-kafka-test")
  integrationTestRuntimeOnly("org.postgresql:postgresql")
}
