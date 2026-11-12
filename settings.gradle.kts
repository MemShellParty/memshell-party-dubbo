pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Plugin, stub and fixture modules compile with a Java 8 toolchain. CI runners only
    // ship JDK 17, so let Gradle provision Temurin 8 instead of pinning it in the workflow.
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots") {
            content {
                includeGroupByRegex("io\\.github\\..*")
            }
        }
    }
}

rootProject.name = "memshell-party-dubbo"

include("dubbo-stub")
include("dubbo-plugin")
include("dubbo-range")
include("dubbo-client")
include("plugin-verification")
include("integration-test")
