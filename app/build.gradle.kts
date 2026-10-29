// Copyright (C) 2026 QualiaMinigames contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinComposeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// ── The Rust library ───────────────────────────────────────────────────────────────
//
// The rules live in `qualia-minigames-core`, reached through the bindings in
// `qualia-minigames-ffi`. This build compiles that crate for Android and generates the
// Kotlin half of the bindings from the library it just built, so the two halves always
// come from one source.
//
// # Where the source comes from
//
// A released build takes the published crate, by version, and checks it against the
// SHA-256 the registry index publishes. A development build points at a local checkout
// with `-Pqualia.minigames.ffi.localDir=<path>`; testing an unpublished change through a
// released version is not testing it at all.
//
// The materialized copy is only reused when it is the version this build asks for,
// otherwise a version bump would silently keep building the old crate.

val ffiCrateVersion = providers.gradleProperty("qualia.minigames.ffi.version").getOrElse("0.1.0")
val ffiCrateSha256 = providers.gradleProperty("qualia.minigames.ffi.sha256").orNull
val ffiLocalDir = providers.gradleProperty("qualia.minigames.ffi.localDir").orNull
val rustSourceDir = ffiLocalDir?.let { rootProject.file(it) } ?: layout.projectDirectory.dir("rust").asFile
val rustManifest = rustSourceDir.resolve("Cargo.toml")

require(ffiLocalDir == null || rustManifest.isFile) {
    "qualia.minigames.ffi.localDir points at $rustSourceDir, which holds no Cargo.toml"
}

// The UniFFI release the bindings generator is pinned to. The generated Kotlin and the
// library that answers it are one protocol, so the version is stated once here and used
// both to install the generator and to name the library file.
val uniffiVersion = "0.32.2"
val uniffiNamespace = "qualia_minigames_ffi"
val uniffiPackage = "com.qiushui1012.app.minigames.ffi"
val rustLibraryName = "libqualia_minigames_ffi.so"

// The Android targets this application ships. A 64-bit ARM device is what minSdk 26 runs on
// in practice; the x86_64 and ARMv7 slices are here because an emulator and an old phone
// both run the application during development.
val androidTargets =
    mapOf(
        "aarch64-linux-android" to "arm64-v8a",
        "armv7-linux-androideabi" to "armeabi-v7a",
        "x86_64-linux-android" to "x86_64",
    )

val generatedBindingsDir = layout.buildDirectory.dir("generated/uniffi/kotlin").get().asFile
val jniLibsDir = layout.buildDirectory.dir("rustJniLibs").get().asFile

// Materializes a published crate into `app/rust/`. The directory is git ignored, so
// deleting it is always safe and is what a version change does.
val materializeRustSource =
    tasks.register("materializeRustSource") {
        description = "Unpacks the published FFI crate into app/rust when it is not there yet."
        onlyIf { ffiLocalDir == null }
        val marker = rustSourceDir.resolve("Cargo.toml")
        outputs.upToDateWhen {
            marker.isFile && marker.readText().contains("version = \"$ffiCrateVersion\"")
        }
        doLast {
            val crateFile = layout.buildDirectory.file("crates/qualia-minigames-ffi-$ffiCrateVersion.crate").get().asFile
            if (!crateFile.isFile) {
                crateFile.parentFile.mkdirs()
                logger.lifecycle("Downloading qualia-minigames-ffi $ffiCrateVersion from crates.io")
                URI(
                    "https://static.crates.io/crates/qualia-minigames-ffi/qualia-minigames-ffi-$ffiCrateVersion.crate",
                ).toURL().openStream().use { input -> crateFile.outputStream().use { output -> input.copyTo(output) } }
            }
            ffiCrateSha256?.let { expected ->
                val actual =
                    MessageDigest.getInstance("SHA-256")
                        .digest(crateFile.readBytes())
                        .joinToString("") { "%02x".format(it) }
                require(actual == expected) {
                    "qualia-minigames-ffi $ffiCrateVersion has SHA-256 $actual, expected $expected"
                }
            }
            if (rustSourceDir.exists()) {
                rustSourceDir.deleteRecursively()
            }
            rustSourceDir.mkdirs()
            copy {
                from(tarTree(resources.gzip(crateFile))) {
                    eachFile {
                        // The archive holds everything under `qualia-minigames-ffi-<version>/`.
                        relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                    }
                }
                into(rustSourceDir)
            }
        }
    }

