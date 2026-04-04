plugins {
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

dependencies {
    implementation(project(":common-api"))
    implementation(project(":common-events"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-amqp")  // Look Here

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Testcontainers for spinning up a real MongoDB during tests (works when Docker is available)
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
    testImplementation("org.testcontainers:mongodb:1.18.3")
}