rootProject.name = "paper-integration-tester"
include("test-client")
include("test-server")
include("code-generator")
include("test-project")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
include("core")
