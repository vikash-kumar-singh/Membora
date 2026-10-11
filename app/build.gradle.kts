import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val firebaseConfigured = file("google-services.json").exists()
val releaseSigningProperties = Properties()
val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
if (releaseSigningPropertiesFile.isFile) {
    releaseSigningPropertiesFile.inputStream().use(releaseSigningProperties::load)
}

fun releaseSigningValue(propertyName: String, environmentName: String): String =
    releaseSigningProperties.getProperty(propertyName)?.trim()?.takeIf(String::isNotEmpty)
        ?: providers.environmentVariable(environmentName).orNull?.trim().orEmpty()

val releaseStoreFile = releaseSigningValue("storeFile", "MEMBORA_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "MEMBORA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "MEMBORA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "MEMBORA_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all(String::isNotBlank)
val imageKitPublicKey = providers.gradleProperty("IMAGEKIT_PUBLIC_KEY")
    .orElse(providers.environmentVariable("IMAGEKIT_PUBLIC_KEY"))
    .orElse("public_cNDr0TfiDtmw5Dgx+VVpRBo7+C4=")
val imageKitUrlEndpoint = providers.gradleProperty("IMAGEKIT_URL_ENDPOINT")
    .orElse(providers.environmentVariable("IMAGEKIT_URL_ENDPOINT"))
    .orElse("https://ik.imagekit.io/membora")

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

if (firebaseConfigured) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")
}

android {
    namespace = "com.membora.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.membora.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"https://api.membora.app/\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "IMAGEKIT_PUBLIC_KEY", imageKitPublicKey.get().asBuildConfigString())
        buildConfigField("String", "IMAGEKIT_URL_ENDPOINT", imageKitUrlEndpoint.get().trimEnd('/').asBuildConfigString())
        if (!firebaseConfigured) {
            resValue("string", "default_web_client_id", "")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Works on emulators and physical devices through `adb reverse tcp:8080 tcp:8080`.
            buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8080/\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails release packaging when the production signing identity is unavailable."
    inputs.property("releaseSigningConfigured", releaseSigningConfigured)
    inputs.property("releaseStoreFile", rootProject.file(releaseStoreFile).absolutePath)
    doLast {
        val configured = inputs.properties["releaseSigningConfigured"] as Boolean
        val configuredStoreFile = inputs.properties["releaseStoreFile"] as String
        check(configured && File(configuredStoreFile).isFile) {
            "Release signing is not configured. Add ignored keystore.properties or the MEMBORA_RELEASE_* environment variables."
        }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.jackson)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.imagekit.android)
    implementation(libs.ucrop)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.google.code.scanner)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
