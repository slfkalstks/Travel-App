plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "com.yeohaeng"
version = "0.1.0"
extra["kotlin.version"] = "1.9.25"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito", module = "mockito-core")
    }
}
kotlin { compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") } }
tasks.test { useJUnitPlatform() }
