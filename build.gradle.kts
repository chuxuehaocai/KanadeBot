import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "moe.cuteyuki"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters (自带 Jackson 3 / tools.jackson)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Jackson 2 annotations - 需要 >= 2.21 以提供 Jackson 3 引用的 com.fasterxml.jackson.annotation.JsonSerializeAs
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Shiro framework (依赖 Jackson 2 / com.fasterxml.jackson, 且要求 Spring Boot 4.x)
    implementation("com.mikuac:shiro:2.5.4")

    // Fastjson
    implementation("com.alibaba.fastjson2:fastjson2:2.0.57")

    // ZXing - QR Code detection & decoding
    implementation("com.google.zxing:javase:3.5.3")
}

tasks.test {
    enabled = false
}

tasks.bootJar {
    enabled = true
    mainClass.set("moe.cuteyuki.kanadebot.KanadeBotApplicationKt")
}

tasks.jar {
    enabled = true
    archiveClassifier.set("plain")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