// Compiles the crate for every Android target and copies each library into a directory the
// Android plugin packages. The Android NDK is found the same way the plugin finds it, so
// the two never disagree about which toolchain is in use.
val buildRustLibrary =
    tasks.register("buildRustLibrary") {
        description = "Cross-compiles the FFI crate for Android and stages the libraries."
        dependsOn(materializeRustSource)
        inputs.dir(rustSourceDir)
        outputs.dir(jniLibsDir)
        doLast {
            // The NDK is located the way the Android plugin locates it, from the SDK the
            // build already resolved, so the two never disagree about which toolchain runs.
            val sdkDir = android.sdkDirectory
            val ndkDir = File(sdkDir, "ndk/${android.ndkVersion}")
            require(ndkDir.isDirectory) {
                "The Android NDK ${android.ndkVersion} is not installed under $sdkDir/ndk."
            }
            require(File(ndkDir, "toolchains/llvm/prebuilt").isDirectory) {
                "The NDK at $ndkDir has no LLVM toolchain."
            }
            if (jniLibsDir.exists()) {
                jniLibsDir.deleteRecursively()
            }
            for ((target, abi) in androidTargets) {
                logger.lifecycle("Building the Rust library for $target ($abi)")
                providers.exec {
                    commandLine(
                        "cargo",
                        "ndk",
                        "-t",
                        target,
                        "build",
                        "--release",
                        "--manifest-path",
                        rustManifest.absolutePath,
                    )
                    workingDir = rustSourceDir
                    environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
                }.result.get().assertNormalExitValue()
                val built = rustSourceDir.resolve("target/$target/release/$rustLibraryName")
                require(built.isFile) { "The Rust build produced no $built" }
                val destination = File(jniLibsDir, "$abi/$rustLibraryName")
                destination.parentFile.mkdirs()
                built.copyTo(destination, overwrite = true)
            }
        }
    }

// Generates the Kotlin half of the bindings from the freshly built library. A host build is
// what the generator reads, because the bindings describe the interface and not the
// instruction set it runs on; the Android libraries are what answer the calls.
// ── The host library the unit tests load ──────────────────────────────────────────
//
// The unit tests run on the JVM, and the rules they exercise are the same Rust code the
// device runs: the point of testing here is that the binding answers correctly, which a mock
// could not show. The host library is therefore built and put on the test classpath, where
// JNA finds it by its plain name.
val hostLibraryName =
    when {
        org.gradle.internal.os.OperatingSystem.current().isWindows -> "qualia_minigames_ffi.dll"
        org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "libqualia_minigames_ffi.dylib"
        else -> "libqualia_minigames_ffi.so"
    }

val buildHostRustLibrary =
    tasks.register("buildHostRustLibrary") {
        description = "Builds the FFI crate for this machine, so the JVM tests can load it."
        dependsOn(materializeRustSource)
        // The two Rust tasks write into the same `target/`, but neither consumes what the
        // other produces: the cross build copies into `jniLibs`, the host build is read by
        // the bindings generator. Declaring that directory as an input of both would make
        // Gradle call them dependent, which they are not.
        mustRunAfter(materializeRustSource)
        doLast {
            providers.exec {
                commandLine(
                    "cargo",
                    "build",
                    "--release",
                    "--manifest-path",
                    rustManifest.absolutePath,
                )
                workingDir = rustSourceDir
            }.result.get().assertNormalExitValue()
        }
    }

val generateUniffiBindings =
    tasks.register("generateUniffiBindings") {
        description = "Generates the Kotlin bindings from the Rust library."
        // The generator reads a host library, because the bindings describe the interface and
        // not the instruction set they answer on. Making that library a prerequisite of this
        // task rather than of the tests alone is what lets a clean `assembleRelease` work: it
        // used to fail with "no host library was found" when no test had run before it.
        dependsOn(buildHostRustLibrary)
        inputs.dir(rustSourceDir)
        outputs.dir(generatedBindingsDir)
        doLast {
            val hostLibrary = findHostLibrary(rustSourceDir.resolve("target/release"))
            require(hostLibrary != null) {
                "No host library was found in ${rustSourceDir.resolve("target/release")}; run `cargo build --release` there first."
            }
            if (generatedBindingsDir.exists()) {
                generatedBindingsDir.deleteRecursively()
            }
            generatedBindingsDir.mkdirs()
            val generatorInstalled =
                providers.exec {
                    commandLine("uniffi-bindgen", "--version")
                    isIgnoreExitValue = true
                }.result.get().exitValue == 0
            require(generatorInstalled) {
                "uniffi-bindgen is not on PATH; install it with `cargo install uniffi --version $uniffiVersion --features cli --locked`."
            }
            providers.exec {
                commandLine(
                    "uniffi-bindgen",
                    "generate",
                    "--library",
                    hostLibrary.absolutePath,
                    "--language",
                    "kotlin",
                    "--out-dir",
                    generatedBindingsDir.absolutePath,
                    "--config",
                    rustSourceDir.resolve("uniffi.toml").absolutePath,
                    "--no-format",
                )
                workingDir = rustSourceDir
            }.result.get().assertNormalExitValue()
        }
    }

