import org.gradle.api.plugins.JavaPlugin

plugins {
    `java`
    id("idea")
}

description = "Dubbo protocol clients used by the integration tests"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

val dubboClientClasspathDir = layout.buildDirectory.dir("dubbo-client-classpaths")

val dubboClientCommon: SourceSet by sourceSets.creating
val dubboClientAlibaba: SourceSet by sourceSets.creating
val dubboClientApache: SourceSet by sourceSets.creating
val dubboProxyClientCommon: SourceSet by sourceSets.creating
val dubboProxyClientAlibaba: SourceSet by sourceSets.creating
val dubboProxyClientApache: SourceSet by sourceSets.creating

val dubboClientCommonImplementation: Configuration by configurations.getting
val dubboClientAlibabaImplementation: Configuration by configurations.getting
val dubboClientApacheImplementation: Configuration by configurations.getting
val dubboProxyClientAlibabaImplementation: Configuration by configurations.getting
val dubboProxyClientApacheImplementation: Configuration by configurations.getting

val dubboClientCommonOutput = dubboClientCommon.output
val dubboProxyClientCommonOutput = dubboProxyClientCommon.output

dubboClientAlibaba.compileClasspath += dubboClientCommonOutput
dubboClientAlibaba.runtimeClasspath += dubboClientCommonOutput
dubboClientApache.compileClasspath += dubboClientCommonOutput
dubboClientApache.runtimeClasspath += dubboClientCommonOutput
dubboProxyClientAlibaba.compileClasspath += dubboProxyClientCommonOutput
dubboProxyClientAlibaba.runtimeClasspath += dubboProxyClientCommonOutput
dubboProxyClientApache.compileClasspath += dubboProxyClientCommonOutput
dubboProxyClientApache.runtimeClasspath += dubboProxyClientCommonOutput

idea {
    module {
        excludeDirs.add(file("src/main"))
    }
}

