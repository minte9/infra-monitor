plugins {
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

group = "com.minte9.monitor"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":common-api"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // OSHI is a simple Java library for reading CPU, RAM, disk and other OS info.
    implementation("com.github.oshi:oshi-core:6.6.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}