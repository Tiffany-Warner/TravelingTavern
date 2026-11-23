import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The session lease is bundled from the TravelingTavern-Extension submodule (pinned tag), never
// committed here. See context/specs/session-lease-app-spec.md → A2.
val sessionLeaseScript: File = rootProject.file("extension/session-lease.js")
if (!sessionLeaseScript.isFile) {
    throw GradleException("session-lease.js not found. Run: git submodule update --init")
}
val leaseVersion: String =
    Regex("""const\s+LEASE_VERSION\s*=\s*(\d+)\s*;""").find(sessionLeaseScript.readText())?.groupValues?.get(1)
        ?: throw GradleException("LEASE_VERSION not found in extension/session-lease.js")

// The leaseTest build talks to exactly one server, the test install, read from the gitignored
// local.properties so its address never appears in committed files (A9).
val leaseTestServerUrl: String? = rootProject.file("local.properties").takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
    ?.getProperty("leaseTest.serverUrl")?.trim()?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.tiffles.travelingtavern"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.tiffles.travelingtavern"
        minSdk = 31
        targetSdk = 36
        // See "Versioning" in CLAUDE.md
        versionCode = 46
        versionName = "0.1.20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The app checks that its page runs this lease code (A2)
        buildConfigField("int", "LEASE_VERSION", leaseVersion)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Installs beside the real app for the lease checklists on the test install (A9)
        create("leaseTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".leasetest"
            // Empty only when unset, and then packaging fails (checkLeaseTestServerUrl below).
            // A URL has no quotes or backslashes, so it goes into the Java literal as is.
            buildConfigField("String", "LEASE_TEST_SERVER_URL", "\"${leaseTestServerUrl.orEmpty()}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates WebView debugging
        buildConfig = true
    }
}

/** Copies the submodule's session-lease.js into generated assets, served as `web/session-lease.js`. */
abstract class CopySessionLeaseTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val leaseScript: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val webDir = outputDir.get().asFile.resolve("web")
        webDir.deleteRecursively()
        webDir.mkdirs()
        leaseScript.get().asFile.copyTo(webDir.resolve("session-lease.js"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val copyTask = tasks.register<CopySessionLeaseTask>(
            "copy${variant.name.replaceFirstChar { it.uppercase() }}SessionLease"
        ) {
            leaseScript.set(sessionLeaseScript)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyTask, CopySessionLeaseTask::outputDir)
    }
}

// Fails only when a leaseTest APK is packaged, so unit tests and lint of that variant still
// run without local.properties (CI).
val checkLeaseTestServerUrl = tasks.register("checkLeaseTestServerUrl") {
    val url = leaseTestServerUrl
    doLast {
        if (url == null) {
            throw GradleException("Set leaseTest.serverUrl in local.properties to the test install's URL")
        }
    }
}
tasks.matching { it.name == "packageLeaseTest" }.configureEach { dependsOn(checkLeaseTestServerUrl) }

dependencies {
    // AndroidX core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // WebView, settings, network, images, JSON
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.tink.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}