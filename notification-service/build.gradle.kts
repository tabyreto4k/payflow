plugins {
  id("payflow.spring-service")
}

dependencies {
  implementation(project(":events-contract"))

  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  // Своей БД у сервиса нет: дедуп держится на Redis [Р6].
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.kafka:spring-kafka")

  integrationTestImplementation("org.testcontainers:kafka")
  integrationTestImplementation("org.springframework.kafka:spring-kafka-test")
}