dependencies {
    dubboClientCommonImplementation("org.slf4j:slf4j-api:1.7.36")

    dubboClientAlibabaImplementation(dubboClientCommonOutput)
    dubboClientAlibabaImplementation("com.alibaba:dubbo:2.6.12")
    dubboClientAlibabaImplementation("com.alibaba:hessian-lite:3.2.4")
    dubboClientAlibabaImplementation("com.caucho:hessian:4.0.51")
    dubboClientAlibabaImplementation("org.apache.httpcomponents:httpclient:4.5.3")
    dubboClientAlibabaImplementation("org.springframework:spring-web:5.3.39")
    dubboClientAlibabaImplementation("io.netty:netty-all:4.1.25.Final")
    dubboClientAlibabaImplementation("org.mortbay.jetty:jetty:6.1.26")
    dubboClientAlibabaImplementation("org.mortbay.jetty:jetty-util:6.1.26")
    dubboClientAlibabaImplementation("org.slf4j:slf4j-api:1.7.36")
    dubboClientAlibabaImplementation("ch.qos.logback:logback-classic:1.2.13")

    dubboClientApacheImplementation(dubboClientCommonOutput)
    dubboClientApacheImplementation("org.apache.dubbo:dubbo:3.3.6") {
        exclude(group = "log4j", module = "log4j")
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
        exclude(group = "io.netty", module = "netty-resolver-dns-native-macos")
    }
    dubboClientApacheImplementation("org.apache.dubbo:dubbo-rpc-triple:3.3.6")
    dubboClientApacheImplementation("org.apache.dubbo.extensions:dubbo-rpc-http:3.3.1")
    dubboClientApacheImplementation("org.apache.dubbo.extensions:dubbo-rpc-hessian:3.3.0")
    dubboClientApacheImplementation("org.apache.dubbo:dubbo-remoting-http:3.3.0-beta.2")
    dubboClientApacheImplementation("com.caucho:hessian:4.0.51")
    dubboClientApacheImplementation("io.netty:netty-all:4.1.119.Final") {
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
        exclude(group = "io.netty", module = "netty-resolver-dns-native-macos")
    }
    dubboClientApacheImplementation("org.slf4j:slf4j-api:2.0.17")
    dubboClientApacheImplementation("ch.qos.logback:logback-classic:1.5.18")

    dubboProxyClientAlibabaImplementation(dubboProxyClientCommonOutput)
    dubboProxyClientAlibabaImplementation("com.alibaba:dubbo:2.6.12")
    dubboProxyClientAlibabaImplementation("com.alibaba:hessian-lite:3.2.4")
    dubboProxyClientAlibabaImplementation("com.caucho:hessian:4.0.51")
    dubboProxyClientAlibabaImplementation("org.apache.httpcomponents:httpclient:4.5.3")
    dubboProxyClientAlibabaImplementation("org.springframework:spring-web:5.3.39")
    dubboProxyClientAlibabaImplementation("io.netty:netty-all:4.1.25.Final")
    dubboProxyClientAlibabaImplementation("org.mortbay.jetty:jetty:6.1.26")
    dubboProxyClientAlibabaImplementation("org.mortbay.jetty:jetty-util:6.1.26")
    dubboProxyClientAlibabaImplementation("org.slf4j:slf4j-api:1.7.36")
    dubboProxyClientAlibabaImplementation("ch.qos.logback:logback-classic:1.2.13")

    dubboProxyClientApacheImplementation(dubboProxyClientCommonOutput)
    dubboProxyClientApacheImplementation("org.apache.dubbo:dubbo:3.3.6") {
        exclude(group = "log4j", module = "log4j")
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
        exclude(group = "io.netty", module = "netty-resolver-dns-native-macos")
    }
    dubboProxyClientApacheImplementation("org.apache.dubbo:dubbo-rpc-triple:3.3.6")
    dubboProxyClientApacheImplementation("org.apache.dubbo.extensions:dubbo-rpc-http:3.3.1")
    dubboProxyClientApacheImplementation("org.apache.dubbo.extensions:dubbo-rpc-hessian:3.3.0")
    dubboProxyClientApacheImplementation("org.apache.dubbo:dubbo-remoting-http:3.3.0-beta.2")
    dubboProxyClientApacheImplementation("com.caucho:hessian:4.0.51")
    dubboProxyClientApacheImplementation("io.netty:netty-all:4.1.119.Final") {
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
        exclude(group = "io.netty", module = "netty-resolver-dns-native-macos")
    }
    dubboProxyClientApacheImplementation("org.slf4j:slf4j-api:2.0.17")
    dubboProxyClientApacheImplementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(dubboProxyClientCommonOutput)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.register("dubboClientClasspath") {
    group = "verification"
    description = "Writes the isolated Alibaba and Apache Dubbo runtime classpaths"
    val alibabaRuntime = dubboClientAlibaba.runtimeClasspath
    val apacheRuntime = dubboClientApache.runtimeClasspath
    val proxyAlibabaRuntime = dubboProxyClientAlibaba.runtimeClasspath
    val proxyApacheRuntime = dubboProxyClientApache.runtimeClasspath
    val alibabaFile = dubboClientClasspathDir.map { it.file("alibaba.txt") }
    val apacheFile = dubboClientClasspathDir.map { it.file("apache.txt") }
    val proxyAlibabaFile = dubboClientClasspathDir.map { it.file("proxy-alibaba.txt") }
    val proxyApacheFile = dubboClientClasspathDir.map { it.file("proxy-apache.txt") }
    inputs.files(alibabaRuntime, apacheRuntime, proxyAlibabaRuntime, proxyApacheRuntime)
    outputs.files(alibabaFile, apacheFile, proxyAlibabaFile, proxyApacheFile)
    doLast {
        alibabaFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(alibabaRuntime.asPath)
        }
        apacheFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(apacheRuntime.asPath)
        }
        proxyAlibabaFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(proxyAlibabaRuntime.asPath)
        }
        proxyApacheFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(proxyApacheRuntime.asPath)
        }
    }
}

tasks.named(JavaPlugin.CLASSES_TASK_NAME) {
    dependsOn(
        dubboClientCommon.classesTaskName,
        dubboClientAlibaba.classesTaskName,
        dubboClientApache.classesTaskName,
        dubboProxyClientCommon.classesTaskName,
        dubboProxyClientAlibaba.classesTaskName,
        dubboProxyClientApache.classesTaskName
    )
}

tasks.test {
    useJUnitPlatform()
}
