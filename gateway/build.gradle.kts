plugins {
  id("payflow.spring-service")
}

val springCloudBom = libs.spring.cloud.bom.get().toString()

dependencyManagement {
  imports { mavenBom(springCloudBom) }
}

dependencies {
  implementation("org.springframework.cloud:spring-cloud-starter-gateway")
  // Rate limiter Spring Cloud Gateway держит счётчики в Redis: инстансов gateway может быть много.
  implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
  // Только разбор HS256-токенов; выпускает их order-service.
  implementation("org.springframework.security:spring-security-oauth2-jose")

  integrationTestImplementation("com.squareup.okhttp3:mockwebserver")
}
