plugins {
    id("java")
}

group = "org.mockbukkit.integrationtester"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks.test {
    useJUnitPlatform()
}