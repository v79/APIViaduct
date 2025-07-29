plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "APIViaduct"
include("router")
include("Sample")
include("openapi")
