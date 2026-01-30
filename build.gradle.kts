plugins {
    kotlin("jvm") version "2.1.0"
    `java-library`
    `maven-publish`
    id("org.jetbrains.dokka") version "2.1.0"
}

group = "com.github.fenrur"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Arrow is optional - users must add it themselves to use asArrow() extensions
    compileOnly("io.arrow-kt:arrow-core:2.0.1")

    // Kotlin Coroutines Flow is optional
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Reactive Streams is optional
    compileOnly("org.reactivestreams:reactive-streams:1.0.4")

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("io.arrow-kt:arrow-core:2.0.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation("org.reactivestreams:reactive-streams:1.0.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "signal"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Signal")
                description.set("A reactive signal library for Kotlin")
                url.set("https://github.com/fenrur/signal")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("fenrur")
                        name.set("Livio TINNIRELLO")
                    }
                }

                scm {
                    url.set("https://github.com/fenrur/signal")
                    connection.set("scm:git:git://github.com/fenrur/signal.git")
                    developerConnection.set("scm:git:ssh://github.com/fenrur/signal.git")
                }
            }
        }
    }

}
