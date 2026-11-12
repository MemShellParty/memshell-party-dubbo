plugins {
    `java`
}

description = "Loads the built plugin jar through PluginBootstrap and verifies the host-visible extension"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // Host surface only. The plugin project is deliberately absent: the jar under test must
    // register itself through the same bootstrap path a real host uses.
    testImplementation("io.github.reajason:memshell-party-api:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-core:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-packer:3.1.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-shelltool-command:3.1.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-shelltool-godzilla:3.0.0")
    testImplementation("io.github.reajason:memshell-party-shelltool-proxy:3.0.0")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.hamcrest)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)

    // The proxy tool loads every shell template during generation, so the transports it
    // supports must resolve even though the plugin generates only the Dubbo ones.
    testImplementation(libs.javax.servlet.api)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.javax.websocket.api)
    testImplementation(libs.netty.all)
    testImplementation(libs.spring.webmvc)
    testImplementation(libs.spring.webflux)
    testImplementation(libs.stub.jetty)
    testImplementation(libs.stub.tomcat)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// CI points this at the downloaded artifact so the test loads the published bytes, not a
// fresh build from the same checkout.
val downloadedPluginJar = providers.gradleProperty("dubboPluginJar")

tasks.test {
    useJUnitPlatform()
    if (downloadedPluginJar.isPresent) {
        // Hash the downloaded jar so a cached test result cannot hide a broken artifact.
        inputs.file(downloadedPluginJar.map { file(it) })
    } else {
        dependsOn(":dubbo-plugin:shadowJar")
    }
    doFirst {
        val jar = downloadedPluginJar.orNull?.let { file(it) }
            ?: project(":dubbo-plugin").tasks.named("shadowJar").get().outputs.files.singleFile
        systemProperty("dubbo.plugin.jar", jar.absolutePath)
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}
