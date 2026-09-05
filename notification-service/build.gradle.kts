plugins {
  id("payflow.spring-service")
}

dependencies {
  implementation(project(":events-contract"))

  implementation("org.springframework.boot:spring-boot-starter-web")
}
