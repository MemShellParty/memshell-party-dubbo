plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

description = "Dubbo server provider and Dubbo shell transports for the command, godzilla and proxy plugins"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
    withSourcesJar()
}

dependencies {
    // Host supplied. compileOnly keeps a single class identity for the SPI contracts.
    compileOnly("io.github.reajason:memshell-party-api:3.2.0-SNAPSHOT")
    compileOnly("io.github.reajason:memshell-party-core:3.2.0-SNAPSHOT")

    // The upstream plugins this server extends. They stay out of the jar: the host installs
    // both plugins and this server contributes only the Dubbo transport mappings to them.
    compileOnly("io.github.reajason:memshell-party-shelltool-command:3.1.0-SNAPSHOT")
    compileOnly("io.github.reajason:memshell-party-shelltool-godzilla:3.0.0")
    compileOnly("io.github.reajason:memshell-party-shelltool-proxy:3.0.0")

    compileOnly(libs.metainf.services)
    annotationProcessor(libs.metainf.services)

    // Ships inside the plugin: the generation engine resolves the injector templates' Dubbo
    // signatures as class bytes without loading them.
    implementation(project(":dubbo-stub"))
    compileOnly(libs.javassist)

    testImplementation("io.github.reajason:memshell-party-api:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-core:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-packer:3.1.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-shelltool-command:3.1.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-shelltool-godzilla:3.0.0")
    testImplementation("io.github.reajason:memshell-party-shelltool-proxy:3.0.0")
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.hamcrest)
    // ProxyGenerator loads every shell template, so each transport the proxy plugin supports
    // must resolve here even though this plugin generates only the Dubbo ones.
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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("memshell-party-dubbo-plugin")
    archiveClassifier.set("")
    mergeServiceFiles()
    // Shadow 9 defaults to EXCLUDE; duplicates must survive until the merge runs.
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
