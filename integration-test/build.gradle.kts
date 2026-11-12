plugins {
    `java`
    id("idea")
}

description = "Dubbo container integration tests for the plugin"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // The host surface the plugin extends, as the host would provide it.
    testImplementation("io.github.reajason:memshell-party-api:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-core:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-packer:3.1.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-shelltool-command:3.1.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-shelltool-godzilla:3.0.0")
    testImplementation("io.github.reajason:memshell-party-shelltool-proxy:3.0.0")

    testImplementation(project(":dubbo-plugin"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.hamcrest)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val dubboRangeProject = project(":dubbo-range")
val dubboClientProject = project(":dubbo-client")

fun dubboProviderJar(taskName: String): Provider<RegularFile> {
    return dubboRangeProject.tasks.named<Jar>(taskName).flatMap { it.archiveFile }
}

fun dubboClientClasspath(clientKind: String): Provider<RegularFile> {
    return dubboClientProject.layout.buildDirectory.file("dubbo-client-classpaths/$clientKind.txt")
}

fun Test.configureDubboSystemProperties(
    clientClasspathTask: TaskProvider<*>,
    providerFatJarsTask: TaskProvider<*>,
    providerJars: Map<String, Provider<RegularFile>>
) {
    val clientClasspaths = mapOf(
        "dubbo.alibaba.client.classpath" to dubboClientClasspath("alibaba"),
        "dubbo.apache.client.classpath" to dubboClientClasspath("apache")
    )
    dependsOn(clientClasspathTask, providerFatJarsTask)
    inputs.files(clientClasspaths.values, providerJars.values)
    doFirst {
        clientClasspaths.forEach { (key, file) ->
            systemProperty(key, file.get().asFile.readText())
        }
        providerJars.forEach { (key, jar) ->
            systemProperty(key, jar.get().asFile.absolutePath)
        }
    }
}

gradle.projectsEvaluated {
    val clientClasspathTask = dubboClientProject.tasks.named("dubboClientClasspath")
    val providerFatJarsTask = dubboRangeProject.tasks.named("dubboProviderFatJars")
    val providerJars = mapOf(
        "dubbo.alibaba.provider.jar" to dubboProviderJar("dubboAlibabaProviderFatJar"),
        "dubbo.apache276.provider.jar" to dubboProviderJar("dubboApache276ProviderFatJar"),
        "dubbo.apache277.provider.jar" to dubboProviderJar("dubboApache277ProviderFatJar"),
        "dubbo.apache278.provider.jar" to dubboProviderJar("dubboApache278ProviderFatJar"),
        "dubbo.apache2723.provider.jar" to dubboProviderJar("dubboApache2723ProviderFatJar"),
        "dubbo.apache336.provider.jar" to dubboProviderJar("dubboApache336ProviderFatJar")
    )
    tasks.withType<Test>().configureEach {
        if (name == "test" || name == "dubboContainerTest") {
            configureDubboSystemProperties(clientClasspathTask, providerFatJarsTask, providerJars)
        }
    }
}

fun Test.configureIntegrationJvm() {
    jvmArgs(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.trax=ALL-UNNAMED",
        "--add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.runtime=ALL-UNNAMED"
    )
    testLogging {
        events("passed", "skipped", "failed")
    }
    val parallelism = providers.gradleProperty("itParallelism").orElse("1")
    inputs.property("itParallelism", parallelism)
    doFirst {
        systemProperty(
            "junit.jupiter.execution.parallel.config.fixed.parallelism",
            parallelism.get()
        )
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("dubbo-container")
    }
    configureIntegrationJvm()
}

tasks.register<Test>("dubboContainerTest") {
    group = "verification"
    description = "Runs DubboService provider/client integration tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("dubbo-container")
    }
    configureIntegrationJvm()
}
