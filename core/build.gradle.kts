plugins {
    id("java")
}

group = "org.mockbukkit.integrationtester"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain{
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter:5.11.3")
    implementation("com.mojang:datafixerupper:8.0.16")
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}