pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "commerce-lab"

include(
    "modules:common",
    "modules:contract",
    "modules:order:order-api",
    "modules:order:order-core",
    "modules:payment:payment-api",
    "modules:payment:payment-core",
    "bootstrap",
)
