import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Whisper model is baked into the APK so speech-to-text works the instant the
// app is installed, with no download. It is fetched at build time rather than
// committed, so no large binary ever enters Git history.
// ---------------------------------------------------------------------------
val whisperModelName = "ggml-tiny.en-q5_1.bin"
val whisperModelUrl =
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$whisperModelName"
val whisperModelSha256 = "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b"
val whisperModelTarget = layout.projectDirectory.file("src/main/assets/models/$whisperModelName")

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val fetchWhisperModel = tasks.register("fetchWhisperModel") {
    group = "speak"
    description = "Downloads the bundled whisper.cpp speech-to-text model into assets."
    val target = whisperModelTarget.asFile
    val url = whisperModelUrl
    val expected = whisperModelSha256
    outputs.file(target)
    outputs.upToDateWhen { target.isFile && target.sha256() == expected }
    doLast {
        if (target.isFile && target.sha256() == expected) {
            logger.lifecycle("Whisper model already present and verified.")
            return@doLast
        }
        target.parentFile.mkdirs()
        val partial = File(target.parentFile, "$whisperModelName.part")
        if (partial.exists()) partial.delete()
        logger.lifecycle("Downloading $url (~31 MB, one time)...")
        var connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        // Hugging Face redirects to a CDN host; follow it manually because the JDK
        // drops redirects that cross protocols.
        var redirects = 0
        while (connection.responseCode in 300..399 && redirects < 5) {
            val location = connection.getHeaderField("Location") ?: break
            connection.disconnect()
            connection = URI(location).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            redirects++
        }
        check(connection.responseCode == 200) {
            "Download failed with HTTP ${connection.responseCode}. Check your connection and re-run."
        }
        connection.inputStream.use { input ->
            FileOutputStream(partial).use { output -> input.copyTo(output, 1 shl 16) }
        }
        connection.disconnect()
        val actual = partial.sha256()
        check(actual == expected) {
            partial.delete()
            "Checksum mismatch for $whisperModelName.\nexpected $expected\nactual   $actual"
        }
        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "Could not move downloaded model into place." }
        logger.lifecycle("Whisper model ready (${target.length() / 1_048_576} MB).")
    }
}

android {
    namespace = "com.speak.app"
    compileSdk = 37
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.speak.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // A 768 MB int4 model is not viable in a 32-bit address space, and every
            // phone with 6 GB of RAM is arm64. Add "x86_64" here if you want to run
            // this on an emulator.
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-O3", "-fvisibility=hidden", "-ffunction-sections", "-fdata-sections")
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DCMAKE_BUILD_TYPE=Release",
                    // Shared across ABIs and cacheable in CI.
                    "-DFETCHCONTENT_BASE_DIR=${rootProject.layout.buildDirectory.get().asFile.absolutePath}/native-deps"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    signingConfigs {
        // Release signing is driven entirely by environment variables so that no
        // keystore, password or alias is ever committed. See docs/RELEASE_SIGNING.md.
        val keystorePath = System.getenv("SPEAK_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank() && File(keystorePath).isFile) {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = System.getenv("SPEAK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SPEAK_KEY_ALIAS")
                keyPassword = System.getenv("SPEAK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // The model must stay uncompressed so whisper.cpp can stream it straight
        // out of the APK without inflating 31 MB into RAM first.
        noCompress += listOf("bin", "gguf")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Make sure the model is in place before assets are merged into the APK.
tasks.named("preBuild").configure { dependsOn(fetchWhisperModel) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
