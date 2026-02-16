plugins {
    kotlin("multiplatform") version "2.1.20"
    id("org.jetbrains.dokka") version "2.1.0"
    `maven-publish`
}

group = "com.github.fenrur"
version = System.getenv("VERSION") ?: "2.0.1"

repositories {
    mavenCentral()
}

kotlin {
    applyDefaultHierarchyTemplate()

    // ============ TARGETS ============

    jvm {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    js {
        browser()
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
    }

    wasmWasi {
        nodejs()
    }

    // Native targets
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    // ============ COMPILER OPTIONS ============

    compilerOptions {
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // ============ SOURCE SETS ============

    sourceSets {
        commonMain {
            dependencies {
                // Kotlin Coroutines Flow is optional at runtime but needed for StateFlowSignal compilation
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }

        jvmMain {
            dependencies {
                // Reactive Streams is optional
                compileOnly("org.reactivestreams:reactive-streams:1.0.4")
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.reactivestreams:reactive-streams:1.0.4")
            }
        }

        // Intermediate source set for single-threaded platforms (JS, WasmJS, WasmWASI)
        val singleThreadedMain by creating {
            dependsOn(commonMain.get())
        }

        jsMain {
            dependsOn(singleThreadedMain)
        }

        wasmJsMain {
            dependsOn(singleThreadedMain)
        }

        wasmWasiMain {
            dependsOn(singleThreadedMain)
        }

        // Intermediate test source set for multi-threaded platforms (JVM + Native)
        val multiThreadedTest by creating {
            dependsOn(commonTest.get())
        }

        jvmTest {
            dependsOn(multiThreadedTest)
        }

        nativeTest {
            dependsOn(multiThreadedTest)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("Signal")
            description.set("A reactive signal library for Kotlin Multiplatform")
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
