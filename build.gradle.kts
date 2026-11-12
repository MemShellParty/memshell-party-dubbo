plugins {
    id("idea")
}

allprojects {
    group = "io.github.reajason"
    version = "1.0.0-SNAPSHOT"

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
