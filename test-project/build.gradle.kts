plugins {
    id("java")
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
}

group = "org.mockbukkit.integrationtester"
version = "unspecified"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(project(":test-client"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

bukkit {
    main = "dev.jsinco.brewery.TheBrewingProject"
    foliaSupported = false
    apiVersion = "1.21"
    authors = listOf("Jsinco", "Mitality", "Thorinwasher")
    commands {
        register("test") {

        }
    }
}