plugins {
    `java-library`
}

description = "Dubbo compile-time signature stubs used by the plugin's injector templates"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

dependencies {
    // ClassGenerator.getClassPool return type; the target JVM's Dubbo supplies the real class.
    compileOnly(libs.javassist)
}