/** Returns the library a host `cargo build --release` produced, or null before one runs. */
fun findHostLibrary(directory: File): File? {
    val names =
        listOf(
            "qualia_minigames_ffi.dll",
            "libqualia_minigames_ffi.so",
            "libqualia_minigames_ffi.dylib",
        )
    return names.map { File(directory, it) }.firstOrNull { it.isFile }
}

// ── Signing ────────────────────────────────────────────────────────────────────────
//
// The keystore and its passwords never enter the repository. A local build reads them from
// `keystore.properties`, which is git ignored; CI writes the same file from repository
// secrets before it builds. Without the file a release build falls back to the debug
// keystore, which installs and runs but is not an identity to publish under.

val signingProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

if (signingProperties.isEmpty && gradle.startParameter.taskNames.any { it.contains("Release") }) {
    logger.warn("keystore.properties not found: the release build will be signed with the debug keystore")
}

val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")

android {
    namespace = "com.qiushui1012.app.minigames"
    compileSdk = 35

    // r28 and newer produce libraries whose load segments are aligned for 16 KB pages, which
    // Android 15 and later may use.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.qiushui1012.app.minigames"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk.abiFilters += androidTargets.values

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            storeFile = signingProperties.getProperty("storeFile")?.let { File(it) } ?: debugKeystore
            storePassword = signingProperties.getProperty("storePassword") ?: "android"
            keyAlias = signingProperties.getProperty("keyAlias") ?: "androiddebugkey"
            keyPassword = signingProperties.getProperty("keyPassword") ?: "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Shrinking is left off: the application reaches its rules through JNA and
            // through the generated bindings, neither of which has been exercised through a
            // shrinking build, and a release build is fast enough without it.
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(generatedBindingsDir)
            jniLibs.srcDir(jniLibsDir)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // The APK strips native symbols by default, which makes a crash inside the Rust
        // library a list of addresses.
        jniLibs.keepDebugSymbols += "**/*.so"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.androidxLifecycleViewmodelCompose)

    implementation(platform(libs.composeBom))
    implementation(libs.composeUi)
    implementation(libs.composeUiGraphics)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)

    // UniFFI's Kotlin bindings call the Rust library through JNA. The `aar` flavour is the
    // one that carries JNA's dispatcher for Android, and 5.17 or newer understands 16 KB
    // page sizes.
    implementation("${libs.jna.get()}@aar")

    implementation(libs.androidxDataStorePreferences)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxDatetime)

    debugImplementation(libs.composeUiTooling)
    debugImplementation(libs.composeUiTestManifest)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(platform(libs.composeBom))
    testImplementation(libs.composeUiTestJunit4)
    testImplementation(libs.composeUiTestManifest)
    // The production dependency is JNA's ar, whose dispatcher is packaged for Android. A${nl}    // JVM test needs the desktop dispatcher, which is the jar flavour of the same library.
    testImplementation("net.java.dev.jna:jna:5.18.1")
}

// The bindings and the libraries are build products of the Rust crate, so every task that
// compiles this module waits for them. Wiring it through the source sets alone would let a
// clean build reach the Kotlin compiler before the generated file exists.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateUniffiBindings)
}

tasks.named("preBuild") {
    dependsOn(generateUniffiBindings)
}

tasks.withType<Test>().configureEach {
    dependsOn(buildHostRustLibrary, generateUniffiBindings)
    // The generated bindings and the host library are what a test needs, and neither is on
    // the classpath by default: the bindings are a source directory, the library is a file.
    systemProperty("jna.library.path", rustSourceDir.resolve("target/release").absolutePath)
    systemProperty("java.library.path", rustSourceDir.resolve("target/release").absolutePath)
}
