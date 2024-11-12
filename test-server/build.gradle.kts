plugins {
    id("java")
}

group = "org.mockbukkit.integrationtester"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain{
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}