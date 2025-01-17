plugins {
    id("java")
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
}

group = "org.mockbukkit.integrationtester"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(project(":test-client"))
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